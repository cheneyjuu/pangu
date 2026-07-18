// 关联业务：映射业主大会正式表决安排确认前的表决事项草案表。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OwnersAssemblySubjectDraftRow {
    private Long draftId;
    private Long sessionId;
    private Long tenantId;
    private String subjectType;
    private String scope;
    private Long scopeReferenceId;
    private String title;
    private String content;
    private Long proposedByUserId;
    private LocalDateTime createTime;
}
