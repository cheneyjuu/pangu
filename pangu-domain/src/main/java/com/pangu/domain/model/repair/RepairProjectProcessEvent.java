// 关联业务：读取维修工程项目已办节点的最小化事件投影，不加载个人业主表决、操作人身份或原始审计载荷。
package com.pangu.domain.model.repair;

import java.time.LocalDateTime;

/**
 * 维修工程项目办理历史事件。
 *
 * <p>原始审计事件还包含操作人和业务载荷；管理端流程记录只需要展示节点、时间和安全业务摘要，
 * 因此该投影故意不承载任何个人身份或原始载荷。</p>
 */
public record RepairProjectProcessEvent(
        Long eventId,
        Long projectId,
        Long tenantId,
        String action,
        LocalDateTime occurredAt
) {
}
