package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 业主名册详情页 · 房产逐条行 → {@code OwnerPropertyDetail} 中转。
 */
@Data
public class OwnerPropertyDetailRow {

    private Long opid;
    private Long buildingId;
    private Long roomId;
    private BigDecimal buildArea;
    private Integer votingDelegate;   // 0/1 → boolean 由 gateway 映射
    private Integer accountStatus;
}
