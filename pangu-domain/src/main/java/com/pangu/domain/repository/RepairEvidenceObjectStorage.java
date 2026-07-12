// 关联业务：保存维修证据、报审材料及盖章结果文件，并提供受控预览地址。
package com.pangu.domain.repository;

import java.net.URL;
import java.time.Duration;

public interface RepairEvidenceObjectStorage {

    StoredObjectMetadata put(
            String objectKey, byte[] content, String contentType, String contentMd5Base64);

    byte[] read(String objectKey);

    boolean exists(String objectKey);

    URL createDownloadUrl(String objectKey, Duration validity);

    URL createPreviewUrl(String objectKey, String originalFileName, Duration validity);

    void delete(String objectKey);

    record StoredObjectMetadata(long size, String contentType, String etag) {
    }
}
