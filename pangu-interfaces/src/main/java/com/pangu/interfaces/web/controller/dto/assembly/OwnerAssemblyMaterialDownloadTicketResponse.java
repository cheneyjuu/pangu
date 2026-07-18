// 关联业务：将业主大会锁定公开材料的临时下载票据返回给当前已授权业主。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.application.assembly.OwnerAssemblyMaterialDownloadTicket;

import java.time.Instant;

/** 下载 URL 仅短时有效，不能被客户端作为永久材料地址保存。 */
public record OwnerAssemblyMaterialDownloadTicketResponse(
        Long materialId,
        String originalFileName,
        String contentType,
        Long fileSize,
        String downloadUrl,
        Instant expiresAt
) {
    public static OwnerAssemblyMaterialDownloadTicketResponse from(OwnerAssemblyMaterialDownloadTicket ticket) {
        return new OwnerAssemblyMaterialDownloadTicketResponse(
                ticket.materialId(), ticket.originalFileName(), ticket.contentType(), ticket.fileSize(),
                ticket.downloadUrl(), ticket.expiresAt());
    }
}
