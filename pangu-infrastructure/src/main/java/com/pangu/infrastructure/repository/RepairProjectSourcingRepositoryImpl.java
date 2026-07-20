// 关联业务：实现维修工程项目方案级邀价、报价版本和中选供应商快照持久化。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.repair.RepairProjectSourcing.Invitation;
import com.pangu.domain.model.repair.RepairProjectSourcing.InvitationStatus;
import com.pangu.domain.model.repair.RepairProjectSourcing.InvitationType;
import com.pangu.domain.model.repair.RepairProjectSourcing.Quote;
import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLine;
import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLineType;
import com.pangu.domain.model.repair.RepairProjectSourcing.Selection;
import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairQuoteConfirmationStatus;
import com.pangu.domain.model.repair.RepairQuoteSubmissionSource;
import com.pangu.domain.model.repair.RepairSupplierQuoteStatus;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.repository.RepairProjectSourcingRepository;
import com.pangu.infrastructure.persistence.entity.RepairProjectSourcingRows.InvitationRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectSourcingRows.QuoteLineRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectSourcingRows.QuoteRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectSourcingRows.SelectionRow;
import com.pangu.infrastructure.persistence.mapper.RepairProjectSourcingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RepairProjectSourcingRepositoryImpl implements RepairProjectSourcingRepository {

    private final RepairProjectSourcingMapper mapper;

    @Override
    public Invitation insertInvitation(Invitation invitation) {
        InvitationRow row = toRow(invitation);
        mapper.insertInvitation(row);
        return findInvitation(
                row.getInvitationId(), invitation.projectId(), invitation.planId(),
                invitation.tenantId(), invitation.supplierDeptId()).orElseThrow();
    }

    @Override
    public List<Invitation> listInvitations(Long projectId, Long planId, Long tenantId) {
        return mapper.listInvitations(projectId, planId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Invitation> listSupplierInvitations(Long supplierDeptId) {
        return mapper.listSupplierInvitations(supplierDeptId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Invitation> findLatestSupplierProjectInvitation(Long projectId, Long supplierDeptId) {
        return Optional.ofNullable(mapper.findLatestSupplierProjectInvitation(projectId, supplierDeptId))
                .map(this::toDomain);
    }

    @Override
    public Optional<Invitation> findSupplierInvitation(
            Long invitationId, Long projectId, Long supplierDeptId) {
        return Optional.ofNullable(mapper.findSupplierInvitation(
                invitationId, projectId, supplierDeptId)).map(this::toDomain);
    }

    @Override
    public Optional<Invitation> findInvitation(
            Long invitationId, Long projectId, Long planId, Long tenantId, Long supplierDeptId) {
        return Optional.ofNullable(mapper.findInvitation(
                invitationId, projectId, planId, tenantId, supplierDeptId)).map(this::toDomain);
    }

    @Override
    public boolean supplierInvited(Long projectId, Long planId, Long tenantId, Long supplierDeptId) {
        return mapper.supplierInvited(projectId, planId, tenantId, supplierDeptId);
    }

    @Override
    public int nextInvitationRound(Long projectId, Long planId, Long tenantId, Long supplierDeptId) {
        return mapper.nextInvitationRound(projectId, planId, tenantId, supplierDeptId);
    }

    @Override
    public int markInvitationSubmitted(Long invitationId, Long tenantId) {
        return mapper.markInvitationSubmitted(invitationId, tenantId);
    }

    @Override
    public Quote insertQuote(Quote quote) {
        QuoteRow row = toRow(quote);
        mapper.insertQuote(row);
        for (QuoteLine quoteLine : quote.quoteLines()) {
            mapper.insertQuoteLine(toRow(row.getQuoteId(), quoteLine));
        }
        return findQuote(row.getQuoteId(), quote.projectId(), quote.planId(), quote.tenantId()).orElseThrow();
    }

    @Override
    public Optional<Quote> findQuote(Long quoteId, Long projectId, Long planId, Long tenantId) {
        return Optional.ofNullable(mapper.findQuote(quoteId, projectId, planId, tenantId))
                .map(row -> toDomain(row, listQuoteLines(row.getQuoteId())));
    }

    @Override
    public Optional<Quote> findLatestSupplierQuote(
            Long projectId, Long planId, Long tenantId, Long supplierDeptId) {
        return Optional.ofNullable(mapper.findLatestSupplierQuote(
                        projectId, planId, tenantId, supplierDeptId))
                .map(row -> toDomain(row, listQuoteLines(row.getQuoteId())));
    }

    @Override
    public List<Quote> listQuotes(Long projectId, Long planId, Long tenantId) {
        List<QuoteRow> rows = mapper.listQuotes(projectId, planId, tenantId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<QuoteLine>> linesByQuoteId = mapper.listQuoteLines(
                        rows.stream().map(QuoteRow::getQuoteId).toList())
                .stream()
                .map(this::toDomain)
                .collect(Collectors.groupingBy(QuoteLine::quoteId));
        return rows.stream()
                .map(row -> toDomain(row, linesByQuoteId.getOrDefault(row.getQuoteId(), List.of())))
                .toList();
    }

    @Override
    public int supersedeQuote(Long quoteId, Long supersededByQuoteId) {
        return mapper.supersedeQuote(quoteId, supersededByQuoteId);
    }

    @Override
    public int requestQuoteRevision(Long quoteId) {
        return mapper.requestQuoteRevision(quoteId);
    }

    @Override
    public int countInitialInvitedSuppliers(Long projectId, Long planId, Long tenantId) {
        return mapper.countInitialInvitedSuppliers(projectId, planId, tenantId);
    }

    @Override
    public int countActiveConfirmedQuotes(Long projectId, Long planId, Long tenantId) {
        return mapper.countActiveConfirmedQuotes(projectId, planId, tenantId);
    }

    @Override
    public Selection insertSelection(Selection selection) {
        SelectionRow row = toRow(selection);
        mapper.insertSelection(row);
        return findCurrentSelection(selection.projectId(), selection.planId(), selection.tenantId()).orElseThrow();
    }

    @Override
    public Optional<Selection> findCurrentSelection(Long projectId, Long planId, Long tenantId) {
        return Optional.ofNullable(mapper.findCurrentSelection(projectId, planId, tenantId)).map(this::toDomain);
    }

    private Invitation toDomain(InvitationRow row) {
        return new Invitation(
                row.getInvitationId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                row.getSupplierDeptId(), row.getSupplierName(), row.getInvitedByUserId(), row.getDeadline(),
                InvitationStatus.valueOf(row.getStatus()), row.getInvitationRound(),
                InvitationType.valueOf(row.getInvitationType()), row.getRevisionReason(),
                row.getSentAt(), row.getRespondedAt());
    }

    private InvitationRow toRow(Invitation invitation) {
        InvitationRow row = new InvitationRow();
        row.setInvitationId(invitation.invitationId());
        row.setProjectId(invitation.projectId());
        row.setPlanId(invitation.planId());
        row.setTenantId(invitation.tenantId());
        row.setSupplierDeptId(invitation.supplierDeptId());
        row.setInvitedByUserId(invitation.invitedByUserId());
        row.setDeadline(invitation.deadline());
        row.setStatus(invitation.status().name());
        row.setInvitationRound(invitation.invitationRound());
        row.setInvitationType(invitation.invitationType().name());
        row.setRevisionReason(invitation.revisionReason());
        return row;
    }

    private Quote toDomain(QuoteRow row, List<QuoteLine> quoteLines) {
        return new Quote(
                row.getQuoteId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                row.getSupplierDeptId(), row.getSupplierName(), row.getAmountExcludingTax(),
                row.getTaxRate(), row.getTaxAmount(), row.getQuoteAmount(), row.getQuoteSummary(),
                row.getAttachmentId(), row.getAttachmentHash(), row.getSubmittedByUserId(),
                row.getSubmittedByRoleKey(), RepairQuoteSubmissionSource.valueOf(row.getSubmissionSource()),
                RepairQuoteConfirmationStatus.valueOf(row.getConfirmationStatus()), row.getOriginalSource(),
                row.getConstructionPeriodDays(), row.getWarrantyDays(),
                Boolean.TRUE.equals(row.getOriginalAmountConfirmed()),
                RepairSupplierQuoteStatus.valueOf(row.getQuoteStatus()), row.getRevisionNo(),
                row.getSupersededByQuoteId(), row.getCreateTime(), quoteLines);
    }

    private QuoteRow toRow(Quote quote) {
        QuoteRow row = new QuoteRow();
        row.setQuoteId(quote.quoteId());
        row.setProjectId(quote.projectId());
        row.setPlanId(quote.planId());
        row.setTenantId(quote.tenantId());
        row.setSupplierDeptId(quote.supplierDeptId());
        row.setSupplierName(quote.supplierName());
        row.setAmountExcludingTax(quote.amountExcludingTax());
        row.setTaxRate(quote.taxRate());
        row.setTaxAmount(quote.taxAmount());
        row.setQuoteAmount(quote.quoteAmount());
        row.setQuoteSummary(quote.quoteSummary());
        row.setAttachmentId(quote.attachmentId());
        row.setAttachmentHash(quote.attachmentHash());
        row.setSubmittedByUserId(quote.submittedByUserId());
        row.setSubmittedByRoleKey(quote.submittedByRoleKey());
        row.setSubmissionSource(quote.submissionSource().name());
        row.setConfirmationStatus(quote.confirmationStatus().name());
        row.setOriginalSource(quote.originalSource());
        row.setConstructionPeriodDays(quote.constructionPeriodDays());
        row.setWarrantyDays(quote.warrantyDays());
        row.setOriginalAmountConfirmed(quote.originalAmountConfirmed());
        row.setQuoteStatus(quote.quoteStatus().name());
        row.setRevisionNo(quote.revisionNo());
        return row;
    }

    private List<QuoteLine> listQuoteLines(Long quoteId) {
        return mapper.listQuoteLines(List.of(quoteId)).stream().map(this::toDomain).toList();
    }

    private QuoteLine toDomain(QuoteLineRow row) {
        return new QuoteLine(
                row.getQuoteLineId(), row.getQuoteId(), row.getWorkPointId(), row.getWorkPointName(),
                row.getLineNo(), row.getItemName(), QuoteLineType.valueOf(row.getLineType()),
                row.getWorkDescription(), row.getSpecificationModel(), row.getBrand(),
                row.getProcurementMethod(), row.getQuantity(), row.getUnit(),
                row.getUnitPriceExcludingTax(), row.getAmountExcludingTax(), row.getRemark());
    }

    private QuoteLineRow toRow(Long quoteId, QuoteLine quoteLine) {
        QuoteLineRow row = new QuoteLineRow();
        row.setQuoteLineId(quoteLine.quoteLineId());
        row.setQuoteId(quoteId);
        row.setWorkPointId(quoteLine.workPointId());
        row.setLineNo(quoteLine.lineNo());
        row.setItemName(quoteLine.itemName());
        row.setLineType(quoteLine.lineType().name());
        row.setWorkDescription(quoteLine.workDescription());
        row.setSpecificationModel(quoteLine.specificationModel());
        row.setBrand(quoteLine.brand());
        row.setProcurementMethod(quoteLine.procurementMethod());
        row.setQuantity(quoteLine.quantity());
        row.setUnit(quoteLine.unit());
        row.setUnitPriceExcludingTax(quoteLine.unitPriceExcludingTax());
        row.setAmountExcludingTax(quoteLine.amountExcludingTax());
        row.setRemark(quoteLine.remark());
        return row;
    }

    private Selection toDomain(SelectionRow row) {
        return new Selection(
                row.getSelectionId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                row.getQuoteId(), row.getSupplierDeptId(), row.getSupplierName(), row.getQuoteAmount(),
                RepairSupplierSelectionMethod.valueOf(row.getSelectionMethod()),
                SupplierSelectionEvaluationRule.valueOf(row.getSelectionEvaluationRule()),
                row.getSelectionRationale(), row.getSelectionEvidenceAttachmentId(),
                row.getGovernanceBasisId(), row.getGovernanceBasisHash(), row.getFrameworkRelationId(),
                row.getConfirmedByUserId(), row.getCreateTime());
    }

    private SelectionRow toRow(Selection selection) {
        SelectionRow row = new SelectionRow();
        row.setSelectionId(selection.selectionId());
        row.setProjectId(selection.projectId());
        row.setPlanId(selection.planId());
        row.setTenantId(selection.tenantId());
        row.setQuoteId(selection.quoteId());
        row.setSupplierDeptId(selection.supplierDeptId());
        row.setSupplierName(selection.supplierName());
        row.setQuoteAmount(selection.quoteAmount());
        row.setSelectionMethod(selection.selectionMethod().name());
        row.setSelectionEvaluationRule(selection.selectionEvaluationRule().name());
        row.setSelectionRationale(selection.selectionRationale());
        row.setSelectionEvidenceAttachmentId(selection.selectionEvidenceAttachmentId());
        row.setGovernanceBasisId(selection.governanceBasisId());
        row.setGovernanceBasisHash(selection.governanceBasisHash());
        row.setFrameworkRelationId(selection.frameworkRelationId());
        row.setConfirmedByUserId(selection.confirmedByUserId());
        return row;
    }
}
