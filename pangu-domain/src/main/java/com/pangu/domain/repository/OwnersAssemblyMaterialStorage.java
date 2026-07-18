// 关联业务：为业主大会办理材料提供私有对象存储边界，隔离公告、选票和纸质凭证的存储实现。
package com.pangu.domain.repository;

import java.net.URL;
import java.time.Duration;

/** 业主大会原始材料的私有对象存储端口。 */
public interface OwnersAssemblyMaterialStorage {

    StoredObjectMetadata put(String objectKey, byte[] content, String contentType, String contentMd5Base64);

    /** 为已获授权的公告、方案或选票模板临时生成下载地址，不持久化 URL。 */
    URL createDownloadUrl(String objectKey, Duration validity);

    void delete(String objectKey);

    record StoredObjectMetadata(long size, String contentType, String etag) {
    }
}
