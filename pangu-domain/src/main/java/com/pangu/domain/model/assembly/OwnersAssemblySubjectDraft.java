// 关联业务：在正式公示和表决安排锁定前保存业主大会的表决事项草案。
package com.pangu.domain.model.assembly;

import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;

import java.time.Instant;

/** 正式投票事项生成前的会前草案，避免要求工作人员手工维护内部表决包标识。 */
public record OwnersAssemblySubjectDraft(
        Long draftId,
        Long sessionId,
        Long tenantId,
        SubjectType subjectType,
        VotingScope scope,
        Long scopeReferenceId,
        String title,
        String content,
        Long proposedByUserId,
        Instant createTime
) {
}
