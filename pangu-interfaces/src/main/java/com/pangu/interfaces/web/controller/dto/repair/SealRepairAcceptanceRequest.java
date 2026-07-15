// 关联业务：接收全小区公共维修验收盖章文件及用印备注。
package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SealRepairAcceptanceRequest(
        @NotNull Long sealedAttachmentId,
        @Size(max = 500) String remark
) {
}
