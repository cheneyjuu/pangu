// 关联业务：映射纸质表决材料送达登记、核对状态和统一送达引用。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class PaperVotingDeliveryRow {
    private Long paperDeliveryId;
    private Long packageId;
    private Long electorateItemId;
    private Long tenantId;
    private Long opid;
    private Long proxyAuthorizationId;
    private String recipientName;
    private String deliveryMethod;
    private String evidenceSourceType;
    private Long evidenceSourceId;
    private String evidenceHash;
    private Long deliveredByUserId;
    private Instant deliveredAt;
    private String status;
    private Long reviewedByUserId;
    private Instant reviewedAt;
    private String reviewNote;
    private Long unifiedDeliveryId;
    private Instant createTime;
    private Instant updateTime;
    private Long version;
}
