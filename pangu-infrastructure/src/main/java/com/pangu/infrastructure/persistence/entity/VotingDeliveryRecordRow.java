// 关联业务：映射表决材料对冻结名册表决代表的送达证据。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class VotingDeliveryRecordRow {
    private Long deliveryId;
    private Long packageId;
    private Long electorateItemId;
    private Long tenantId;
    private Integer deliveryChannel;
    private String deliveryMethod;
    private String evidenceHash;
    private Long deliveredByUserId;
    private Instant deliveredAt;
}
