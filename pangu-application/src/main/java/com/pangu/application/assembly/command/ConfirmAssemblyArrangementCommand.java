// 关联业务：将会前表决事项和已归档材料锁定为业主大会正式公示与表决安排。
package com.pangu.application.assembly.command;

import java.time.Instant;
import java.util.List;

public record ConfirmAssemblyArrangementCommand(
        Long sessionId,
        Long tenantId,
        Integer publicNoticeDays,
        Instant voteStartAt,
        Instant voteEndAt,
        Long publicNoticeMaterialId,
        List<Long> planAttachmentMaterialIds,
        Long ballotTemplateMaterialId
) {
}
