// 关联业务：向业主本人披露其有表决权的维修方案，并把本人操作接入统一线上表决能力。
package com.pangu.application.repair;

import com.pangu.application.voting.OnlineVotingException;
import com.pangu.application.voting.OnlineVotingService;
import com.pangu.application.voting.VotingDecisionResultProjector;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProjectVoting;
import com.pangu.domain.model.voting.OnlineBallotSubmission;
import com.pangu.domain.model.voting.OnlinePaperAssistanceRequest;
import com.pangu.domain.model.voting.OnlineVotingAcknowledgement;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairProjectVotingRepository;
import com.pangu.domain.repository.VotingExecutionRepository;
import com.pangu.domain.repository.VotingResultRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;

/**
 * 业主维修表决入口。
 *
 * <p>项目编号只用于找到已冻结的统一表决包；是否有权查看和投票仍以包内冻结名册及当前实名
 * C 端身份为准。返回内容不包含其他业主状态、个人选择或内部对象存储地址。
 */
@Service
@RequiredArgsConstructor
public class OwnerRepairProjectVotingService {

    private final RepairProjectVotingRepository repairVotingRepository;
    private final RepairProjectRepository projectRepository;
    private final VotingExecutionRepository votingExecutionRepository;
    private final VotingSubjectRepository subjectRepository;
    private final VotingResultRepository resultRepository;
    private final VotingDecisionResultProjector votingDecisionResultProjector;
    private final OnlineVotingService onlineVotingService;
    private final UserContextHolder userContextHolder;

    @Transactional(readOnly = true)
    public List<RepairProjectVoting.OwnerTask> listTasks() {
        UserContext owner = requireOwner();
        return repairVotingRepository.listOwnerTasks(owner.tenantId(), owner.uid());
    }

    @Transactional(readOnly = true)
    public Disclosure disclosure(Long projectId) {
        Resolved resolved = resolve(projectId);
        List<Long> ownerOpids = resolved.tasks().stream().map(RepairProjectVoting.OwnerTask::opid).distinct().toList();
        OnlineVotingService.OwnerProgress progress = onlineVotingService.ownerProgress(
                resolved.executionPackage().getPackageId(), resolved.owner().tenantId(), ownerOpids);
        List<RepairProject.PlanAttachment> planAttachments = projectRepository.listPlanAttachments(
                resolved.voting().planId(), resolved.owner().tenantId());
        List<RepairProject.Attachment> projectAttachments = projectRepository.listAttachments(
                resolved.project().projectId(), resolved.owner().tenantId());
        List<PublishedAttachment> disclosedAttachments = new ArrayList<>(planAttachments.stream()
                .map(reference -> projectAttachments.stream()
                        .filter(attachment -> attachment.attachmentId().equals(reference.attachmentId()))
                        .findFirst()
                        .map(attachment -> new PublishedAttachment(
                                attachment.attachmentId(), reference.purpose(), attachment.originalFileName(),
                                attachment.contentType(), attachment.fileSize()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList());
        if (resolved.voting().paperBallotTemplateAttachmentId() != null) {
            projectAttachments.stream()
                    .filter(attachment -> attachment.attachmentId()
                            .equals(resolved.voting().paperBallotTemplateAttachmentId()))
                    .filter(attachment -> Objects.equals(
                            attachment.sha256(), resolved.voting().paperBallotTemplateHash()))
                    .findFirst()
                    .map(attachment -> new PublishedAttachment(
                            attachment.attachmentId(), RepairProject.AttachmentPurpose.PAPER_BALLOT_TEMPLATE,
                            attachment.originalFileName(), attachment.contentType(), attachment.fileSize()))
                    .ifPresent(disclosedAttachments::add);
        }
        return new Disclosure(
                resolved.project().projectId(), resolved.project().projectNo(), resolved.project().projectName(),
                new PlanSummary(resolved.plan().planId(), resolved.plan().versionNo(),
                        resolved.plan().planDescription(), resolved.plan().budgetTotal()),
                new SubjectSummary(resolved.subject().getSubjectId(), resolved.subject().getTitle(),
                        resolved.subject().getContent()),
                resultRepository.findBySubjectId(resolved.subject().getSubjectId())
                        .map(this::toResultSummary)
                        .orElse(null),
                resolved.executionPackage().getCollectionMode(),
                resolved.executionPackage().getStatus(), resolved.executionPackage().getPackageHash(),
                resolved.executionPackage().getVoteStartAt(), resolved.executionPackage().getVoteEndAt(),
                resolved.tasks(), progress,
                projectRepository.listWorkPoints(resolved.plan().planId(), resolved.owner().tenantId()),
                disclosedAttachments);
    }

    @Transactional
    public OnlineVotingAcknowledgement acknowledge(
            Long projectId, Long opid, String packageHash, Boolean confirmed) {
        Resolved resolved = resolve(projectId);
        try {
            return onlineVotingService.acknowledge(new OnlineVotingService.AcknowledgeCommand(
                    resolved.executionPackage().getPackageId(), resolved.owner().tenantId(), opid,
                    packageHash, confirmed, Instant.now()));
        } catch (OnlineVotingException ex) {
            throw translate(ex);
        }
    }

    @Transactional
    public OnlineBallotSubmission submit(
            Long projectId,
            Long opid,
            String packageHash,
            Boolean confirmed,
            String idempotencyKey,
            List<OnlineVotingService.Decision> decisions) {
        Resolved resolved = resolve(projectId);
        try {
            return onlineVotingService.submit(new OnlineVotingService.SubmitCommand(
                    resolved.executionPackage().getPackageId(), resolved.owner().tenantId(), opid,
                    packageHash, confirmed, idempotencyKey, decisions, Instant.now()));
        } catch (OnlineVotingException ex) {
            throw translate(ex);
        }
    }

    @Transactional
    public OnlinePaperAssistanceRequest requestPaperAssistance(
            Long projectId, Long opid, String packageHash) {
        Resolved resolved = resolve(projectId);
        try {
            return onlineVotingService.requestPaperAssistance(new OnlineVotingService.PaperAssistanceCommand(
                    resolved.executionPackage().getPackageId(), resolved.owner().tenantId(), opid,
                    packageHash, Instant.now()));
        } catch (OnlineVotingException ex) {
            throw translate(ex);
        }
    }

    @Transactional
    public OnlinePaperAssistanceRequest withdrawPaperAssistance(
            Long projectId, Long requestId, Long opid, String packageHash) {
        Resolved resolved = resolve(projectId);
        try {
            return onlineVotingService.withdrawPaperAssistance(
                    resolved.executionPackage().getPackageId(), requestId, resolved.owner().tenantId(), opid,
                    packageHash, Instant.now());
        } catch (OnlineVotingException ex) {
            throw translate(ex);
        }
    }

    private Resolved resolve(Long projectId) {
        UserContext owner = requireOwner();
        List<RepairProjectVoting.OwnerTask> tasks = repairVotingRepository
                .listOwnerTasks(owner.tenantId(), owner.uid()).stream()
                .filter(task -> Objects.equals(task.projectId(), projectId))
                .toList();
        if (tasks.isEmpty()) {
            throw new RepairWorkOrderApplicationException(
                    NOT_FOUND, "当前业主没有该维修事项的表决权");
        }
        RepairProject project = projectRepository.findProject(projectId, owner.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "维修工程项目不存在"));
        Long planId = tasks.getFirst().planId();
        RepairProject.PlanVersion plan = projectRepository.listPlans(projectId, owner.tenantId()).stream()
                .filter(candidate -> candidate.planId().equals(planId)).findFirst()
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "本次表决对应的维修方案不存在"));
        RepairProjectVoting voting = repairVotingRepository.find(projectId, planId, owner.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "本次维修表决记录不存在"));
        VotingExecutionPackage executionPackage = votingExecutionRepository.findPackage(
                        voting.executionPackageId(), owner.tenantId())
                .filter(candidate -> candidate.getBusinessType()
                        == VotingExecutionPackage.BusinessType.REPAIR_PROJECT)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "本次维修表决尚未准备完成"));
        VotingSubject subject = subjectRepository.findById(voting.subjectId())
                .filter(candidate -> Objects.equals(candidate.getTenantId(), owner.tenantId()))
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "本次维修表决事项不存在"));
        return new Resolved(owner, project, plan, voting, executionPackage, subject, tasks);
    }

    private UserContext requireOwner() {
        UserContext owner = userContextHolder.current();
        if (owner == null || !owner.isCUser() || owner.accountId() == null
                || owner.uid() == null || owner.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到当前小区业主身份");
        }
        return owner;
    }

    private RepairWorkOrderApplicationException translate(OnlineVotingException failure) {
        RepairWorkOrderApplicationException.Reason reason = switch (failure.getReason()) {
            case NOT_FOUND -> NOT_FOUND;
            case FORBIDDEN, AUTHENTICATION_REQUIRED -> FORBIDDEN;
            case INVALID_ARGUMENT -> PARAM_INVALID;
            case INVALID_STATUS, ALREADY_SUBMITTED, CONCURRENT_MODIFICATION -> INVALID_STATUS;
        };
        return new RepairWorkOrderApplicationException(reason, failure.getMessage(), failure);
    }

    private ResultSummary toResultSummary(VotingResultRepository.Snapshot snapshot) {
        VotingDecisionResultProjector.View view = votingDecisionResultProjector.project(snapshot);
        return new ResultSummary(
                view.quorumSatisfied(), view.passed(), view.totalArea(), view.totalOwnerCount(),
                view.participatingArea(), view.participatingOwnerCount(),
                view.supportArea(), view.supportOwnerCount(),
                view.againstArea(), view.againstOwnerCount(),
                view.abstainArea(), view.abstainOwnerCount(), view.nonResponse());
    }

    public record Disclosure(
            Long projectId,
            String projectNo,
            String projectName,
            PlanSummary plan,
            SubjectSummary subject,
            ResultSummary result,
            VotingExecutionPackage.CollectionMode collectionMode,
            VotingExecutionPackage.Status status,
            String packageHash,
            Instant voteStartAt,
            Instant voteEndAt,
            List<RepairProjectVoting.OwnerTask> properties,
            OnlineVotingService.OwnerProgress progress,
            List<RepairProject.WorkPoint> workPoints,
            List<PublishedAttachment> attachments
    ) {
        public Disclosure {
            properties = properties == null ? List.of() : List.copyOf(properties);
            workPoints = workPoints == null ? List.of() : List.copyOf(workPoints);
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    private record Resolved(
            UserContext owner,
            RepairProject project,
            RepairProject.PlanVersion plan,
            RepairProjectVoting voting,
            VotingExecutionPackage executionPackage,
            VotingSubject subject,
            List<RepairProjectVoting.OwnerTask> tasks
    ) {
    }

    public record PlanSummary(Long planId, Integer versionNo, String description, java.math.BigDecimal budgetTotal) {
    }

    public record SubjectSummary(Long subjectId, String title, String content) {
    }

    /** 表决结束后向相关业主公开的汇总结果，不包含逐户选择。 */
    public record ResultSummary(
            boolean quorumSatisfied,
            boolean passed,
            java.math.BigDecimal totalArea,
            long totalOwnerCount,
            java.math.BigDecimal participatingArea,
            long participatingOwnerCount,
            java.math.BigDecimal supportArea,
            Long supportOwnerCount,
            java.math.BigDecimal againstArea,
            Long againstOwnerCount,
            java.math.BigDecimal abstainArea,
            Long abstainOwnerCount,
            VotingDecisionResultProjector.NonResponseSummary nonResponse
    ) {
    }

    public record PublishedAttachment(
            Long attachmentId,
            RepairProject.AttachmentPurpose purpose,
            String originalFileName,
            String contentType,
            Long fileSize
    ) {
    }
}
