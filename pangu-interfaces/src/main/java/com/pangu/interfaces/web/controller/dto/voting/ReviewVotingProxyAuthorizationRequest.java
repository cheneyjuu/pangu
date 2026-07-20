// 关联业务：接收另一名工作人员对书面委托原件和代理人身份的核对结论。
package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.application.voting.VotingProxyAuthorizationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewVotingProxyAuthorizationRequest(
        @NotNull VotingProxyAuthorizationService.ReviewDecision decision,
        @NotBlank String reviewNote
) {
}
