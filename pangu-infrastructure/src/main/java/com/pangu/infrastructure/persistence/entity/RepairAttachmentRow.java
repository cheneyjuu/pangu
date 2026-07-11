package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairAttachmentRow {
    private Long attachmentId;
    private Long workOrderId;
    private Long tenantId;
    private String attachmentKind;
    private String objectKey;
    private String originalFileName;
    private String contentType;
    private long declaredSize;
    private Long actualSize;
    private String etag;
    private String status;
    private Long uploadedByAccountId;
    private String boundAction;
    private LocalDateTime createTime;
    private LocalDateTime confirmedAt;
}
