// 关联业务：表达议题、选举、投票提交和结算用例的稳定失败原因。
package com.pangu.application.voting;

/**
 * 投票结算应用层异常。
 *
 * <p>映射到 web 层 {@code ElectionErrorCode} 由 Phase 6 完成；本期作为 application
 * 与 web 之间的契约，使用强类型 {@link Reason} 表达失败语义。
 */
public class VotingApplicationException extends RuntimeException {

    public enum Reason {
        SUBJECT_NOT_FOUND,
        SUBJECT_NOT_VOTING,
        SUBJECT_ALREADY_SETTLED,
        DENOMINATOR_RESOLVE_FAILED,
        SUBJECT_TYPE_NOT_SUPPORTED,
        ATTESTATION_FAILED,
        CONCURRENT_SETTLEMENT,

        // ========== M3-2 议题生命周期 + 投票提交新增 ==========
        /** ELECTION 类型立项被拒（M3-3 才放开）；MAJOR/GENERAL 与立项者角色不匹配也用此。 */
        PROPOSE_FORBIDDEN_FOR_TYPE,
        /** 议题不在 DRAFT 状态，不允许公示。 */
        SUBJECT_NOT_DRAFT,
        /** 议题不在 PUBLISHED 状态，不允许政府强撤。 */
        SUBJECT_NOT_PUBLISHED,
        /** 议题不在 VOTING 状态，业主不可投票。 */
        SUBJECT_NOT_VOTING_CASTABLE,
        /** opid 与当前 uid 不匹配（业主 A 用业主 B 的 opid 投票）。 */
        OPID_NOT_OWNED,
        /** opid 所在楼栋不在议题 scope 内（scope=BUILDING 议题）。 */
        OPID_OUT_OF_SCOPE,
        /** UNIQUE(subject_id, opid, target) 冲突；前端回 409。 */
        VOTE_ALREADY_CAST,
        /** 非选举线上表决未达到 L2 实名认证。 */
        AUTH_LEVEL_INSUFFICIENT,
        /** 撤回行为被拒（DRAFT 阶段非本人 / PUBLISHED 阶段非政府 / VOTING+ 一律拒）。 */
        CANCEL_FORBIDDEN,
        /** 议题状态在写入并发下被推翻（cancel/publish 乐观锁失败）。 */
        CONCURRENT_LIFECYCLE_MODIFICATION,

        // ========== M3-3 ELECTION 选举全流程新增 ==========
        /** ELECTION 立项未携带 maxWinners（应选名额）或 < 1。 */
        ELECTION_MAX_WINNERS_REQUIRED,
        /** ELECTION 投票缺 candidateId（targetId）。 */
        ELECTION_TARGET_REQUIRED,
        /** ELECTION 线上投票未达到 L3 活体实名认证。 */
        ELECTION_AUTH_LEVEL_INSUFFICIENT,
        /** 候选人不存在。 */
        CANDIDATE_NOT_FOUND,
        /** 候选人不可被投票（不属于本议题 / 非 APPROVED）。 */
        CANDIDATE_NOT_VOTABLE,
        /** 同一议题内重复提名同一 uid。 */
        CANDIDATE_ALREADY_NOMINATED,
        /** 资格审查并发冲突（候选人已不在期望的审查阶段）或非法状态迁移。 */
        CANDIDATE_REVIEW_CONFLICT,
        /** 当前角色不允许执行候选人审查动作。 */
        CANDIDATE_REVIEW_FORBIDDEN,
        /** 该 opid 在本选举已投满 maxWinners 票。 */
        VOTE_LIMIT_EXCEEDED,
        /** 议题不在可提名状态（仅 DRAFT/PUBLISHED 允许增改候选人名单）。 */
        SUBJECT_NOT_NOMINATABLE,
        /** ELECTION 议题公示前必须至少有 1 名 APPROVED 候选人。 */
        ELECTION_NO_APPROVED_CANDIDATE,
        /** 换届选举在途，新 GENERAL/MAJOR 立项被熔断（放行 ELECTION）。 */
        PROPOSE_FROZEN_HANDOVER,

        // ========== M5-B ELECTION 议题双签 ==========
        /** 议题不在居委会初审中。 */
        SUBJECT_NOT_PENDING_COMMITTEE,
        /** 议题不在街道办终审中。 */
        SUBJECT_NOT_PENDING_STREET,
        /** 审批驳回必须填写原因。 */
        REVIEW_REJECT_REASON_REQUIRED,
        /** E3：驳回必须携带 C1-C5 客观理由码。 */
        REJECT_REASON_CODE_REQUIRED,
        /** E3：驳回必须携带 JSONB 证据链。 */
        REJECT_EVIDENCE_REQUIRED
    }

    private final Reason reason;

    public VotingApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public VotingApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
