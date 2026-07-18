// 关联业务：聚合业主大会当前办理上下文，供页面按业务步骤展示而无需输入内部标识。
package com.pangu.interfaces.web.controller.dto.assembly;

import java.util.List;

public record OwnersAssemblyWorkspaceResponse(
        OwnersAssemblySessionResponse assembly,
        OwnersAssemblyArrangementResponse arrangement,
        List<OwnersAssemblySubjectDraftResponse> draftSubjects,
        List<OwnersAssemblyFormalSubjectResponse> formalSubjects,
        List<OwnersAssemblyMaterialResponse> materials
) {
}
