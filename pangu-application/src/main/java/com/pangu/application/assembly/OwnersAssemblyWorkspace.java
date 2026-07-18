// 关联业务：聚合业主大会会前事项、材料和已锁定表决安排，供管理端按真实办理顺序展示。
package com.pangu.application.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyMaterial;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleSnapshot;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.assembly.OwnersAssemblySubjectDraft;
import com.pangu.domain.model.voting.VotingSubject;

import java.util.List;

public record OwnersAssemblyWorkspace(
        OwnersAssemblySession assembly,
        OwnersAssemblyPackage arrangement,
        OwnersAssemblyRuleSnapshot ruleSnapshot,
        List<OwnersAssemblySubjectDraft> draftSubjects,
        List<VotingSubject> formalSubjects,
        List<OwnersAssemblyMaterial> materials
) {
}
