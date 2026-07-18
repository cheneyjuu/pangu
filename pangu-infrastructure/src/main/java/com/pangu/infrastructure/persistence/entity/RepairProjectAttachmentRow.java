// 关联业务：映射独立于报修工单的维修工程原始附件元数据。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairProjectAttachmentRow {
    private Long attachmentId;
    private Long projectId;
    private Long tenantId;
    private String objectKey;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String etag;
    private String sha256;
    private Long uploadedByAccountId;
    private Long uploadedByUserId;
    private LocalDateTime createTime;
}
