// 关联业务：映射维修实施方案正文私有图片及其草稿绑定状态。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairNarrativeImageRow {
    private Long imageId;
    private Long tenantId;
    private Long projectId;
    private Long planId;
    private String objectKey;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String etag;
    private String sha256;
    private Long uploadedByAccountId;
    private Long uploadedByUserId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime boundAt;
}
