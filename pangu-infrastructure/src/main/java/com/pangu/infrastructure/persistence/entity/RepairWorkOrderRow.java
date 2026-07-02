package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RepairWorkOrderRow {
    private Long workOrderId;
    private String orderNo;
    private Long tenantId;
    private String title;
    private String description;
    private String source;
    private String spaceScope;
    private String status;
    private Long reporterAccountId;
    private Long reporterUid;
    private Long reporterUserId;
    private Long roomId;
    private Long buildingId;
    private String locationText;
    private Integer needManualLocation;
    private Integer locationLocked;
    private Long assignedUserId;
    private String assigneeRoleKey;
    private Long assigneeDeptId;
    private String category;
    private String riskLevel;
    private String surveySummary;
    private BigDecimal planBudget;
    private String fundSource;
    private Integer fundGateBlocked;
    private Integer satisfactionScore;
    private String satisfactionComment;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
