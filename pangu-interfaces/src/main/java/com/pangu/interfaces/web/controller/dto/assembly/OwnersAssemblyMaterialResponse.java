// 关联业务：向办理页面返回已归档的业主大会材料元数据，不暴露对象存储路径和文件摘要。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyMaterial;

import java.time.Instant;

public record OwnersAssemblyMaterialResponse(
        Long materialId,
        String materialType,
        String originalFileName,
        String contentType,
        Long fileSize,
        Instant createTime
) {
    public static OwnersAssemblyMaterialResponse from(OwnersAssemblyMaterial material) {
        return new OwnersAssemblyMaterialResponse(
                material.materialId(), material.materialType().name(), material.originalFileName(),
                material.contentType(), material.fileSize(), material.createTime());
    }
}
