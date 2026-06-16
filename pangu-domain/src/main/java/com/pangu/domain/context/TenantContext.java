package com.pangu.domain.context;

/**
 * 租户上下文（基于 ThreadLocal 隔离）
 * 用于在当前执行线程的调用链中透明流转租户ID (tenant_id)
 */
public class TenantContext {

    private static final ThreadLocal<Long> CONTEXT = new ThreadLocal<>();

    /**
     * 设置当前线程的租户 ID
     * @param tenantId 租户 ID
     */
    public static void setTenantId(Long tenantId) {
        CONTEXT.set(tenantId);
    }

    /**
     * 获取当前线程的租户 ID
     * @return 租户 ID
     */
    public static Long getTenantId() {
        return CONTEXT.get();
    }

    /**
     * 清理当前线程的租户上下文，防止内存泄露 (防 ThreadLocal 泄露最佳实践)
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
