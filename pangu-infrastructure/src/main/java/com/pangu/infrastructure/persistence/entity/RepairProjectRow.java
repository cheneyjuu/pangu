// 关联业务：映射维修工程项目主表，保存后端判定的流程、范围、资金账簿与当前方案。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairProjectRow {
    private Long projectId;
    private String projectNo;
    private Long tenantId;
    private String projectName;
    private String workflowType;
    private String scopeType;
    private Long buildingId;
    private String unitName;
    private String fundSource;
    private String governancePath;
    private String status;
    private Long activePlanId;
    private Integer version;
    private Long createdByAccountId;
    private Long createdByUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
