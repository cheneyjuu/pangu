package com.pangu.interfaces.web.controller.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增自然人账号并绑定管理端工作身份。
 */
public record CreateWorkIdentityAccountRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名不能超过50个字符")
        String realName,

        @NotNull(message = "deptId不能为空")
        Long deptId,

        @NotBlank(message = "roleKey不能为空")
        String roleKey,

        @Size(max = 50, message = "显示名称不能超过50个字符")
        String nickName,

        List<Long> buildingIds,

        Boolean forceBuildingTransfer) {

    public boolean isForceBuildingTransfer() {
        return Boolean.TRUE.equals(forceBuildingTransfer);
    }
}
