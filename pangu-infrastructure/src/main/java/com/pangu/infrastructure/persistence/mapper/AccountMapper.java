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
     * C 端冷启动：手机号首次登录时创建自然人账号。
     */
    int insertAccount(AccountInsertRow row);

    /**
     * C 端冷启动：为自然人创建业主端身份。
     */
    int insertCUser(CUserInsertRow row);

    /**
     * 根据 account_id 查询 C 端 uid。
     */
    Long selectCUserUidByAccountId(@Param("accountId") Long accountId);

    /**
     * 读取当前自然人实名与手机号原值，供房产绑定暗号对账。
     */
    AccountIdentityRow selectIdentityByAccountId(@Param("accountId") Long accountId);

    /**
     * L2 实名：更新自然人实名信息并标记已实名。
     */
    int updateIdentity(@Param("accountId") Long accountId,
                       @Param("realName") String realName,
                       @Param("idCardNumber") String idCardNumber);

    /**
     * 更新最近活跃身份（{@code login} / {@code switch-identity} 调用后回填）。
     */
    int updateLastActiveIdentity(@Param("accountId") Long accountId,
                                 @Param("identityId") Long identityId,
                                 @Param("identityType") String identityType);

    /**
     * 更新 C 端最近活跃小区，房产绑定成功后回填。
     */
    int updateCUserLastActiveTenant(@Param("uid") Long uid,
                                    @Param("tenantId") Long tenantId);

    @Data
    class AccountInsertRow {
        private Long accountId;
        private String phone;
        private String realName;
        private int realNameVerified;
    }

    @Data
    class CUserInsertRow {
        private Long uid;
        private Long accountId;
        private int authLevel;
    }

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

    @Data
    class AccountIdentityRow {
        private Long accountId;
        private String phone;
        private String realNameCipher;
        private Integer realNameVerified;
        private Integer status;
    }
}
