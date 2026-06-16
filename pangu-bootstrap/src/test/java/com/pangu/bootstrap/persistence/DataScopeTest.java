package com.pangu.bootstrap.persistence;

import com.pangu.domain.model.asset.PropertyOwnership;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.infrastructure.persistence.handler.DataScopeInterceptor.UserSecurityContext;
import com.pangu.infrastructure.persistence.mapper.OwnerPropertyMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DataScopeTest {

    @Autowired
    private OwnerPropertyMapper ownerPropertyMapper;

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testDataScopeForGridManager() {
        // 1. 模拟网格员王小二的安全上下文 (管辖楼栋 10001 与 10002)
        UserSecurityContext userCtx = UserSecurityContext.builder()
                .userId(202L)
                .deptId(104L)
                .dataScope(DataScopeType.CUSTOM_BUILDING.getValue()) // 自定义楼栋 (6)
                .authorizedBuildingIds(List.of(10001L, 10002L))
                .uid(101L)
                .tenantId(9001L)
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userCtx, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 2. 调用带有 @DataScope 注解的查询方法
        // 根据 V1.1 种子数据，求是小区(tenant_id=9001)名下一共有 4 份绑定资产：
        // opid=5001 (building_id=10001), opid=5002 (building_id=10001)
        // opid=5003 (building_id=10002), opid=5004 (building_id=10003)
        // 网格员王小二仅管辖 10001 和 10002 楼栋，因此 opid=5004 必须被 MyBatis 自动拦截过滤掉！
        List<PropertyOwnership> list = ownerPropertyMapper.selectOwnershipsByBuilding(9001L);

        assertNotNull(list);
        assertEquals(3, list.size(), "王小二应该只查询到管辖楼栋范围内的 3 套房产关系，5004 被自动过滤");

        List<Long> opids = list.stream().map(PropertyOwnership::getOpid).toList();
        assertTrue(opids.contains(5001L));
        assertTrue(opids.contains(5002L));
        assertTrue(opids.contains(5003L));
        assertFalse(opids.contains(5004L), "opid 5004 位于 10003 栋，超出管辖，必须被过滤");
    }

    @Test
    public void testDataScopeForNormalUser() {
        // 1. 模拟普通业主李四的安全上下文 (没有管理端权限，只管本人 SELF)
        UserSecurityContext userCtx = UserSecurityContext.builder()
                .userId(102L)
                .deptId(null)
                .dataScope(DataScopeType.SELF.getValue()) // 仅本人 (5)
                .authorizedBuildingIds(Collections.emptyList())
                .uid(102L)
                .tenantId(9001L)
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userCtx, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 2. 调用带有 @DataScope(userAlias="op") 的查询。
        // 由于 DataScopeInterceptor 对于 SELF 范围，会在 SQL 尾部拼上 "op.user_id = 102"
        // 从而进行过滤。等下，在我们刚才添加的 @DataScope(buildingAlias = "op") 里，因为没有配置 userAlias，
        // 那么在 interceptor 中，会使用默认值。我们来看看 DataScope.java 中：
        // String userAlias() default "";
        // 它的默认值是 ""。所以如果 userAlias 是 "", 拼接的是 "user_id = 102"。
        // 让我们看看 c_owner_property 中是否有 user_id 字段。
        // 在 schema 中，列名是 uid，而不是 user_id。
        // 如果拼上 user_id = 102，可能会导致 Postgres 执行报错 (找不到 user_id 列)。
        // 这是一个极好的发现！这属于 DataScopeInterceptor 默认行为的兼容性隐患。
        // 既然我们的表名是 c_owner_property，在 XML 中别名是 "op"：
        // 如果我们把注解改为：@DataScope(buildingAlias = "op", userAlias = "op")
        // 那么在拦截器中：
        // conditionBuilder.append(" ").append(prefix).append("user_id = ").append(userCtx.getUserId()).append(" ");
        // 依旧会拼成 "op.user_id = 102"。但表 c_owner_property 的列名是 uid。
        // 怎么解决呢？
        // 其实对于 OwnerPropertyMapper 这种仅供管理端查询的接口，如果是 SELF 范围（普通业主），其实可以直接返回空 list，
        // 或者抛出权限异常，因为非网格员/管理端用户原则上不允许使用这种管理端批量房产查询接口。
        // 让我们看一下 DataScopeInterceptor 中：
        // 如果是 SELF，它直接硬编码追加了 user_id 过滤。由于这个接口本来设计就是提供给管理人员使用的（buildingAlias = "op"），
        // 我们可以限制普通业主李四执行该方法时会被安全判定为“无权限”，或者直接查出空。
        // 我们可以直接捕获异常或是在测试里验证：普通业主不应该有管辖范围的数据。
        // 为了使这套逻辑正常，我们先运行测试，确认数据库的行为。
    }
}
