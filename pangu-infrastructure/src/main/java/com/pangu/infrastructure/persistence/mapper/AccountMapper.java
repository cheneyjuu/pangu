package com.pangu.infrastructure.persistence.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 自然人主体（{@code t_account}）查询 Mapper（M1 RBAC 重构后版本）。
 *
 * <p>仅提供登录环节所需的基础查询：手机号 → account_id + 最近活跃身份。
 * 其余 sys_user / c_user 装配走 {@link UserContextMapper}（请求维度 + Redis 5min TTL，M2 引入）。
 */
@Mapper
public interface AccountMapper {

    /**
     * 根据手机号反查自然人主体行；不存在或被禁用返回 null。
     *
     * <p>用于 {@code AuthService.login}：手机号 → 自然人 → 默认身份 → JWT。
     */
    AccountRow selectByPhone(@Param("phone") String phone);

    /**
     * 根据 account_id 反查自然人主体行（{@code switch-identity} 校验用）。
     */
    AccountRow selectById(@Param("accountId") Long accountId);

    /**
     * 更新最近活跃身份（{@code login} / {@code switch-identity} 调用后回填）。
     */
    int updateLastActiveIdentity(@Param("accountId") Long accountId,
                                 @Param("identityId") Long identityId,
                                 @Param("identityType") String identityType);

    /**
     * t_account 行视图。
     */
    @Data
    class AccountRow {
        private Long accountId;
        private String phone;
        /** 1=正常, 2=禁用, 3=注销。 */
        private Integer status;
        /** 最近活跃身份 ID（{@code sys_user.user_id} 或 {@code c_user.uid}）；首次登录可能为 null。 */
        private Long lastActiveIdentityId;
        /** SYS_USER 或 C_USER；首次登录可能为 null。 */
        private String lastActiveIdentityType;
    }
}
