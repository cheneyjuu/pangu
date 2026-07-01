package com.pangu.bootstrap.persistence;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.asset.PropertyOwnership;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.infrastructure.persistence.mapper.OwnerPropertyMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code DataScopeInterceptor} 端到端 SQL 重写测试（M1 RBAC 重构后版本）。
 *
 * <p>本测试**不再走** {@code SecurityContextHolder} +
 * {@code DataScopeInterceptor.UserSecurityContext}（旧契约已剥离），
 * 改为直接操作 {@link UserContextHolder} ThreadLocal Bean，模拟 JWT 过滤器装配后的请求线程态。
 *
 * <p>所依赖的 V1.1 seed 数据（{@code c_owner_property}，tenant_id=10001）：
 * <pre>
 *   uid=70001 / building=30001 / room=30001101 / area=100
 *   uid=70002 / building=30002 / room=30002502 / area=85
 *   uid=70002 / building=30005 / room=30005201 / area=90
 * </pre>
 */
@SpringBootTest
public class DataScopeTest {

    @Autowired
    private OwnerPropertyMapper ownerPropertyMapper;

    @Autowired
    private UserContextHolder userContextHolder;

    @AfterEach
    public void tearDown() {
        userContextHolder.clear();
    }

    /**
     * {@link DataScopeType#ALL_COMMUNITY}：社区全量视野（业委会主任 / 街道办 / 党组织书记），
     * 拦截器直接放行，应返回 tenant 内全部 3 条记录。
     */
    @Test
    public void allCommunityScope_returnsAllRows() {
        userContextHolder.set(buildSysUserCtx(
                DataScopeType.ALL_COMMUNITY,
                Set.of()
        ));

        List<PropertyOwnership> list = ownerPropertyMapper.selectOwnershipsByBuilding(10001L);

        assertNotNull(list);
        assertEquals(3, list.size(), "ALL_COMMUNITY 应返回 tenant 全量房产关系");
    }

    /**
     * {@link DataScopeType#OWNER_GROUP}：业主集合（网格员 / 业主代表），
     * 拦截器附加 {@code building_id IN (...)} 过滤；管辖 30001/30002 时应只返回 2 条。
     */
    @Test
    public void ownerGroupScope_filtersByAuthorizedBuildings() {
        userContextHolder.set(buildSysUserCtx(
                DataScopeType.OWNER_GROUP,
                Set.of(30001L, 30002L)
        ));

        List<PropertyOwnership> list = ownerPropertyMapper.selectOwnershipsByBuilding(10001L);

        assertNotNull(list);
        assertEquals(2, list.size(), "OWNER_GROUP 应仅返回授权楼栋内的 2 条记录");
    }

    /**
     * {@link DataScopeType#OWNER_GROUP} + 空管辖楼栋：拦截器应附加
     * {@code building_id IN (-1)} 截流，返回 0 条。
     */
    @Test
    public void ownerGroupScope_emptyAuthorizedBuildings_returnsEmpty() {
        userContextHolder.set(buildSysUserCtx(
                DataScopeType.OWNER_GROUP,
                Set.of()
        ));

        List<PropertyOwnership> list = ownerPropertyMapper.selectOwnershipsByBuilding(10001L);

        assertNotNull(list);
        assertEquals(0, list.size(), "OWNER_GROUP 无授权楼栋应被 IN (-1) 截流");
    }

    private UserContext buildSysUserCtx(DataScopeType scope, Set<Long> authorizedBuildingIds) {
        return new UserContext(
                999803L,                                  // accountId（V1.1 刘主任）
                UserContext.IdentityType.SYS_USER,
                800003L,                                  // sys_user.user_id
                10001L,                                   // tenantId
                101L,                                     // deptId（求是居委会）
                UserContext.DeptCategory.G,
                2,
                scope,
                AuthenticationLevel.L1,
                "TEST_ROLE",
                Set.of(),
                authorizedBuildingIds
        );
    }
}
