// 关联业务：携带小区注册申请乐观锁版本，防止并发覆盖审核状态。
package com.pangu.interfaces.web.controller.dto.registration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 小区注册状态动作版本请求。
 */
public record CommunityRegistrationVersionRequest(
        @NotNull @PositiveOrZero Integer expectedVersion
) {
}
