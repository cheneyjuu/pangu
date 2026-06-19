package com.pangu.interfaces.web.controller.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建角色请求 DTO（M2-4 SaaS 后台）。
 *
 * <p>取值范围在 application 层 {@code RoleAdminApplicationService.validate(...)} 与 schema CHECK 双重兜底。
 */
public record CreateRoleRequest(
        @NotBlank @Size(max = 50) String roleKey,
        @NotBlank @Size(max = 50) String roleName,
        @NotBlank @Pattern(regexp = "G|B|S",
                message = "allowedDeptCategory 取值必须为 G/B/S") String allowedDeptCategory,
        String fixedDataScope,
        @NotBlank String defaultDataScope) {}
