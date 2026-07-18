// 关联业务：映射业主大会公告、选票和纸质凭证的私有文件元数据表。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OwnersAssemblyMaterialRow {
    private Long materialId;
    private Long sessionId;
    private Long tenantId;
    private String materialType;
    private String objectKey;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String etag;
    private String contentSha256;
    private Long uploadedByAccountId;
    private Long uploadedByUserId;
    private LocalDateTime createTime;
}
