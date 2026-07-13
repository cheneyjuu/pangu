// 关联业务：定义物业管理模式变更决议材料的私有对象存储端口。
package com.pangu.domain.repository;

import java.net.URL;
import java.time.Duration;

/**
 * 物业管理模式变更材料私有对象存储端口。
 */
public interface PropertyManagementModeChangeMaterialStorage {

    StoredObjectMetadata put(String objectKey, byte[] content, String contentType, String contentMd5Base64);

    URL createPreviewUrl(String objectKey, String originalFileName, Duration validity);

    void delete(String objectKey);

    record StoredObjectMetadata(long size, String contentType, String etag) {
    }
}
