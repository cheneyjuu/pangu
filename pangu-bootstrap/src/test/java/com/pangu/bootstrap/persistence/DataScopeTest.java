package com.pangu.bootstrap.persistence;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.asset.PropertyOwnership;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.infrastructure.persistence.mapper.OwnerPropertyMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * <p>测试使用独立 tenant/building 区间，避免依赖可演进的演示 seed 数据：
 * <pre>
 *   tenant=19001 / building=91001, 91002, 91005
 *   tenant=19002 / building=91001, 91002
 * </pre>
 */
@SpringBootTest
public class DataScopeTest {

    private static final long TENANT_PRIMARY = 19001L;
    private static final long TENANT_CROSS = 19002L;
    private static final long BUILDING_A = 91001L;
    private static final long BUILDING_B = 91002L;
    private static final long BUILDING_C = 91005L;

    @Autowired
    private OwnerPropertyMapper ownerPropertyMapper;

    @Autowired
    private UserContextHolder userContextHolder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setUp() {
        cleanTestRows();
        jdbcTemplate.update("""
                INSERT INTO c_owner_property
                    (uid, tenant_id, building_id, room_id, build_area, is_voting_delegate, account_status)
                VALUES
                    (70001, ?, ?, 190019100101, 100.00, 1, 1),
                    (70002, ?, ?, 190019100201,  85.00, 1, 1),
                    (70002, ?, ?, 190019100501,  90.00, 1, 1),
                    (70001, ?, ?, 190029100101,  88.00, 0, 1),
                    (70002, ?, ?, 190029100201,  77.00, 0, 1)
                """,
                TENANT_PRIMARY, BUILDING_A,
                TENANT_PRIMARY, BUILDING_B,
                TENANT_PRIMARY, BUILDING_C,
                TENANT_CROSS, BUILDING_A,
                TENANT_CROSS, BUILDING_B);
    }

    @AfterEach
    public void tearDown() {
        userContextHolder.clear();
        cleanTestRows();
    }

    private void cleanTestRows() {
        jdbcTemplate.update("""
                DELETE FROM c_owner_property
                WHERE tenant_id IN (?, ?)
                """, TENANT_PRIMARY, TENANT_CROSS);
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

        List<PropertyOwnership> list = ownerPropertyMapper.selectOwnershipsByBuilding(TENANT_PRIMARY);

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
                Set.of(BUILDING_A, BUILDING_B)
        ));

        List<PropertyOwnership> list = ownerPropertyMapper.selectOwnershipsByBuilding(TENANT_PRIMARY);

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

    @Test
    public void ownerGroupScope_filtersByTenantAndBuildingPair() {
        userContextHolder.set(new UserContext(
                999803L,
                UserContext.IdentityType.SYS_USER,
                800003L,
                TENANT_PRIMARY,
                101L,
                UserContext.DeptCategory.G,
                2,
                DataScopeType.OWNER_GROUP,
                AuthenticationLevel.L1,
                "TEST_ROLE",
                Set.of(),
                Set.of(BUILDING_A, BUILDING_B),
                Set.of(
                        new WorkIdentityBuildingScope(TENANT_PRIMARY, BUILDING_B),
                        new WorkIdentityBuildingScope(TENANT_CROSS, BUILDING_A))));

        List<PropertyOwnership> list = ownerPropertyMapper.selectOwnershipsByBuilding(null);

        assertNotNull(list);
        assertEquals(2, list.size(), "OWNER_GROUP 应按 tenant/building 组合过滤，而不是裸 building_id");
    }

    private UserContext buildSysUserCtx(DataScopeType scope, Set<Long> authorizedBuildingIds) {
        return new UserContext(
                999803L,                                  // accountId（V1.1 刘主任）
                UserContext.IdentityType.SYS_USER,
                800003L,                                  // sys_user.user_id
                TENANT_PRIMARY,                           // tenantId
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
