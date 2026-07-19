// 关联业务：向业主返回整包在线表决的本人回执，不返回任何具体选择。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.OnlineBallotSubmission;

import java.time.Instant;

public record OnlineBallotReceiptResponse(
        Long submissionId,
        Long opid,
        String confirmationHash,
        Instant submittedAt
) {
    public static OnlineBallotReceiptResponse from(OnlineBallotSubmission source) {
        return new OnlineBallotReceiptResponse(
                source.submissionId(), source.opid(), source.confirmationHash(), source.submittedAt());
    }
}
