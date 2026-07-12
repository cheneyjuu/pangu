// 关联业务：复用私有 OSS 能力保存和预览小区注册审核材料。
package com.pangu.infrastructure.oss;

import com.pangu.domain.repository.CommunityRegistrationMaterialStorage;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Duration;

/**
 * 小区注册材料对象存储适配器。
 */
@Component
@RequiredArgsConstructor
public class CommunityRegistrationMaterialStorageAdapter implements CommunityRegistrationMaterialStorage {

    private final RepairEvidenceObjectStorage delegate;

    @Override
    public StoredObjectMetadata put(
            String objectKey,
            byte[] content,
            String contentType,
            String contentMd5Base64) {
        RepairEvidenceObjectStorage.StoredObjectMetadata stored =
                delegate.put(objectKey, content, contentType, contentMd5Base64);
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
