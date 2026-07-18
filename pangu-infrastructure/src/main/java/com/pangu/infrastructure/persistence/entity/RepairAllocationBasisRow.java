// 关联业务：承载维修项目创建前从已核验产权名册汇总的费用承担范围。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RepairAllocationBasisRow {
    private String scopeLabel;
    private Long roomCount;
    private Long ownerCount;
    private BigDecimal totalBuildArea;
}
