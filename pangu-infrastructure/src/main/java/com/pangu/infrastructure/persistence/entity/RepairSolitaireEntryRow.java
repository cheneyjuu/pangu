package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RepairSolitaireEntryRow {
    private Long roomId;
    private Long ownerUid;
    private String choice;
    private BigDecimal buildArea;
    private String originalText;
}
