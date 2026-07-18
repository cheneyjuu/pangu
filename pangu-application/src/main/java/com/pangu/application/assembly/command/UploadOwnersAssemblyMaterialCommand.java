// 关联业务：承载业主大会公告、方案、选票和纸质办理凭证的原始文件上传输入。
package com.pangu.application.assembly.command;

import com.pangu.domain.model.assembly.OwnersAssemblyMaterial.MaterialType;

public record UploadOwnersAssemblyMaterialCommand(
        Long sessionId,
        Long tenantId,
        MaterialType materialType,
        String originalFileName,
        String contentType,
        byte[] content
) {
}
