package com.pangu.interfaces.web.controller.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 楼栋责任田分配请求 DTO。
 *
 * <p>{@code targetRoleKey} 受 application 层白名单二次校验（GRID_MEMBER /
 * VOLUNTEER / OWNER_REPRESENTATIVE 三选一）。{@code force=true} 时表示「转移」
 * ——若楼栋已被同角色其他用户占用，先 revoke 占用者再分配给当前 userId。
 */
public record AssignBuildingRequest(
        @NotNull Long buildingId,
        @NotBlank
        @Pattern(regexp = "GRID_MEMBER|VOLUNTEER|OWNER_REPRESENTATIVE",
                message = "targetRoleKey 必须为 GRID_MEMBER/VOLUNTEER/OWNER_REPRESENTATIVE")
        String targetRoleKey,
        Boolean force) {

    /** force null 默认 false。 */
    public boolean isForce() {
        return Boolean.TRUE.equals(force);
    }
}
