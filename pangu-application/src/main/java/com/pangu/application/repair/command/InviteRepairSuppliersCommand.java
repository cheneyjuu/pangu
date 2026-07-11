package com.pangu.application.repair.command;

import java.time.LocalDateTime;
import java.util.List;

public record InviteRepairSuppliersCommand(
        List<Long> supplierDeptIds,
        LocalDateTime deadline,
        String remark
) {
}
