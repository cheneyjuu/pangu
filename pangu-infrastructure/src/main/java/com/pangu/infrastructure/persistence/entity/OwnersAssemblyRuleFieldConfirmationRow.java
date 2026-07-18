// 关联业务：映射业主大会议事规则逐项核对记录，保存原件条款与主任或副主任确认事实。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OwnersAssemblyRuleFieldConfirmationRow {
    private Long confirmationId;
    private Long ruleId;
    private Long tenantId;
    private String configurationSha256;
    private String fieldKey;
    private Integer sourcePageNumber;
    private String sourceClause;
    private String status;
    private Long confirmedByAccountId;
    private Long confirmedByUserId;
    private String confirmedByCommitteePosition;
    private LocalDateTime confirmedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
