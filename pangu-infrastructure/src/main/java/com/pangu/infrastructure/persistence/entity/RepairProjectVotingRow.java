// 关联业务：映射维修授权提案与统一表决包之间的唯一关联。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class RepairProjectVotingRow {
    private Long linkId;
    private Long projectId;
    private Long planId;
    private Long tenantId;
    private Long subjectId;
    private Long executionPackageId;
    private Long ruleId;
    private String ruleConfigurationHash;
    private Long paperBallotTemplateAttachmentId;
    private String paperBallotTemplateHash;
    private String collectionMode;
    private String status;
    private String result;
    private Long preparedByUserId;
    private Instant preparedAt;
    private Long openedByUserId;
    private Instant openedAt;
    private Long settledByUserId;
    private Instant settledAt;
    private Long version;
}
