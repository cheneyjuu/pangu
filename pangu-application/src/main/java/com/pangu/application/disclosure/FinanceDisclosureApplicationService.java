package com.pangu.application.disclosure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pangu.application.disclosure.command.ComposeCommand;
import com.pangu.application.disclosure.command.CompareCommand;
import com.pangu.application.disclosure.command.LockAndPublishCommand;
import com.pangu.application.handover.HandoverCircuitBreaker;
import com.pangu.application.lock.GovernanceLockApplicationException;
import com.pangu.application.lock.GovernanceLockApplicationService;
import com.pangu.application.lock.command.LockCommand;
import com.pangu.application.support.ApplicationRoleGuard;
import com.pangu.application.support.LockedTransactionTemplate;
import com.pangu.application.support.PayloadHasher;
import com.pangu.application.support.StateMutationTemplate;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.lock.DistributedLockTemplate;
import com.pangu.domain.model.disclosure.DisclosureDiff;
import com.pangu.domain.model.disclosure.DisclosureType;
import com.pangu.domain.model.disclosure.FinanceDisclosureSnapshot;
import com.pangu.domain.model.disclosure.FundLedgerSnapshotData;
import com.pangu.domain.model.lock.GovernanceLock;
import com.pangu.domain.model.lock.LockEntityType;
import com.pangu.domain.repository.DisclosureCompareRepository;
import com.pangu.domain.repository.DisclosureCompareRepository.CompareRecord;
import com.pangu.domain.repository.FinanceDisclosureRepository;
import com.pangu.domain.repository.FinanceDisclosureRepository.DuplicateSnapshotException;
import com.pangu.domain.repository.FinanceDisclosureRepository.OptimisticLockException;
import com.pangu.domain.repository.FundLedgerQueryGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 财务公示编排服务（M2-3）。
 *
 * <p>四条 use case：
 * <ol>
 *   <li>{@link #compose(ComposeCommand)} 聚合 → 落 DRAFT 快照（Redis 红线锁串行化）；</li>
 *   <li>{@link #lockAndPublish(LockAndPublishCommand)} 单事务推进 DRAFT → LOCKED → PUBLISHED，
 *       内部委派 {@link GovernanceLockApplicationService#lock(LockCommand)} 取得治理锁；</li>
 *   <li>{@link #compare(CompareCommand)} 计算 W/R/N 差分并落 audit 表；</li>
 *   <li>{@link #getReadablePublishedSnapshot} 业主侧 GET，校验 tenant + status=PUBLISHED。</li>
 * </ol>
 *
 * <p><b>本期边界</b>：仅 {@link DisclosureType#MAINTENANCE_FUND} 真正可用；
 * 走 {@link DisclosureType#COMMON_FUND} 直接抛 {@code DISCLOSURE_TYPE_NOT_SUPPORTED}（HTTP 409）。
 *
 * <p><b>trigger 9 陷阱</b>：{@link FinanceDisclosureSnapshot#markLocked(Long)} 已把
 * status / governanceLockId / lockedAt 同步落到聚合根，repo.update 会一次 UPDATE 全部下推；
 * 不能拆分成两次 UPDATE，否则触发「LOCKED 状态必须挂治理锁 governance_lock_id」反弹。
 */
@Slf4j
@Service
public class FinanceDisclosureApplicationService {

    private static final Duration COMPOSE_LOCK_TTL = Duration.ofSeconds(30);
    private static final Set<String> COMPOSE_ROLES = Set.of("COMMITTEE_DIRECTOR", "COMMUNITY_ADMIN");
    private static final Set<String> AUDIT_ROLES = Set.of("GOV_SUPER_ADMIN", "COMMUNITY_ADMIN");
    private static final String PUBLISH_ROLE = "COMMITTEE_DIRECTOR";
    /** t_fund_ledger_entry.direction=1：维修资金入账。 */
    private static final int LEDGER_DIRECTION_INFLOW = 1;
    /** t_fund_ledger_entry.direction=2：维修资金出账。 */
    private static final int LEDGER_DIRECTION_OUTFLOW = 2;

    /** 内部 canonical mapper：保证 hash / 持久化 JSON 在不同 JVM 上一致。 */
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final FinanceDisclosureRepository disclosureRepository;
    private final DisclosureCompareRepository compareRepository;
    private final FundLedgerQueryGateway ledgerQueryGateway;
    private final GovernanceLockApplicationService lockApplicationService;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;
    private final DisclosureDiffCalculator diffCalculator;
    private final HandoverCircuitBreaker handoverCircuitBreaker;
    private final UserContextHolder userContextHolder;

    public FinanceDisclosureApplicationService(
            FinanceDisclosureRepository disclosureRepository,
            DisclosureCompareRepository compareRepository,
            FundLedgerQueryGateway ledgerQueryGateway,
            GovernanceLockApplicationService lockApplicationService,
            DistributedLockTemplate distributedLockTemplate,
            TransactionTemplate transactionTemplate,
            DisclosureDiffCalculator diffCalculator,
            HandoverCircuitBreaker handoverCircuitBreaker,
            UserContextHolder userContextHolder) {
        this.disclosureRepository = disclosureRepository;
        this.compareRepository = compareRepository;
        this.ledgerQueryGateway = ledgerQueryGateway;
        this.lockApplicationService = lockApplicationService;
        this.distributedLockTemplate = distributedLockTemplate;
        this.transactionTemplate = transactionTemplate;
        this.diffCalculator = diffCalculator;
        this.handoverCircuitBreaker = handoverCircuitBreaker;
        this.userContextHolder = userContextHolder;
    }

    // -----------------------------------------------------------------
    // 1) compose
    // -----------------------------------------------------------------

    /**
     * 聚合本期数据 → 落 DRAFT 快照。
     *
     * <p>三层并发防御：Redis 锁串行化同 (tenant, type, period) 的 compose 调用；
     * 事务内重读最大 statisticsVersion 决定下一版本号；DB 唯一索引兜底。
     */
    public FinanceDisclosureSnapshot compose(ComposeCommand cmd) {
        requireAnyRole(COMPOSE_ROLES, "仅业委会主任或居委会可聚合财务公示快照");
        requireMaintenanceFund(cmd.disclosureType());
        if (cmd.tenantId() == null || cmd.composedByUserId() == null
                || cmd.period() == null || cmd.period().isBlank()) {
            throw new IllegalArgumentException("ComposeCommand 字段不完整");
        }
        String key = String.format("lock:disclosure:tenant:%d:type:%s:period:%s",
                cmd.tenantId(), cmd.disclosureType().name(), cmd.period());
        return LockedTransactionTemplate.execute(
                distributedLockTemplate, transactionTemplate, key, COMPOSE_LOCK_TTL,
                () -> doComposeWithinTx(cmd),
                e -> new FinanceDisclosureApplicationException(
                        FinanceDisclosureApplicationException.Reason.SNAPSHOT_DUPLICATE,
                        "目标期间正在被另一线程聚合，请稍后再试", e));
    }

    private FinanceDisclosureSnapshot doComposeWithinTx(ComposeCommand cmd) {
        // 1) 取数（V2.2 只读 gateway）
        FundLedgerSnapshotData data = ledgerQueryGateway
                .composeMaintenanceFundData(cmd.tenantId(), cmd.period());
        if (data.accounts().isEmpty() && data.entrySummaries().isEmpty()) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.LEDGER_QUERY_EMPTY,
                    "tenantId=" + cmd.tenantId() + " period=" + cmd.period()
                            + " 期间内无任何账户与流水可聚合");
        }

        // 2) 决定 statisticsVersion
        int nextVersion = disclosureRepository
                .findLatestByPeriod(cmd.tenantId(), cmd.disclosureType(), cmd.period())
                .map(FinanceDisclosureSnapshot::getStatisticsVersion)
                .map(v -> v + 1)
                .orElse(1);

        // 3) canonical JSON + payload hash
        String payloadJson = serializeCanonical(data);
        String hashInput = payloadJson + "|" + cmd.tenantId() + "|"
                + cmd.disclosureType().name() + "|" + cmd.period() + "|" + nextVersion;
        String payloadHash = PayloadHasher.sha256Hex(hashInput);

        // 4) 聚合根 + 持久化（唯一索引兜底）
        FinanceDisclosureSnapshot snapshot = FinanceDisclosureSnapshot.compose(
                cmd.tenantId(), cmd.period(), cmd.disclosureType(),
                payloadJson, payloadHash, cmd.composedByUserId(), nextVersion);
        try {
            return disclosureRepository.insert(snapshot);
        } catch (DuplicateSnapshotException e) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.SNAPSHOT_DUPLICATE,
                    "财务公示快照已存在（唯一索引兜底） tenantId=" + cmd.tenantId()
                            + " type=" + cmd.disclosureType()
                            + " period=" + cmd.period(), e);
        }
    }

    // -----------------------------------------------------------------
    // 2) lockAndPublish
    // -----------------------------------------------------------------

    /**
     * 单事务推进 DRAFT → LOCKED → PUBLISHED。
     *
     * <p>调用方必须持有 {@code disclosure:publish} 权限（业委会主任）。
     */
    @Transactional
    public FinanceDisclosureSnapshot lockAndPublish(LockAndPublishCommand cmd) {
        requireRole(PUBLISH_ROLE, "仅业委会主任可锁定并发布财务公示");
        if (cmd.tenantId() == null || cmd.snapshotId() == null || cmd.userId() == null) {
            throw new IllegalArgumentException("LockAndPublishCommand 字段不完整");
        }
        FinanceDisclosureSnapshot snapshot = loadForUpdate(cmd.snapshotId());
        if (!snapshot.getTenantId().equals(cmd.tenantId())) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.SNAPSHOT_NOT_FOUND,
                    "快照与当前租户不匹配 snapshotId=" + cmd.snapshotId());
        }
        // 换届熔断（HANDOVER_LOCK）：业委会换届选举在途期间冻结财务公示发布，待选举结算/撤销后自动恢复。
        handoverCircuitBreaker.activeElectionSubjectId(cmd.tenantId()).ifPresent(electionId -> {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.HANDOVER_IN_PROGRESS,
                    "换届选举进行中，财务公示发布已熔断 electionSubjectId=" + electionId);
        });
        // 内部委派治理锁：lock 内部已含 Redis 锁 + 行锁 + 唯一索引三层；
        // GovernanceLockApplicationException 由 GlobalExceptionHandler 统一处理（透传）。
        GovernanceLock lock = lockApplicationService.lock(new LockCommand(
                cmd.tenantId(), LockEntityType.FINANCE_DISCLOSURE, snapshot.getSnapshotId(),
                cmd.userId(), snapshot.getPayloadHash()));

        StateMutationTemplate.advance(snapshot,
                current -> current.markLocked(lock.getLockId()),
                this::updateSnapshot,
                this::mapStateException);
        StateMutationTemplate.advance(snapshot,
                FinanceDisclosureSnapshot::markPublished,
                this::updateSnapshot,
                this::mapStateException);

        return snapshot;
    }

    // -----------------------------------------------------------------
    // 3) compare
    // -----------------------------------------------------------------

    /**
     * 比对两份快照并落 audit 行；幂等：同一 (prev, curr) 已有审计记录则直接返回旧 diff。
     */
    @Transactional
    public DisclosureDiff compare(CompareCommand cmd) {
        requireAnyRole(AUDIT_ROLES, "仅街道办或居委会可执行财务公示差分审计");
        if (cmd.tenantId() == null || cmd.prevSnapshotId() == null
                || cmd.currSnapshotId() == null || cmd.auditedByUserId() == null) {
            throw new IllegalArgumentException("CompareCommand 字段不完整");
        }
        if (cmd.prevSnapshotId().equals(cmd.currSnapshotId())) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.COMPARE_INVALID_PAIR,
                    "prev 与 curr 不能是同一个 snapshotId");
        }

        FinanceDisclosureSnapshot prev = loadById(cmd.prevSnapshotId());
        FinanceDisclosureSnapshot curr = loadById(cmd.currSnapshotId());

        if (!prev.getTenantId().equals(cmd.tenantId())
                || !curr.getTenantId().equals(cmd.tenantId())) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.COMPARE_INVALID_PAIR,
                    "prev/curr 必须属于同一租户");
        }
        if (prev.getDisclosureType() != curr.getDisclosureType()) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.COMPARE_INVALID_PAIR,
                    "prev/curr 公示类型不一致");
        }
        if (prev.getComposedAt() != null && curr.getComposedAt() != null
                && !prev.getComposedAt().isBefore(curr.getComposedAt())) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.COMPARE_INVALID_PAIR,
                    "prev.composedAt 必须早于 curr.composedAt");
        }

        // 幂等：已审计过直接复用
        var existing = compareRepository.findByPair(prev.getSnapshotId(), curr.getSnapshotId());
        if (existing.isPresent()) {
            return parseDiffOrEmpty(existing.get().diffJson());
        }

        DisclosureDiff diff = diffCalculator.diff(
                parseTree(prev.getDataPayload()), parseTree(curr.getDataPayload()));
        String diffJson = serializeCanonicalTree(diff);

        compareRepository.insert(new CompareRecord(
                null, cmd.tenantId(), prev.getSnapshotId(), curr.getSnapshotId(),
                diffJson, diff.writeCount(), diff.readCount(), diff.noChangeCount(),
                cmd.auditedByUserId(), Instant.now(), null));
        return diff;
    }

    // -----------------------------------------------------------------
    // 4) 业主侧 GET
    // -----------------------------------------------------------------

    /**
     * 业主侧拉单期：必须 status=PUBLISHED，且 tenant 一致。
     */
    public FinanceDisclosureSnapshot getReadablePublishedSnapshot(Long snapshotId, Long currentTenantId) {
        FinanceDisclosureSnapshot snapshot = loadById(snapshotId);
        if (currentTenantId == null || !currentTenantId.equals(snapshot.getTenantId())) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.SNAPSHOT_NOT_FOUND,
                    "快照与当前租户不匹配 snapshotId=" + snapshotId);
        }
        if (!snapshot.isReadableByOwner()) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.DISCLOSURE_NOT_PUBLISHED,
                    "快照尚未公示发布 snapshotId=" + snapshotId
                            + " status=" + snapshot.getStatus());
        }
        return snapshot;
    }

    /**
     * 业主首页读取当前小区最近一期已公示的专项维修资金收支摘要。
     *
     * <p>只从状态为 {@code PUBLISHED} 的快照解析，不把草稿、锁定快照或实时账目暴露给业主端。
     * 尚未公示时返回空结果，属于正常业务状态而非读取失败。
     */
    public Optional<PublishedMaintenanceFundSummary> getLatestPublishedMaintenanceFundSummary(
            Long currentTenantId) {
        if (currentTenantId == null) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.SNAPSHOT_NOT_FOUND,
                    "未识别到租户上下文，禁止读取财务公示摘要");
        }
        return disclosureRepository.findLatestPublished(
                        currentTenantId, DisclosureType.MAINTENANCE_FUND)
                .map(this::toMaintenanceFundSummary);
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private void requireMaintenanceFund(DisclosureType type) {
        if (type != DisclosureType.MAINTENANCE_FUND) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.DISCLOSURE_TYPE_NOT_SUPPORTED,
                    "本期仅支持 MAINTENANCE_FUND，未启用 type=" + type);
        }
    }

    private void requireRole(String expectedRole, String message) {
        requireAnyRole(Set.of(expectedRole), message);
    }

    private void requireAnyRole(Set<String> expectedRoles, String message) {
        ApplicationRoleGuard.requireAnyRole(userContextHolder, expectedRoles, message,
                msg -> new FinanceDisclosureApplicationException(
                        FinanceDisclosureApplicationException.Reason.DISCLOSURE_ROLE_FORBIDDEN, msg));
    }

    private FinanceDisclosureSnapshot loadById(Long snapshotId) {
        return disclosureRepository.findById(snapshotId)
                .orElseThrow(() -> new FinanceDisclosureApplicationException(
                        FinanceDisclosureApplicationException.Reason.SNAPSHOT_NOT_FOUND,
                        "财务公示快照不存在 snapshotId=" + snapshotId));
    }

    private FinanceDisclosureSnapshot loadForUpdate(Long snapshotId) {
        return disclosureRepository.findByIdForUpdate(snapshotId)
                .orElseThrow(() -> new FinanceDisclosureApplicationException(
                        FinanceDisclosureApplicationException.Reason.SNAPSHOT_NOT_FOUND,
                        "财务公示快照不存在 snapshotId=" + snapshotId));
    }

    private PublishedMaintenanceFundSummary toMaintenanceFundSummary(FinanceDisclosureSnapshot snapshot) {
        FundLedgerSnapshotData data = parseFundLedgerSnapshotData(snapshot.getDataPayload());
        return new PublishedMaintenanceFundSummary(
                snapshot.getSnapshotId(),
                snapshot.getPeriod(),
                sumByDirection(data, LEDGER_DIRECTION_INFLOW),
                sumByDirection(data, LEDGER_DIRECTION_OUTFLOW),
                snapshot.getPublishedAt());
    }

    private FundLedgerSnapshotData parseFundLedgerSnapshotData(String payload) {
        try {
            return CANONICAL_MAPPER.readValue(payload, FundLedgerSnapshotData.class);
        } catch (IOException e) {
            // 已发布快照的载荷由 compose 固化；解析失败必须显性失败，不能用零值掩盖公示数据异常。
            throw new IllegalStateException("已发布维修资金公示快照载荷无法解析", e);
        }
    }

    private BigDecimal sumByDirection(FundLedgerSnapshotData data, int direction) {
        return data.entrySummaries().stream()
                .filter(entry -> Objects.equals(entry.direction(), direction))
                .map(FundLedgerSnapshotData.EntrySummary::totalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void updateSnapshot(FinanceDisclosureSnapshot snapshot) {
        try {
            disclosureRepository.update(snapshot);
        } catch (OptimisticLockException e) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.SNAPSHOT_CONCURRENT_MODIFICATION,
                    "财务公示快照已被其他操作并发修改，请刷新后重试", e);
        }
    }

    private FinanceDisclosureApplicationException mapStateException(IllegalStateException e) {
        return new FinanceDisclosureApplicationException(
                FinanceDisclosureApplicationException.Reason.SNAPSHOT_INVALID_TRANSITION,
                e.getMessage() == null ? "非法状态流转" : e.getMessage(), e);
    }

    /** Canonical JSON 序列化（Object → String）。 */
    private String serializeCanonical(Object value) {
        try {
            return CANONICAL_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Canonical JSON 序列化失败", e);
        }
    }

    private String serializeCanonicalTree(Object value) {
        return serializeCanonical(value);
    }

    private JsonNode parseTree(String json) {
        try {
            return CANONICAL_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("快照 payload 不是合法 JSON", e);
        }
    }

    /** compare 幂等路径：把 audit 表里的 diff_json 反序列化回 DisclosureDiff（仅返回 W/R 数量与列表）。 */
    private DisclosureDiff parseDiffOrEmpty(String diffJson) {
        try {
            return CANONICAL_MAPPER.readValue(diffJson, DisclosureDiff.class);
        } catch (IOException e) {
            log.warn("audit diff_json 反序列化失败，返回空 diff: {}", e.getMessage());
            return new DisclosureDiff(java.util.List.of(), java.util.List.of(), 0);
        }
    }

    /** 仅 GovernanceLock 透传：committee/street 双签解锁场景目前不接入 disclosure，本期不暴露。 */
    @SuppressWarnings("unused")
    private static GovernanceLockApplicationException suppressUnusedImport() {
        return null;
    }
}
