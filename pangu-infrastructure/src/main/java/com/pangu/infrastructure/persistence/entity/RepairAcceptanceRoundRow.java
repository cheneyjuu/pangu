// 关联业务：映射维修完工验收及整改复验轮次。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class RepairAcceptanceRoundRow {
    private Long acceptanceId;
    private Long workOrderId;
    private Long tenantId;
    private Long policyId;
    private Integer roundNo;
    private String status;
}
