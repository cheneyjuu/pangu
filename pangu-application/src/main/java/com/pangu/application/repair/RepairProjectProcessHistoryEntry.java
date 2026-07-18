// 关联业务：向管理端展示维修工程项目的已办流程节点，不暴露个人业主表决选择或内部审计载荷。
package com.pangu.application.repair;

import java.time.LocalDateTime;

/** 管理端可查看的维修工程项目办理历史条目。 */
public record RepairProjectProcessHistoryEntry(
        Long eventId,
        String title,
        String summary,
        LocalDateTime occurredAt
) {
}
