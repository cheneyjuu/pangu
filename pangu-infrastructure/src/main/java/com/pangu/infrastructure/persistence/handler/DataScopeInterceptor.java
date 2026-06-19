package com.pangu.infrastructure.persistence.handler;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.infrastructure.persistence.annotation.DataScope;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MyBatis 动态 SQL 数据权限拦截器（M1 RBAC 重构后版本）。
 *
 * <p>核心职责：拦截 {@code StatementHandler.prepare} 方法，根据当前线程
 * {@link UserContext#dataScopeType()} 重写 SQL 注入行级 WHERE 条件：
 * <ul>
 *   <li>{@link DataScopeType#ALL_COMMUNITY}：社区全量视野，直接放行；</li>
 *   <li>{@link DataScopeType#OWNER_GROUP}：业主集合，通过
 *       {@link UserContext#authorizedBuildingIds()} 拼接 {@code building_id IN (...)}；
 *       无授权楼栋时拼接 {@code IN (-1)} 截流；</li>
 *   <li>{@link DataScopeType#ORG_ONLY}：仅本部门，通过 {@code deptId} 拼接 {@code dept_id = ?}。</li>
 * </ul>
 *
 * <p>安全前置约束：
 * <ul>
 *   <li>{@link UserContextHolder#current()} 为 null 时直接拒绝 — 拒绝任何在无上下文情况下放行原 SQL；</li>
 *   <li>解析失败时抛 {@link IllegalStateException} 由 GlobalExceptionHandler 转 500；</li>
 *   <li>注释驱动 — 仅 {@code @DataScope} 标注的 mapper 方法被拦截。</li>
 * </ul>
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
@Slf4j
@Component
public class DataScopeInterceptor implements Interceptor {

    private final UserContextHolder userContextHolder;

    public DataScopeInterceptor(UserContextHolder userContextHolder) {
        this.userContextHolder = userContextHolder;
    }

    @Override
    public Object intercept(Invocation invocation) throws Exception {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);

        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        String mappedId = mappedStatement.getId();

        DataScope dataScopeAnno = getAnnotation(mappedId);
        if (dataScopeAnno == null) {
            return invocation.proceed();
        }

        UserContext ctx = userContextHolder.current();
        if (ctx == null) {
            // 上下文缺失即拒绝放行 — 反向漏洞修补（不再在生产路径走 mock 兜底）
            throw new IllegalStateException(
                    "数据权限上下文缺失，拒绝访问受保护查询：mappedId=" + mappedId);
        }

        DataScopeType scope = ctx.dataScopeType();
        if (scope == null || scope == DataScopeType.ALL_COMMUNITY) {
            // 社区全量视野（业委会主任 / 街道办 / 党组织书记）— 直接放行
            return invocation.proceed();
        }

        BoundSql boundSql = statementHandler.getBoundSql();
        String originalSql = boundSql.getSql();
        String modifiedSql = rewriteSql(originalSql, dataScopeAnno, ctx);
        metaObject.setValue("delegate.boundSql.sql", modifiedSql);
        return invocation.proceed();
    }

    private String rewriteSql(String sql, DataScope dataScopeAnno, UserContext ctx) {
        try {
            Select selectStatement = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainSelect = (PlainSelect) selectStatement.getSelectBody();

            String condition = buildScopeCondition(dataScopeAnno, ctx);
            Expression scopeExpression = CCJSqlParserUtil.parseCondExpression(condition);
            Expression originalWhere = plainSelect.getWhere();
            if (originalWhere == null) {
                plainSelect.setWhere(scopeExpression);
            } else {
                plainSelect.setWhere(new AndExpression(originalWhere, scopeExpression));
            }
            return selectStatement.toString();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("DataScope SQL 重写失败，拒绝放行原 SQL：sql={}", sql, e);
            throw new IllegalStateException("数据权限 SQL 重写失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据 dataScopeType 构造 WHERE 子句。
     * 调用方保证 scope ≠ {@link DataScopeType#ALL_COMMUNITY}（已在 intercept 提前放行）。
     */
    private String buildScopeCondition(DataScope dataScopeAnno, UserContext ctx) {
        DataScopeType scope = ctx.dataScopeType();
        if (scope == DataScopeType.OWNER_GROUP) {
            String buildingAlias = dataScopeAnno.buildingAlias();
            String prefix = buildingAlias.isEmpty() ? "" : buildingAlias + ".";
            Set<Long> buildingIds = ctx.authorizedBuildingIds();
            if (buildingIds == null || buildingIds.isEmpty()) {
                return " " + prefix + "building_id IN (-1) ";
            }
            String idListStr = buildingIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            return " " + prefix + "building_id IN (" + idListStr + ") ";
        }
        if (scope == DataScopeType.ORG_ONLY) {
            String deptAlias = dataScopeAnno.deptAlias();
            String prefix = deptAlias.isEmpty() ? "" : deptAlias + ".";
            if (ctx.deptId() == null) {
                throw new IllegalStateException(
                        "ORG_ONLY 范围要求 UserContext.deptId 非空，当前为 null（accountId="
                                + ctx.accountId() + "）");
            }
            return " " + prefix + "dept_id = " + ctx.deptId() + " ";
        }
        throw new IllegalStateException("未实现的 dataScope 类型: " + scope);
    }

    private DataScope getAnnotation(String mappedId) {
        try {
            int lastDotIndex = mappedId.lastIndexOf(".");
            String className = mappedId.substring(0, lastDotIndex);
            String methodName = mappedId.substring(lastDotIndex + 1);

            Class<?> mapperClass = Class.forName(className);
            for (Method method : mapperClass.getMethods()) {
                if (method.getName().equals(methodName)
                        && method.isAnnotationPresent(DataScope.class)) {
                    return method.getAnnotation(DataScope.class);
                }
            }
        } catch (Exception e) {
            // 忽略反射异常 — 当作无注解处理（不拦截）
        }
        return null;
    }

    @Override
    public void setProperties(Properties properties) {
        // 无自定义属性配置
    }
}
