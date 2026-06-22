package com.pangu.domain.gateway.dto;

/**
 * 角色分页查询参数（平台级，无 tenantId）。
 *
 * <p>{@code sys_role} 是 SaaS 平台级配置表（跨租户共享，无 tenant/dept/building 维度），
 * 故本查询对象不携带租户上下文——行级收口由 endpoint {@code @PreAuthorize} 平台级权限
 * {@code admin:role:read} 保证，而非 {@code @DataScope}。
 *
 * <p>所有过滤字段均可空：null 即不过滤。{@code roleKey/roleName} 走模糊匹配（LIKE '%x%'）。
 *
 * @param roleKey   可空；非空则 role_key LIKE '%x%'
 * @param roleName  可空；非空则 role_name LIKE '%x%'
 * @param isSystem  可空；0/1 过滤
 * @param status    可空；'0' 正常 / '1' 停用
 * @param page      页码（1-based，controller 已做 Math.max(page,1) 兜底）
 * @param size      页大小（controller 已做 Math.min(Math.max(size,1),100) 兜底）
 */
public record RoleQuery(
        String roleKey,
        String roleName,
        Integer isSystem,
        String status,
        int page,
        int size) {
}
