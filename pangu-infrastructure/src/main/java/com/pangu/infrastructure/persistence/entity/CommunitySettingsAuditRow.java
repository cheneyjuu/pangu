// 关联业务：映射社区设置审计表及操作人账号、姓名、角色的只读联查结果。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class CommunitySettingsAuditRow {
    private Long auditId;
    private Long tenantId;
    private String operationType;
    private String payloadJson;
    private Long operatorAccountId;
    private Long operatorUserId;
    private String operatorName;
    private String operatorRoleName;
    private Instant createTime;
}
