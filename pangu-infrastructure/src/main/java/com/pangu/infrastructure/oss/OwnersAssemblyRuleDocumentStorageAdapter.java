// 关联业务：复用私有 OSS 存储保存业主大会议事规则原件，并仅生成短时预览地址。
package com.pangu.infrastructure.oss;

import com.pangu.domain.repository.OwnersAssemblyRuleDocumentStorage;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OwnersAssemblyRuleDocumentStorageAdapter implements OwnersAssemblyRuleDocumentStorage {

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
