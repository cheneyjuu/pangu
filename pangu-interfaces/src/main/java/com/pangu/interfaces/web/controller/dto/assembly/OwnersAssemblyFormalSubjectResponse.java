// 关联业务：向办理页面返回已进入正式表决安排的事项摘要。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.application.assembly.OwnersAssemblyWorkspace;
import com.pangu.application.voting.VotingDecisionResultProjector;

import java.math.BigDecimal;

public record OwnersAssemblyFormalSubjectResponse(
        Long subjectId,
        String subjectType,
        String title,
        String content,
        String status,
        Result result
) {
    public static OwnersAssemblyFormalSubjectResponse from(OwnersAssemblyWorkspace.FormalSubject subject) {
        return new OwnersAssemblyFormalSubjectResponse(
                subject.subjectId(), subject.subjectType().name(), subject.title(), subject.content(), subject.status(),
                Result.from(subject.result()));
    }

    public record Result(
            boolean quorumSatisfied,
            boolean passed,
            BigDecimal totalArea,
            long totalOwnerCount,
            BigDecimal participatingArea,
            long participatingOwnerCount,
            BigDecimal supportArea,
            Long supportOwnerCount,
            BigDecimal againstArea,
            Long againstOwnerCount,
            BigDecimal abstainArea,
            Long abstainOwnerCount,
            VotingDecisionResultProjector.NonResponseSummary nonResponse
    ) {
        private static Result from(OwnersAssemblyWorkspace.Result result) {
            return result == null ? null : new Result(
                    result.quorumSatisfied(), result.passed(), result.totalArea(), result.totalOwnerCount(),
                    result.participatingArea(), result.participatingOwnerCount(),
                    result.supportArea(), result.supportOwnerCount(),
                    result.againstArea(), result.againstOwnerCount(),
                    result.abstainArea(), result.abstainOwnerCount(), result.nonResponse());
        }
    }
}
