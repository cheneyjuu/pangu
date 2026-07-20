// 关联业务：登记已收回纸质表决票的票号、对应专有部分、回收时间和原件材料。
package com.pangu.interfaces.web.controller.dto.assembly;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RegisterAssemblyPaperBallotRequest(
        @NotBlank String ballotNumber,
        @NotNull Long opid,
        Long proxyAuthorizationId,
        @NotNull Instant receivedAt,
        @NotNull Long ballotMaterialId
) {
}
