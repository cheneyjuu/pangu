package com.pangu.interfaces.web.controller.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增管理端工作身份请求。
 */
public record CreateWorkIdentityRequest(
        @NotNull Long deptId,
        @NotBlank String roleKey,
        @Size(max = 50) String nickName,
        List<Long> buildingIds,
        Boolean forceBuildingTransfer) {

    public boolean isForceBuildingTransfer() {
        return Boolean.TRUE.equals(forceBuildingTransfer);
    }
}
