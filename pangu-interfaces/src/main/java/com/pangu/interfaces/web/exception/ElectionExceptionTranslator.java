package com.pangu.interfaces.web.exception;

import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.waiver.WaiverApplicationException;
import com.pangu.domain.policy.ReasonTextPolicy;

/**
 * 应用层异常 → web 层 {@link ElectionErrorCode} 翻译器。
 *
 * <p>设计动机：
 * <ul>
 *   <li>application 层仅依赖 domain，不能感知 web 层 ErrorCode；</li>
 *   <li>web 层 GlobalExceptionHandler 对 {@link WaiverApplicationException} /
 *       {@link VotingApplicationException} 进行集中翻译，业务层只需关心
 *       领域语义（{@code Reason} 枚举），web 表达由本类负责。</li>
 * </ul>
 *
 * <p>将翻译逻辑独立成类（而不是塞进 GlobalExceptionHandler）的原因：
 * 便于单元测试覆盖 Reason → ErrorCode 的全表映射，避免漏 case。
 */
public final class ElectionExceptionTranslator {

    private ElectionExceptionTranslator() {
    }

    /**
     * Waiver 应用层异常 → web 层 ErrorCode。优先使用细分的水文检测原因，
     * 退化到 {@link WaiverApplicationException.Reason} 枚举映射。
     */
    public static ElectionErrorCode translate(WaiverApplicationException ex) {
        ReasonTextPolicy.FailureReason textFailure = ex.getReasonTextFailure();
        if (textFailure != null) {
            return switch (textFailure) {
                case TOO_SHORT, NULL_OR_EMPTY, LOW_SUBSTANTIVE_RATIO -> ElectionErrorCode.WAIVER_REASON_TOO_SHORT;
                case HIGH_REPETITION -> ElectionErrorCode.WAIVER_REASON_TOO_REPETITIVE;
                case LOW_ENTROPY -> ElectionErrorCode.WAIVER_REASON_LOW_ENTROPY;
            };
        }
        return switch (ex.getReason()) {
            case WAIVER_ALREADY_PENDING -> ElectionErrorCode.WAIVER_ALREADY_PENDING;
            case WAIVER_NOT_FOUND -> ElectionErrorCode.WAIVER_NOT_FOUND;
            case INVALID_TRANSITION -> ElectionErrorCode.WAIVER_INVALID_TRANSITION;
            case TENANT_MISMATCH -> ElectionErrorCode.WAIVER_TENANT_MISMATCH;
            case SUBJECT_NOT_ELIGIBLE -> ElectionErrorCode.WAIVER_SUBJECT_NOT_ELIGIBLE;
            case CONCURRENT_MODIFICATION -> ElectionErrorCode.WAIVER_CONCURRENT_MODIFICATION;
            case INITIATOR_NOT_COMMITTEE -> ElectionErrorCode.WAIVER_INITIATOR_NOT_COMMITTEE;
            case APPROVER_DEPT_INVALID -> ElectionErrorCode.WAIVER_APPROVER_DEPT_INVALID;
            case APPROVER_CONFLICT -> ElectionErrorCode.WAIVER_APPROVER_CONFLICT;
            // REASON_TOO_SHORT / REASON_REJECTED 仅作为 textFailure 缺失时的兜底，
            // 正常路径都会带 textFailure 进入上面的分支
            case REASON_TOO_SHORT -> ElectionErrorCode.WAIVER_REASON_TOO_SHORT;
            case REASON_REJECTED -> ElectionErrorCode.WAIVER_REASON_REJECTED;
        };
    }

    /**
     * 投票结算应用层异常 → web 层 ErrorCode。
     */
    public static ElectionErrorCode translate(VotingApplicationException ex) {
        return switch (ex.getReason()) {
            case SUBJECT_NOT_FOUND -> ElectionErrorCode.SUBJECT_NOT_FOUND;
            case SUBJECT_NOT_VOTING -> ElectionErrorCode.SUBJECT_NOT_VOTING;
            case SUBJECT_ALREADY_SETTLED -> ElectionErrorCode.SUBJECT_ALREADY_SETTLED;
            case SUBJECT_TYPE_NOT_SUPPORTED -> ElectionErrorCode.SUBJECT_TYPE_NOT_SUPPORTED;
            case CONCURRENT_SETTLEMENT -> ElectionErrorCode.SUBJECT_CONCURRENT_SETTLEMENT;
            case DENOMINATOR_RESOLVE_FAILED -> ElectionErrorCode.DENOMINATOR_RESOLVE_FAILED;
            case ATTESTATION_FAILED -> ElectionErrorCode.ATTESTATION_FAILED;
            // M3-2 lifecycle + voting cast
            case SUBJECT_NOT_DRAFT -> ElectionErrorCode.SUBJECT_NOT_DRAFT;
            case SUBJECT_NOT_PUBLISHED -> ElectionErrorCode.SUBJECT_NOT_PUBLISHED;
            case SUBJECT_NOT_VOTING_CASTABLE -> ElectionErrorCode.SUBJECT_NOT_VOTING_CASTABLE;
            case PROPOSE_FORBIDDEN_FOR_TYPE -> ElectionErrorCode.PROPOSE_FORBIDDEN_FOR_TYPE;
            case CANCEL_FORBIDDEN -> ElectionErrorCode.CANCEL_FORBIDDEN;
            case CONCURRENT_LIFECYCLE_MODIFICATION -> ElectionErrorCode.LIFECYCLE_CONCURRENT_MODIFICATION;
            case AUTH_LEVEL_INSUFFICIENT -> ElectionErrorCode.AUTH_LEVEL_INSUFFICIENT;
            case OPID_NOT_OWNED -> ElectionErrorCode.OPID_NOT_OWNED;
            case OPID_OUT_OF_SCOPE -> ElectionErrorCode.OPID_OUT_OF_SCOPE;
            case VOTE_ALREADY_CAST -> ElectionErrorCode.VOTE_ALREADY_CAST;
        };
    }
}
