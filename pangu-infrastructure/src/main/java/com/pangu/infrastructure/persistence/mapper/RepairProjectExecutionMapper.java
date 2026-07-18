// 关联业务：映射维修工程合同、施工、材料、结算、项目验收、付款和完工披露 SQL。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.AcceptancePartyRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.AcceptancePolicyRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.AcceptanceRoundRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.AcceptanceSummaryRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.CompletionDisclosureRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.ContractRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.ContractSignatureRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.CostReviewRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.ExecutionRecordRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.MaterialInspectionRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.PaymentEvidenceRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.PaymentRequestRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.SettlementItemRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.SettlementRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface RepairProjectExecutionMapper {

    int insertCostReview(CostReviewRow row);

    CostReviewRow findCostReview(
            @Param("projectId") Long projectId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId);

    int insertContract(ContractRow row);

    int insertContractSignature(ContractSignatureRow row);

    ContractRow findContract(@Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    List<Long> listSupplierProjectIds(
            @Param("tenantId") Long tenantId,
            @Param("supplierDeptId") Long supplierDeptId);

    List<ContractSignatureRow> listContractSignatures(@Param("contractId") Long contractId);

    int insertExecutionRecord(ExecutionRecordRow row);

    int insertExecutionAttachment(
            @Param("recordId") Long recordId,
            @Param("tenantId") Long tenantId,
            @Param("attachmentId") Long attachmentId,
            @Param("sortOrder") int sortOrder);

    int verifyExecutionRecord(
            @Param("recordId") Long recordId,
            @Param("projectId") Long projectId,
            @Param("tenantId") Long tenantId,
            @Param("status") String status,
            @Param("verifiedByUserId") Long verifiedByUserId,
            @Param("opinion") String opinion);

    ExecutionRecordRow findExecutionRecord(
            @Param("recordId") Long recordId,
            @Param("projectId") Long projectId,
            @Param("tenantId") Long tenantId);

    List<ExecutionRecordRow> listExecutionRecords(
            @Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    List<Long> listExecutionAttachmentIds(
            @Param("recordId") Long recordId, @Param("tenantId") Long tenantId);

    int insertMaterialInspection(MaterialInspectionRow row);

    int insertMaterialPhoto(
            @Param("inspectionId") Long inspectionId,
            @Param("tenantId") Long tenantId,
            @Param("attachmentId") Long attachmentId,
            @Param("sortOrder") int sortOrder);

    int verifyMaterialInspection(
            @Param("inspectionId") Long inspectionId,
            @Param("projectId") Long projectId,
            @Param("tenantId") Long tenantId,
            @Param("status") String status,
            @Param("verifiedByUserId") Long verifiedByUserId,
            @Param("opinion") String opinion);

    MaterialInspectionRow findMaterialInspection(
            @Param("inspectionId") Long inspectionId,
            @Param("projectId") Long projectId,
            @Param("tenantId") Long tenantId);

    List<MaterialInspectionRow> listMaterialInspections(
            @Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    List<Long> listMaterialPhotoIds(
            @Param("inspectionId") Long inspectionId, @Param("tenantId") Long tenantId);

    int insertSettlement(SettlementRow row);

    int nextSettlementVersion(@Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    int insertSettlementItem(SettlementItemRow row);

    SettlementRow findActiveSettlement(
            @Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    List<SettlementItemRow> listSettlementItems(@Param("settlementId") Long settlementId);

    int verifySettlement(
            @Param("settlementId") Long settlementId,
            @Param("projectId") Long projectId,
            @Param("tenantId") Long tenantId,
            @Param("status") String status,
            @Param("verifiedByUserId") Long verifiedByUserId,
            @Param("opinion") String opinion);

    int invalidateVerifiedSettlement(
            @Param("settlementId") Long settlementId,
            @Param("projectId") Long projectId,
            @Param("tenantId") Long tenantId,
            @Param("opinion") String opinion);

    int insertAcceptancePolicy(@Param("row") AcceptancePolicyRow row,
                               @Param("minimumApprovals") int minimumApprovals,
                               @Param("lockedByUserId") Long lockedByUserId);

    int snapshotAcceptanceAffectedOwners(
            @Param("policyId") Long policyId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId);

    int insertAcceptanceRound(@Param("row") AcceptanceRoundRow row,
                              @Param("submittedByUserId") Long submittedByUserId);

    AcceptancePolicyRow findAcceptancePolicy(
            @Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    AcceptanceRoundRow findCollectingAcceptance(
            @Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    AcceptanceRoundRow findLatestAcceptance(
            @Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    int affectedOwnerIncluded(
            @Param("policyId") Long policyId,
            @Param("tenantId") Long tenantId,
            @Param("roomId") Long roomId,
            @Param("ownerUid") Long ownerUid);

    List<Long> listAffectedOwnerRoomIds(
            @Param("policyId") Long policyId,
            @Param("tenantId") Long tenantId,
            @Param("ownerUid") Long ownerUid);

    List<Long> listOpenAcceptanceProjectIds(
            @Param("tenantId") Long tenantId,
            @Param("ownerUid") Long ownerUid);

    int insertAcceptanceParty(@Param("row") AcceptancePartyRow row, @Param("tenantId") Long tenantId);

    List<AcceptancePartyRow> listAcceptanceParties(
            @Param("acceptanceId") Long acceptanceId, @Param("tenantId") Long tenantId);

    AcceptanceSummaryRow summarizeAcceptance(
            @Param("acceptanceId") Long acceptanceId, @Param("tenantId") Long tenantId);

    int completeAcceptance(
            @Param("acceptanceId") Long acceptanceId,
            @Param("tenantId") Long tenantId,
            @Param("status") String status,
            @Param("resultAttachmentId") Long resultAttachmentId,
            @Param("completedByUserId") Long completedByUserId,
            @Param("remark") String remark);

    int supersedeAcceptancePolicy(
            @Param("policyId") Long policyId,
            @Param("projectId") Long projectId,
            @Param("tenantId") Long tenantId);

    BigDecimal sumActivePaymentRequests(
            @Param("projectId") Long projectId,
            @Param("contractId") Long contractId,
            @Param("tenantId") Long tenantId);

    int insertPaymentRequest(@Param("row") PaymentRequestRow row,
                             @Param("eligibilityResultJson") String eligibilityResultJson);

    int insertPaymentEvidence(
            @Param("paymentRequestId") Long paymentRequestId,
            @Param("tenantId") Long tenantId,
            @Param("evidenceCode") String evidenceCode,
            @Param("attachmentId") Long attachmentId);

    List<PaymentRequestRow> listPaymentRequests(
            @Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    List<PaymentEvidenceRow> listPaymentEvidence(@Param("paymentRequestId") Long paymentRequestId);

    int insertCompletionDisclosure(CompletionDisclosureRow row);

    int insertCompletionDisclosurePhoto(
            @Param("disclosureId") Long disclosureId,
            @Param("tenantId") Long tenantId,
            @Param("attachmentId") Long attachmentId,
            @Param("sortOrder") int sortOrder);

    CompletionDisclosureRow findCompletionDisclosure(
            @Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    List<Long> listCompletionDisclosurePhotoIds(
            @Param("disclosureId") Long disclosureId, @Param("tenantId") Long tenantId);
}
