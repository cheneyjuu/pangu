// 关联业务：接收尚未录入纸票的作废原因，保留错误登记而不是删除或覆盖。
package com.pangu.interfaces.web.controller.dto.assembly;

import jakarta.validation.constraints.NotBlank;

public record VoidAssemblyPaperBallotRequest(
        @NotBlank String reason
) {
}
