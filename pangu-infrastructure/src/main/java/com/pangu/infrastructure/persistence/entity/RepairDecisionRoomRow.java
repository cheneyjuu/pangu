package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RepairDecisionRoomRow {
    private Long roomId;
    private String roomLabel;
    private BigDecimal buildArea;
}
