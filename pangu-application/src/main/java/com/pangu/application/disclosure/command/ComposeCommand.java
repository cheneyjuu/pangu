package com.pangu.application.disclosure.command;

import com.pangu.domain.model.disclosure.DisclosureType;

/**
 * 财务公示快照聚合命令。
 *
 * @param tenantId            租户 ID
 * @param period              期间字符串：YYYY-MM 或 YYYYQ[1-4]
 * @param disclosureType      公示类型（本期仅 MAINTENANCE_FUND 真正可用）
 * @param composedByUserId    聚合操作人 sys_user.user_id（来自 SecurityUtils）
 */
public record ComposeCommand(
        Long tenantId,
        String period,
        DisclosureType disclosureType,
        Long composedByUserId
) {
}
