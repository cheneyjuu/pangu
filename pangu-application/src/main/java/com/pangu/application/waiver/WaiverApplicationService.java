package com.pangu.application.waiver;

import com.pangu.application.support.PayloadHasher;
import com.pangu.application.waiver.command.CommitteeReviewCommand;
import com.pangu.application.waiver.command.RevokeWaiverCommand;
import com.pangu.application.waiver.command.StreetReviewCommand;
import com.pangu.application.waiver.command.SubmitDraftCommand;
import com.pangu.domain.lock.DistributedLockTemplate;
import com.pangu.domain.lock.DistributedLockTemplate.DistributedLockAcquisitionException;
import com.pangu.domain.model.voting.CandidatePoolSnapshot;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.waiver.PartyRatioWaiver;
import com.pangu.domain.policy.ReasonTextPolicy;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import com.pangu.domain.repository.PartyRatioWaiverRepository;
import com.pangu.domain.repository.PartyRatioWaiverRepository.DuplicateActiveWaiverException;
import com.pangu.domain.repository.PartyRatioWaiverRepository.OptimisticLockException;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * 党员比例放宽申请编排服务。
 *
 * <p>编排四条 use case：
 * <ol>
 *   <li>{@link #submitDraft} 居委会发起 + 直接进入初审（DRAFT → PENDING_COMMITTEE）；</li>
 *   <li>{@link #reviewByCommittee} 居委会初审（通过 → PENDING_STREET，驳回 → REJECTED）；</li>
 *   <li>{@link #reviewByStreet} 街道办终审（通过 → APPROVED + 锁 payloadHash + 上链 stub，驳回 → REJECTED）；</li>
 *   <li>{@link #revoke} 人工撤销（DRAFT / PENDING_COMMITTEE / PENDING_STREET / APPROVED → REVOKED）。</li>
 * </ol>
 *
 * <p>{@link #submitDraft} 的并发与去重防御为三层：
 * <pre>
 *   ① Redis 红线锁（{@code lock:waiver:tenant:{T}:subject:{S}}）—— 单进程串行化；
 *   ② DB SELECT FOR UPDATE     —— 跨进程串行化、防止两个红线锁拿在不同实例；
 *   ③ 部分唯一索引             —— 兜底，捕获 DuplicateKeyException 转友好错误码。
 * </pre>
 *
 * <p>本服务对外只抛 {@link WaiverApplicationException}；状态机与 dept_type 校验由聚合根
 * {@link PartyRatioWaiver} 完成，本服务捕获 {@link IllegalStateException} 转译。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaiverApplicationService {

    /** Redis 锁 TTL（远大于单次事务执行时间，但短于业务上限以防死锁）。 */
    private static final Duration WAIVER_LOCK_TTL = Duration.ofSeconds(30);

    private final PartyRatioWaiverRepository waiverRepository;
    private final VotingSubjectRepository subjectRepository;
    private final ElectionCandidateRegistry candidateRegistry;
    private final ReasonTextPolicy reasonTextPolicy;
    private final DistributedLockTemplate lockTemplate;
    private final PayloadHasher payloadHasher;
    private final TransactionTemplate transactionTemplate;

    /**
     * 居委会发起申请并直接进入初审待审。
     *
     * <p>实现要点：
     * <ul>
     *   <li>聚合根 {@link PartyRatioWaiver#draft} 完成构造期参数校验；</li>
     *   <li>{@link PartyRatioWaiver#transitionTo} DRAFT → PENDING_COMMITTEE；</li>
     *   <li>分母快照（partyPoolSize / totalEligibleSize）使用「申请瞬间」候选人池快照，
     *       后续断路器对账以本快照为基准。</li>
     * </ul>
     */
    public PartyRatioWaiver submitDraft(SubmitDraftCommand cmd) {
        // 0. dept_type 校验前置（避免无谓抢锁）
        if (cmd.initiatorDeptType() == null || cmd.initiatorDeptType() != 2) {
            throw new WaiverApplicationException(
                    WaiverApplicationException.Reason.INITIATOR_NOT_COMMITTEE,
                    "申请发起人必须是居委会用户（dept_type=2），当前=" + cmd.initiatorDeptType());
        }

        // 1. 水文检测（拒在锁外，避免长事务持锁）
        ReasonTextPolicy.ValidationResult vr = reasonTextPolicy.validate(cmd.reasonText());
        if (!vr.valid()) {
            throw new WaiverApplicationException(vr.failureReason(), vr.message());
        }

        // 2. 议题校验：存在 + 租户匹配 + 状态允许
        VotingSubject subject = subjectRepository.findById(cmd.subjectId())
                .orElseThrow(() -> new WaiverApplicationException(
                        WaiverApplicationException.Reason.SUBJECT_NOT_ELIGIBLE,
                        "议题不存在 subjectId=" + cmd.subjectId()));
        if (!subject.getTenantId().equals(cmd.tenantId())) {
            throw new WaiverApplicationException(
                    WaiverApplicationException.Reason.TENANT_MISMATCH,
                    "申请人租户与议题租户不一致");
        }

        String lockKey = String.format("lock:waiver:tenant:%d:subject:%d",
                cmd.tenantId(), cmd.subjectId());

        // 锁外发起 → 锁内开事务 → 事务提交后释放锁。
        // 用 TransactionTemplate 而非 @Transactional 是为绕开 Spring 自调用代理失效问题：
        // 在锁回调里通过 this.method() 调用一个带 @Transactional 的方法，AOP advice 不会被触发。
        try {
            return lockTemplate.executeWithLock(lockKey, WAIVER_LOCK_TTL, () ->
                    transactionTemplate.execute(status -> doSubmitDraftWithinTx(cmd)));
        } catch (DistributedLockAcquisitionException e) {
            // 锁被另一线程占用 → 语义上等同于「同议题已有一份正在提交/活跃」
            // 三层防御里第①层即被拦下，对客户端统一为 WAIVER_ALREADY_PENDING 友好错误码。
            throw new WaiverApplicationException(
                    WaiverApplicationException.Reason.WAIVER_ALREADY_PENDING,
                    "本议题已有另一份放宽申请正在提交中，请稍后再试", e);
        }
    }

    /**
     * 锁内事务体：行锁查询 → 候选人池快照 → 聚合根状态机 → 持久化。
     * 抛出的异常会让 {@link TransactionTemplate} 触发回滚。
     */
    private PartyRatioWaiver doSubmitDraftWithinTx(SubmitDraftCommand cmd) {
        // 行锁查询同议题活跃 waiver
        Optional<PartyRatioWaiver> existing =
                waiverRepository.findActiveBySubjectIdForUpdate(cmd.subjectId());
        if (existing.isPresent()) {
            throw new WaiverApplicationException(
                    WaiverApplicationException.Reason.WAIVER_ALREADY_PENDING,
                    "本议题已存在活跃的放宽申请 waiverId=" + existing.get().getWaiverId());
        }

        // 候选人池快照（COUNT 落定本次申请的分母基线）
        CandidatePoolSnapshot pool = candidateRegistry.countActivePool(cmd.subjectId());

        // 聚合根构造 + 状态流转
        PartyRatioWaiver waiver = PartyRatioWaiver.draft(
                cmd.subjectId(), cmd.tenantId(), cmd.initiatorUserId(),
                cmd.requestedRatio(), pool.partyCount(), pool.eligibleCount(),
                cmd.reasonText(), cmd.reasonEvidenceKeys());
        try {
            waiver.transitionTo(com.pangu.domain.model.waiver.WaiverStatus.PENDING_COMMITTEE);
        } catch (IllegalStateException e) {
            throw new WaiverApplicationException(
                    WaiverApplicationException.Reason.INVALID_TRANSITION, e.getMessage(), e);
        }

        // 持久化（兜底唯一索引）
        try {
            return waiverRepository.insert(waiver);
        } catch (DuplicateActiveWaiverException e) {
            throw new WaiverApplicationException(
                    WaiverApplicationException.Reason.WAIVER_ALREADY_PENDING,
                    "本议题已存在活跃的放宽申请（唯一索引兜底）", e);
        }
    }

    /** 居委会初审。 */
    @Transactional
    public PartyRatioWaiver reviewByCommittee(CommitteeReviewCommand cmd) {
        PartyRatioWaiver waiver = loadForUpdate(cmd.waiverId());
        try {
            if (cmd.approve()) {
                waiver.approveByCommittee(cmd.approverUserId(), cmd.approverDeptType(), cmd.opinion());
            } else {
                waiver.reject(cmd.approverUserId(), cmd.approverDeptType(), cmd.opinion());
            }
        } catch (IllegalStateException e) {
            throw mapStateException(e);
        }
        try {
            waiverRepository.update(waiver);
        } catch (OptimisticLockException e) {
            throw new WaiverApplicationException(
                    WaiverApplicationException.Reason.CONCURRENT_MODIFICATION,
                    "Waiver 已被其他操作并发修改，请刷新后重试", e);
        }
        return waiver;
    }

    /**
     * 街道办终审。通过 → APPROVED 后立刻锁定 {@code local_payload_hash}（可重放摘要），
     * 后续异步上链由 outbox 流程驱动。
     */
    @Transactional
    public PartyRatioWaiver reviewByStreet(StreetReviewCommand cmd) {
        PartyRatioWaiver waiver = loadForUpdate(cmd.waiverId());
        try {
            if (cmd.approve()) {
                waiver.approveByStreet(cmd.approverUserId(), cmd.approverDeptType(), cmd.opinion());
                String hash = payloadHasher.hashWaiverApproval(waiver);
                waiver.lockLocalPayloadHash(hash);
            } else {
                waiver.reject(cmd.approverUserId(), cmd.approverDeptType(), cmd.opinion());
            }
        } catch (IllegalStateException e) {
            throw mapStateException(e);
        }
        try {
            waiverRepository.update(waiver);
        } catch (OptimisticLockException e) {
            throw new WaiverApplicationException(
                    WaiverApplicationException.Reason.CONCURRENT_MODIFICATION,
                    "Waiver 已被其他操作并发修改，请刷新后重试", e);
        }
        return waiver;
    }

    /** 人工撤销。 */
    @Transactional
    public PartyRatioWaiver revoke(RevokeWaiverCommand cmd) {
        PartyRatioWaiver waiver = loadForUpdate(cmd.waiverId());
        try {
            waiver.revokeManually();
        } catch (IllegalStateException e) {
            throw mapStateException(e);
        }
        try {
            waiverRepository.update(waiver);
        } catch (OptimisticLockException e) {
            throw new WaiverApplicationException(
                    WaiverApplicationException.Reason.CONCURRENT_MODIFICATION,
                    "Waiver 已被其他操作并发修改，请刷新后重试", e);
        }
        log.info("Waiver 已被 user={} 人工撤销 waiverId={}", cmd.operatorUserId(), cmd.waiverId());
        return waiver;
    }

    /** GET 详情（不加锁）。 */
    public Optional<PartyRatioWaiver> findById(Long waiverId) {
        return waiverRepository.findById(waiverId);
    }

    private PartyRatioWaiver loadForUpdate(Long waiverId) {
        return waiverRepository.findByIdForUpdate(waiverId)
                .orElseThrow(() -> new WaiverApplicationException(
                        WaiverApplicationException.Reason.WAIVER_NOT_FOUND,
                        "Waiver 不存在 waiverId=" + waiverId));
    }

    private WaiverApplicationException mapStateException(IllegalStateException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("部门类型不符")) {
            return new WaiverApplicationException(
                    WaiverApplicationException.Reason.APPROVER_DEPT_INVALID, msg, e);
        }
        if (msg.contains("终审与初审审批人不能为同一人")) {
            return new WaiverApplicationException(
                    WaiverApplicationException.Reason.APPROVER_CONFLICT, msg, e);
        }
        return new WaiverApplicationException(
                WaiverApplicationException.Reason.INVALID_TRANSITION, msg, e);
    }

    /** 仅供测试与监控 hooks，提供默认 ratio 常量。 */
    public static BigDecimal defaultRatioFloor() {
        return new BigDecimal("0.50");
    }
}
