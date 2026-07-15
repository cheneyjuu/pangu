// 关联业务：用项目乐观锁版本确认并锁定维修实施方案快照。
package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LockRepairPlanRequest(
        @NotNull @Min(0) Integer expectedProjectVersion
) {
}
