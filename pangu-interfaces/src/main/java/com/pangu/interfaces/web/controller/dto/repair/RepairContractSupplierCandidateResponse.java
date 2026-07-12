// 关联业务：返回维修合同阶段已锁定供应商及其企业核验结果。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.RepairContractSupplierCandidate;

import java.math.BigDecimal;

public record RepairContractSupplierCandidateResponse(
        Long quoteId,
        Long supplierDeptId,
        String supplierName,
        BigDecimal quoteAmount,
        String verificationStatus,
        boolean contractEligible,
        String contractEligibilityMessage
) {

    public static RepairContractSupplierCandidateResponse from(RepairContractSupplierCandidate candidate) {
        return new RepairContractSupplierCandidateResponse(
                candidate.quoteId(),
                candidate.supplierDeptId(),
                candidate.supplierName(),
                candidate.quoteAmount(),
                candidate.verificationStatus(),
                candidate.contractEligible(),
                candidate.contractEligibilityMessage());
    }
}
