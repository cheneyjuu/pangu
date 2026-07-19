// 关联业务：保存回收纸质表决票的票号、冻结模板、专有部分和受控原件，不把上传等同于有效投票。
package com.pangu.domain.model.voting;

import java.time.Instant;

/** 一张回收纸票的不可变原始事实。 */
public record PaperBallot(
        Long paperBallotId,
        Long packageId,
        Long electorateItemId,
        Long tenantId,
        Long opid,
        String ballotNumber,
        String templateHash,
        String materialSourceType,
        Long materialSourceId,
        String materialHash,
        Long receivedByUserId,
        Instant receivedAt,
        Status status,
        Long voidedByUserId,
        Instant voidedAt,
        String voidReason,
        Instant createTime,
        Instant updateTime,
        Long version
) {

    public enum Status {
        RECEIVED,
        IN_ENTRY,
        COMPLETED,
        VOIDED
    }
}
