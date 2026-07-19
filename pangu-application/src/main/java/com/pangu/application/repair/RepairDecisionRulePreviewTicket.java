// 关联业务：提供小区维修事项表决依据原件的短时私有预览地址。
package com.pangu.application.repair;

import java.time.Instant;

public record RepairDecisionRulePreviewTicket(
        Long ruleId,
        String previewUrl,
        Instant expiresAt
) {
}
