// 关联业务：登记业主大会收到的纸质表决票编号、专有部分、回收时间和受控原件。
package com.pangu.application.assembly.command;

import java.time.Instant;

public record RegisterAssemblyPaperBallotCommand(
        Long sessionId,
        Long tenantId,
        Long opid,
        Long proxyAuthorizationId,
        String ballotNumber,
        Long ballotMaterialId,
        Long receivedByUserId,
        Instant receivedAt
) {
}
