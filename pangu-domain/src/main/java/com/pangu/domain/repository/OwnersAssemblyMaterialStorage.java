// 关联业务：为业主大会办理材料提供私有对象存储边界，隔离公告、选票和纸质凭证的存储实现。
package com.pangu.domain.repository;

/** 业主大会原始材料的私有对象存储端口。 */
public interface OwnersAssemblyMaterialStorage {

    StoredObjectMetadata put(String objectKey, byte[] content, String contentType, String contentMd5Base64);

    void delete(String objectKey);

    record StoredObjectMetadata(long size, String contentType, String etag) {
    }
}
