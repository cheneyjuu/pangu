// 关联业务：保存业主本人对一个锁定表决包全部事项的一次在线确认和不可变回执。
package com.pangu.domain.model.voting;

import java.time.Instant;
import java.util.List;

public record OnlineBallotSubmission(
        Long submissionId,
        Long packageId,
        Long electorateItemId,
        Long tenantId,
        Long accountId,
        Long uid,
        Long opid,
        String idempotencyKey,
        String packageHash,
        String choiceManifestHash,
        String confirmationHash,
        Status status,
        Instant submittedAt,
        List<Item> items
) {
    public OnlineBallotSubmission {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public enum Status {
        ACCEPTED
    }

    public record Item(
            Long submissionItemId,
            Long submissionId,
            Long subjectId,
            VoteChoice choice,
            Long unifiedBallotId,
            String itemConfirmationHash
    ) {
    }
}
