package com.pangu.bootstrap.disclosure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2.7 财务公示 4 个 endpoint 的 {@code @PreAuthorize} 矩阵。
 *
 * <p>用 V1.1 求是小区 seed 用户对 4 条权限通路反复打靶：
 * <ul>
 *     <li>陈网格员（GRID_OPERATOR） —— 三条带 hasAuthority 的全 403；</li>
 *     <li>李四（C_USER，无 sys_role） —— compose / publish / audit 全 403，
 *         GET 单期通过 {@code isAuthenticated()} 但 service 层因 snapshotId 不存在抛 404；</li>
 *     <li>刘主任（COMMUNITY_ADMIN） —— compose / audit 通过；publish 应 403；</li>
 *     <li>周主任（COMMITTEE_DIRECTOR） —— compose / publish 通过到 service 层；audit 应 403；</li>
 *     <li>正向通路：周主任带 disclosure:publish 调 publish endpoint 命中不存在 snapshotId
 *         → SNAPSHOT_NOT_FOUND（HTTP 404 / code=41104）。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
public class DisclosurePreAuthorizeMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long TENANT_RUSHI = 10001L;

    // V1.1 求是小区 seed
    private static final long ACC_GRID = 999804L, USR_GRID = 800004L;       // 陈网格员 GRID_OPERATOR（无 disclosure:* 任一权限）
    private static final long ACC_COMM = 999803L, USR_COMM = 800003L;       // 刘主任   COMMUNITY_ADMIN（compose + audit）
    private static final long ACC_DIR  = 999811L, USR_DIR  = 800101L;       // 周主任   COMMITTEE_DIRECTOR（compose + publish）
    private static final long ACC_LISI = 999913L, UID_LISI = 70002L;        // 李四     C_USER（无 sys_role）

    // ===== 403 拒绝路径 =====

    @Test
    public void gridOperatorCannotCompose_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_GRID, "SYS_USER", USR_GRID, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/disclosures/compose")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "period", "2026-06",
                                "disclosureType", "MAINTENANCE_FUND"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void gridOperatorCannotPublish_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_GRID, "SYS_USER", USR_GRID, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/disclosures/99999/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void gridOperatorCannotAudit_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_GRID, "SYS_USER", USR_GRID, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/disclosures/99999/compare/99998")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void communityAdminCannotPublish_403() throws Exception {
        // COMMUNITY_ADMIN 拥有 compose / audit 但无 publish（redline=1 仅给 COMMITTEE_DIRECTOR）
        String token = jwtTokenProvider.generateToken(ACC_COMM, "SYS_USER", USR_COMM, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/disclosures/99999/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void committeeDirectorCannotAudit_403() throws Exception {
        // COMMITTEE_DIRECTOR 仅有 compose / publish，无 audit
        String token = jwtTokenProvider.generateToken(ACC_DIR, "SYS_USER", USR_DIR, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/disclosures/99999/compare/99998")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void cUserCannotCompose_403() throws Exception {
        // C 端业主李四：c_user 无 sys_role 关联 → permissions 集为空
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/disclosures/compose")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "period", "2026-06",
                                "disclosureType", "MAINTENANCE_FUND"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    // ===== 正向通路：authority 通过 → service 层抛 SNAPSHOT_NOT_FOUND =====

    @Test
    public void committeeDirectorWithProperAuthority_publishHitsSnapshotNotFound_404() throws Exception {
        // 反向验证 @PreAuthorize 配置正确：周主任带 disclosure:publish 通过 PreAuthorize 后，
        // 业务层因 snapshotId=99999 不存在抛 SNAPSHOT_NOT_FOUND（HTTP 404 / code=41104）。
        String token = jwtTokenProvider.generateToken(ACC_DIR, "SYS_USER", USR_DIR, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/disclosures/99999/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(41104)));
    }

    @Test
    public void communityAdminWithProperAuthority_compareHitsSnapshotNotFound_404() throws Exception {
        // 刘主任带 disclosure:audit 通过 PreAuthorize 后，service 层因 snapshotId 不存在抛 404。
        String token = jwtTokenProvider.generateToken(ACC_COMM, "SYS_USER", USR_COMM, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/disclosures/99999/compare/99998")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(41104)));
    }

    @Test
    public void cUserCanGetEndpoint_butSnapshotNotFound_404() throws Exception {
        // GET 单期采用 isAuthenticated()，C_USER 通过认证；service 层因 snapshotId 不存在抛 404。
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/disclosures/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(41104)));
    }

    @Test
    public void legacyFundDisclosurePublishPermission_removedFromCatalogAndGrants() {
        Integer permissionRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE permission_key = 'fund:disclosure:publish'",
                Integer.class);
        Integer grantRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role_permission WHERE permission_key = 'fund:disclosure:publish'",
                Integer.class);

        assertEquals(0, permissionRows);
        assertEquals(0, grantRows);
    }
}
