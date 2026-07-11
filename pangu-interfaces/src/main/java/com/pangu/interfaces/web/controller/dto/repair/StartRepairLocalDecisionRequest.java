package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.Size;

public record StartRepairLocalDecisionRequest(
        @Size(max = 24) String scopeType,
        @Size(max = 80) String unitName,
        @Size(max = 120) String scopeLabel,
        @Size(max = 500) String remark
) {
}
