package com.pangu.interfaces.web.controller.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 角色数据范围写回请求（M4-1）。
 *
 * <p>取值范围在 application 层 {@code ALLOWED_DATA_SCOPES} 白名单与 schema CHECK 双重兜底；
 * {@code fixed_data_scope} 非空的角色由应用层 {@code ROLE_SCOPE_LOCKED} 拒绝。
 */
public record UpdateDataScopeRequest(
        @NotBlank
        @Pattern(regexp = "ALL_COMMUNITY|OWNER_GROUP|ORG_ONLY",
                message = "defaultDataScope 取值必须为 ALL_COMMUNITY/OWNER_GROUP/ORG_ONLY")
        String defaultDataScope) {}
