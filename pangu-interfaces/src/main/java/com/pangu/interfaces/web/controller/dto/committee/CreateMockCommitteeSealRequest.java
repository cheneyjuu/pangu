// 关联业务：管理端在开发测试环境创建明确无效力的业主自治组织模拟电子印章。
package com.pangu.interfaces.web.controller.dto.committee;

import com.pangu.application.committee.command.CreateMockCommitteeSealCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMockCommitteeSealRequest(
        @Size(max = 120) String sealName,
        @NotBlank @Size(max = 32) String sealType
) {
    public CreateMockCommitteeSealCommand toCommand() {
        return new CreateMockCommitteeSealCommand(sealName, sealType);
    }
}
