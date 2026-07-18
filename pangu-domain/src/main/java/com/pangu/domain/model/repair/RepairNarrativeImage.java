// 关联业务：维修实施方案正文中的私有图片，上传后只能绑定到同租户的一个方案版本。
package com.pangu.domain.model.repair;

import java.time.LocalDateTime;

/**
 * 维修方案正文图片的可信引用。
 *
 * <p>正文只保存 imageId，不保存外部图片地址；展示时再生成同源短期访问地址。
 */
public record RepairNarrativeImage(
        Long imageId,
        Long tenantId,
        Long projectId,
        Long planId,
        String objectKey,
        String originalFileName,
        String contentType,
        Long fileSize,
        String etag,
        String sha256,
        Long uploadedByAccountId,
        Long uploadedByUserId,
        Status status,
        LocalDateTime createTime,
        LocalDateTime boundAt
) {

    public enum Status {
        DRAFT,
        BOUND
    }
}
