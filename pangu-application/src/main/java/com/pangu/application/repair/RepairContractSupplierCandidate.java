// 关联业务：向合同签署阶段提供已由推荐结果锁定的供应商及其签约资格。
package com.pangu.application.repair;

import java.math.BigDecimal;

/**
 * 维修合同的唯一供应商候选。
 *
 * <p>合同阶段不能重新选择供应商，候选供应商必须来自该工单最新的物业推荐结果。</p>
 */
public record RepairContractSupplierCandidate(
        Long quoteId,
        Long supplierDeptId,
        String supplierName,
        BigDecimal quoteAmount,
        String verificationStatus,
        boolean contractEligible,
        String contractEligibilityMessage
) {
}
