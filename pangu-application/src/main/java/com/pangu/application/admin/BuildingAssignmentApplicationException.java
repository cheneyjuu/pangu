package com.pangu.application.admin;

/**
 * 楼栋责任田分配应用层业务异常（含可机读的失败原因）。
 *
 * <p>{@link Reason} 与 web 层 {@code BuildingAssignmentErrorCode} 一一映射，由
 * {@code GlobalExceptionHandler} + {@code BuildingAssignmentExceptionTranslator} 转换。
 * 沿用 {@code RoleAdminApplicationException} 范式（Reason 枚举 + 独立 translator）。
 */
public class BuildingAssignmentApplicationException extends RuntimeException {

    public enum Reason {
        /** 分配者角色不在白名单（非主任/超管/书记）。 */
        FORBIDDEN,
        /** 必填参数缺失（roleKey/userId/buildingId）。 */
        PARAM_INVALID,
        /** 目标用户不存在或不持可分配角色。 */
        USER_NOT_FOUND,
        /** 楼栋不在分配者租户范围内。 */
        BUILDING_NOT_IN_SCOPE,
        /** 撤销时无生效授予记录。 */
        ASSIGNMENT_NOT_FOUND,
        /** 目标用户不满足合规要求（账号状态/实名/楼栋上限）。 */
        USER_NOT_COMPLIANT,
        /** 该楼栋已被同角色其他用户占用；前端需走 force=true 二次确认转移。 */
        BUILDING_OCCUPIED_BY_SAME_ROLE
    }

    private final Reason reason;

    public BuildingAssignmentApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
