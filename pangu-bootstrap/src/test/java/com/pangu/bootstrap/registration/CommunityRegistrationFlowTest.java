// 关联业务：验证小区注册申请、退回补充、属地/平台审核、租户开通和最小授权闭环。
package com.pangu.bootstrap.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小区注册第一阶段端到端测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class CommunityRegistrationFlowTest {

    private static final String DIRECTOR_PHONE = "13900007301";
    private static final String OWNER_PHONE = "13900007302";
    private static final String PROPERTY_SERVICE_DIRECTOR_PHONE = "13900007303";
    private static final long STREET_ACCOUNT_ID = 999801L;
    private static final long STREET_USER_ID = 800001L;
    private static final long PLATFORM_ACCOUNT_ID = 991500L;
    private static final long PLATFORM_USER_ID = 891500L;
    private static final String PLATFORM_PHONE = "13900007315";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean private RepairEvidenceObjectStorage objectStorage;

    @BeforeEach
    public void setUp() throws Exception {
        cleanup();
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        invocation.getArgument(1, byte[].class).length,
                        invocation.getArgument(2, String.class),
                        "TEST-ETAG"));
        when(objectStorage.createPreviewUrl(anyString(), anyString(), any()))
                .thenReturn(new URL("https://preview.example.test/registration-material"));
        createPlatformReviewer();
    }

    @AfterEach
    public void tearDown() {
        cleanup();
    }

    @Test
    public void streetReview_returnResubmitAndApprove_createsTenantCommitteeIdentityAndAudit() throws Exception {
        LoginSession applicant = ownerLogin(DIRECTOR_PHONE);
        String streetToken = jwtTokenProvider.generateToken(
                STREET_ACCOUNT_ID, "SYS_USER", STREET_USER_ID, null);

        String createdJson = mockMvc.perform(post("/api/v1/community-registrations")
                        .header("Authorization", "Bearer " + applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationPayload(
                                "张主任", "COMMITTEE_DIRECTOR", "春申新苑", "上海市闵行区春申路 100 弄", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andExpect(jsonPath("$.data.version", is(0)))
                .andReturn().getResponse().getContentAsString();
        long applicationId = objectMapper.readTree(createdJson).path("data").path("applicationId").asLong();

        LoginSession duplicateApplicant = ownerLogin(OWNER_PHONE);
        mockMvc.perform(post("/api/v1/community-registrations")
                        .header("Authorization", "Bearer " + duplicateApplicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationPayload(
                                "李业主", "OWNER", "春申新苑", "上海市闵行区春申路 100 弄", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(42906)));

        mockMvc.perform(post("/api/v1/community-registrations/" + applicationId + "/submit")
                        .header("Authorization", "Bearer " + applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(42907)));

        uploadMaterial(applicant.token(), applicationId, "COMMITTEE_FILING");

        mockMvc.perform(post("/api/v1/community-registrations/" + applicationId + "/submit")
                        .header("Authorization", "Bearer " + applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUBMITTED")))
                .andExpect(jsonPath("$.data.version", is(1)));

        mockMvc.perform(get("/api/v1/admin/community-registrations")
                        .header("Authorization", "Bearer " + applicant.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/community-registrations/" + applicationId + "/reviews")
                        .header("Authorization", "Bearer " + streetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "RETURN",
                                "reviewMode", "STREET",
                                "reviewComment", "请补充备案文件首页",
                                "expectedVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("RETURNED")))
                .andExpect(jsonPath("$.data.version", is(2)));

        mockMvc.perform(put("/api/v1/community-registrations/" + applicationId)
                        .header("Authorization", "Bearer " + applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationPayload(
                                "张主任", "COMMITTEE_DIRECTOR", "春申新苑", "上海市闵行区春申路 100 弄", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("RETURNED")))
                .andExpect(jsonPath("$.data.version", is(3)));

        mockMvc.perform(put("/api/v1/community-registrations/" + applicationId)
                        .header("Authorization", "Bearer " + applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationPayload(
                                "张主任", "COMMITTEE_DIRECTOR", "春申新苑", "上海市闵行区春申路 100 弄", 2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(42908)));

        mockMvc.perform(post("/api/v1/community-registrations/" + applicationId + "/submit")
                        .header("Authorization", "Bearer " + applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUBMITTED")))
                .andExpect(jsonPath("$.data.version", is(4)));

        String approvedJson = mockMvc.perform(post(
                        "/api/v1/admin/community-registrations/" + applicationId + "/reviews")
                        .header("Authorization", "Bearer " + streetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVE",
                                "reviewMode", "STREET",
                                "reviewComment", "备案材料与属地信息核验通过",
                                "expectedVersion", 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")))
                .andExpect(jsonPath("$.data.version", is(5)))
                .andExpect(jsonPath("$.data.onboarding.status", is("FOUNDATION_PENDING")))
                .andExpect(jsonPath("$.data.onboarding.officialAffiliationStatus", is("STREET_CONFIRMED")))
                .andExpect(jsonPath("$.data.onboarding.ownerAccessQrStatus", is("DISABLED")))
                .andReturn().getResponse().getContentAsString();

        JsonNode approved = objectMapper.readTree(approvedJson).path("data");
        long tenantId = approved.path("provisionedTenantId").asLong();
        long workUserId = approved.path("onboarding").path("applicantWorkUserId").asLong();
        assertNotNull(approved.path("onboarding").path("initializationDeptId").numberValue());
        assertNotNull(approved.path("onboarding").path("committeeDeptId").numberValue());

        mockMvc.perform(post("/api/v1/admin/community-registrations/" + applicationId + "/reviews")
                        .header("Authorization", "Bearer " + streetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVE",
                                "reviewMode", "STREET",
                                "reviewComment", "备案材料与属地信息核验通过",
                                "expectedVersion", 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")))
                .andExpect(jsonPath("$.data.provisionedTenantId", is((int) tenantId)));

        assertEquals("HANDOVER_LOCK", jdbcTemplate.queryForObject(
                "SELECT governance_status FROM t_tenant_community WHERE tenant_id = ?",
                String.class, tenantId));
        assertEquals(480, jdbcTemplate.queryForObject(
                "SELECT planned_household_count FROM t_tenant_community WHERE tenant_id = ?",
                Integer.class, tenantId));
        assertEquals("TRUST", jdbcTemplate.queryForObject(
                "SELECT property_mode FROM t_tenant_community WHERE tenant_id = ?",
                String.class, tenantId));
        mockMvc.perform(get("/api/v1/auth/managed-communities")
                        .header("Authorization", "Bearer " + streetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.communities[*].tenant_id", hasItem((int) tenantId)))
                .andExpect(jsonPath("$.data.communities[*].tenant_name", hasItem("春申新苑")))
                .andExpect(jsonPath("$.data.communities[*].property_mode", hasItem("TRUST")));

        String switchedCommunityJson = mockMvc.perform(post("/api/v1/auth/switch-managed-community")
                .header("Authorization", "Bearer " + streetToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("targetTenantId", tenantId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active_tenant_id", is((int) tenantId)))
                .andExpect(jsonPath("$.data.user_info.tenant_id", is((int) tenantId)))
                .andExpect(jsonPath("$.data.user_info.property_mode", is("TRUST")))
                .andReturn().getResponse().getContentAsString();
        String switchedCommunityToken = objectMapper.readTree(switchedCommunityJson)
                .path("data").path("new_access_token").asText();
        mockMvc.perform(get("/api/v1/admin/property-roster/topology")
                        .header("Authorization", "Bearer " + switchedCommunityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId", is((int) tenantId)))
                .andExpect(jsonPath("$.data.communityName", is("春申新苑")));
        assertEquals("COMMITTEE_DIRECTOR", jdbcTemplate.queryForObject("""
                SELECT role.role_key
                FROM sys_user_role user_role
                JOIN sys_role role ON role.role_id = user_role.role_id
                WHERE user_role.user_id = ?
                """, String.class, workUserId));
        assertEquals("DIRECTOR", jdbcTemplate.queryForObject("""
                SELECT position
                FROM t_committee_member_position
                WHERE tenant_id = ? AND user_id = ? AND status = 1
                """, String.class, tenantId, workUserId));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", DIRECTOR_PHONE,
                                "smsCode", "123456",
                                "clientPortal", "B"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_info.identity_type", is("SYS_USER")))
                .andExpect(jsonPath("$.data.user_info.active_identity_id", is((int) workUserId)))
                .andExpect(jsonPath("$.data.user_info.role_key", is("COMMITTEE_DIRECTOR")))
                .andExpect(jsonPath("$.data.user_info.tenant_id", is((int) tenantId)))
                .andExpect(jsonPath("$.data.user_info.tenant_name", is("春申新苑")))
                .andExpect(jsonPath("$.data.user_info.property_mode", is("TRUST")));
        String committeeToken = jwtTokenProvider.generateToken(
                applicant.accountId(), "SYS_USER", workUserId, tenantId);
        mockMvc.perform(get("/api/v1/admin/property-roster/topology")
                        .header("Authorization", "Bearer " + committeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId", is((int) tenantId)))
                .andExpect(jsonPath("$.data.communityName", is("春申新苑")))
                .andExpect(jsonPath("$.data.householdCount", is(0)))
                .andExpect(jsonPath("$.data.buildings.length()", is(0)));

        String modeChangeJson = mockMvc.perform(post("/api/v1/admin/property-management-mode-changes")
                        .header("Authorization", "Bearer " + committeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestedPropertyMode", "FUND_RAISING",
                                "ownersAssemblyResolutionReference", "OA-2026-春申-001",
                                "changeReason", "业主大会已作出调整物业服务计费模式的有效决议"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currentPropertyMode", is("TRUST")))
                .andExpect(jsonPath("$.data.requestedPropertyMode", is("FUND_RAISING")))
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andExpect(jsonPath("$.data.version", is(0)))
                .andReturn().getResponse().getContentAsString();
        long modeChangeRequestId = objectMapper.readTree(modeChangeJson).path("data").path("requestId").asLong();
        uploadPropertyManagementModeChangeMaterial(
                committeeToken, modeChangeRequestId, "OWNERS_ASSEMBLY_RESOLUTION");

        mockMvc.perform(post("/api/v1/admin/property-management-mode-changes/" + modeChangeRequestId + "/submit")
                        .header("Authorization", "Bearer " + committeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUBMITTED")))
                .andExpect(jsonPath("$.data.version", is(1)));

        mockMvc.perform(post("/api/v1/admin/property-management-mode-changes/" + modeChangeRequestId + "/reviews")
                        .header("Authorization", "Bearer " + switchedCommunityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "EXECUTE",
                                "reviewComment", "业主大会决议及材料核验通过",
                                "expectedVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("EXECUTED")))
                .andExpect(jsonPath("$.data.effectivePropertyMode", is("FUND_RAISING")))
                .andExpect(jsonPath("$.data.version", is(2)));
        assertEquals("FUND_RAISING", jdbcTemplate.queryForObject(
                "SELECT property_mode FROM t_tenant_community WHERE tenant_id = ?",
                String.class, tenantId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_property_management_mode_change_audit
                WHERE request_id = ? AND event_type = 'MODE_EXECUTED'
                """, Integer.class, modeChangeRequestId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_community_registration_review WHERE application_id = ?",
                Integer.class, applicationId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_community_onboarding_workspace WHERE application_id = ?",
                Integer.class, applicationId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_community_registration_audit
                WHERE application_id = ? AND event_type = 'TENANT_PROVISIONED'
                """, Integer.class, applicationId));

        mockMvc.perform(get("/api/v1/auth/shadows")
                        .header("Authorization", "Bearer " + applicant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shadows[*].role_key", hasItem("COMMITTEE_DIRECTOR")));
    }

    @Test
    public void platformFallback_ownerApproval_keepsOwnerWithoutAdminIdentityAndRecordsFallback() throws Exception {
        LoginSession applicant = ownerLogin(OWNER_PHONE);
        String streetToken = jwtTokenProvider.generateToken(
                STREET_ACCOUNT_ID, "SYS_USER", STREET_USER_ID, null);
        String platformToken = jwtTokenProvider.generateToken(
                PLATFORM_ACCOUNT_ID, "SYS_USER", PLATFORM_USER_ID, null);

        long applicationId = createAndSubmitOwnerApplication(applicant);

        mockMvc.perform(post("/api/v1/admin/community-registrations/" + applicationId + "/reviews")
                        .header("Authorization", "Bearer " + streetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVE",
                                "reviewMode", "PLATFORM_FALLBACK",
                                "fallbackReason", "属地街镇尚未接入平台，由平台运营代审",
                                "expectedVersion", 1))))
                .andExpect(status().isForbidden());

        String approvedJson = mockMvc.perform(post(
                        "/api/v1/admin/community-registrations/" + applicationId + "/reviews")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVE",
                                "reviewMode", "PLATFORM_FALLBACK",
                                "reviewComment", "小区真实性与业主本人材料核验通过",
                                "fallbackReason", "属地街镇尚未接入平台，由平台运营代审并待后续确认归属",
                                "expectedVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")))
                .andExpect(jsonPath("$.data.onboarding.officialAffiliationStatus",
                        is("PLATFORM_REVIEWED_PENDING_STREET_CONFIRMATION")))
                .andExpect(jsonPath("$.data.onboarding.ownerAccessQrStatus", is("DISABLED")))
                .andReturn().getResponse().getContentAsString();

        JsonNode approved = objectMapper.readTree(approvedJson).path("data");
        long tenantId = approved.path("provisionedTenantId").asLong();
        assertNull(approved.path("onboarding").path("committeeDeptId").isNull()
                ? null : approved.path("onboarding").path("committeeDeptId").numberValue());
        assertNull(approved.path("onboarding").path("applicantWorkUserId").isNull()
                ? null : approved.path("onboarding").path("applicantWorkUserId").numberValue());
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user user_account
                JOIN sys_dept dept ON dept.dept_id = user_account.dept_id
                WHERE user_account.account_id = ? AND dept.tenant_id = ?
                """, Integer.class, applicant.accountId(), tenantId));
        assertEquals("PLATFORM_FALLBACK", jdbcTemplate.queryForObject("""
                SELECT review_mode
                FROM t_community_registration_review
                WHERE application_id = ? AND decision = 'APPROVE'
                """, String.class, applicationId));
    }

    @Test
    public void newCommunity_propertyServiceOrganizationVerificationEnablesTenantProjectDepartment() throws Exception {
        LoginSession applicant = ownerLogin(PROPERTY_SERVICE_DIRECTOR_PHONE);
        String streetToken = jwtTokenProvider.generateToken(
                STREET_ACCOUNT_ID, "SYS_USER", STREET_USER_ID, null);

        String registrationJson = mockMvc.perform(post("/api/v1/community-registrations")
                        .header("Authorization", "Bearer " + applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationPayload(
                                "物业张主任", "COMMITTEE_DIRECTOR", "春申物业服务苑", "上海市闵行区春申路 300 弄", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long applicationId = objectMapper.readTree(registrationJson).path("data").path("applicationId").asLong();
        uploadMaterial(applicant.token(), applicationId, "COMMITTEE_FILING");
        mockMvc.perform(post("/api/v1/community-registrations/" + applicationId + "/submit")
                        .header("Authorization", "Bearer " + applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        String approvedJson = mockMvc.perform(post(
                        "/api/v1/admin/community-registrations/" + applicationId + "/reviews")
                        .header("Authorization", "Bearer " + streetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVE",
                                "reviewMode", "STREET",
                                "reviewComment", "新小区业委会备案材料核验通过",
                                "expectedVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")))
                .andReturn().getResponse().getContentAsString();
        JsonNode approved = objectMapper.readTree(approvedJson).path("data");
        long tenantId = approved.path("provisionedTenantId").asLong();
        long committeeUserId = approved.path("onboarding").path("applicantWorkUserId").asLong();
        String committeeToken = jwtTokenProvider.generateToken(
                applicant.accountId(), "SYS_USER", committeeUserId, tenantId);

        String streetCommunityJson = mockMvc.perform(post("/api/v1/auth/switch-managed-community")
                        .header("Authorization", "Bearer " + streetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetTenantId", tenantId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String streetCommunityToken = objectMapper.readTree(streetCommunityJson)
                .path("data").path("new_access_token").asText();

        mockMvc.perform(get("/api/v1/admin/work-identities/dept-options?roleKey=PROPERTY_MANAGER")
                        .header("Authorization", "Bearer " + streetCommunityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));

        String organizationJson = mockMvc.perform(post("/api/v1/admin/property-service-organizations")
                        .header("Authorization", "Bearer " + committeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "legalName", "上海春申物业服务有限公司",
                                "unifiedSocialCreditCode", "91310112MA1G0ABCD1",
                                "projectDeptName", "春申物业服务苑项目部",
                                "serviceContactName", "王经理",
                                "serviceContactPhone", "13900007311",
                                "serviceBasis", "PRELIMINARY_PROPERTY_SERVICE",
                                "serviceStartDate", "2026-07-13"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andReturn().getResponse().getContentAsString();
        long organizationId = objectMapper.readTree(organizationJson).path("data").path("organizationId").asLong();
        uploadPropertyServiceMaterial(committeeToken, organizationId, "BUSINESS_LICENSE");
        uploadPropertyServiceMaterial(committeeToken, organizationId, "PROPERTY_SERVICE_CONTRACT");

        mockMvc.perform(post("/api/v1/admin/property-service-organizations/" + organizationId + "/submit")
                        .header("Authorization", "Bearer " + committeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_VERIFICATION")));

        String verifiedJson = mockMvc.perform(post("/api/v1/admin/property-service-organizations/" + organizationId
                                + "/verifications/manual")
                        .header("Authorization", "Bearer " + streetCommunityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceCode", "GSXT_WEB",
                                "verificationResult", "PASSED",
                                "evidenceReference", "GSXT-20260713-0001",
                                "remark", "企业名称与统一社会信用代码核验一致"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.verifications[0].verificationMethod", is("PROPERTY_MANUAL")))
                .andReturn().getResponse().getContentAsString();
        long projectDeptId = objectMapper.readTree(verifiedJson).path("data").path("projectDeptId").asLong();

        mockMvc.perform(get("/api/v1/admin/work-identities/dept-options?roleKey=PROPERTY_MANAGER")
                        .header("Authorization", "Bearer " + streetCommunityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].deptId", hasItem((int) projectDeptId)))
                .andExpect(jsonPath("$.data[*].tenantId", hasItem((int) tenantId)));

        mockMvc.perform(post("/api/v1/admin/work-identities/accounts/" + applicant.accountId() + "/shadows")
                        .header("Authorization", "Bearer " + streetCommunityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "deptId", projectDeptId,
                                "roleKey", "PROPERTY_MANAGER",
                                "nickName", "王经理",
                                "buildingIds", List.of(),
                                "forceBuildingTransfer", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deptId", is((int) projectDeptId)))
                .andExpect(jsonPath("$.data.roleKey", is("PROPERTY_MANAGER")));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_dept
                WHERE dept_id = ?
                  AND tenant_id = ?
                  AND dept_type = 3
                  AND dept_category = 'S'
                """, Integer.class, projectDeptId, tenantId));
    }

    private long createAndSubmitOwnerApplication(LoginSession applicant) throws Exception {
        String createdJson = mockMvc.perform(post("/api/v1/community-registrations")
                        .header("Authorization", "Bearer " + applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationPayload(
                                "李业主", "OWNER", "莲浦家园", "上海市闵行区莲花南路 200 弄", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long applicationId = objectMapper.readTree(createdJson).path("data").path("applicationId").asLong();
        uploadMaterial(applicant.token(), applicationId, "OWNER_IDENTITY_PROOF");
        mockMvc.perform(post("/api/v1/community-registrations/" + applicationId + "/submit")
                        .header("Authorization", "Bearer " + applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        return applicationId;
    }

    private void uploadMaterial(String token, long applicationId, String materialType) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "registration-proof.pdf", "application/pdf",
                "%PDF-1.4 test registration proof".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/community-registrations/" + applicationId + "/materials")
                        .file(file)
                        .param("materialType", materialType)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.materialType", is(materialType)));
    }

    private void uploadPropertyServiceMaterial(String token, long organizationId, String materialType) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "property-service-proof.pdf", "application/pdf",
                "%PDF-1.4 property service proof".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/property-service-organizations/" + organizationId + "/materials")
                        .file(file)
                        .param("materialType", materialType)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.materialType", is(materialType)));
    }

    private void uploadPropertyManagementModeChangeMaterial(
            String token,
            long requestId,
            String materialType) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "owners-assembly-resolution.pdf", "application/pdf",
                "%PDF-1.4 owners assembly resolution".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/property-management-mode-changes/" + requestId + "/materials")
                        .file(file)
                        .param("materialType", materialType)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.materialType", is(materialType)));
    }

    private LoginSession ownerLogin(String phone) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", phone,
                                "smsCode", "123456",
                                "clientPortal", "OWNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_info.identity_type", is("C_USER")))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return new LoginSession(
                data.path("access_token").asText(), data.path("user_info").path("account_id").asLong());
    }

    private Map<String, Object> registrationPayload(
            String applicantName,
            String identity,
            String communityName,
            String address,
            Integer expectedVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicantName", applicantName);
        payload.put("claimedIdentity", identity);
        payload.put("provinceCode", "310000");
        payload.put("provinceName", "上海市");
        payload.put("cityCode", "310100");
        payload.put("cityName", "上海市");
        payload.put("districtCode", "310112");
        payload.put("districtName", "闵行区");
        payload.put("communityName", communityName);
        payload.put("communityAddress", address);
        payload.put("declaredHouseholdCount", 480);
        payload.put("housingTags", List.of("COMMERCIAL_HOUSING", "SHOP"));
        payload.put("declaredPropertyMode", "TRUST");
        if (expectedVersion != null) {
            payload.put("expectedVersion", expectedVersion);
        }
        return payload;
    }

    private void createPlatformReviewer() {
        jdbcTemplate.update("""
                INSERT INTO t_account (
                    account_id, phone, real_name, real_name_verified,
                    last_active_identity_id, last_active_identity_type, status
                ) VALUES (?, ?, 'MOCK_平台运营', 1, ?, 'SYS_USER', 1)
                """, PLATFORM_ACCOUNT_ID, PLATFORM_PHONE, PLATFORM_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user (user_id, account_id, dept_id, user_name, nick_name, status)
                VALUES (?, ?, 1, 'platform_registration_reviewer', '平台运营审核员', '0')
                """, PLATFORM_USER_ID, PLATFORM_ACCOUNT_ID);
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT role_id FROM sys_role WHERE role_key = 'PLATFORM_OPERATOR'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id, effective_data_scope, granted_by)
                VALUES (?, ?, 'ALL_COMMUNITY', ?)
                """, PLATFORM_USER_ID, roleId, STREET_USER_ID);
    }

    private void cleanup() {
        List<Long> applicationIds = jdbcTemplate.queryForList("""
                SELECT application_id
                FROM t_community_registration_application
                WHERE applicant_phone IN (?, ?, ?)
                """, Long.class, DIRECTOR_PHONE, OWNER_PHONE, PROPERTY_SERVICE_DIRECTOR_PHONE);
        List<Long> tenantIds = applicationIds.stream()
                .map(applicationId -> jdbcTemplate.queryForObject("""
                        SELECT provisioned_tenant_id
                        FROM t_community_registration_application
                        WHERE application_id = ?
                        """, Long.class, applicationId))
                .filter(Objects::nonNull)
                .toList();

        for (Long applicationId : applicationIds) {
            jdbcTemplate.update("DELETE FROM t_community_registration_audit WHERE application_id = ?", applicationId);
            jdbcTemplate.update("DELETE FROM t_community_registration_review WHERE application_id = ?", applicationId);
            jdbcTemplate.update("DELETE FROM t_community_registration_material WHERE application_id = ?", applicationId);
            jdbcTemplate.update("DELETE FROM t_community_registration_housing_tag WHERE application_id = ?", applicationId);
            jdbcTemplate.update("DELETE FROM t_community_onboarding_workspace WHERE application_id = ?", applicationId);
        }
        for (Long tenantId : tenantIds) {
            jdbcTemplate.update("""
                    DELETE FROM t_property_management_mode_change_audit
                    WHERE request_id IN (
                        SELECT request_id
                        FROM t_property_management_mode_change_request
                        WHERE tenant_id = ?
                    )
                    """, tenantId);
            jdbcTemplate.update("""
                    DELETE FROM t_property_management_mode_change_material
                    WHERE request_id IN (
                        SELECT request_id
                        FROM t_property_management_mode_change_request
                        WHERE tenant_id = ?
                    )
                    """, tenantId);
            jdbcTemplate.update("DELETE FROM t_property_management_mode_change_request WHERE tenant_id = ?", tenantId);
            List<Long> enterpriseDeptIds = jdbcTemplate.queryForList("""
                    SELECT DISTINCT enterprise.enterprise_dept_id
                    FROM t_property_service_enterprise enterprise
                    JOIN t_property_service_organization organization
                      ON organization.enterprise_id = enterprise.enterprise_id
                    WHERE organization.tenant_id = ?
                      AND enterprise.enterprise_dept_id IS NOT NULL
                    """, Long.class, tenantId);
            jdbcTemplate.update("""
                    DELETE FROM t_property_service_organization_audit
                    WHERE organization_id IN (
                        SELECT organization_id
                        FROM t_property_service_organization
                        WHERE tenant_id = ?
                    )
                    """, tenantId);
            jdbcTemplate.update("""
                    DELETE FROM t_property_service_organization_verification
                    WHERE organization_id IN (
                        SELECT organization_id
                        FROM t_property_service_organization
                        WHERE tenant_id = ?
                    )
                    """, tenantId);
            jdbcTemplate.update("""
                    DELETE FROM t_property_service_organization_material
                    WHERE organization_id IN (
                        SELECT organization_id
                        FROM t_property_service_organization
                        WHERE tenant_id = ?
                    )
                    """, tenantId);
            jdbcTemplate.update("DELETE FROM t_property_service_organization WHERE tenant_id = ?", tenantId);
            for (Long enterpriseDeptId : enterpriseDeptIds) {
                jdbcTemplate.update("""
                        DELETE FROM t_property_service_enterprise
                        WHERE enterprise_dept_id = ?
                          AND NOT EXISTS (
                              SELECT 1
                              FROM t_property_service_organization
                              WHERE enterprise_id = t_property_service_enterprise.enterprise_id
                          )
                        """, enterpriseDeptId);
            }
            jdbcTemplate.update("DELETE FROM t_committee_member_position WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("""
                    DELETE FROM sys_user_role
                    WHERE user_id IN (
                        SELECT user_account.user_id
                        FROM sys_user user_account
                        JOIN sys_dept dept ON dept.dept_id = user_account.dept_id
                        WHERE dept.tenant_id = ?
                    )
                    """, tenantId);
            jdbcTemplate.update("""
                    DELETE FROM sys_user
                    WHERE dept_id IN (SELECT dept_id FROM sys_dept WHERE tenant_id = ?)
                    """, tenantId);
            jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ?", tenantId);
            for (Long enterpriseDeptId : enterpriseDeptIds) {
                jdbcTemplate.update("DELETE FROM sys_dept WHERE dept_id = ?", enterpriseDeptId);
            }
        }
        for (Long applicationId : applicationIds) {
            jdbcTemplate.update("DELETE FROM t_community_registration_application WHERE application_id = ?", applicationId);
        }
        for (Long tenantId : tenantIds) {
            jdbcTemplate.update("DELETE FROM t_tenant_community WHERE tenant_id = ?", tenantId);
        }
        jdbcTemplate.update("DELETE FROM c_user WHERE account_id IN (SELECT account_id FROM t_account WHERE phone IN (?, ?, ?))",
                DIRECTOR_PHONE, OWNER_PHONE, PROPERTY_SERVICE_DIRECTOR_PHONE);
        jdbcTemplate.update("DELETE FROM t_account WHERE phone IN (?, ?, ?)",
                DIRECTOR_PHONE, OWNER_PHONE, PROPERTY_SERVICE_DIRECTOR_PHONE);

        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", PLATFORM_USER_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE user_id = ?", PLATFORM_USER_ID);
        jdbcTemplate.update("DELETE FROM t_account WHERE account_id = ?", PLATFORM_ACCOUNT_ID);
    }

    private record LoginSession(String token, long accountId) {
    }
}
