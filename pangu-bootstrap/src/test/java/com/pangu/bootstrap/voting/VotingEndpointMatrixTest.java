package com.pangu.bootstrap.voting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3-2 议题生命周期 + 业主投票 endpoint 的 {@code @PreAuthorize} × seed user 矩阵
 * （与 {@code DisputePreAuthorizeMatrixTest} 同风格）。
 *
 * <p>V1.1 求是小区 seed + V1.4/V3.0 角色授权：
 * <ul>
 *   <li>吴经办员（GOV_OPERATOR，role 14）—— create:election + audit，可提交初审；</li>
 *   <li>陈网格员（GRID_OPERATOR，role 4）—— 有 publish/audit，无 create/cancel/双签审批；</li>
 *   <li>刘主任（COMMUNITY_ADMIN，role 2）—— create + publish + audit + committee-review，无 cancel/street-review；</li>
 *   <li>王街道（GOV_SUPER_ADMIN，role 1）—— street-review + cancel；</li>
 *   <li>李四（C_USER，无 sys_role）—— G 端全 403，C 端 isAuthenticated 通过到 service 层。</li>
 * </ul>
 *
 * <p>断言两类码：PreAuthorize 拒绝 → {@code code=403}；通过 PreAuthorize 后命中 service
 * 业务异常 → ElectionErrorCode（SUBJECT_NOT_FOUND=40910 / ELECTION_MAX_WINNERS_REQUIRED=40940 /
 * CANCEL_FORBIDDEN=40924 / OPID_NOT_OWNED=40331）。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class VotingEndpointMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final long TENANT_RUSHI = 10001L;

    private static final long ACC_STREET = 999801L, USR_STREET = 800001L; // 王街道 GOV_SUPER_ADMIN
    private static final long ACC_OPERATOR = 999805L, USR_OPERATOR = 800005L; // 吴经办员 GOV_OPERATOR
    private static final long ACC_GRID = 999804L, USR_GRID = 800004L;   // 陈网格员 GRID_OPERATOR（publish/audit）
    private static final long ACC_COMM = 999803L, USR_COMM = 800003L;   // 刘主任 COMMUNITY_ADMIN（create/publish/audit）
    private static final long ACC_LISI = 999913L, UID_LISI = 70002L;    // 李四 C_USER（无 sys_role）

    private String sysToken(long acc, long userId) {
        return jwtTokenProvider.generateToken(acc, "SYS_USER", userId, TENANT_RUSHI);
    }

    private String cToken(long acc, long uid) {
        return jwtTokenProvider.generateToken(acc, "C_USER", uid, TENANT_RUSHI);
    }

    private String proposeBody(String subjectType) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "矩阵测试议案");
        body.put("subjectType", subjectType);
        body.put("scope", "COMMUNITY");
        body.put("voteStartAt", "2026-07-01T00:00:00Z");
        body.put("voteEndAt", "2026-07-15T00:00:00Z");
        return objectMapper.writeValueAsString(body);
    }

    private String reviewBody(String decision) throws Exception {
        return objectMapper.writeValueAsString(Map.of("decision", decision));
    }

    // ===== 立项 create：无 create 权限 → 403 =====

    @Test
    public void gridOperatorCannotPropose_403() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposeBody("GENERAL")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void cUserCannotPropose_403() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects")
                        .header("Authorization", "Bearer " + cToken(ACC_LISI, UID_LISI))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposeBody("GENERAL")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void communityAdminProposeElection_rejectedByPermissionMatrix_403() throws Exception {
        // 刘主任只有通用 create；ELECTION 立项必须有 voting:subject:create:election。
        mockMvc.perform(post("/api/v1/voting-subjects")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposeBody("ELECTION")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void govOperatorProposeElectionWithoutMaxWinners_passesPreAuth_thenMaxWinnersRequired_409() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects")
                        .header("Authorization", "Bearer " + sysToken(ACC_OPERATOR, USR_OPERATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposeBody("ELECTION")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(40940)));
    }

    // ===== 审计查看 audit =====

    @Test
    public void gridOperatorAudit_passesPreAuth_thenNotFound_404() throws Exception {
        // V3.4 起网格员补了 voting:subject:audit（参与议题相关写动作的角色需要读议题）。
        mockMvc.perform(get("/api/v1/voting-subjects/99999")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void communityAdminAudit_passesPreAuth_thenNotFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/voting-subjects/99999")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void gridOperatorMonitor_passesPreAuth_thenNotFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/voting-subjects/99999/monitor")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void cUserMonitor_forbidden_403() throws Exception {
        mockMvc.perform(get("/api/v1/voting-subjects/99999/monitor")
                        .header("Authorization", "Bearer " + cToken(ACC_LISI, UID_LISI)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void gridOperatorMobilizationPermissions_passesPreAuth_thenNotFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/voting-subjects/99999/mobilization-permissions/me")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void cUserMobilizationPermissions_forbidden_403() throws Exception {
        mockMvc.perform(get("/api/v1/voting-subjects/99999/mobilization-permissions/me")
                        .header("Authorization", "Bearer " + cToken(ACC_LISI, UID_LISI)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void gridOperatorSendMobilizationReminder_passesPreAuth_thenNotFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/mobilization-reminders")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("buildingId", 30001L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void cUserSendMobilizationReminder_forbidden_403() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/mobilization-reminders")
                        .header("Authorization", "Bearer " + cToken(ACC_LISI, UID_LISI))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("buildingId", 30001L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void gridOperatorReminderDeliveries_passesPreAuth_thenNotFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/voting-subjects/99999/reminder-deliveries")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void cUserReminderDeliveries_forbidden_403() throws Exception {
        mockMvc.perform(get("/api/v1/voting-subjects/99999/reminder-deliveries")
                        .header("Authorization", "Bearer " + cToken(ACC_LISI, UID_LISI)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void gridOperatorOfflineProxyVote_passesPreAuth_thenNotFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/offline-proxy-votes")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "opid", 30001101L,
                                "choice", "SUPPORT",
                                "offlineEvidenceHash", "hash-001"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void cUserOfflineProxyVote_forbidden_403() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/offline-proxy-votes")
                        .header("Authorization", "Bearer " + cToken(ACC_LISI, UID_LISI))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "opid", 30001101L,
                                "choice", "SUPPORT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void gridOperatorReminderTasks_passesPreAuth_200() throws Exception {
        mockMvc.perform(get("/api/v1/reminder/tasks")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    public void cUserReminderTasks_forbidden_403() throws Exception {
        mockMvc.perform(get("/api/v1/reminder/tasks")
                        .header("Authorization", "Bearer " + cToken(ACC_LISI, UID_LISI)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    // ===== 公示 publish：陈网格员有 publish，通过 PreAuth → 业务 NOT_FOUND =====

    @Test
    public void gridOperatorPublish_passesPreAuth_thenNotFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/publish")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void streetAdminPublish_forbidden_403() throws Exception {
        // ELECTION 发布必须走 street-review；V3.27 起街道办不再持有通用直接公示权限。
        mockMvc.perform(post("/api/v1/voting-subjects/99999/publish")
                        .header("Authorization", "Bearer " + sysToken(ACC_STREET, USR_STREET)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    // ===== 撤回 cancel：isAuthenticated 通过，service/controller 二次授权 =====

    @Test
    public void gridOperatorCancel_noCancelNoCreate_forbidden_403() throws Exception {
        // 陈网格员既无 voting:subject:cancel 也无 voting:subject:create → controller 抛 CANCEL_FORBIDDEN
        mockMvc.perform(post("/api/v1/voting-subjects/99999/cancel")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "网格员无撤回权"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(40924)));
    }

    @Test
    public void communityAdminCancel_hasCreate_passesGate_thenNotFound_404() throws Exception {
        // 刘主任有 create（canCreate=true），通过 controller 授权门 → findByIdForUpdate 99999 不存在
        mockMvc.perform(post("/api/v1/voting-subjects/99999/cancel")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "业委会发起人撤回草稿"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    // ===== ELECTION 双签审批 endpoint =====

    @Test
    public void operatorSubmitForReview_passesPreAuth_thenNotFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/submit-for-review")
                        .header("Authorization", "Bearer " + sysToken(ACC_OPERATOR, USR_OPERATOR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void gridOperatorSubmitForReview_noCreate_forbidden_403() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/submit-for-review")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void communityAdminSubmitForReview_genericCreateForbidden_403() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/submit-for-review")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void communityAdminCommitteeReview_passesPreAuth_thenNotFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/committee-review")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("APPROVE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void gridOperatorCommitteeReview_forbidden_403() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/committee-review")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("APPROVE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void streetAdminCommitteeReview_forbidden_403() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/committee-review")
                        .header("Authorization", "Bearer " + sysToken(ACC_STREET, USR_STREET))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("APPROVE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void communityAdminCommitteeReview_missingDecision_400() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/committee-review")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(40951)));
    }

    @Test
    public void streetAdminStreetReview_passesPreAuth_thenNotFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/street-review")
                        .header("Authorization", "Bearer " + sysToken(ACC_STREET, USR_STREET))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("APPROVE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void communityAdminStreetReview_forbidden_403() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/street-review")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("APPROVE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void streetAdminStreetReview_missingDecision_400() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/street-review")
                        .header("Authorization", "Bearer " + sysToken(ACC_STREET, USR_STREET))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(40951)));
    }

    @Test
    public void streetAdminConfirmHandover_allowed_200() throws Exception {
        mockMvc.perform(post("/api/v1/handover/confirm")
                        .header("Authorization", "Bearer " + sysToken(ACC_STREET, USR_STREET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", is("NORMAL")));
    }

    @Test
    public void communityAdminConfirmHandover_forbidden_403() throws Exception {
        mockMvc.perform(post("/api/v1/handover/confirm")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    // ===== C 端业主 endpoint（isAuthenticated）=====

    @Test
    public void cUserListMine_returnsEmpty_200() throws Exception {
        mockMvc.perform(get("/api/v1/me/voting-subjects")
                        .header("Authorization", "Bearer " + cToken(ACC_LISI, UID_LISI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    public void cUserMyDetail_notFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/me/voting-subjects/99999")
                        .header("Authorization", "Bearer " + cToken(ACC_LISI, UID_LISI)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40910)));
    }

    @Test
    public void sysUserHittingOwnerEndpoint_noUid_opidNotOwned_403() throws Exception {
        // SYS_USER（陈网格员）无 c_user.uid → controller#requireUid() 抛 OPID_NOT_OWNED
        mockMvc.perform(get("/api/v1/me/voting-subjects")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(40331)));
    }

    // ===== 未登录 → 403 =====

    @Test
    public void anonymousPropose_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposeBody("GENERAL")))
                .andExpect(status().isForbidden());
    }

    @Test
    public void anonymousListMine_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/me/voting-subjects"))
                .andExpect(status().isForbidden());
    }
}
