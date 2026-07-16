// 关联业务：校验业主对关联报修的可见权，并投影当前已锁定维修实施方案。
package com.pangu.application.repair;

import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.AllocationRoom;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.Item;
import com.pangu.domain.model.repair.RepairProject.PlanAttachment;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProjectSourcing.Quote;
import com.pangu.domain.model.repair.RepairProjectSourcing.Selection;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairProjectSourcingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final RepairNarrativeImageService narrativeImageService;

    @Transactional(readOnly = true)
    public Optional<OwnerRepairProjectDisclosure> findPublishedByWorkOrder(Long workOrderId) {
        // 复用报修本人可见规则，避免工程查询自行放宽楼栋或小区数据边界。
        var workOrder = workOrderService.findMine(workOrderId);
        return projectRepository.findProjectByActivePlanWorkOrder(workOrderId, workOrder.tenantId())
                .flatMap(project -> disclosure(workOrderId, project));
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
        List<Item> items = projectRepository.listItems(plan.planId(), project.tenantId());
        List<AllocationRoom> allocation = projectRepository.listAllocationRooms(plan.planId(), project.tenantId());
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
                        plan.budgetTotal(), plan.allocationRuleType(), plan.allocationRuleDescription(),
                        plan.supplierSelectionMethod(), plan.supplierSelectionReason(),
                        publishedSelection(selection, selectedQuote),
                        plan.constructionManagementRequirements(), plan.evidenceRequirements(),
                        plan.safetyRequirements(), plan.acceptanceMethod(),
                        plan.affectedOwnerScopeDescription(), plan.minimumAffectedOwnerAcceptors(),
                        plan.affectedOwnerPassRule(), plan.affectedOwnerApprovalRatio(),
                        plan.settlementMethod(), plan.plannedStartDate(), plan.plannedCompletionDate(),
                        plan.warrantyDays(), plan.priceReviewRequired(), plan.paymentMilestones(),
                        items.stream().map(this::toItem).toList(), allocationSummary(allocation),
                        attachments, plan.lockedAt()));
    }

    private OwnerRepairProjectDisclosure.PublishedSupplierSelection publishedSelection(
            Selection selection, Quote quote) {
        if (selection == null || quote == null) {
            return null;
        }
        return new OwnerRepairProjectDisclosure.PublishedSupplierSelection(
                quote.quoteId(), quote.supplierDeptId(), quote.supplierName(), quote.quoteAmount(),
                quote.quoteSummary(), quote.attachmentId(), selection.selectionMethod(),
                selection.recommendationReason(), selection.insufficientQuoteReason());
    }

    private OwnerRepairProjectDisclosure.PublishedItem toItem(Item item) {
        return new OwnerRepairProjectDisclosure.PublishedItem(
                item.itemId(), item.itemNo(), item.locationText(), item.workContent(),
                item.quantity(), item.unit(), item.estimatedUnitPrice(), item.estimatedAmount());
    }

    private OwnerRepairProjectDisclosure.AllocationSummary allocationSummary(List<AllocationRoom> allocation) {
        return new OwnerRepairProjectDisclosure.AllocationSummary(
                allocation.stream().map(AllocationRoom::roomId).distinct().count(),
                allocation.stream().map(AllocationRoom::ownerUid).distinct().count(),
                allocation.stream().map(AllocationRoom::buildArea)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
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
