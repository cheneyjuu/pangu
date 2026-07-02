package com.pangu.application.admin.command;

import java.util.List;

/**
 * 新增管理端工作身份命令。
 */
public record CreateWorkIdentityCommand(
        Long accountId,
        Long deptId,
        String roleKey,
        String nickName,
        List<Long> buildingIds,
        boolean forceBuildingTransfer) {

    public CreateWorkIdentityCommand {
        buildingIds = buildingIds == null ? List.of() : List.copyOf(buildingIds);
    }
}
