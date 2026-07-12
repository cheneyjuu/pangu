package com.pangu.domain.repository;

import com.pangu.domain.model.asset.OwnerPropertyDetail;
import com.pangu.domain.model.repair.RepairBuildingDecisionSnapshot;
import com.pangu.domain.model.repair.RepairAcceptanceRecord;
import com.pangu.domain.model.repair.RepairAcceptanceSummary;
import com.pangu.domain.model.repair.RepairApprovalAttachment;
import com.pangu.domain.model.repair.RepairContractSignature;
import com.pangu.domain.model.repair.RepairDecisionRoom;
import com.pangu.domain.model.repair.RepairFrameworkRelation;
import com.pangu.domain.model.repair.RepairLocationOption;
import com.pangu.domain.model.repair.RepairLocalDecision;
import com.pangu.domain.model.repair.RepairOwnerLocalDecision;
import com.pangu.domain.model.repair.RepairQuoteInvitation;
import com.pangu.domain.model.repair.RepairSpaceScope;
import com.pangu.domain.model.repair.RepairSupplierQuote;
import com.pangu.domain.model.repair.RepairSupplierOrganization;
import com.pangu.domain.model.repair.RepairSupplierRecommendation;
import com.pangu.domain.model.repair.SupplierActivationInvitation;
import com.pangu.domain.model.repair.RepairSolitaireEntry;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderEvent;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RepairWorkOrderRepository {

    Optional<OwnerPropertyDetail> findOwnerProperty(Long uid, Long tenantId, Long opid);

    boolean buildingExists(Long tenantId, Long buildingId);

    boolean roomExists(Long tenantId, Long buildingId, Long roomId);

    List<RepairLocationOption> listLocationOptions(Long tenantId,
                                                   List<WorkIdentityBuildingScope> authorizedBuildingScopes,
                                                   boolean restrictToScopes);

    Optional<RepairWorkOrder> findDuplicate(Long tenantId,
                                            Long reporterAccountId,
                                            RepairSpaceScope spaceScope,
                                            Long roomId,
                                            Long buildingId,
                                            String title,
                                            LocalDateTime since);

    RepairWorkOrder insert(RepairWorkOrder workOrder);

    Optional<RepairWorkOrder> findById(Long workOrderId);

    Optional<RepairWorkOrder> findByIdForOwner(Long workOrderId, Long accountId, Long uid, Long tenantId);

    List<RepairWorkOrder> listForOwner(Long accountId, Long uid, Long tenantId);

    List<RepairWorkOrder> listForAdmin(Long tenantId,
                                       String roleKey,
                                       Long userId,
                                       List<WorkIdentityBuildingScope> authorizedBuildingScopes,
                                       RepairWorkOrderStatus status,
                                       RepairSpaceScope scope,
                                       String keyword,
                                       int page,
                                       int size);

    long countForAdmin(Long tenantId,
                       String roleKey,
                       Long userId,
                       List<WorkIdentityBuildingScope> authorizedBuildingScopes,
                       RepairWorkOrderStatus status,
                       RepairSpaceScope scope,
                       String keyword);

    List<RepairWorkOrder> listForSupplier(Long supplierDeptId);

    int update(RepairWorkOrder workOrder);

    void lockQuoteSubmission(Long workOrderId);

    RepairSupplierQuote insertQuote(RepairSupplierQuote quote);

    Optional<RepairSupplierQuote> findActiveQuote(Long workOrderId, Long tenantId, Long supplierDeptId);

    void supersedeQuote(Long quoteId);

    void linkSupersededQuote(Long quoteId, Long supersededByQuoteId);

    Optional<RepairSupplierQuote> findQuote(Long quoteId, Long workOrderId, Long tenantId);

    int countQuotes(Long workOrderId, Long tenantId);

    int countQuoteInvitations(Long workOrderId, Long tenantId);

    List<RepairQuoteInvitation> listQuoteInvitations(Long workOrderId, Long tenantId);

    Optional<String> findSupplierLegalName(Long supplierDeptId);

    List<RepairSupplierOrganization> listSupplierOrganizations(Long tenantId);

    List<RepairSupplierQuote> listSupplierQuotes(Long workOrderId, Long tenantId);

    List<RepairSupplierQuote> listSupplierQuoteHistory(Long workOrderId, Long tenantId, Long supplierDeptId);

    Optional<RepairSupplierQuote> findLatestSupplierQuote(Long workOrderId, Long tenantId, Long supplierDeptId);

    List<RepairFrameworkRelation> listActiveFrameworkRelations(Long tenantId, String serviceCategory);

    Long registerSupplierOrganization(Long tenantId,
                                      String legalName,
                                      String unifiedSocialCreditCode,
                                      String contactName,
                                      String contactPhone,
                                      Long requestedByUserId);

    Optional<RepairSupplierOrganization> findSupplierOrganization(Long tenantId, Long supplierDeptId);

    Optional<SupplierActivationInvitation> findReusableSupplierActivationInvitation(
            Long supplierDeptId, String contactPhone, LocalDateTime now);

    Optional<SupplierActivationInvitation> findSupplierActivationInvitationForUpdate(Long invitationId);

    boolean supplierHasActiveIdentity(Long supplierDeptId, String contactPhone);

    void cancelPendingSupplierActivationInvitations(Long supplierDeptId, String contactPhone);

    SupplierActivationInvitation insertSupplierActivationInvitation(
            Long tenantId,
            Long supplierDeptId,
            Long workOrderId,
            String contactName,
            String contactPhone,
            String invitationTokenHash,
            LocalDateTime expiresAt,
            Long invitedByUserId);

    void markSupplierActivationInvitationExpired(Long invitationId);

    void markSupplierActivationInvitationActivated(
            Long invitationId, Long accountId, Long userId, LocalDateTime activatedAt);

    boolean supplierVerified(Long supplierDeptId);

    void inviteSuppliers(Long workOrderId,
                         Long tenantId,
                         Long invitedByUserId,
                         List<Long> supplierDeptIds,
                         LocalDateTime deadline);

    void inviteSupplierRevisions(Long workOrderId,
                                 Long tenantId,
                                 Long invitedByUserId,
                                 List<Long> supplierDeptIds,
                                 LocalDateTime deadline,
                                 String revisionReason);

    void markActiveQuoteRevisionRequested(Long workOrderId, Long tenantId, Long supplierDeptId);

    boolean supplierCanAccess(Long workOrderId, Long tenantId, Long supplierDeptId);

    RepairSupplierRecommendation insertRecommendation(RepairSupplierRecommendation recommendation);

    Optional<RepairSupplierRecommendation> findLatestRecommendation(Long workOrderId, Long tenantId);

    boolean frameworkRelationActive(Long relationId,
                                    Long tenantId,
                                    Long supplierDeptId,
                                    String serviceCategory);

    Optional<RepairBuildingDecisionSnapshot> loadBuildingDecisionSnapshot(Long tenantId,
                                                                          Long buildingId,
                                                                          String unitName);

    List<RepairDecisionRoom> listDecisionRooms(Long tenantId, Long buildingId, String unitName);

    RepairLocalDecision insertLocalDecision(RepairLocalDecision decision);

    Optional<RepairLocalDecision> findLocalDecision(Long workOrderId, Long tenantId);

    List<RepairOwnerLocalDecision> listOwnerLocalDecisions(Long uid, Long tenantId);

    Optional<RepairOwnerLocalDecision> findOwnerLocalDecision(Long decisionId, Long opid, Long uid, Long tenantId);

    void submitOnlineDecisionVote(Long decisionId, Long tenantId, Long roomId, Long ownerUid,
                                  Long accountId, String choice, BigDecimal buildArea);

    List<RepairSolitaireEntry> listSolitaireEntries(Long decisionId, Long tenantId);

    int updateLocalDecisionResult(RepairLocalDecision decision);

    void replaceSolitaireEntries(Long decisionId,
                                 Long tenantId,
                                 Long verifiedByUserId,
                                 List<RepairSolitaireEntry> entries);

    void insertSolitaireEvidence(Long decisionId,
                                 Long tenantId,
                                 String attachmentHash,
                                 Long uploadedByAccountId,
                                 Long uploadedByUserId);

    void insertAssemblyDecision(Long workOrderId, Long tenantId, Long packageId);

    Optional<Long> findAssemblyDecisionPackageId(Long workOrderId, Long tenantId);

    int updateAssemblyDecisionResult(Long workOrderId, Long tenantId, String result);

    Long insertApprovalPackage(Long workOrderId,
                               Long tenantId,
                               String officialDocumentHash,
                               String mergedPackageHash,
                               boolean printedAndAttached,
                               Long submittedByUserId,
                               List<RepairApprovalAttachment> attachments);

    Optional<Long> findActiveApprovalPackageId(Long workOrderId, Long tenantId);

    void updateApprovalPackageStatus(Long packageId, Long tenantId, String status);

    void insertPriceReview(Long workOrderId,
                           Long packageId,
                           Long tenantId,
                           String reviewMode,
                           BigDecimal reviewedAmount,
                           String reviewReportHash,
                           String conclusion,
                           String opinion,
                           Long reviewedByUserId);

    Optional<BigDecimal> findLatestApprovedPrice(Long workOrderId, Long tenantId);

    void insertGovernanceApproval(Long workOrderId,
                                  Long tenantId,
                                  Long approverUserId,
                                  String approverPosition,
                                  String opinion);

    void insertGovernanceSeal(Long workOrderId,
                              Long tenantId,
                              Long sealedByUserId,
                              String sealType,
                              String sealedFileHash,
                              String remark);

    Optional<String> findActiveCommitteePosition(Long tenantId, Long userId);

    Long insertContract(Long workOrderId,
                        Long tenantId,
                        Long supplierDeptId,
                        String supplierName,
                        BigDecimal contractAmount,
                        String repairScopeHash,
                        String fundSource,
                        String signingMethod,
                        String contractFileHash,
                        Long createdByUserId);

    Optional<Long> findActiveContractId(Long workOrderId, Long tenantId);

    boolean recommendedQuoteMatches(Long workOrderId,
                                    Long tenantId,
                                    Long supplierDeptId,
                                    String supplierName);

    void markRecommendedQuoteContractConfirmed(Long workOrderId, Long tenantId);

    void insertContractSignatures(Long contractId, List<RepairContractSignature> signatures);

    int markContractEffective(Long contractId, String finalContractFileHash);

    Optional<Long> findEffectiveContractId(Long workOrderId, Long tenantId);

    BigDecimal findContractPaymentRemaining(Long contractId);

    Long insertPaymentRequest(Long workOrderId,
                              Long contractId,
                              Long tenantId,
                              String milestoneType,
                              BigDecimal requestedAmount,
                              String conditionEvidenceHash,
                              Long requestedByUserId);

    void replaceAcceptanceScope(Long workOrderId,
                                Long tenantId,
                                Long createdByUserId,
                                List<Long> roomIds,
                                List<String> affectedReasons);

    boolean roomInAcceptanceScope(Long workOrderId, Long tenantId, Long roomId);

    boolean ownerOwnsAcceptanceRoom(Long workOrderId, Long tenantId, Long roomId, Long uid);

    List<Long> listOwnerAcceptanceRooms(Long workOrderId, Long tenantId, Long uid);

    void insertAcceptanceRecord(Long workOrderId, Long tenantId, RepairAcceptanceRecord record);

    RepairAcceptanceSummary summarizeAcceptance(Long workOrderId, Long tenantId);

    void insertEvent(RepairWorkOrderEvent event);

    List<RepairWorkOrderEvent> listEvents(Long workOrderId, Long tenantId);
}
