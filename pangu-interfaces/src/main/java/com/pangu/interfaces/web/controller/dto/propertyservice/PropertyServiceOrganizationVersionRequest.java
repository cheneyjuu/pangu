// 关联业务：携带物业服务组织登记状态动作的乐观锁版本，避免并发提交覆盖。
package com.pangu.interfaces.web.controller.dto.propertyservice;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 物业服务组织状态动作版本请求。
 */
public record PropertyServiceOrganizationVersionRequest(
        @NotNull @PositiveOrZero Integer expectedVersion
) {
}
