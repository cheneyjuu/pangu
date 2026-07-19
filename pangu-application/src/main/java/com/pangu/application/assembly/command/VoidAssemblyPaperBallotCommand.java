// 关联业务：在尚未录入前作废错误登记的业主大会纸票，并保留经办人、时间和原因。
package com.pangu.application.assembly.command;

import java.time.Instant;

public record VoidAssemblyPaperBallotCommand(
        Long sessionId,
        Long paperBallotId,
        Long tenantId,
        String reason,
        Long voidedByUserId,
        Instant voidedAt
) {
}
