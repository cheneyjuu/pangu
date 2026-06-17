package com.pangu.infrastructure.persistence.handler;

import com.pangu.domain.model.user.DataScopeType;
import com.pangu.infrastructure.persistence.annotation.DataScope;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * MyBatis 动态 SQL 数据权限拦截器 (ABAC 行级数据隔离核心组件)
 * 通过拦截 StatementHandler.prepare 方法，实现基于角色数据范围(data_scope)的 SQL 重写
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
@Slf4j
@Component
public class DataScopeInterceptor implements Interceptor {

    /**
     * 非生产环境兜底：注入 {@link MockDataScopeUserSecurityContextProvider} 提供 mock 上下文。
     * 生产环境（{@code spring.profiles.active=prod}）下该 Bean 不存在，
     * {@link #fetchUserSecurityContext()} 在 SecurityContext 缺失时直接返回 null，
     * 由 {@link #intercept} 按「无权限」拒绝放行。
     */
    @Autowired(required = false)
    private MockDataScopeUserSecurityContextProvider mockProvider;

    @Override
    public Object intercept(Invocation invocation) throws Exception {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);

        // 获取 MappedStatement (包含方法ID及配置)
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        String mappedId = mappedStatement.getId();

        // 1. 获取 Mapper 方法上的 @DataScope 注解
        DataScope dataScopeAnno = getAnnotation(mappedId);
        if (dataScopeAnno == null) {
            return invocation.proceed(); // 无注解，直接放行
        }

        // 2. 获取当前登录用户的数据权限属性（生产路径强制要求 SecurityContext 命中）
        UserSecurityContext userCtx = fetchUserSecurityContext();
        if (userCtx == null) {
            // 生产环境无 mock 兜底：上下文缺失即拒绝放行（反向漏洞修补）
            throw new IllegalStateException(
                    "数据权限上下文缺失，拒绝访问受保护查询：mappedId=" + mappedId);
        }
        if (DataScopeType.ALL.getValue().equals(userCtx.getDataScope())) {
            return invocation.proceed(); // 超级管理员不限制数据范围
        }

        // 3. 解析并重写 SQL
        BoundSql boundSql = statementHandler.getBoundSql();
        String originalSql = boundSql.getSql();

        String modifiedSql = rewriteSql(originalSql, dataScopeAnno, userCtx);
        metaObject.setValue("delegate.boundSql.sql", modifiedSql);

        return invocation.proceed();
    }

    /**
     * 重写 SQL 拼接数据范围约束
     */
    private String rewriteSql(String sql, DataScope dataScopeAnno, UserSecurityContext userCtx) {
        try {
            Select selectStatement = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainSelect = (PlainSelect) selectStatement.getSelectBody();

            StringBuilder conditionBuilder = new StringBuilder();

            // 方案 6: 自定义楼栋数据权限 (主要针对网格员/楼栋志愿者)
            if (DataScopeType.CUSTOM_BUILDING.getValue().equals(userCtx.getDataScope())) {
                String buildingAlias = dataScopeAnno.buildingAlias();
                String prefix = buildingAlias.isEmpty() ? "" : buildingAlias + ".";
                
                List<Long> buildingIds = userCtx.getAuthorizedBuildingIds();
                if (buildingIds == null || buildingIds.isEmpty()) {
                    // 无管辖楼栋，拼接 1=0 拦截所有数据
                    conditionBuilder.append(" ").append(prefix).append("building_id IN (-1) ");
                } else {
                    String idListStr = buildingIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                    conditionBuilder.append(" ").append(prefix).append("building_id IN (").append(idListStr).append(") ");
                }
            }
            // 方案 4: 本部门及以下数据权限
            else if (DataScopeType.OWN_DEPT_AND_CHILD.getValue().equals(userCtx.getDataScope())) {
                String deptAlias = dataScopeAnno.deptAlias();
                String prefix = deptAlias.isEmpty() ? "" : deptAlias + ".";
                
                // 现实中会通过 sys_dept 树计算出当前部门及其所有子部门 ID
                // 此处进行 mock：拼接当前用户所属部门及子集
                conditionBuilder.append(" ").append(prefix).append("dept_id = ").append(userCtx.getDeptId()).append(" ");
            }
            // 方案 5: 仅本人数据权限
            else if (DataScopeType.SELF.getValue().equals(userCtx.getDataScope())) {
                String userAlias = dataScopeAnno.userAlias();
                String prefix = userAlias.isEmpty() ? "" : userAlias + ".";
                String userColumn = dataScopeAnno.userColumn();
                // c_owner_property 业主表用 uid 字段，业务表用 user_id；通过 @DataScope(userColumn=...) 显式指定
                conditionBuilder.append(" ").append(prefix).append(userColumn)
                        .append(" = ").append(userCtx.getUid()).append(" ");
            }
            else {
                // CUSTOM_DEPT / OWN_DEPT 等尚未实现的 dataScope 路径：受保护查询无法生成约束条件，
                // 拒绝放行原 SQL，避免「无 WHERE 条件 → 全表扫描 → 越权读」的硬性安全漏洞。
                throw new IllegalStateException(
                        "未实现的 dataScope 类型，受保护查询不能放行原 SQL: dataScope=" + userCtx.getDataScope());
            }

            if (conditionBuilder.length() > 0) {
                Expression originalWhere = plainSelect.getWhere();
                Expression scopeExpression = CCJSqlParserUtil.parseCondExpression(conditionBuilder.toString());

                if (originalWhere == null) {
                    plainSelect.setWhere(scopeExpression);
                } else {
                    plainSelect.setWhere(new AndExpression(originalWhere, scopeExpression));
                }
                return selectStatement.toString();
            }
            // 理论上不可达——所有 dataScope 分支要么填 conditionBuilder 要么直接 throw
            throw new IllegalStateException(
                    "DataScope SQL 重写后 conditionBuilder 为空，拒绝放行原 SQL: dataScope=" + userCtx.getDataScope());
        } catch (IllegalStateException e) {
            // 已经是显式拒绝放行的状态，直接向上抛
            throw e;
        } catch (Exception e) {
            // 解析失败不再静默放行——审计能看到失败而非默默放行原 SQL，
            // 由上层 GlobalExceptionHandler 转化为 500 系统错误（Phase 6 将映射至 ElectionErrorCode.DATA_SCOPE_PARSE_FAILED）
            log.error("DataScope SQL 重写失败，拒绝放行原 SQL：mappedId 由调用方记录, sql={}", sql, e);
            throw new IllegalStateException("数据权限 SQL 重写失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取类/方法上的数据权限注解
     */
    private DataScope getAnnotation(String mappedId) {
        try {
            int lastDotIndex = mappedId.lastIndexOf(".");
            String className = mappedId.substring(0, lastDotIndex);
            String methodName = mappedId.substring(lastDotIndex + 1);

            Class<?> mapperClass = Class.forName(className);
            for (Method method : mapperClass.getMethods()) {
                if (method.getName().equals(methodName)) {
                    if (method.isAnnotationPresent(DataScope.class)) {
                        return method.getAnnotation(DataScope.class);
                    }
                }
            }
        } catch (Exception e) {
            // 忽略反射异常
        }
        return null;
    }

    /**
     * 从 Spring Security 获取上下文，缺失时根据 profile 走 mock 或返回 null。
     *
     * <p>生产环境（无 {@link MockDataScopeUserSecurityContextProvider} Bean）下，
     * 当 SecurityContext 缺失或 principal 类型不符时返回 null，
     * 由 {@link #intercept} 按「无权限」拒绝放行。
     */
    private UserSecurityContext fetchUserSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserSecurityContext) {
            return (UserSecurityContext) auth.getPrincipal();
        }
        // 仅非生产环境（@Profile("!prod")）注入 mockProvider；生产环境直接返回 null
        if (mockProvider != null) {
            return mockProvider.provide();
        }
        return null;
    }

    @Override
    public void setProperties(Properties properties) {
        // 无自定义属性配置
    }

    // ===================================================================
    // 数据权限安全上下文内部载体 (供安全与 MyBatis 共用)
    // ===================================================================
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSecurityContext {
        private Long userId;
        private Long deptId;
        /**
         * 部门类型（来自 sys_dept.dept_type）：1=街道办，2=居委会，3=物业，
         * 业主等无 sys_user 绑定的纯 C 端用户为 null。
         */
        private Integer deptType;
        private String dataScope;
        private List<Long> authorizedBuildingIds;
        private Long uid;
        private Long tenantId;
    }
}
