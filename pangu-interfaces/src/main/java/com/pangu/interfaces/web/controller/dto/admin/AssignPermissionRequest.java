package com.pangu.interfaces.web.controller.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 角色 ↔ permission 授予请求。 */
public record AssignPermissionRequest(
        @NotBlank @Size(max = 64) String permissionKey) {}
