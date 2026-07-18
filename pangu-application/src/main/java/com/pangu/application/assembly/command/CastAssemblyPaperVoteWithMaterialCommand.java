// 关联业务：用业主大会内已归档的回收选票原件录入纸质表决。
package com.pangu.application.assembly.command;

import com.pangu.domain.model.voting.VoteChoice;

public record CastAssemblyPaperVoteWithMaterialCommand(
        Long sessionId,
        Long tenantId,
        Long subjectId,
        Long opid,
        VoteChoice choice,
        Long ballotMaterialId,
        Long enteredByUserId
) {
}
