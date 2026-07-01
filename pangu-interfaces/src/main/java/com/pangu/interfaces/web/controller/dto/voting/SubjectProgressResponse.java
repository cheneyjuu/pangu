package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.VotingProgress;
import com.pangu.domain.model.voting.VotingScope;

import java.math.BigDecimal;

/**
 * 议题双过半进度视图（M4-2）。
 *
 * <p>同时给出参与 / 赞成的绝对量与四个比例（4 位小数），并显式带出门槛分式
 * {@code thresholdNumerator/thresholdDenominator = 2/3}，前端进度环无需硬编码阈值。
 *
 * <p>{@code settled=true} 时数据来自法定结算快照，{@code supportArea/supportOwnerCount} 及其比例为空
 * （快照表无 support 列），前端需容忍 null。
 *
 * <p>{@code denominatorSnapshotId/denominatorMerkleRoot} 指向立项时冻结的分母快照与行级 Merkle root；
 * 存量或非选举议题未冻结时可为空。
 */
public record SubjectProgressResponse(
        Long subjectId,
        SubjectStatus status,
        VotingScope scope,
        Long scopeReferenceId,
        BigDecimal totalArea,
        long totalOwnerCount,
        BigDecimal participatingArea,
        long participatingOwnerCount,
        BigDecimal participatingAreaRatio,
        BigDecimal participatingOwnerRatio,
        BigDecimal supportArea,
        Long supportOwnerCount,
        BigDecimal supportAreaRatio,
        BigDecimal supportOwnerRatio,
        int thresholdNumerator,
        int thresholdDenominator,
        boolean quorumSatisfied,
        boolean settled,
        boolean passed,
        Long denominatorSnapshotId,
        String denominatorMerkleRoot
) {
    public static SubjectProgressResponse from(VotingProgress p) {
        boolean hasSupport = p.supportOwnerCount() != null;
        return new SubjectProgressResponse(
                p.subjectId(),
                p.status(),
                p.scope(),
                p.scopeReferenceId(),
                p.totalArea(),
                p.totalOwnerCount(),
                p.participatingArea(),
                p.participatingOwnerCount(),
                p.participatingAreaRatio(),
                p.participatingOwnerRatio(),
                p.supportArea(),
                p.supportOwnerCount(),
                hasSupport ? p.supportAreaRatio() : null,
                hasSupport ? p.supportOwnerRatio() : null,
                2,
                3,
                p.quorumSatisfied(),
                p.settled(),
                p.passed(),
                p.denominatorSnapshotId(),
                p.denominatorMerkleRoot());
    }
}
