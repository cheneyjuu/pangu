// 关联业务：保存小区注册证明材料，并签发短时私有预览地址。
package com.pangu.domain.repository;

import java.net.URL;
import java.time.Duration;

/**
 * 小区注册材料私有对象存储端口。
 */
public interface CommunityRegistrationMaterialStorage {

    StoredObjectMetadata put(String objectKey, byte[] content, String contentType, String contentMd5Base64);

    URL createPreviewUrl(String objectKey, String originalFileName, Duration validity);

    void delete(String objectKey);

    record StoredObjectMetadata(long size, String contentType, String etag) {
    }
}
