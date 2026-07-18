// 关联业务：以已关联来源重新核验维修工程唯一决定范围，防止待核验草稿永久无法冻结。
package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReverifyRepairDecisionScopeRequest(
        @NotNull @Min(0) Integer expectedProjectVersion
) {
}
