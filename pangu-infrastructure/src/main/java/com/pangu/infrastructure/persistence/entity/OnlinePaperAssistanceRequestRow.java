// 关联业务：映射互联网表决中业主申请纸质办理的状态。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class OnlinePaperAssistanceRequestRow {
    private Long requestId;
    private Long packageId;
    private Long electorateItemId;
    private Long tenantId;
    private Long accountId;
    private Long uid;
    private Long opid;
    private String status;
    private Instant requestedAt;
    private Instant fulfilledAt;
    private Instant withdrawnAt;
    private Long paperDeliveryId;
}
