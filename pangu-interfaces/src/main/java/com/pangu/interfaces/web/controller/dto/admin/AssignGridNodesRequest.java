package com.pangu.interfaces.web.controller.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 为网格员分配一个或多个网格组织节点。
 */
public record AssignGridNodesRequest(
        @NotEmpty(message = "gridDeptIds不能为空")
        List<@NotNull(message = "gridDeptIds不允许包含null") Long> gridDeptIds) {
}
