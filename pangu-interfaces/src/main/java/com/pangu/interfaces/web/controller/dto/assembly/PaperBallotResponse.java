// 关联业务：向管理端展示已回收纸质表决票的登记信息与当前处理阶段。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.PaperBallot;

import java.time.Instant;

public record PaperBallotResponse(
        Long paperBallotId,
        Long packageId,
        Long opid,
        String ballotNumber,
        Long ballotMaterialId,
        String status,
        Long receivedByUserId,
        Instant receivedAt,
        String voidReason
) {
    public static PaperBallotResponse from(PaperBallot ballot) {
        return new PaperBallotResponse(
                ballot.paperBallotId(),
                ballot.packageId(),
                ballot.opid(),
                ballot.ballotNumber(),
                ballot.materialSourceId(),
                ballot.status().name(),
                ballot.receivedByUserId(),
                ballot.receivedAt(),
                ballot.voidReason());
    }
}
