// 关联业务：持久化一个维修工程唯一的共有与决定范围核验快照。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairDecisionScopeRow {
    private Long decisionScopeId;
    private Long projectId;
    private Long tenantId;
    private String scopeType;
    private Long buildingId;
    private String unitName;
    private String verificationStatus;
    private String verificationBasis;
    private Boolean legacyReadOnly;
    private LocalDateTime createTime;
}
