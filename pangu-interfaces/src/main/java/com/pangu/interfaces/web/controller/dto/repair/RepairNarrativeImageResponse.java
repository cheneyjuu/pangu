// 关联业务：返回维修实施方案正文图片的稳定引用和短期预览地址。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.RepairNarrativeImageService.PreviewTicket;
import com.pangu.application.repair.RepairNarrativeImageService.UploadResult;

import java.time.Instant;

public record RepairNarrativeImageResponse(
        Long imageId,
        String source,
        String previewUrl,
        Instant expiresAt,
        String originalFileName,
        String contentType,
        Long fileSize
) {

    public static RepairNarrativeImageResponse from(UploadResult result) {
        return new RepairNarrativeImageResponse(
                result.image().imageId(), source(result.image().imageId()),
                result.preview().previewUrl(), result.preview().expiresAt(),
                result.image().originalFileName(), result.image().contentType(), result.image().fileSize());
    }

    public static RepairNarrativeImageResponse from(PreviewTicket ticket) {
        return new RepairNarrativeImageResponse(
                ticket.imageId(), source(ticket.imageId()), ticket.previewUrl(), ticket.expiresAt(),
                null, null, null);
    }

    private static String source(Long imageId) {
        return "repair-image://" + imageId;
    }
}
