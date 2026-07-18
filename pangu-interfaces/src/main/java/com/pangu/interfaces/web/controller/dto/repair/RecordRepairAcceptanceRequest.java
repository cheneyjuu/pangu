// 关联业务：接收验收参与人的身份、组织、结论和纸质或电子签署证据。
package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordRepairAcceptanceRequest(
        Long roomId,
        Long ownerUid,
        @Size(max = 48) String participantType,
        @NotBlank @Size(max = 120) String participantName,
        @Size(max = 160) String participantOrganization,
        @NotBlank @Size(max = 32) String conclusion,
        @Size(max = 1000) String opinion,
        @Size(max = 128) String signatureHash,
        @Size(max = 128) String evidenceHash,
        @Size(max = 500) String remark
) {
}
