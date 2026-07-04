package com.pangu.application.dispute;

import com.pangu.application.dispute.command.AddEvidenceCommand;
import com.pangu.application.dispute.command.ConcludeCommand;
import com.pangu.application.dispute.command.DecideCommand;
import com.pangu.application.dispute.command.EscalateCommand;
import com.pangu.application.dispute.command.GotoLitigationCommand;
import com.pangu.application.dispute.command.OpenCommand;
import com.pangu.application.dispute.command.StartReviewCommand;
import com.pangu.application.dispute.command.WithdrawCommand;
import com.pangu.application.support.StateMutationTemplate;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.dispute.Decision;
import com.pangu.domain.model.dispute.Dispute;
import com.pangu.domain.model.dispute.DisputeEvidence;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.domain.repository.DisputeDecisionRepository;
import com.pangu.domain.repository.DisputeDecisionRepository.DuplicateDecisionException;
import com.pangu.domain.repository.DisputeEvidenceRepository;
import com.pangu.domain.repository.DisputeRepository;
import com.pangu.domain.repository.DisputeRepository.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 业主异议 use case 编排服务。
 *
 * <p>编排 9 条 use case：{@link #open} / {@link #startReview} / {@link #decide} / {@link #escalate}
 * / {@link #gotoLitigation} / {@link #withdraw} / {@link #concludeFinal} / {@link #addEvidence}
 * / {@link #getDispute}。
 *
 * <p>并发模型：{@link #open} 无锁（业主独立资源）；其他 mutating 用乐观锁
 * （捕获 {@link OptimisticLockException} 转 {@link DisputeApplicationException.Reason#DISPUTE_CONCURRENT_MODIFICATION}）。
 *
 * <p>状态机校验由聚合根 {@link Dispute} 完成；本服务捕获 {@link IllegalStateException} → 转译。
 *
 * <p>{@link #decide} 严格按 "update 主表 → insert decision" 顺序执行（V2.8 trigger 11 兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeApplicationService {

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository evidenceRepository;
    private final DisputeDecisionRepository decisionRepository;
    private final UserContextHolder userContextHolder;

    /** 业主提起异议：返回带主键的聚合。 */
    @Transactional
    public Dispute open(OpenCommand cmd) {
        Dispute d = Dispute.open(
                cmd.tenantId(), cmd.raisedByOwnerId(), cmd.relatedPropertyOpid(), cmd.disputeKind(),
                cmd.relatedEntityType(), cmd.relatedEntityId(),
                cmd.businessPayloadJson());
        return disputeRepository.insert(d);
    }

    /** 行政机关受理：RAISED → UNDER_REVIEW_LEVEL_<currentLevel>。 */
    @Transactional
    public Dispute startReview(StartReviewCommand cmd) {
        return executeDisputeAction(cmd.disputeId(), Dispute::startReview);
    }

    /**
     * 出具决议：先 update 主表 status 转 DECIDED_LEVEL_N_<KIND>，再 insert decision。
     * 顺序由 V2.8 trigger 11 兜底（反向顺序会被 trigger 11 反弹）。
     */
    @Transactional
    public Decision decide(DecideCommand cmd) {
        Dispute d = loadForUpdate(cmd.disputeId());
        Decision decision;
        try {
            decision = d.decide(cmd.decisionKind(), cmd.decidedByUserId(),
                    cmd.content(), cmd.docUrl());
        } catch (IllegalStateException e) {
            throw mapStateException(e);
        }
        // 先更新主表 status → DECIDED_LEVEL_N_<KIND>
        updateWithOptimisticLock(d);
        // 再插入 decision；UK 冲突 → DECISION_DUPLICATE
        try {
            return decisionRepository.insert(new Decision(
                    null, d.getDisputeId(), decision.reviewLevel(), decision.decidedByUserId(),
                    decision.kind(), decision.content(), decision.docUrl(), decision.decidedAt()));
        } catch (DuplicateDecisionException e) {
            throw new DisputeApplicationException(
                    DisputeApplicationException.Reason.DECISION_DUPLICATE,
                    "该 dispute 在 level=" + decision.reviewLevel() + " 已存在决议", e);
        }
    }

    /** 业主升级到下一级。校验 owner 一致性 + Level 4 REJECTED 必须走 gotoLitigation。 */
    @Transactional
    public Dispute escalate(EscalateCommand cmd) {
        return executeOwnerAction(cmd.disputeId(), cmd.requestByOwnerId(), Dispute::escalate);
    }

    /** 业主走 Level 5 行政诉讼。 */
    @Transactional
    public Dispute gotoLitigation(GotoLitigationCommand cmd) {
        return executeOwnerAction(cmd.disputeId(), cmd.requestByOwnerId(), Dispute::gotoLitigation);
    }

    /** 业主撤回。 */
    @Transactional
    public Dispute withdraw(WithdrawCommand cmd) {
        return executeOwnerAction(cmd.disputeId(), cmd.requestByOwnerId(), Dispute::withdraw);
    }

    /** 业主接受最终决议。 */
    @Transactional
    public Dispute concludeFinal(ConcludeCommand cmd) {
        return executeOwnerAction(cmd.disputeId(), cmd.requestByOwnerId(), Dispute::concludeFinal);
    }

    /** 业主补充证据；终态拒绝。 */
    @Transactional
    public DisputeEvidence addEvidence(AddEvidenceCommand cmd) {
        Dispute d = loadForUpdate(cmd.disputeId());
        ensureOwner(d, cmd.requestByOwnerId());
        if (d.getStatus().isClosed()) {
            throw new DisputeApplicationException(
                    DisputeApplicationException.Reason.EVIDENCE_DISPUTE_CLOSED,
                    "异议已结案，无法补充证据 status=" + d.getStatus());
        }
        DisputeEvidence ev = new DisputeEvidence(
                null, d.getDisputeId(), cmd.evidenceKind(),
                cmd.contentUrl(), cmd.description(), Instant.now());
        return evidenceRepository.insert(ev);
    }

    /**
     * 查询单条异议（业主端）。业主只能查自己 raisedByOwnerId 的 dispute；
     * 不一致返回 NOT_FOUND（避免存在性泄漏，与 plan §6 一致）。
     */
    public Dispute getDispute(Long disputeId, Long currentOwnerId) {
        Dispute d = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeApplicationException(
                        DisputeApplicationException.Reason.DISPUTE_NOT_FOUND,
                        "异议不存在 disputeId=" + disputeId));
        if (currentOwnerId != null && !currentOwnerId.equals(d.getRaisedByOwnerId())) {
            // 业主端越权查询：返回 NOT_FOUND 而非 NOT_OWNER，避免存在性泄漏
            throw new DisputeApplicationException(
                    DisputeApplicationException.Reason.DISPUTE_NOT_FOUND,
                    "异议不存在 disputeId=" + disputeId);
        }
        return d;
    }

    /** 业主"我的异议"列表。 */
    public List<Dispute> listOwnerDisputes(Long tenantId, Long ownerId, int limit, int offset) {
        return disputeRepository.findByOwner(tenantId, ownerId, limit, offset);
    }

    /** 仲裁工作台。 */
    public List<Dispute> listJurisdiction(Long tenantId, Integer level, String status,
                                          int limit, int offset) {
        UserContext ctx = userContextHolder.current();
        if (ctx != null && ctx.dataScopeType() == DataScopeType.OWNER_GROUP) {
            return disputeRepository.findForJurisdictionByBuildingScopes(
                    ctx.authorizedBuildingScopes(), level, status, limit, offset);
        }
        return disputeRepository.findForJurisdiction(tenantId, level, status, limit, offset);
    }

    private Dispute loadForUpdate(Long disputeId) {
        return disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new DisputeApplicationException(
                        DisputeApplicationException.Reason.DISPUTE_NOT_FOUND,
                        "异议不存在 disputeId=" + disputeId));
    }

    private Dispute executeDisputeAction(Long disputeId,
                                         StateMutationTemplate.StateMutation<Dispute> mutation) {
        return StateMutationTemplate.execute(
                () -> loadForUpdate(disputeId),
                mutation,
                this::updateWithOptimisticLock,
                this::mapStateException);
    }

    private Dispute executeOwnerAction(Long disputeId,
                                       Long requestByOwnerId,
                                       StateMutationTemplate.StateMutation<Dispute> mutation) {
        return StateMutationTemplate.execute(
                () -> {
                    Dispute d = loadForUpdate(disputeId);
                    ensureOwner(d, requestByOwnerId);
                    return d;
                },
                mutation,
                this::updateWithOptimisticLock,
                this::mapStateException);
    }

    private void ensureOwner(Dispute d, Long requestByOwnerId) {
        if (requestByOwnerId == null || !requestByOwnerId.equals(d.getRaisedByOwnerId())) {
            throw new DisputeApplicationException(
                    DisputeApplicationException.Reason.DISPUTE_NOT_OWNER,
                    "当前用户非该异议发起人 disputeId=" + d.getDisputeId());
        }
    }

    private void updateWithOptimisticLock(Dispute d) {
        try {
            disputeRepository.update(d);
        } catch (OptimisticLockException e) {
            throw new DisputeApplicationException(
                    DisputeApplicationException.Reason.DISPUTE_CONCURRENT_MODIFICATION,
                    "异议已被并发修改，请刷新后重试 disputeId=" + d.getDisputeId(), e);
        }
    }

    private DisputeApplicationException mapStateException(IllegalStateException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("Only DECIDED_LEVEL_N_REJECTED can escalate")
                || msg.contains("can escalate")) {
            return new DisputeApplicationException(
                    DisputeApplicationException.Reason.DISPUTE_ESCALATE_REQUIRES_REJECTED, msg, e);
        }
        if (msg.contains("Level 4 REJECTED must go to LITIGATION")) {
            return new DisputeApplicationException(
                    DisputeApplicationException.Reason.DISPUTE_LEVEL_EXCEEDED, msg, e);
        }
        if (msg.contains("status level=") && msg.contains("mismatches currentReviewLevel")) {
            return new DisputeApplicationException(
                    DisputeApplicationException.Reason.DISPUTE_TYPE_LEVEL_MISMATCH, msg, e);
        }
        return new DisputeApplicationException(
                DisputeApplicationException.Reason.DISPUTE_INVALID_TRANSITION, msg, e);
    }
}
