package com.pangu.infrastructure.persistence.handler;

import com.pangu.domain.model.user.DataScopeType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 非生产环境的 mock 数据权限上下文兜底（王小二网格员）。
 *
 * <p>本类仅在 {@code @Profile("!prod")} 下激活——生产环境不存在该 Bean，
 * {@link DataScopeInterceptor#fetchUserSecurityContext()} 在 SecurityContext 缺失时
 * 直接返回 null，由调用方按「无权限」处理（拒绝放行）。
 *
 * <p>该 Bean 的存在意义：避免开发态/测试态在每次请求都需要走完整登录流程；
 * 但坚决不允许在生产路径中以任何方式被注入。
 */
@Component
@Profile("!prod")
public class MockDataScopeUserSecurityContextProvider {

    /** 王小二（user_id = 202, dept_id = 104, GRID_MANAGER, 楼栋 = [10001, 10002]）。 */
    public DataScopeInterceptor.UserSecurityContext provide() {
        return DataScopeInterceptor.UserSecurityContext.builder()
                .userId(202L)
                .deptId(104L)
                .dataScope(DataScopeType.CUSTOM_BUILDING.getValue())
                .authorizedBuildingIds(List.of(10001L, 10002L))
                .uid(101L)
                .tenantId(9001L)
                .build();
    }
}
