// 关联业务：映射回收纸票的票号、冻结模板、受控原件和办理状态。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class PaperBallotRow {
    private Long paperBallotId;
    private Long packageId;
    private Long electorateItemId;
    private Long tenantId;
    private Long opid;
    private String ballotNumber;
    private String templateHash;
    private String materialSourceType;
    private Long materialSourceId;
    private String materialHash;
    private Long receivedByUserId;
    private Instant receivedAt;
    private String status;
    private Long voidedByUserId;
    private Instant voidedAt;
    private String voidReason;
    private Instant createTime;
    private Instant updateTime;
    private Long version;
}
