// 关联业务：接收尚未用于纸票的书面委托撤销原因。
package com.pangu.interfaces.web.controller.dto.voting;

import jakarta.validation.constraints.NotBlank;

public record RevokeVotingProxyAuthorizationRequest(@NotBlank String reason) {
}
