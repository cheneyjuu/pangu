// 关联业务：在业主大会进入正式公示前冻结当时有效议事规则，保证后续规则变更不影响本次办理和计票。
package com.pangu.domain.model.assembly;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 已进入正式办理的业主大会所适用的不可变议事规则快照。
 *
 * <p>快照复制规则原件标识、结构化配置及其摘要，而不是在办理或结算时重新读取当前 ACTIVE 规则。
 * 因此规则版本被替代后，历史会次仍可还原当时的送达、渠道和计票口径。
 */
public record OwnersAssemblyRuleSnapshot(
        Long ruleSnapshotId,
        Long sessionId,
        Long tenantId,
        Long ruleId,
        String ruleName,
        String ruleVersion,
        LocalDate effectiveDate,
        String sourceFileName,
        String sourceSha256,
        OwnersAssemblyRuleConfiguration configuration,
        String configurationSha256,
        Long snapshottedByAccountId,
        Long snapshottedByUserId,
        Instant createTime
) {
}
