// 关联业务：校验业主对关联报修的可见权，并投影当前已锁定维修实施方案。
package com.pangu.application.repair;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.FundingSlice;
import com.pangu.domain.model.repair.RepairProject.PlanAttachment;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.WorkPoint;
import com.pangu.domain.model.repair.RepairProjectSourcing.Quote;
import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLine;
import com.pangu.domain.model.repair.RepairProjectSourcing.Selection;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairProjectSourcingRepository;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerRepairProjectQueryService {

    private final RepairWorkOrderService workOrderService;
    private final RepairProjectRepository projectRepository;
    private final RepairProjectSourcingRepository sourcingRepository;
    private final RepairProjectGovernanceRepository governanceRepository;
    private final RepairNarrativeImageService narrativeImageService;
    private final UserContextHolder userContextHolder;

    @Transactional(readOnly = true)
    public Optional<OwnerRepairProjectDisclosure> findPublishedByWorkOrder(Long workOrderId) {
        // 复用报修本人可见规则，避免工程查询自行放宽楼栋或小区数据边界。
        var workOrder = workOrderService.findMine(workOrderId);
        return projectRepository.findProjectByActivePlanWorkOrder(workOrderId, workOrder.tenantId())
                .flatMap(project -> disclosure(workOrderId, project));
    }

    @Transactional(readOnly = true)
    public Optional<OwnerRepairProjectDisclosure> findPublishedByDecision(Long decisionId) {
        UserContext owner = userContextHolder.current();
        if (owner == null || !owner.isCUser() || owner.uid() == null || owner.tenantId() == null) {
            return Optional.empty();
        }
        return governanceRepository.findOwnerDecisionTask(decisionId, owner.uid(), owner.tenantId())
                .flatMap(task -> projectRepository.findProject(task.projectId(), owner.tenantId()))
                .flatMap(project -> disclosure(null, project));
    }

    private Optional<OwnerRepairProjectDisclosure> disclosure(Long workOrderId, RepairProject project) {
        if (project.activePlanId() == null) {
            return Optional.empty();
        }
        Optional<PlanVersion> publishedPlan = projectRepository.listPlans(project.projectId(), project.tenantId())
                .stream()
                .filter(plan -> plan.planId().equals(project.activePlanId()))
                .filter(plan -> plan.status() == PlanStatus.LOCKED)
                .findFirst();
        return publishedPlan.map(plan -> toDisclosure(workOrderId, project, plan));
    }

    private OwnerRepairProjectDisclosure toDisclosure(
            Long workOrderId, RepairProject project, PlanVersion plan) {
        List<WorkPoint> workPoints = projectRepository.listWorkPoints(plan.planId(), project.tenantId());
        List<FundingSlice> fundingSlices = projectRepository.findDecisionScope(project.projectId(), project.tenantId())
                .map(scope -> projectRepository.listFundingSlices(scope.decisionScopeId(), project.tenantId()))
                .orElseGet(List::of);
        Map<Long, Attachment> attachmentsById = projectRepository
                .listAttachments(project.projectId(), project.tenantId())
                .stream()
                .collect(Collectors.toMap(Attachment::attachmentId, Function.identity()));
        List<OwnerRepairProjectDisclosure.PublishedAttachment> attachments = new ArrayList<>(projectRepository
                .listPlanAttachments(plan.planId(), project.tenantId())
                .stream()
                .sorted(Comparator.comparing(PlanAttachment::sortOrder))
                .map(reference -> toAttachment(reference, attachmentsById.get(reference.attachmentId())))
                .flatMap(Optional::stream)
                .toList());
        Selection selection = sourcingRepository.findCurrentSelection(
                project.projectId(), plan.planId(), project.tenantId()).orElse(null);
        Quote selectedQuote = selection == null ? null : sourcingRepository.findQuote(
                selection.quoteId(), project.projectId(), plan.planId(), project.tenantId()).orElse(null);
        if (selectedQuote != null && attachments.stream().noneMatch(
                attachment -> attachment.attachmentId().equals(selectedQuote.attachmentId()))) {
            Attachment quoteAttachment = attachmentsById.get(selectedQuote.attachmentId());
            if (quoteAttachment != null) {
                attachments.add(new OwnerRepairProjectDisclosure.PublishedAttachment(
                        quoteAttachment.attachmentId(), RepairProject.AttachmentPurpose.ORIGINAL_QUOTE,
                        quoteAttachment.originalFileName(), quoteAttachment.contentType(), quoteAttachment.fileSize()));
            }
        }
        return new OwnerRepairProjectDisclosure(
                workOrderId, project.projectId(), project.projectNo(), project.projectName(),
                project.workflowType(), project.scopeType(), project.buildingId(), project.unitName(),
                project.fundSource(), project.governancePath(), project.status(),
                new OwnerRepairProjectDisclosure.PublishedPlan(
                        plan.planId(), plan.versionNo(), narrativeImageService.resolveForPlan(
                                plan.planId(), project.tenantId(), plan.planDescription()),
                        plan.budgetTotal(), plan.supplierSelectionMethod(), plan.supplierSelectionReason(),
                        publishedSelection(selection, selectedQuote),
                        workPoints.stream().map(this::toWorkPoint).toList(),
                        fundingSlices.stream().map(this::toFundingSlice).toList(),
                        attachments, plan.lockedAt()));
    }

    private OwnerRepairProjectDisclosure.PublishedSupplierSelection publishedSelection(
            Selection selection, Quote quote) {
        if (selection == null || quote == null) {
            return null;
        }
        return new OwnerRepairProjectDisclosure.PublishedSupplierSelection(
                quote.quoteId(), quote.supplierDeptId(), quote.supplierName(),
                quote.amountExcludingTax(), quote.taxRate(), quote.taxAmount(), quote.quoteAmount(),
                quote.quoteSummary(), quote.attachmentId(), quote.constructionPeriodDays(), quote.warrantyDays(),
                selection.selectionMethod(), null, null,
                quote.quoteLines().stream().map(line -> publishedQuoteLine(line, quote.taxRate()))
                        .toList());
    }

    private OwnerRepairProjectDisclosure.PublishedQuoteLine publishedQuoteLine(
            QuoteLine line, BigDecimal quoteTaxRate) {
        BigDecimal taxMultiplier = BigDecimal.ONE.add(quoteTaxRate.movePointLeft(2));
        // 兼容已发布的小程序字段；税率真值仍只存于报价头，逐行含税金额仅为披露投影。
        BigDecimal taxIncludedUnitPrice = line.unitPriceExcludingTax()
                .multiply(taxMultiplier)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxIncludedAmount = line.amountExcludingTax()
                .multiply(taxMultiplier)
                .setScale(2, RoundingMode.HALF_UP);
        return new OwnerRepairProjectDisclosure.PublishedQuoteLine(
                line.workPointId(), line.workPointName(), line.lineNo(), line.itemName(),
                line.lineType(), line.workDescription(), line.specificationModel(), line.brand(),
                line.procurementMethod(), line.quantity(), line.unit(),
                line.unitPriceExcludingTax(), line.amountExcludingTax(),
                taxIncludedUnitPrice, quoteTaxRate, taxIncludedAmount, line.remark());
    }

    private OwnerRepairProjectDisclosure.PublishedWorkPoint toWorkPoint(WorkPoint workPoint) {
        return new OwnerRepairProjectDisclosure.PublishedWorkPoint(
                workPoint.workPointId(), workPoint.businessName(), workPoint.buildingId(), workPoint.unitName(),
                workPoint.locationType(), workPoint.referenceRoomId(), workPoint.commonAreaName(),
                workPoint.spaceName(), workPoint.orientation(), workPoint.component(), workPoint.specificPart(),
                workPoint.symptom(), workPoint.causeStatus(), workPoint.causeBasis(), workPoint.proposedMeasure(),
                workPoint.technicalRequirements(), workPoint.quantity(), workPoint.unit(),
                workPoint.preliminaryEstimatedAmount(), workPoint.estimateSource());
    }

    private OwnerRepairProjectDisclosure.PublishedFundingSlice toFundingSlice(FundingSlice fundingSlice) {
        return new OwnerRepairProjectDisclosure.PublishedFundingSlice(
                fundingSlice.sourceType(), fundingSlice.approvedAmount());
    }

    private Optional<OwnerRepairProjectDisclosure.PublishedAttachment> toAttachment(
            PlanAttachment reference, Attachment attachment) {
        if (attachment == null) {
            return Optional.empty();
        }
        return Optional.of(new OwnerRepairProjectDisclosure.PublishedAttachment(
                attachment.attachmentId(), reference.purpose(), attachment.originalFileName(),
                attachment.contentType(), attachment.fileSize()));
    }
}
