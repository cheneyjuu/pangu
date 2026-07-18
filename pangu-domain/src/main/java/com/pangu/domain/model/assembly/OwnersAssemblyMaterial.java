// 关联业务：保存业主大会公告、方案附件、纸质选票及送达、回收凭证的不可变原始材料元数据。
package com.pangu.domain.model.assembly;

import java.time.Instant;

/** 业主大会办理材料；文件摘要由服务端生成，并作为正式表决安排的锁定依据。 */
public record OwnersAssemblyMaterial(
        Long materialId,
        Long sessionId,
        Long tenantId,
        MaterialType materialType,
        String objectKey,
        String originalFileName,
        String contentType,
        Long fileSize,
        String etag,
        String contentSha256,
        Long uploadedByAccountId,
        Long uploadedByUserId,
        Instant createTime
) {

    public enum MaterialType {
        PUBLIC_NOTICE,
        PLAN_ATTACHMENT,
        PAPER_BALLOT_TEMPLATE,
        DELIVERY_EVIDENCE,
        PAPER_BALLOT
    }
}
