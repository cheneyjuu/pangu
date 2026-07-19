// 关联业务：接收业主对本次表决包全部事项的在线选择和最终确认。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.application.voting.OnlineVotingService;
import com.pangu.domain.model.voting.VoteChoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record OnlineBallotSubmissionRequest(
        @NotNull Long opid,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}") String packageHash,
        @NotNull Boolean confirmed,
        @NotEmpty List<@Valid Decision> decisions
) {
    public List<OnlineVotingService.Decision> toCommands() {
        return decisions.stream()
                .map(decision -> new OnlineVotingService.Decision(decision.subjectId(), decision.choice()))
                .toList();
    }

    public record Decision(@NotNull Long subjectId, @NotNull VoteChoice choice) {
    }
}
