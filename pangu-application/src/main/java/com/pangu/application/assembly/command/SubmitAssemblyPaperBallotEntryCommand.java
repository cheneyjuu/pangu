// 关联业务：提交一版覆盖业主大会表决包全部事项的纸票结构化录入。
package com.pangu.application.assembly.command;

import com.pangu.domain.model.voting.PaperBallotEntry;

import java.time.Instant;
import java.util.List;

public record SubmitAssemblyPaperBallotEntryCommand(
        Long sessionId,
        Long paperBallotId,
        Long tenantId,
        List<PaperBallotEntry.Item> items,
        Long enteredByUserId,
        Instant enteredAt
) {
}
