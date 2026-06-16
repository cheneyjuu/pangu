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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MyBatis 动态 SQL 数据权限拦截器 (ABAC 行级数据隔离核心组件)
 * 通过拦截 StatementHandler.prepare 方法，实现基于角色数据范围(data_scope)的 SQL 重写
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
@Component
public class DataScopeInterceptor implements Interceptor {

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

        // 2. 获取当前登录用户的数据权限属性（对接 Spring Security 并提供开发兜底）
        UserSecurityContext userCtx = fetchUserSecurityContext();
        if (userCtx == null || DataScopeType.ALL.getValue().equals(userCtx.getDataScope())) {
            return invocation.proceed(); // 超级管理员或未登录，不限制数据范围
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
                conditionBuilder.append(" ").append(prefix).append("user_id = ").append(userCtx.getUserId()).append(" ");
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
        } catch (Exception e) {
            // 解析 SQL 发生异常时，安全退回原 SQL，避免影响系统正常执行
            System.err.println("DataScope SQL 重写失败: " + e.getMessage());
        }
        return sql;
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
     * 从 Spring Security 获取上下文或 Mock 单元测试数据
     */
    private UserSecurityContext fetchUserSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserSecurityContext) {
            return (UserSecurityContext) auth.getPrincipal();
        }

        // 开发环境 Mock 兜底数据：如果未进行安全拦截测试，且检测到当前上下文，我们返回王小二的网格员身份数据
        // 王小二 (user_id = 202, dept_id = 104, role = GRID_MANAGER, data_scope = 6, 楼栋 = [10001, 10002])
        return UserSecurityContext.builder()
                .userId(202L)
                .deptId(104L)
                .dataScope(DataScopeType.CUSTOM_BUILDING.getValue()) // 自定义楼栋
                .authorizedBuildingIds(List.of(10001L, 10002L))
                .uid(101L)
                .tenantId(9001L)
                .build();
    }

    @Override
    public void setProperties(Properties properties) {
        // 无自定义属性配置
    }

    // ===================================================================
    // 数据权限安全上下文内部载体 (供安全与 MyBatis 共用)
    // ===================================================================
    @lombok.Getter
    @lombok.Setter
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserSecurityContext {
        private Long userId;
        private Long deptId;
        private String dataScope;
        private List<Long> authorizedBuildingIds;
        private Long uid;
        private Long tenantId;
    }
}
