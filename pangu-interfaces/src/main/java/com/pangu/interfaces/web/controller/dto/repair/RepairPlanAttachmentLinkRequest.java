// 关联业务：把项目原始附件按用途引用到尚未锁定的维修实施方案。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairProject.AttachmentPurpose;
import jakarta.validation.constraints.NotNull;

public record RepairPlanAttachmentLinkRequest(
        @NotNull Long attachmentId,
        @NotNull AttachmentPurpose purpose
) {
}
