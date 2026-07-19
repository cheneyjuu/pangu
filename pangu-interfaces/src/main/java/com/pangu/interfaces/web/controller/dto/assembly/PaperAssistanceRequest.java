// 关联业务：接收互联网表决中业主为本人专有部分申请或撤回纸质办理。
package com.pangu.interfaces.web.controller.dto.assembly;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PaperAssistanceRequest(
        @NotNull Long opid,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}") String packageHash
) {
}
