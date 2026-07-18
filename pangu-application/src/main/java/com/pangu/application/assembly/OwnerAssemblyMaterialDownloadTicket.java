// 关联业务：为已发布业主大会中的已锁定公开材料生成当前业主可用的临时下载票据。
package com.pangu.application.assembly;

import java.time.Instant;

/** 临时下载地址不入库，客户端只能在有效期内使用。 */
public record OwnerAssemblyMaterialDownloadTicket(
        Long materialId,
        String originalFileName,
        String contentType,
        Long fileSize,
        String downloadUrl,
        Instant expiresAt
) {
}
