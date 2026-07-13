// 关联业务：保存和预览物业服务组织登记、核验所需的私有证明材料。
package com.pangu.domain.repository;

import java.net.URL;
import java.time.Duration;

/**
 * 物业服务组织材料私有对象存储端口。
 */
public interface PropertyServiceOrganizationMaterialStorage {

    StoredObjectMetadata put(String objectKey, byte[] content, String contentType, String contentMd5Base64);

    URL createPreviewUrl(String objectKey, String originalFileName, Duration validity);

    void delete(String objectKey);

    record StoredObjectMetadata(long size, String contentType, String etag) {
    }
}
