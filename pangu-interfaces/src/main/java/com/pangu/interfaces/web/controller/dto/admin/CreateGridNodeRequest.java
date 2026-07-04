package com.pangu.interfaces.web.controller.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建网格组织节点；父级居委会由当前管理身份在后端推导。
 */
public record CreateGridNodeRequest(
        @NotBlank(message = "机构名称不能为空")
        @Size(max = 100, message = "机构名称不能超过100个字符")
        String deptName) {
}
