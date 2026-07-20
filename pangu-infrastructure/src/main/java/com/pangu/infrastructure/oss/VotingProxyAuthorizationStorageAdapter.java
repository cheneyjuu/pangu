// 关联业务：复用私有对象存储保存正式表决书面委托原件并签发短时预览地址。
package com.pangu.infrastructure.oss;

import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import com.pangu.domain.repository.VotingProxyAuthorizationStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class VotingProxyAuthorizationStorageAdapter implements VotingProxyAuthorizationStorage {

    private final RepairEvidenceObjectStorage delegate;

    @Override
    public StoredObjectMetadata put(String objectKey, byte[] content, String contentType, String contentMd5Base64) {
        RepairEvidenceObjectStorage.StoredObjectMetadata stored = delegate.put(
                objectKey, content, contentType, contentMd5Base64);
        return new StoredObjectMetadata(stored.size(), stored.contentType(), stored.etag());
    }

    @Override
    public URL createPreviewUrl(String objectKey, String originalFileName, Duration validity) {
        return delegate.createPreviewUrl(objectKey, originalFileName, validity);
    }

    @Override
    public void delete(String objectKey) {
        delegate.delete(objectKey);
    }
}
