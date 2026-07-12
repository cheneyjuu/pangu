// 关联业务：接收维修盖章方式、已盖章文件或平台电子印章选择。
package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SealRepairGovernanceRequest(
        @NotBlank @Size(max = 32) String sealingMethod,
        Long sealedAttachmentId,
        Long electronicSealId,
        @Size(max = 500) String remark
) {
}
