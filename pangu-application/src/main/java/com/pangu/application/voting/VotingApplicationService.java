package com.pangu.application.voting;

import com.pangu.application.support.PayloadHasher;
import com.pangu.application.voting.command.SettleSubjectCommand;
import com.pangu.domain.model.attestation.AttestationPayload;
import com.pangu.domain.model.attestation.AttestationReceipt;
import com.pangu.domain.model.attestation.JudicialChainPort;
import com.pangu.domain.model.voting.Denominator;
import com.pangu.domain.model.voting.PartyRatioPolicyResolver;
import com.pangu.domain.model.voting.RatioCheckTrigger;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingDenominatorResolver;
import com.pangu.domain.model.voting.VotingEngineRouter;
import com.pangu.domain.model.voting.VotingResult;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VoteItemRepository;
import com.pangu.domain.repository.VotingResultRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 投票结算编排服务。
 *
 * <p>{@link #settle} 编排链路：
 * <ol>
 *   <li>{@code SELECT FOR UPDATE} 拿议题行锁；幂等校验已结算则直接返回；</li>
 *   <li>调用 {@link PartyRatioPolicyResolver}（{@code SETTLE} 触发点）执行党员比例
 *       断路器 —— 若已自然达标 / 候选人池萎缩，automaticly 撤销 waiver 回落 50%；</li>
 *   <li>调用 {@link VotingDenominatorResolver} 生成或读取分母快照（双重去重 + Merkle 摘要）；</li>
 *   <li>加载 {@link VoteItem} → 调度引擎 {@link VotingEngineRouter} 计算结果；</li>
 *   <li>{@link JudicialChainPort#attest} 落 outbox 司法链 stub，回填 tx hash；</li>
 *   <li>持久化 {@link VotingResult} 快照 + 议题 status -&gt; SETTLED。</li>
 * </ol>
 *
 * <p>本编排链路与议题类型无关：GENERAL / MAJOR / ELECTION 统一走上述六步，类型差异完全下沉到
 * {@link VotingEngineRouter}（ELECTION 由 router 还原 {@code ElectionSubject} 并挂载 APPROVED 候选人后
 * 路由进选举引擎，M3-3 接通）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VotingApplicationService {

    private final VotingSubjectRepository subjectRepository;
    private final VoteItemRepository voteItemRepository;
    private final VotingResultRepository resultRepository;
    private final VotingDenominatorResolver denominatorResolver;
    private final PartyRatioPolicyResolver partyRatioPolicyResolver;
    private final VotingEngineRouter engineRouter;
    private final JudicialChainPort judicialChainPort;
    private final PayloadHasher payloadHasher;

    /**
     * 触发议题结算。幂等：重复调用对已 SETTLED 的议题直接返回历史快照。
     *
     * <p>事务边界：整段（议题行锁、断路器、分母解析、结果落库、status 翻转）共享一个事务。
     * 司法链 stub 调用本身不做远程 IO（仅落 outbox），因此可以放在事务内。
     */
    @Transactional
    public VotingResultRepository.Snapshot settle(SettleSubjectCommand cmd) {
        // 1. 行锁 + 幂等校验
        VotingSubject subject = subjectRepository.findByIdForUpdate(cmd.subjectId())
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + cmd.subjectId()));
        if (subject.getStatus() == SubjectStatus.SETTLED) {
            log.info("Subject already settled, returning historical snapshot subjectId={}", cmd.subjectId());
            return resultRepository.findBySubjectId(cmd.subjectId())
                    .orElseThrow(() -> new VotingApplicationException(
                            VotingApplicationException.Reason.SUBJECT_ALREADY_SETTLED,
                            "议题状态为 SETTLED 但结果快照缺失，数据损坏"));
        }
        if (subject.getStatus() != SubjectStatus.VOTING && subject.getStatus() != SubjectStatus.CLOSED) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_VOTING,
                    "议题不在可结算状态 status=" + subject.getStatus());
        }

        // 2. 党员比例断路器（SETTLE 触发点）
        Optional<BigDecimal> ratioOverride =
                partyRatioPolicyResolver.resolveRatio(cmd.subjectId(), RatioCheckTrigger.SETTLE);
        BigDecimal effectiveRatio = ratioOverride.orElse(new BigDecimal("0.50"));
        subject.setPartyRatioFloor(effectiveRatio);

        // 3. 分母快照
        Denominator denom;
        try {
            denom = denominatorResolver.resolve(subject);
        } catch (RuntimeException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.DENOMINATOR_RESOLVE_FAILED,
                    "分母快照生成失败 subjectId=" + cmd.subjectId(), e);
        }

        // 4. 加载有效投票
        List<VoteItem> votes = voteItemRepository.findValidVotes(cmd.subjectId());

        // 5. 引擎结算（按议题类型路由）
        VotingResult<? extends VotingSubject> result;
        try {
            result = engineRouter.settle(subject, votes, denom);
        } catch (VotingEngineRouter.UnsupportedSubjectTypeException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_TYPE_NOT_SUPPORTED,
                    e.getMessage(), e);
        }

        // 6. 司法链 stub 上链
        int newSettleVersion = resultRepository.findBySubjectId(cmd.subjectId())
                .map(s -> s.statisticsVersion() + 1)
                .orElse(1);
        String payloadJson = serializeResult(result, effectiveRatio, denom, newSettleVersion);
        String localHash = PayloadHasher.sha256Hex(payloadJson);
        AttestationPayload payload = new AttestationPayload(
                "VOTING_RESULT_ATTEST",
                cmd.subjectId(),
                subject.getTenantId(),
                localHash,
                buildBusinessPayload(result, effectiveRatio, denom, newSettleVersion),
                Instant.now());
        AttestationReceipt receipt;
        try {
            receipt = judicialChainPort.attest(payload);
        } catch (RuntimeException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.ATTESTATION_FAILED,
                    "司法链 stub 上链失败 subjectId=" + cmd.subjectId(), e);
        }

        // 7. 写入结果快照
        VotingResultRepository.Snapshot snapshot = VotingResultRepository.Snapshot.from(
                result, newSettleVersion, payloadJson, denom.snapshotId(), receipt.txHash());
        resultRepository.upsert(snapshot);

        // 8. 议题 status -> SETTLED（乐观锁）
        int updated = subjectRepository.updateStatus(
                cmd.subjectId(), SubjectStatus.SETTLED.getDbValue(), subject.getVersion());
        if (updated != 1) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CONCURRENT_SETTLEMENT,
                    "议题在结算过程中被并发修改 subjectId=" + cmd.subjectId());
        }

        log.info("Subject settled subjectId={} type={} statisticsVersion={} passed={} txHash={}",
                cmd.subjectId(), subject.getSubjectType(), newSettleVersion,
                snapshot.passed(), receipt.txHash());

        return snapshot;
    }

    /**
     * 极简 payload 序列化：稳定有序键 + 字符串拼接。本期不引入 jackson 是因为
     * application 层尽量保持框架轻量；后续若 payload 形态变复杂再升级。
     */
    private String serializeResult(VotingResult<? extends VotingSubject> result,
                                    BigDecimal effectiveRatio,
                                    Denominator denom,
                                    int statisticsVersion) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        appendJson(sb, "subjectId", result.getSubject().getSubjectId()); sb.append(',');
        appendJson(sb, "subjectType", result.getSubject().getSubjectType() == null
                ? null : result.getSubject().getSubjectType().name()); sb.append(',');
        appendJson(sb, "statisticsVersion", statisticsVersion); sb.append(',');
        appendJson(sb, "totalArea", result.getTotalArea() == null ? null : result.getTotalArea().toPlainString()); sb.append(',');
        appendJson(sb, "totalOwnerCount", result.getTotalOwnerCount()); sb.append(',');
        appendJson(sb, "participatingArea", result.getParticipatingArea() == null ? null : result.getParticipatingArea().toPlainString()); sb.append(',');
        appendJson(sb, "participatingOwnerCount", result.getParticipatingOwnerCount()); sb.append(',');
        appendJson(sb, "quorumSatisfied", result.isQuorumSatisfied()); sb.append(',');
        appendJson(sb, "passed", result.isPassed()); sb.append(',');
        appendJson(sb, "supportArea", result.getSupportArea() == null ? null : result.getSupportArea().toPlainString()); sb.append(',');
        appendJson(sb, "supportOwnerCount", result.getSupportOwnerCount()); sb.append(',');
        appendJson(sb, "effectivePartyRatioFloor", effectiveRatio.toPlainString()); sb.append(',');
        appendJson(sb, "denominatorSnapshotId", denom.snapshotId()); sb.append(',');
        appendJson(sb, "denominatorAggregateHash", denom.snapshotHash());
        sb.append('}');
        return sb.toString();
    }

    private Map<String, Object> buildBusinessPayload(VotingResult<? extends VotingSubject> result,
                                                      BigDecimal effectiveRatio,
                                                      Denominator denom,
                                                      int statisticsVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subjectId", result.getSubject().getSubjectId());
        payload.put("subjectType", result.getSubject().getSubjectType() == null
                ? null : result.getSubject().getSubjectType().name());
        payload.put("statisticsVersion", statisticsVersion);
        payload.put("passed", result.isPassed());
        payload.put("quorumSatisfied", result.isQuorumSatisfied());
        payload.put("effectivePartyRatioFloor", effectiveRatio.toPlainString());
        payload.put("denominatorSnapshotId", denom.snapshotId());
        payload.put("denominatorAggregateHash", denom.snapshotHash());
        return payload;
    }

    private void appendJson(StringBuilder sb, String key, Object value) {
        sb.append('"').append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append('"').append(escape(value.toString())).append('"');
        }
    }

    private String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
