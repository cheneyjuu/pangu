package com.pangu.interfaces.web.controller.dto.admin;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 更新网格组织节点的楼栋范围。
 */
public record UpdateGridBuildingScopeRequest(
        @NotEmpty List<Long> buildingIds) {
}
