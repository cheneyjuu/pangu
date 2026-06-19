package com.pangu.bootstrap.rbac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Waiver 端 {@code @PreAuthorize} 拒绝路径矩阵覆盖。
 *
 * <p>覆盖 V1.1 求是小区 4 类典型用户对 5 个 endpoint 的拒绝场景：
 * <ul>
 *     <li>陈网格员（GRID_OPERATOR，仅有 waiver:read）→ submit 应 403</li>
 *     <li>刘主任（COMMUNITY_ADMIN，无 waiver:approve:street）→ street-review 应 403</li>
 *     <li>张三业主代表（OWNER_REPRESENTATIVE B 端，仅 waiver:read）→ submit 应 403</li>
 *     <li>赵经理（PROPERTY_MANAGER S 端，仅 waiver:read）→ committee-review 应 403</li>
 *     <li>李四业主（C 端 c_user，无 sys_role 无任何 sys_permission）→ GET 应 403</li>
 * </ul>
 *
 * <p>本测试不依赖业务流程是否真存在 waiver_id；@PreAuthorize 在路由前拦截，
 * 因此即使 waiver_id 是不存在的 99999 也能验证拒绝路径。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class PreAuthorizeMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final long TENANT_RUSHI = 10001L;

    // V1.1 求是小区 seed 关键身份
    private static final long ACC_GRID = 999804L, USR_GRID = 800004L;       // 陈网格员 GRID_OPERATOR
    private static final long ACC_COMM = 999803L, USR_COMM = 800003L;       // 刘主任 COMMUNITY_ADMIN
    private static final long ACC_REP  = 999812L, USR_REP  = 800102L;       // 张三 OWNER_REPRESENTATIVE
    private static final long ACC_PROP = 999821L, USR_PROP = 800201L;       // 赵经理 PROPERTY_MANAGER
    private static final long ACC_LISI = 999913L, UID_LISI = 70002L;        // 李四 C_USER

    @Test
    public void gridOperatorCannotSubmitWaiver_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_GRID, "SYS_USER", USR_GRID, TENANT_RUSHI);
        Map<String, Object> body = Map.of(
                "requestedRatio", new BigDecimal("0.30"),
                "reasonText", "测试拒绝路径，长度足够"
        );
        mockMvc.perform(post("/api/v1/elections/1/waivers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void communityAdminCannotApproveByStreet_403() throws Exception {
        // COMMUNITY_ADMIN 拥有 waiver:approve:committee 但无 waiver:approve:street
        String token = jwtTokenProvider.generateToken(ACC_COMM, "SYS_USER", USR_COMM, TENANT_RUSHI);
        Map<String, Object> body = Map.of("approve", true, "opinion", "no");
        mockMvc.perform(post("/api/v1/waivers/99999/street-review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void ownerRepresentativeCannotSubmitWaiver_403() throws Exception {
        // OWNER_REPRESENTATIVE 是 B 端，无 waiver:submit
        String token = jwtTokenProvider.generateToken(ACC_REP, "SYS_USER", USR_REP, TENANT_RUSHI);
        Map<String, Object> body = Map.of(
                "requestedRatio", new BigDecimal("0.30"),
                "reasonText", "B 端用户不应能 submit waiver"
        );
        mockMvc.perform(post("/api/v1/elections/1/waivers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void propertyManagerCannotApproveByCommittee_403() throws Exception {
        // PROPERTY_MANAGER 是 S 端，仅有 waiver:read
        String token = jwtTokenProvider.generateToken(ACC_PROP, "SYS_USER", USR_PROP, TENANT_RUSHI);
        Map<String, Object> body = Map.of("approve", true, "opinion", "no");
        mockMvc.perform(post("/api/v1/waivers/99999/committee-review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void cUserHasNoSysPermission_cannotReadWaiver_403() throws Exception {
        // C 端业主李四：c_user 无 sys_role 关联 → permissions 集为空 → waiver:read 也无
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/waivers/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }
}
