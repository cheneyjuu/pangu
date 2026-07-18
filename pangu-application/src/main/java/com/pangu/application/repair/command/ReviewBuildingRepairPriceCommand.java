// 关联业务：记录业委会对楼栋维修方案的内部审价或第三方审价结论。
package com.pangu.application.repair.command;

import java.math.BigDecimal;

public record ReviewBuildingRepairPriceCommand(
        Integer expectedProcessVersion,
        String reviewMode,
        BigDecimal reviewedAmount,
        Long reportAttachmentId,
        String conclusion,
        String opinion
) {
}
