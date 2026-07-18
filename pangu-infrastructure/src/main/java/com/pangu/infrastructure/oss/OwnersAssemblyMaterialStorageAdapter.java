// 关联业务：复用私有 OSS 存储业主大会公告、选票和纸质送达、回收凭证。
package com.pangu.infrastructure.oss;

import com.pangu.domain.repository.OwnersAssemblyMaterialStorage;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OwnersAssemblyMaterialStorageAdapter implements OwnersAssemblyMaterialStorage {

    private final RepairEvidenceObjectStorage delegate;

    @Override
    public StoredObjectMetadata put(String objectKey, byte[] content, String contentType, String contentMd5Base64) {
        RepairEvidenceObjectStorage.StoredObjectMetadata stored = delegate.put(
                objectKey, content, contentType, contentMd5Base64);
        return new StoredObjectMetadata(stored.size(), stored.contentType(), stored.etag());
    }

    @Override
    public URL createDownloadUrl(String objectKey, Duration validity) {
        return delegate.createDownloadUrl(objectKey, validity);
    }

    @Override
    public void delete(String objectKey) {
        delegate.delete(objectKey);
    }
}
