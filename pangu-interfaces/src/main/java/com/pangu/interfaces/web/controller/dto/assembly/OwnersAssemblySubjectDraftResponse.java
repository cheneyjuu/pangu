// 关联业务：向办理页面返回业主大会会前表决事项草案的业务信息。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblySubjectDraft;

import java.time.Instant;

public record OwnersAssemblySubjectDraftResponse(
        Long draftId,
        String subjectType,
        String title,
        String content,
        Instant createTime
) {
    public static OwnersAssemblySubjectDraftResponse from(OwnersAssemblySubjectDraft draft) {
        return new OwnersAssemblySubjectDraftResponse(
                draft.draftId(), draft.subjectType().name(), draft.title(), draft.content(),
                draft.createTime());
    }
}
