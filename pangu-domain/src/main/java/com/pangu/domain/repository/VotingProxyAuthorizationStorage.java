// 关联业务：私有保存书面委托原件，并只向有权限的办理人员签发短时预览地址。
package com.pangu.domain.repository;

import java.net.URL;
import java.time.Duration;

public interface VotingProxyAuthorizationStorage {

    StoredObjectMetadata put(String objectKey, byte[] content, String contentType, String contentMd5Base64);

    URL createPreviewUrl(String objectKey, String originalFileName, Duration validity);

    void delete(String objectKey);

    record StoredObjectMetadata(long size, String contentType, String etag) {
    }
}
