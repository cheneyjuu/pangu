package com.pangu.application.admin;

/**
 * 角色管理应用层业务异常（含可机读的失败原因）。
 *
 * <p>{@link Reason} 与 web 层 {@code RoleAdminErrorCode} 一一映射，由
 * {@code GlobalExceptionHandler} + {@code RoleAdminExceptionTranslator} 完成转换。
 * 本异常本身不依赖 web 层，避免反向依赖。
 */
public class RoleAdminApplicationException extends RuntimeException {

    public enum Reason {
        /** 角色不存在（按 roleId 查询为空）。 */
        ROLE_NOT_FOUND,
        /** {@code sys_role.role_key} UNIQUE 冲突。 */
        ROLE_KEY_DUPLICATE,
        /** trigger 7 拒绝删除 is_system=1 的预置角色。 */
        ROLE_PROTECTED,
        /** 同一 (role_id, permission_key) 已经存在授予记录。 */
        PERMISSION_ALREADY_ASSIGNED,
        /** trigger 6 一致性失败：端归属不匹配 / redline 缺 fixed_data_scope / permission 不存在。 */
        PERMISSION_ASSIGNMENT_INCONSISTENT,
        /** 必填字段缺失或非法（角色键为空、dept_category 取值非 G/B/S 等应用层校验）。 */
        ROLE_PARAM_INVALID
    }

    private final Reason reason;

    public RoleAdminApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RoleAdminApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
