// 关联业务：承载业主大会正式公示前新增表决事项草案的应用层输入。
package com.pangu.application.assembly.command;

import com.pangu.domain.model.voting.SubjectType;

public record CreateAssemblySubjectDraftCommand(
        Long sessionId,
        Long tenantId,
        SubjectType subjectType,
        String title,
        String content,
        Long proposedByUserId
) {
}
