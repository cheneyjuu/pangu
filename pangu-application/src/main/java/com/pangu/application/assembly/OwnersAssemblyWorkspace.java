// 关联业务：聚合业主大会会前事项、材料和已锁定表决安排，供管理端按真实办理顺序展示。
package com.pangu.application.assembly;

import com.pangu.application.voting.VotingDecisionResultProjector;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleSnapshot;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.assembly.OwnersAssemblySubjectDraft;
import com.pangu.domain.model.voting.SubjectType;

import java.math.BigDecimal;
import java.util.List;

public record OwnersAssemblyWorkspace(
        OwnersAssemblySession assembly,
        OwnersAssemblyPackage arrangement,
        OwnersAssemblyRuleSnapshot ruleSnapshot,
        List<OwnersAssemblySubjectDraft> draftSubjects,
        List<FormalSubject> formalSubjects,
        List<OwnersAssemblyMaterial> materials
) {

    /** 管理端正式事项投影只返回汇总结果，不泄露逐户票面。 */
    public record FormalSubject(
            Long subjectId,
            SubjectType subjectType,
            String title,
            String content,
            String status,
            Result result
    ) {
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
    }
}
