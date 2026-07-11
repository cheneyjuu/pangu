package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OwnersAssemblyDeliveryRecordRow {
    private Long deliveryId;
    private Long packageId;
    private Long tenantId;
    private Long opid;
    private Long uid;
    private String deliveryChannel;
    private String deliveryMethod;
    private String evidenceHash;
    private Long deliveredByUserId;
    private LocalDateTime deliveredAt;
}
