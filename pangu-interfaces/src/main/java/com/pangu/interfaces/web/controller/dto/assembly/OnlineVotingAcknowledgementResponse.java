// 关联业务：向业主返回锁定材料阅读确认的受控回执。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.OnlineVotingAcknowledgement;

import java.time.Instant;

public record OnlineVotingAcknowledgementResponse(
        Long acknowledgementId,
        Long opid,
        String acknowledgementHash,
        Instant acknowledgedAt
) {
    public static OnlineVotingAcknowledgementResponse from(OnlineVotingAcknowledgement source) {
        return new OnlineVotingAcknowledgementResponse(
                source.acknowledgementId(), source.opid(), source.acknowledgementHash(), source.acknowledgedAt());
    }
}
