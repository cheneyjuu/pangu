// 关联业务：保存和预览小区维修征询规则的私有备案原件。
package com.pangu.domain.repository;

import java.net.URL;
import java.time.Duration;

public interface RepairDecisionRuleDocumentStorage {

    StoredObjectMetadata put(String objectKey, byte[] content, String contentType, String contentMd5Base64);

    URL createPreviewUrl(String objectKey, String originalFileName, Duration validity);

    void delete(String objectKey);

    record StoredObjectMetadata(long size, String contentType, String etag) {
    }
}
