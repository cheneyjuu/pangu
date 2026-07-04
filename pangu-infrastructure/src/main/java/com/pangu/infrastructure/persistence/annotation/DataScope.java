package com.pangu.infrastructure.persistence.annotation;

import java.lang.annotation.*;

/**
 * 数据权限过滤注解
 * 标记在 MyBatis Mapper 接口方法上，表明该方法查询需要进行行级数据范围过滤
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {
    /**
     * 部门表/字段的别名 (如 "d" 或 "")，对应 SQL 中的 dept_id 过滤
     */
    String deptAlias() default "";

    /**
     * 用户表/字段的别名 (如 "u" 或 "")，对应 SQL 中的 user_id/uid 过滤
     */
    String userAlias() default "";

    /**
     * 用户身份列名（默认 user_id；c_owner_property 等业主表请显式指定 "uid"）
     */
    String userColumn() default "user_id";

    /**
     * 物理楼栋表/字段的别名 (如 "b" 或 "")，对应 SQL 中的 building_id 过滤
     */
    String buildingAlias() default "";

    /**
     * 租户表/字段的别名 (如 "op" 或 "")，对应 SQL 中的 tenant_id 过滤。
     *
     * <p>OWNER_GROUP 跨小区范围需要 tenant_id + building_id 组合过滤；
     * 未配置时保留旧的 building_id-only 行为。
     */
    String tenantAlias() default "";
}
