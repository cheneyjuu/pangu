// 关联业务：映射维修工程实施方案中的点位、工作内容、预算与来源报修事项。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RepairProjectItemRow {
    private Long itemId;
    private Long projectId;
    private Long planId;
    private Long tenantId;
    private String itemNo;
    private Long buildingId;
    private String unitName;
    private Long roomId;
    private String locationText;
    private String workContent;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal estimatedUnitPrice;
    private BigDecimal estimatedAmount;
    private Integer sortOrder;
    private List<Long> linkedWorkOrderIds;
    private LocalDateTime createTime;
}
