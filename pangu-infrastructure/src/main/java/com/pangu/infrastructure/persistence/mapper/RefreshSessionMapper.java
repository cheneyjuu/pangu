// 关联业务：将刷新凭证的摘要和会话身份上下文持久化，支持 JWT 过期后的安全续期。
package com.pangu.infrastructure.persistence.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 刷新会话数据库访问 Mapper。 */
@Mapper
public interface RefreshSessionMapper {

    int insert(NewRefreshSessionRow row);

    /**
     * 以一次 UPDATE ... RETURNING 原子消费凭证，防止并发重放。
     */
    RefreshSessionRow consume(@Param("tokenHash") String tokenHash);

    @Data
    class NewRefreshSessionRow {
        private String tokenHash;
        private Long accountId;
        private String identityType;
        private Long activeIdentityId;
        private Long tenantId;
        private long expiresInSeconds;
    }

    @Data
    class RefreshSessionRow {
        private Long accountId;
        private String identityType;
        private Long activeIdentityId;
        private Long tenantId;
    }
}
