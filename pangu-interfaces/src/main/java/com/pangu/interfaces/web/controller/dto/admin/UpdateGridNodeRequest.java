package com.pangu.interfaces.web.controller.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新网格组织节点基础信息。
 */
public record UpdateGridNodeRequest(
        @NotBlank(message = "机构名称不能为空")
        @Size(max = 100, message = "机构名称不能超过100个字符")
        String deptName) {
}
