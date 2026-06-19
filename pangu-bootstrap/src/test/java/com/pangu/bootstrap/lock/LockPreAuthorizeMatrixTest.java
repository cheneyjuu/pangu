package com.pangu.bootstrap.lock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 治理锁端 {@code @PreAuthorize} 拒绝路径矩阵覆盖。
 *
 * <p>覆盖 V2.5 注册的两条 lock permission 在 V1.1 求是小区 seed 用户矩阵下的拒绝场景：
 * <ul>
 *     <li>陈网格员（GRID_OPERATOR）—— 两个 unlock endpoint 全 403；</li>
 *     <li>刘主任（COMMUNITY_ADMIN，仅 lock:unlock:street）—— committee-sign 应 403；</li>
 *     <li>周主任（COMMITTEE_DIRECTOR，仅 lock:unlock:committee）—— street-sign 应 403；</li>
 *     <li>李四（C_USER，无 sys_role）—— committee-sign 应 403；</li>
 *     <li>正向通路：周主任带 lock:unlock:committee 调 committee-sign，
 *         @PreAuthorize 应放行 → 控制器命中不存在 lockId → LOCK_NOT_FOUND（404）。</li>
 * </ul>
 *
 * <p>本测试不依赖业务流程是否真存在 lockId；@PreAuthorize 在路由前拦截，
 * 因此即使 lockId 是不存在的 99999 也能区分 403（authority 缺失）/ 404（authority 通过、业务未命中）。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class LockPreAuthorizeMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final long TENANT_RUSHI = 10001L;

    // V1.1 求是小区 seed
    private static final long ACC_GRID = 999804L, USR_GRID = 800004L;       // 陈网格员 GRID_OPERATOR（无任何 lock 权限）
    private static final long ACC_COMM = 999803L, USR_COMM = 800003L;       // 刘主任   COMMUNITY_ADMIN（仅 lock:unlock:street）
    private static final long ACC_DIR  = 999811L, USR_DIR  = 800101L;       // 周主任   COMMITTEE_DIRECTOR（仅 lock:unlock:committee）
    private static final long ACC_LISI = 999913L, UID_LISI = 70002L;        // 李四     C_USER（无 sys_role）

    @Test
    public void gridOperatorCannotCommitteeSign_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_GRID, "SYS_USER", USR_GRID, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/locks/99999/committee-sign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("signature", "sig-c"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void communityAdminCannotCommitteeSign_403() throws Exception {
        // COMMUNITY_ADMIN 拥有 lock:unlock:street 但无 committee
        String token = jwtTokenProvider.generateToken(ACC_COMM, "SYS_USER", USR_COMM, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/locks/99999/committee-sign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("signature", "sig-c"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void committeeDirectorCannotStreetSign_403() throws Exception {
        // COMMITTEE_DIRECTOR 拥有 lock:unlock:committee 但无 street
        String token = jwtTokenProvider.generateToken(ACC_DIR, "SYS_USER", USR_DIR, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/locks/99999/street-sign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("signature", "sig-s"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void cUserHasNoLockPermission_committeeSign_403() throws Exception {
        // C 端业主李四：c_user 无 sys_role 关联 → permissions 集为空
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/locks/99999/committee-sign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("signature", "sig-c"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void committeeDirectorWithProperAuthority_passesPreAuthorize_butLockNotFound_404() throws Exception {
        // 反向验证 @PreAuthorize 配置正确：周主任带 lock:unlock:committee 通过 PreAuthorize 后，
        // 业务层因 lockId=99999 不存在抛 LOCK_NOT_FOUND（HTTP 404）。
        String token = jwtTokenProvider.generateToken(ACC_DIR, "SYS_USER", USR_DIR, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/locks/99999/committee-sign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("signature", "sig-c"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(41003)));
    }
}
