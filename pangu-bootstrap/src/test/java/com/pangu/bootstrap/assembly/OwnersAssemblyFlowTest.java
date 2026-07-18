// 关联业务：验证业主大会的会前事项、公示安排与纸质/线上表决状态机。
package com.pangu.bootstrap.assembly;

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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class OwnersAssemblyFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACC_DIRECTOR = 999811L;
    private static final long USR_DIRECTOR = 800101L;
    private static final long ACC_OWNER = 999913L;
    private static final long UID_OWNER = 70002L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    @BeforeEach
    public void setUpObjectStorage() {
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2),
                        "owners-assembly-material-etag"));
    }

    @AfterEach
    public void clean() {
        jdbcTemplate.update("DELETE FROM t_owners_assembly_session WHERE title LIKE 'IT-业主大会-%'");
        jdbcTemplate.update("""
                DELETE FROM t_vote_item
                WHERE subject_id IN (
                    SELECT subject_id FROM t_voting_subject WHERE title LIKE 'IT-业主大会-%'
                )
                """);
        jdbcTemplate.update("""
                DELETE FROM t_voting_result
                WHERE subject_id IN (
                    SELECT subject_id FROM t_voting_subject WHERE title LIKE 'IT-业主大会-%'
                )
                """);
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE title LIKE 'IT-业主大会-%'");
        jdbcTemplate.update("UPDATE c_user SET auth_level = 2 WHERE uid = ?", UID_OWNER);
    }

    @Test
    public void paperAndOnlineAssembly_onlineRealNameVoteSupersedesPaperBeforeDeadline() throws Exception {
        jdbcTemplate.update("UPDATE c_user SET auth_level = 3 WHERE uid = ?", UID_OWNER);
        Long opid = jdbcTemplate.queryForObject("""
                SELECT opid FROM c_owner_property
                WHERE uid = ? AND tenant_id = ? AND building_id = 30001
                ORDER BY opid LIMIT 1
                """, Long.class, UID_OWNER, TENANT);
        String directorToken = token(ACC_DIRECTOR, "SYS_USER", USR_DIRECTOR);
        String ownerToken = token(ACC_OWNER, "C_USER", UID_OWNER);
        Instant voteStartAt = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant voteEndAt = Instant.now().plus(1, ChronoUnit.DAYS);

        long sessionId = createSession(directorToken);
        long packageId = createPackage(directorToken, sessionId, voteStartAt, voteEndAt);
        long subjectId = addSubject(directorToken, packageId);

        mockMvc.perform(post("/api/v1/owners-assembly-packages/" + packageId + "/lock")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PUBLIC_NOTICE")))
                .andExpect(jsonPath("$.data.packageHash").isNotEmpty());
        jdbcTemplate.update("""
                UPDATE t_owners_assembly_package
                SET public_notice_end_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE package_id = ?
                """, packageId);

        mockMvc.perform(post("/api/v1/owners-assembly-packages/" + packageId + "/open-voting")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOTING")));

        mockMvc.perform(get("/api/v1/me/voting-subjects/" + subjectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject.assemblyPackage.packageId", is((int) packageId)))
                .andExpect(jsonPath("$.data.subject.assemblyPackage.votingChannelPolicy", is("PAPER_AND_ONLINE")))
                .andExpect(jsonPath("$.data.subject.assemblyPackage.onlineAllowed", is(true)))
                .andExpect(jsonPath("$.data.subject.assemblyPackage.ballotTemplateHash", is("ballot-template-hash")));

        recordDelivery(directorToken, packageId, opid, "PAPER", "HAND_DELIVERY", "paper-delivery-hash");
        mockMvc.perform(post("/api/v1/owners-assembly-packages/" + packageId + "/paper-votes")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subjectId", subjectId,
                                "opid", opid,
                                "choice", "SUPPORT",
                                "ballotFileHash", "paper-ballot-hash"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.voteChannel", is("PAPER")));

        recordDelivery(directorToken, packageId, opid, "ONLINE", "APP_PUSH", "online-delivery-hash");
        mockMvc.perform(post("/api/v1/me/owners-assembly-packages/" + packageId + "/online-votes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subjectId", subjectId,
                                "opid", opid,
                                "choice", "AGAINST",
                                "ballotFileHash", "online-ballot-hash",
                                "signatureHash", "owner-esign-hash"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.voteChannel", is("ONLINE")))
                .andExpect(jsonPath("$.data.valid", is(true)));

        Integer activeOnlineVotes = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM t_vote_item
                WHERE subject_id = ? AND opid = ? AND valid_flag = 1
                  AND vote_channel = 1 AND choice = 2
                """, Integer.class, subjectId, opid);
        Integer invalidPaperVotes = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM t_vote_item
                WHERE subject_id = ? AND opid = ? AND valid_flag = 0
                  AND vote_channel = 2
                """, Integer.class, subjectId, opid);
        Integer invalidAssemblyPaperRecords = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM t_owners_assembly_vote_record
                WHERE subject_id = ? AND opid = ? AND valid_flag = 0
                  AND vote_channel = 'PAPER'
                """, Integer.class, subjectId, opid);
        assertEquals(1, activeOnlineVotes);
        assertEquals(1, invalidPaperVotes);
        assertEquals(1, invalidAssemblyPaperRecords);

        mockMvc.perform(post("/api/v1/me/owners-assembly-packages/" + packageId + "/online-votes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subjectId", subjectId,
                                "opid", opid,
                                "choice", "SUPPORT",
                                "ballotFileHash", "online-ballot-hash-2",
                                "signatureHash", "owner-esign-hash-2"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(42610)));
    }

    @Test
    public void writtenAssembly_canDraftSubjectsBeforeConfirmingPublicNoticeArrangement() throws Exception {
        String directorToken = token(ACC_DIRECTOR, "SYS_USER", USR_DIRECTOR);
        String title = "IT-业主大会-书面征询-" + System.nanoTime();
        String createResponse = mockMvc.perform(post("/api/v1/owners-assemblies")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", title,
                                "preparationMode", "WRITTEN_DECISION"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("PREPARING")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long sessionId = objectMapper.readTree(createResponse).path("data").path("sessionId").asLong();

        mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/subjects")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subjectType", "MAJOR",
                                "title", "IT-业主大会-公共区域改造方案-" + System.nanoTime(),
                                "content", "公共区域改造方案及其附件索引"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subjectType", is("MAJOR")));

        mockMvc.perform(get("/api/v1/owners-assemblies/" + sessionId + "/workspace")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assembly.title", is(title)))
                .andExpect(jsonPath("$.data.assembly.preparationMode", is("WRITTEN_DECISION")))
                .andExpect(jsonPath("$.data.arrangement").doesNotExist())
                .andExpect(jsonPath("$.data.draftSubjects.length()", is(1)))
                .andExpect(jsonPath("$.data.draftSubjects[0].subjectType", is("MAJOR")))
                .andExpect(jsonPath("$.data.draftSubjects[0].partyRatioFloor").doesNotExist());
    }

    @Test
    public void writtenAssembly_confirmsPaperOnlyArrangementFromUploadedMaterialsWithoutExposingTechnicalFields() throws Exception {
        String directorToken = token(ACC_DIRECTOR, "SYS_USER", USR_DIRECTOR);
        String title = "IT-业主大会-材料归档-" + System.nanoTime();
        long sessionId = createWrittenSession(directorToken, title);

        mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/subjects")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subjectType", "MAJOR",
                                "title", "IT-业主大会-公共区域改造方案-" + System.nanoTime(),
                                "content", "公共区域改造方案及其附件索引"))))
                .andExpect(status().isCreated());

        long publicNoticeMaterialId = uploadMaterial(
                directorToken, sessionId, "PUBLIC_NOTICE", "公示公告.pdf", "application/pdf", "notice");
        long planMaterialId = uploadMaterial(
                directorToken, sessionId, "PLAN_ATTACHMENT", "改造方案.pdf", "application/pdf", "plan");
        long ballotTemplateMaterialId = uploadMaterial(
                directorToken, sessionId, "PAPER_BALLOT_TEMPLATE", "盖章选票模板.pdf", "application/pdf", "ballot");

        mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/arrangement")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "publicNoticeDays", 7,
                                "voteStartAt", Instant.now().plus(8, ChronoUnit.DAYS).toString(),
                                "voteEndAt", Instant.now().plus(15, ChronoUnit.DAYS).toString(),
                                "publicNoticeMaterialId", publicNoticeMaterialId,
                                "planAttachmentMaterialIds", List.of(planMaterialId),
                                "ballotTemplateMaterialId", ballotTemplateMaterialId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PACKAGE_DRAFT")))
                .andExpect(jsonPath("$.data.votingChannelPolicy", is("PAPER_ONLY")))
                .andExpect(jsonPath("$.data.packageId").doesNotExist())
                .andExpect(jsonPath("$.data.announcementHash").doesNotExist());

        mockMvc.perform(get("/api/v1/owners-assemblies/" + sessionId + "/workspace")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.arrangement.votingChannelPolicy", is("PAPER_ONLY")))
                .andExpect(jsonPath("$.data.arrangement.packageId").doesNotExist())
                .andExpect(jsonPath("$.data.formalSubjects.length()", is(1)))
                .andExpect(jsonPath("$.data.materials.length()", is(3)))
                .andExpect(jsonPath("$.data.materials[0].objectKey").doesNotExist())
                .andExpect(jsonPath("$.data.materials[0].contentSha256").doesNotExist());

        verify(objectStorage, times(3)).put(anyString(), any(byte[].class), anyString(), anyString());
    }

    @Test
    public void newAssemblyWorkflow_rejectsOnlineAndHybridMeetingModeUntilIdentityBindingIsVerified() throws Exception {
        String directorToken = token(ACC_DIRECTOR, "SYS_USER", USR_DIRECTOR);

        mockMvc.perform(post("/api/v1/owners-assemblies")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "IT-业主大会-不允许线上混合模式-" + System.nanoTime(),
                                "preparationMode", "ONLINE_AND_OFFLINE"))))
                .andExpect(status().isBadRequest());
    }

    private long createSession(String token) throws Exception {
        String response = mockMvc.perform(post("/api/v1/owners-assemblies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "IT-业主大会-公共维修-" + System.nanoTime(),
                                "preparationMode", "FULL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("PREPARING")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("sessionId").asLong();
    }

    private long createWrittenSession(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/v1/owners-assemblies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", title,
                                "preparationMode", "WRITTEN_DECISION"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("sessionId").asLong();
    }

    private long uploadMaterial(String token,
                                long sessionId,
                                String materialType,
                                String fileName,
                                String contentType,
                                String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, contentType, content.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v1/owners-assemblies/" + sessionId + "/materials")
                        .file(file)
                        .param("materialType", materialType)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("materialId").asLong();
    }

    private long createPackage(String token, long sessionId, Instant voteStartAt, Instant voteEndAt) throws Exception {
        String response = mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/packages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "votingChannelPolicy", "PAPER_AND_ONLINE",
                                "publicNoticeDays", 7,
                                "announcementHash", "announcement-hash",
                                "attachmentManifestHash", "attachment-manifest-hash",
                                "ballotTemplateHash", "ballot-template-hash",
                                "electronicSealHash", "committee-e-seal-hash",
                                "voteStartAt", voteStartAt.toString(),
                                "voteEndAt", voteEndAt.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("PACKAGE_DRAFT")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("packageId").asLong();
    }

    private long addSubject(String token, long packageId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/owners-assembly-packages/" + packageId + "/subjects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subjectType", "GENERAL",
                                "scope", "BUILDING",
                                "scopeReferenceId", 30001,
                                "title", "IT-业主大会-1号楼公共维修事项-" + System.nanoTime(),
                                "content", "1号楼公共维修事项纸质与线上双渠道表决"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(response).path("data");
        return data.path("subjectId").asLong();
    }

    private void recordDelivery(String token,
                                long packageId,
                                long opid,
                                String channel,
                                String method,
                                String evidenceHash) throws Exception {
        mockMvc.perform(post("/api/v1/owners-assembly-packages/" + packageId + "/deliveries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "opid", opid,
                                "deliveryChannel", channel,
                                "deliveryMethod", method,
                                "evidenceHash", evidenceHash))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deliveryChannel", is(channel)));
    }

    private String token(long accountId, String identityType, long activeIdentityId) {
        return jwtTokenProvider.generateToken(accountId, identityType, activeIdentityId, TENANT);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
