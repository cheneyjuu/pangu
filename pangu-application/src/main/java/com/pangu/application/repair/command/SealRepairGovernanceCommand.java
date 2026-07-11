package com.pangu.application.repair.command;

public record SealRepairGovernanceCommand(
        String sealType,
        String sealedFileHash,
        String remark
) {
}
