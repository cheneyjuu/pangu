package com.pangu.bootstrap.persistence;

import com.pangu.domain.gateway.CommitteeKeyRevocationGateway;
import com.pangu.domain.model.attestation.JudicialChainPort;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.infrastructure.persistence.mapper.OwnerPropertyMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产 profile 下 {@code DataScopeInterceptor} 在上下文缺失时不再回退到 mock 用户的回归测试（M2-5.2）。
 *
 * <p>背景：M1 重构以前，{@code DataScopeInterceptor} 在 UserContext 为 null 时会回退到 Mock 王小二
 * 上下文以方便本地联调。生产环境一旦误启用此回退即等同于绕过权限校验。M1 重构已移除该兜底
 * （见 {@code DataScopeInterceptor#intercept}），本测试用于回归保护：当任何后续 PR 试图
 * 重新引入 mock 兜底时，本测试会立刻失败。
 *
 * <p>验证手段：
 * <ol>
 *   <li>清空 {@link UserContextHolder} ThreadLocal（无登录上下文）；</li>
 *   <li>直接调用 {@link OwnerPropertyMapper#selectOwnershipsByBuilding(Long)}（标注了 {@code @DataScope}）；</li>
 *   <li>断言抛出的异常 cause 链中能找到 message 含「数据权限上下文缺失」的
 *       {@link IllegalStateException}。MyBatis 会把 interceptor 异常包装为
 *       {@code MyBatisSystemException → PersistenceException → IllegalStateException}，
 *       这是 {@code GlobalExceptionHandler} 收到的真实形态。</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("prod")
public class ProductionEmptyContextTest {

    @Autowired
    private OwnerPropertyMapper ownerPropertyMapper;

    @Autowired
    private UserContextHolder userContextHolder;

    @MockBean
    private JudicialChainPort judicialChainPort;

    @MockBean
    private CommitteeKeyRevocationGateway committeeKeyRevocationGateway;

    @BeforeEach
    public void setUp() {
        // 显式清掉任何遗留 ThreadLocal —— 模拟 JwtAuthenticationFilter 未运行的场景。
        userContextHolder.clear();
    }

    @AfterEach
    public void tearDown() {
        userContextHolder.clear();
    }

    /**
     * 上下文缺失（{@code UserContextHolder.current() == null}）时，对带 {@code @DataScope}
     * 的 mapper 查询必须抛 {@link IllegalStateException}，禁止任何 mock 兜底。
     *
     * <p>同时验证 message 含「数据权限上下文缺失」前缀，因为
     * {@code GlobalExceptionHandler.handleIllegalStateException} 据此识别并翻译为
     * {@code DATA_SCOPE_PARSE_FAILED}（500 SYSTEM）。
     */
    @Test
    public void emptyContext_dataScopeMapper_throwsRefusal() {
        // MyBatis 会把 interceptor 抛出的 IllegalStateException 包装为 MyBatisSystemException
        // → PersistenceException → IllegalStateException 链。这里捕外层 Exception，再沿 cause
        // 链定位真实拒绝点，等价于 GlobalExceptionHandler 看到的根因。
        Exception ex = assertThrows(Exception.class, () -> {
            ownerPropertyMapper.selectOwnershipsByBuilding(10001L);
        }, "上下文缺失时 @DataScope mapper 必须抛异常，不得 mock 兜底");

        Throwable cause = ex;
        IllegalStateException refusal = null;
        while (cause != null) {
            if (cause instanceof IllegalStateException ise
                    && ise.getMessage() != null
                    && ise.getMessage().contains("数据权限")) {
                refusal = ise;
                break;
            }
            cause = cause.getCause();
        }
        assertNotNull(refusal, "cause 链中必须含 IllegalStateException + message 含「数据权限」"
                + "，证明是 DataScopeInterceptor 在 UserContext 缺失时主动抛出，实际：" + ex);
        assertTrue(refusal.getMessage().contains("上下文缺失"),
                "message 应含「上下文缺失」，证明走的是 M1 重构后的拒绝路径而非 mock 兜底，实际：" + refusal.getMessage());
    }
}
