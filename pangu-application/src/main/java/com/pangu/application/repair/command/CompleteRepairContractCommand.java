package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairContractSignature;

import java.util.List;

public record CompleteRepairContractCommand(
        List<RepairContractSignature> signatures,
        String finalContractFileHash,
        String remark
) {
}
