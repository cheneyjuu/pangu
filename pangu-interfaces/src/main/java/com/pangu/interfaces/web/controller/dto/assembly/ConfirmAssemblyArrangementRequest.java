// 关联业务：接收业主大会公示、表决时间和已上传材料的锁定安排。
package com.pangu.interfaces.web.controller.dto.assembly;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record ConfirmAssemblyArrangementRequest(
        @NotNull Instant voteStartAt,
        @NotNull Instant voteEndAt,
        @NotNull Long publicNoticeMaterialId,
        @NotEmpty List<@NotNull Long> planAttachmentMaterialIds,
        @NotNull Long ballotTemplateMaterialId
) {
}
