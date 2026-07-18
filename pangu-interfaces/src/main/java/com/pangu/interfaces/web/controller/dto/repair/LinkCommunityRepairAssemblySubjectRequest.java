// 关联业务：校验全小区维修锁定方案关联正式业主大会单个表决事项的输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.LinkCommunityRepairAssemblySubjectCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LinkCommunityRepairAssemblySubjectRequest(
        @NotNull @Min(0) Integer expectedProjectVersion,
        @NotNull Long packageId,
        @NotNull Long subjectId
) {
    public LinkCommunityRepairAssemblySubjectCommand toCommand() {
        return new LinkCommunityRepairAssemblySubjectCommand(
                expectedProjectVersion, packageId, subjectId);
    }
}
