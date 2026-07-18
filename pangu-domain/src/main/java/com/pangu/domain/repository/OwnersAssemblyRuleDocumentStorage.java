// 关联业务：保存和受控预览业主大会议事规则的私有原件。
package com.pangu.domain.repository;

import java.net.URL;
import java.time.Duration;

public interface OwnersAssemblyRuleDocumentStorage {

    StoredObjectMetadata put(String objectKey, byte[] content, String contentType, String contentMd5Base64);

    URL createPreviewUrl(String objectKey, String originalFileName, Duration validity);

    void delete(String objectKey);

    record StoredObjectMetadata(long size, String contentType, String etag) {
    }
}
