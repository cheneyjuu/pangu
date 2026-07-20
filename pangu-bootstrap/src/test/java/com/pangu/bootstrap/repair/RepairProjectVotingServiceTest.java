// 关联业务：验证维修授权提案只能按精确费用承担房屋原子接入统一表决，范围漂移时不创建孤立记录。
package com.pangu.bootstrap.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.repair.RepairProjectVotingService;
import com.pangu.application.repair.RepairWorkOrderApplicationException;
import com.pangu.application.voting.FormalVotingRulePolicy;
import com.pangu.application.voting.VotingDecisionResultProjector;
import com.pangu.application.voting.VotingExecutionService;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProjectVoting;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.OwnersAssemblyRuleRepository;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairProjectVotingRepository;
import com.pangu.domain.repository.VotingResultRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepairProjectVotingServiceTest {

    @Mock private RepairProjectRepository projectRepository;
    @Mock private RepairProjectVotingRepository votingRepository;
    @Mock private RepairProjectGovernanceRepository governanceRepository;
    @Mock private OwnersAssemblyRuleRepository ruleRepository;
    @Mock private VotingSubjectRepository subjectRepository;
    @Mock private VotingResultRepository resultRepository;
    @Mock private VotingExecutionService votingExecutionService;
    @Mock private FormalVotingRulePolicy rulePolicy;
    @Mock private UserContextHolder userContextHolder;

    private RepairProjectVotingService service;
    private RepairProject project;
    private RepairProject.PlanVersion plan;
    private OwnersAssemblyRule rule;
    private Instant startAt;
    private Instant endAt;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new RepairProjectVotingService(
                projectRepository, votingRepository, governanceRepository, ruleRepository,
                subjectRepository, resultRepository, new VotingDecisionResultProjector(objectMapper),
                votingExecutionService, rulePolicy, userContextHolder, objectMapper);
        UserContext actor = new UserContext(
                9001L, UserContext.IdentityType.SYS_USER, 800101L, 10001L,
                10L, UserContext.DeptCategory.B, 1, DataScopeType.ALL_COMMUNITY,
                AuthenticationLevel.L1, "COMMITTEE_DIRECTOR", Set.of(), Set.of());
        when(userContextHolder.current()).thenReturn(actor);
        project = org.mockito.Mockito.mock(RepairProject.class);
        when(project.projectId()).thenReturn(101L);
        when(project.tenantId()).thenReturn(10001L);
        when(project.projectName()).thenReturn("一号楼外墙维修");
        when(project.status()).thenReturn(RepairProject.Status.AUTHORIZATION_IN_PROGRESS);
        when(project.activePlanId()).thenReturn(201L);
        when(project.version()).thenReturn(3);
        plan = org.mockito.Mockito.mock(RepairProject.PlanVersion.class);
        when(plan.planId()).thenReturn(201L);
        when(plan.versionNo()).thenReturn(2);
        when(plan.status()).thenReturn(RepairProject.PlanStatus.AUTHORIZATION_FROZEN);
        when(plan.authorizationSnapshotHash()).thenReturn("a".repeat(64));
        when(plan.budgetTotal()).thenReturn(new BigDecimal("120000.00"));
        rule = org.mockito.Mockito.mock(OwnersAssemblyRule.class);
        when(rule.ruleId()).thenReturn(301L);
        when(rule.ruleName()).thenReturn("小区议事规则");
        when(rule.ruleVersion()).thenReturn("2026-01");
        when(rule.configurationSha256()).thenReturn("b".repeat(64));
        OwnersAssemblyRuleConfiguration ruleConfiguration =
                org.mockito.Mockito.mock(OwnersAssemblyRuleConfiguration.class);
        when(ruleConfiguration.proxyVotingPolicy()).thenReturn(
                OwnersAssemblyRuleConfiguration.ProxyVotingPolicy.NOT_ALLOWED);
        when(rule.configuration()).thenReturn(ruleConfiguration);
        startAt = Instant.now().plusSeconds(3600);
        endAt = startAt.plusSeconds(86400);
        when(projectRepository.findProjectForUpdate(101L, 10001L)).thenReturn(Optional.of(project));
        when(projectRepository.listPlans(101L, 10001L)).thenReturn(List.of(plan));
        when(votingRepository.find(101L, 201L, 10001L)).thenReturn(Optional.empty());
        when(ruleRepository.findActive(10001L)).thenReturn(Optional.of(rule));
        RepairProject.Attachment template = org.mockito.Mockito.mock(RepairProject.Attachment.class);
        when(template.attachmentId()).thenReturn(401L);
        when(template.projectId()).thenReturn(101L);
        when(template.tenantId()).thenReturn(10001L);
        when(template.originalFileName()).thenReturn("已用印表决票模板.pdf");
        when(template.contentType()).thenReturn("application/pdf");
        when(template.sha256()).thenReturn("c".repeat(64));
        when(projectRepository.findAttachment(401L, 101L, 10001L)).thenReturn(Optional.of(template));
    }

    @Test
    void preparesOneRepairPackageFromExactFrozenRooms() {
        RepairProject.AllocationRoom allocation = new RepairProject.AllocationRoom(
                1L, 201L, 10001L, 501L, 401L, "1单元", 601L,
                new BigDecimal("88.50"), null);
        VotingElectorateSnapshot.Candidate candidate = new VotingElectorateSnapshot.Candidate(
                701L, 501L, 401L, new BigDecimal("88.50"), 801L, 601L, true);
        when(projectRepository.listAllocationRooms(201L, 10001L)).thenReturn(List.of(allocation));
        when(votingExecutionService.listElectorateCandidatesByRoomIds(10001L, List.of(501L)))
                .thenReturn(List.of(candidate));
        when(subjectRepository.insert(any(VotingSubject.class))).thenAnswer(invocation -> {
            VotingSubject subject = invocation.getArgument(0);
            subject.setSubjectId(901L);
            return subject;
        });
        VotingExecutionPackage executionPackage = packageWithId(1001L);
        when(votingExecutionService.create(any())).thenReturn(executionPackage);
        when(votingExecutionService.freezeExactElectorate(
                anyLong(), anyLong(), any(), anyLong(), any())).thenReturn(executionPackage);
        RepairProjectVoting link = new RepairProjectVoting(
                1101L, 101L, 201L, 10001L, 901L, 1001L, 301L, "b".repeat(64),
                401L, "c".repeat(64),
                VotingExecutionPackage.CollectionMode.PAPER,
                RepairProjectVoting.Status.PREPARED, null, 800101L, Instant.now(),
                null, null, null, null, 0L);
        when(votingRepository.insert(any())).thenReturn(link);
        when(projectRepository.advanceVersion(101L, 10001L, 3)).thenReturn(1);

        RepairProjectVotingService.Details result = service.prepare(
                101L, new RepairProjectVotingService.PrepareCommand(
                        3, VotingExecutionPackage.CollectionMode.PAPER, 401L, startAt, endAt));

        assertEquals(1101L, result.voting().linkId());
        assertEquals(1001L, result.executionPackage().getPackageId());
        verify(votingExecutionService).freezeExactElectorate(
                anyLong(), anyLong(), any(), anyLong(), any());
        verify(projectRepository).advanceVersion(101L, 10001L, 3);
    }

    @Test
    void rejectsRosterAreaDriftBeforeCreatingSubjectOrPackage() {
        RepairProject.AllocationRoom allocation = new RepairProject.AllocationRoom(
                1L, 201L, 10001L, 501L, 401L, "1单元", 601L,
                new BigDecimal("88.50"), null);
        VotingElectorateSnapshot.Candidate changed = new VotingElectorateSnapshot.Candidate(
                701L, 501L, 401L, new BigDecimal("89.00"), 801L, 601L, true);
        when(projectRepository.listAllocationRooms(201L, 10001L)).thenReturn(List.of(allocation));
        when(votingExecutionService.listElectorateCandidatesByRoomIds(10001L, List.of(501L)))
                .thenReturn(List.of(changed));

        RepairWorkOrderApplicationException failure = assertThrows(
                RepairWorkOrderApplicationException.class,
                () -> service.prepare(101L, new RepairProjectVotingService.PrepareCommand(
                        3, VotingExecutionPackage.CollectionMode.PAPER, 401L, startAt, endAt)));

        assertEquals("房屋楼栋或法定面积已变化，请重新核验并形成新方案版本 roomId=501", failure.getMessage());
        verify(subjectRepository, never()).insert(any());
        verify(votingExecutionService, never()).create(any());
        verify(votingRepository, never()).insert(any());
    }

    private VotingExecutionPackage packageWithId(Long packageId) {
        VotingExecutionPackage executionPackage = VotingExecutionPackage.draft(
                10001L, VotingExecutionPackage.BusinessType.REPAIR_PROJECT, 201L,
                "REPAIR_AUTHORIZATION_PROPOSAL", 201L, "a".repeat(64),
                "OWNERS_ASSEMBLY_RULE_VERSION", 301L, "b".repeat(64),
                com.pangu.domain.model.voting.VotingScope.REPAIR_ALLOCATION, 201L,
                VotingExecutionPackage.CollectionMode.PAPER,
                VotingExecutionPackage.DuplicateBallotPolicy.NOT_APPLICABLE,
                startAt, endAt, 800101L);
        executionPackage.assignId(packageId);
        return executionPackage;
    }
}
