// 关联业务：持久化维修工程方案内可定位、可勘验的维修点位及其来源关联。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RepairWorkPointRow {
    private Long workPointId;
    private Long projectId;
    private Long planId;
    private Long tenantId;
    private String businessName;
    private Long buildingId;
    private String unitName;
    private String locationType;
    private Long referenceRoomId;
    private String commonAreaName;
    private String spaceName;
    private String orientation;
    private String component;
    private String specificPart;
    private String symptom;
    private String causeStatus;
    private String causeBasis;
    private String proposedMeasure;
    private String technicalRequirements;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal preliminaryEstimatedAmount;
    private String estimateSource;
    private Integer sortOrder;
    private Boolean legacyReadOnly;
    private List<Long> linkedWorkOrderIds;
    private LocalDateTime createTime;
}
