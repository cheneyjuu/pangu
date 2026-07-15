// 关联业务：定义维修工程合同、施工、材料、结算、项目验收、付款和完工披露的持久化端口。
package com.pangu.domain.repository;

import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceParty;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptancePolicy;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRound;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceSummary;
import com.pangu.domain.model.repair.RepairProjectExecution.CompletionDisclosure;
import com.pangu.domain.model.repair.RepairProjectExecution.Contract;
import com.pangu.domain.model.repair.RepairProjectExecution.ContractSignature;
import com.pangu.domain.model.repair.RepairProjectExecution.CostReview;
import com.pangu.domain.model.repair.RepairProjectExecution.ExecutionRecord;
import com.pangu.domain.model.repair.RepairProjectExecution.MaterialInspection;
import com.pangu.domain.model.repair.RepairProjectExecution.PaymentEvidence;
import com.pangu.domain.model.repair.RepairProjectExecution.PaymentRequest;
import com.pangu.domain.model.repair.RepairProjectExecution.Settlement;
import com.pangu.domain.model.repair.RepairProjectExecution.SettlementItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RepairProjectExecutionRepository {

    CostReview insertCostReview(CostReview review);

    Optional<CostReview> findCostReview(Long projectId, Long planId, Long tenantId);

    Contract insertContract(Contract contract);

    void insertContractSignatures(Long tenantId, List<ContractSignature> signatures);

    Optional<Contract> findContract(Long projectId, Long tenantId);

    List<ContractSignature> listContractSignatures(Long contractId);

    ExecutionRecord insertExecutionRecord(ExecutionRecord record);

    void insertExecutionAttachments(Long recordId, Long tenantId, List<Long> attachmentIds);

    int verifyExecutionRecord(
            Long recordId, Long projectId, Long tenantId, String status,
            Long verifiedByUserId, String opinion);

    Optional<ExecutionRecord> findExecutionRecord(Long recordId, Long projectId, Long tenantId);

    List<ExecutionRecord> listExecutionRecords(Long projectId, Long tenantId);

    MaterialInspection insertMaterialInspection(MaterialInspection inspection);

    void insertMaterialPhotos(Long inspectionId, Long tenantId, List<Long> attachmentIds);

    int verifyMaterialInspection(
            Long inspectionId, Long projectId, Long tenantId, String status,
            Long verifiedByUserId, String opinion);

    Optional<MaterialInspection> findMaterialInspection(Long inspectionId, Long projectId, Long tenantId);

    List<MaterialInspection> listMaterialInspections(Long projectId, Long tenantId);

    Settlement insertSettlement(Settlement settlement);

    int nextSettlementVersion(Long projectId, Long tenantId);

    void insertSettlementItems(Long settlementId, List<SettlementItem> items);

    Optional<Settlement> findActiveSettlement(Long projectId, Long tenantId);

    int verifySettlement(
            Long settlementId, Long projectId, Long tenantId,
            String status, Long verifiedByUserId, String opinion);

    int invalidateVerifiedSettlement(Long settlementId, Long projectId, Long tenantId, String opinion);

    AcceptancePolicy insertAcceptancePolicy(
            AcceptancePolicy policy, int minimumApprovals, Long lockedByUserId);

    void snapshotAcceptanceAffectedOwners(Long policyId, Long planId, Long tenantId, String reason);

    AcceptanceRound startAcceptance(
            Long projectId, Long tenantId, Long policyId, Long settlementId, Long submittedByUserId);

    Optional<AcceptancePolicy> findAcceptancePolicy(Long projectId, Long tenantId);

    Optional<AcceptanceRound> findCollectingAcceptance(Long projectId, Long tenantId);

    Optional<AcceptanceRound> findLatestAcceptance(Long projectId, Long tenantId);

    boolean affectedOwnerIncluded(Long policyId, Long tenantId, Long roomId, Long ownerUid);

    List<Long> listAffectedOwnerRoomIds(Long policyId, Long tenantId, Long ownerUid);

    List<Long> listOpenAcceptanceProjectIds(Long tenantId, Long ownerUid);

    void insertAcceptanceParty(Long tenantId, AcceptanceParty party);

    List<AcceptanceParty> listAcceptanceParties(Long acceptanceId, Long tenantId);

    AcceptanceSummary summarizeAcceptance(Long acceptanceId, Long tenantId);

    int completeAcceptance(
            Long acceptanceId, Long tenantId, String status, Long resultAttachmentId,
            Long completedByUserId, String remark);

    int supersedeAcceptancePolicy(Long policyId, Long projectId, Long tenantId);

    BigDecimal sumActivePaymentRequests(Long projectId, Long contractId, Long tenantId);

    PaymentRequest insertPaymentRequest(PaymentRequest request, String eligibilityResultJson);

    void insertPaymentEvidence(Long paymentRequestId, Long tenantId, List<PaymentEvidence> evidence);

    List<PaymentRequest> listPaymentRequests(Long projectId, Long tenantId);

    CompletionDisclosure insertCompletionDisclosure(CompletionDisclosure disclosure);

    void insertCompletionDisclosurePhotos(Long disclosureId, Long tenantId, List<Long> attachmentIds);

    Optional<CompletionDisclosure> findCompletionDisclosure(Long projectId, Long tenantId);
}
