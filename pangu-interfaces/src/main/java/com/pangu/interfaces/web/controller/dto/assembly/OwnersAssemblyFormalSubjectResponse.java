// 关联业务：向办理页面返回已进入正式表决安排的事项摘要。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.VotingSubject;

public record OwnersAssemblyFormalSubjectResponse(
        Long subjectId,
        String subjectType,
        String title,
        String status
) {
    public static OwnersAssemblyFormalSubjectResponse from(VotingSubject subject) {
        return new OwnersAssemblyFormalSubjectResponse(
                subject.getSubjectId(), subject.getSubjectType().name(), subject.getTitle(), subject.getStatus().name());
    }
}
