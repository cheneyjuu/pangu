package com.pangu.domain.repository;

import java.net.URL;
import java.time.Duration;

public interface RepairEvidenceObjectStorage {

    StoredObjectMetadata put(
            String objectKey, byte[] content, String contentType, String contentMd5Base64);

    URL createDownloadUrl(String objectKey, Duration validity);

    void delete(String objectKey);

    record StoredObjectMetadata(long size, String contentType, String etag) {
    }
}
