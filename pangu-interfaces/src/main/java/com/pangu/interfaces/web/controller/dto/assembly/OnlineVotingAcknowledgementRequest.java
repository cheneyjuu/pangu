// 关联业务：接收业主对本次锁定表决材料的本人阅读确认。
package com.pangu.interfaces.web.controller.dto.assembly;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OnlineVotingAcknowledgementRequest(
        @NotNull Long opid,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}") String packageHash,
        @NotNull Boolean confirmed
) {
}
