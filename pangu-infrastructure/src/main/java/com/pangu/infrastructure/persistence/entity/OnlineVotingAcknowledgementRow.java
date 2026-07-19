// 关联业务：映射业主线上阅读确认及其统一送达关联。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class OnlineVotingAcknowledgementRow {
    private Long acknowledgementId;
    private Long packageId;
    private Long electorateItemId;
    private Long tenantId;
    private Long accountId;
    private Long uid;
    private Long opid;
    private String packageHash;
    private String acknowledgementHash;
    private Long unifiedDeliveryId;
    private Instant acknowledgedAt;
}
