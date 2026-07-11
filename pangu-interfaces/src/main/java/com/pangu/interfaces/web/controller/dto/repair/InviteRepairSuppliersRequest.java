package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record InviteRepairSuppliersRequest(
        @NotEmpty @Size(max = 20) List<Long> supplierDeptIds,
        LocalDateTime deadline,
        @Size(max = 500) String remark
) {
}
