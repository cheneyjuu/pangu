// 关联业务：为管理端按需查看业主大会议事规则原件生成短时私有预览地址。
package com.pangu.application.assembly;

import java.time.Instant;

public record OwnersAssemblyRulePreviewTicket(
        Long ruleId,
        String originalFileName,
        String contentType,
        Long fileSize,
        String previewUrl,
        Instant expiresAt
) {
}
