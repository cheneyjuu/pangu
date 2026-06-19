package com.pangu.domain.context;

/**
 * 装载当前请求 {@link UserContext} 的端口（Hexagonal Port）。
 *
 * <p>由 infrastructure 适配器以 MyBatis JOIN +（可选）Redis 5min TTL 缓存实现：
 * 给定自然人 {@code accountId} + 活跃身份 {@code identityType} +
 * {@code activeIdentityId}，去 {@code sys_user / sys_user_role / sys_role /
 * sys_role_permission / sys_user_building / t_account / c_user} 表反查
 * 装配出完整 {@link UserContext}。
 *
 * <p>单点入口集中管理「身份 → RBAC 权限 / scope / building 列表」全部反查逻辑，
 * application/domain 层不直接依赖任何持久化细节。
 */
public interface UserContextLoader {

    /**
     * 根据自然人 + 活跃身份装配上下文。
     *
     * @param accountId            自然人主体 ID（{@code t_account.account_id}）
     * @param identityType         身份类型（SYS_USER 或 C_USER）
     * @param activeIdentityId     活跃身份 ID：{@code sys_user.user_id} 或 {@code c_user.uid}
     * @param tenantIdHint         JWT 中携带的 tenant 提示；适配器以此覆盖默认 tenant
     * @return 装配完成的 {@link UserContext}；身份不存在或已禁用时返回 null。
     */
    UserContext load(Long accountId,
                     UserContext.IdentityType identityType,
                     Long activeIdentityId,
                     Long tenantIdHint);
}
