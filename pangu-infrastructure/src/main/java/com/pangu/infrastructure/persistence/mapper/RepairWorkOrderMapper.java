package com.pangu.infrastructure.persistence.mapper;

import com.pangu.domain.model.repair.RepairAcceptanceRecord;
import com.pangu.domain.model.repair.RepairContractSignature;
import com.pangu.domain.model.repair.RepairSolitaireEntry;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.infrastructure.persistence.entity.OwnerPropertyDetailRow;
import com.pangu.infrastructure.persistence.entity.RepairBuildingDecisionSnapshotRow;
import com.pangu.infrastructure.persistence.entity.RepairDecisionRoomRow;
import com.pangu.infrastructure.persistence.entity.RepairFrameworkRelationRow;
import com.pangu.infrastructure.persistence.entity.RepairAcceptanceSummaryRow;
import com.pangu.infrastructure.persistence.entity.RepairLocationOptionRow;
import com.pangu.infrastructure.persistence.entity.RepairQuoteInvitationRow;
import com.pangu.infrastructure.persistence.entity.RepairLocalDecisionRow;
import com.pangu.infrastructure.persistence.entity.RepairSupplierQuoteRow;
import com.pangu.infrastructure.persistence.entity.RepairSupplierOrganizationRow;
import com.pangu.infrastructure.persistence.entity.RepairSupplierRecommendationRow;
import com.pangu.infrastructure.persistence.entity.RepairWorkOrderEventRow;
import com.pangu.infrastructure.persistence.entity.RepairWorkOrderRow;
import com.pangu.infrastructure.persistence.entity.SupplierActivationInvitationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RepairWorkOrderMapper {

    OwnerPropertyDetailRow selectOwnerProperty(@Param("uid") Long uid,
                                               @Param("tenantId") Long tenantId,
                                               @Param("opid") Long opid);

    boolean existsBuilding(@Param("tenantId") Long tenantId,
                           @Param("buildingId") Long buildingId);

    boolean existsRoom(@Param("tenantId") Long tenantId,
                       @Param("buildingId") Long buildingId,
                       @Param("roomId") Long roomId);

    List<RepairLocationOptionRow> selectLocationOptions(
            @Param("tenantId") Long tenantId,
            @Param("buildingScopes") List<WorkIdentityBuildingScope> buildingScopes,
            @Param("restrictToScopes") boolean restrictToScopes);

    RepairWorkOrderRow findDuplicate(@Param("tenantId") Long tenantId,
                                     @Param("reporterAccountId") Long reporterAccountId,
                                     @Param("spaceScope") String spaceScope,
                                     @Param("roomId") Long roomId,
                                     @Param("buildingId") Long buildingId,
                                     @Param("title") String title,
                                     @Param("since") LocalDateTime since);

    int insert(RepairWorkOrderRow row);

    RepairWorkOrderRow findById(@Param("workOrderId") Long workOrderId);

    RepairWorkOrderRow findByIdForOwner(@Param("workOrderId") Long workOrderId,
                                        @Param("accountId") Long accountId,
                                        @Param("uid") Long uid,
                                        @Param("tenantId") Long tenantId);

    List<RepairWorkOrderRow> listForOwner(@Param("accountId") Long accountId,
                                          @Param("uid") Long uid,
                                          @Param("tenantId") Long tenantId);

    List<RepairWorkOrderRow> listForAdmin(@Param("tenantId") Long tenantId,
                                          @Param("roleKey") String roleKey,
                                          @Param("userId") Long userId,
                                          @Param("buildingScopes") List<WorkIdentityBuildingScope> buildingScopes,
                                          @Param("status") String status,
                                          @Param("scope") String scope,
                                          @Param("keyword") String keyword,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    long countForAdmin(@Param("tenantId") Long tenantId,
                       @Param("roleKey") String roleKey,
                       @Param("userId") Long userId,
                       @Param("buildingScopes") List<WorkIdentityBuildingScope> buildingScopes,
                       @Param("status") String status,
                       @Param("scope") String scope,
                       @Param("keyword") String keyword);

    List<RepairWorkOrderRow> listForSupplier(@Param("supplierDeptId") Long supplierDeptId);

    int update(RepairWorkOrderRow row);

    int insertQuote(RepairSupplierQuoteRow row);

    RepairSupplierQuoteRow findQuote(@Param("quoteId") Long quoteId,
                                     @Param("workOrderId") Long workOrderId,
                                     @Param("tenantId") Long tenantId);

    int countQuotes(@Param("workOrderId") Long workOrderId,
                    @Param("tenantId") Long tenantId);

    int countQuoteInvitations(@Param("workOrderId") Long workOrderId,
                              @Param("tenantId") Long tenantId);

    List<RepairQuoteInvitationRow> listQuoteInvitations(@Param("workOrderId") Long workOrderId,
                                                        @Param("tenantId") Long tenantId);

    String findSupplierLegalName(@Param("supplierDeptId") Long supplierDeptId);

    List<RepairSupplierOrganizationRow> listSupplierOrganizations(@Param("tenantId") Long tenantId);

    List<RepairSupplierQuoteRow> listSupplierQuotes(@Param("workOrderId") Long workOrderId,
                                                    @Param("tenantId") Long tenantId);

    List<RepairFrameworkRelationRow> listActiveFrameworkRelations(@Param("tenantId") Long tenantId,
                                                                  @Param("serviceCategory") String serviceCategory);

    Long findSupplierDeptIdByUscc(@Param("unifiedSocialCreditCode") String unifiedSocialCreditCode);

    Long findProvisionalSupplierDeptId(@Param("tenantId") Long tenantId,
                                       @Param("legalName") String legalName);

    Long nextDeptId();

    int insertSupplierDept(@Param("deptId") Long deptId,
                           @Param("legalName") String legalName);

    int insertSupplierProfile(@Param("supplierDeptId") Long supplierDeptId,
                              @Param("unifiedSocialCreditCode") String unifiedSocialCreditCode,
                              @Param("legalName") String legalName,
                              @Param("contactName") String contactName,
                              @Param("contactPhone") String contactPhone);

    int completeSupplierProfile(@Param("supplierDeptId") Long supplierDeptId,
                                @Param("unifiedSocialCreditCode") String unifiedSocialCreditCode,
                                @Param("contactName") String contactName,
                                @Param("contactPhone") String contactPhone);

    int insertSupplierTenantRelation(@Param("tenantId") Long tenantId,
                                     @Param("supplierDeptId") Long supplierDeptId,
                                     @Param("requestedByUserId") Long requestedByUserId);

    RepairSupplierOrganizationRow findSupplierOrganization(@Param("tenantId") Long tenantId,
                                                           @Param("supplierDeptId") Long supplierDeptId);

    SupplierActivationInvitationRow findReusableSupplierActivationInvitation(
            @Param("supplierDeptId") Long supplierDeptId,
            @Param("contactPhone") String contactPhone,
            @Param("now") LocalDateTime now);

    SupplierActivationInvitationRow findSupplierActivationInvitationForUpdate(
            @Param("invitationId") Long invitationId);

    boolean supplierHasActiveIdentity(@Param("supplierDeptId") Long supplierDeptId,
                                      @Param("contactPhone") String contactPhone);

    int cancelPendingSupplierActivationInvitations(@Param("supplierDeptId") Long supplierDeptId,
                                                   @Param("contactPhone") String contactPhone);

    int insertSupplierActivationInvitation(SupplierActivationInvitationRow row);

    int markSupplierActivationInvitationExpired(@Param("invitationId") Long invitationId);

    int markSupplierActivationInvitationActivated(@Param("invitationId") Long invitationId,
                                                  @Param("accountId") Long accountId,
                                                  @Param("userId") Long userId,
                                                  @Param("activatedAt") LocalDateTime activatedAt);

    boolean supplierVerified(@Param("supplierDeptId") Long supplierDeptId);

    int insertQuoteInvitation(@Param("workOrderId") Long workOrderId,
                              @Param("tenantId") Long tenantId,
                              @Param("supplierDeptId") Long supplierDeptId,
                              @Param("invitedByUserId") Long invitedByUserId,
                              @Param("deadline") LocalDateTime deadline);

    boolean supplierCanAccess(@Param("workOrderId") Long workOrderId,
                              @Param("tenantId") Long tenantId,
                              @Param("supplierDeptId") Long supplierDeptId);

    int insertRecommendation(RepairSupplierRecommendationRow row);

    boolean frameworkRelationActive(@Param("relationId") Long relationId,
                                    @Param("tenantId") Long tenantId,
                                    @Param("supplierDeptId") Long supplierDeptId,
                                    @Param("serviceCategory") String serviceCategory);

    RepairBuildingDecisionSnapshotRow loadBuildingDecisionSnapshot(@Param("tenantId") Long tenantId,
                                                                   @Param("buildingId") Long buildingId,
                                                                   @Param("unitName") String unitName);

    List<RepairDecisionRoomRow> listDecisionRooms(@Param("tenantId") Long tenantId,
                                                  @Param("buildingId") Long buildingId,
                                                  @Param("unitName") String unitName);

    int insertLocalDecision(RepairLocalDecisionRow row);

    RepairLocalDecisionRow findLocalDecision(@Param("workOrderId") Long workOrderId,
                                             @Param("tenantId") Long tenantId);

    int updateLocalDecisionResult(RepairLocalDecisionRow row);

    int deleteSolitaireEntries(@Param("decisionId") Long decisionId,
                               @Param("tenantId") Long tenantId);

    int insertSolitaireEntry(@Param("decisionId") Long decisionId,
                             @Param("tenantId") Long tenantId,
                             @Param("verifiedByUserId") Long verifiedByUserId,
                             @Param("entry") RepairSolitaireEntry entry);

    int insertSolitaireEvidence(@Param("decisionId") Long decisionId,
                                @Param("tenantId") Long tenantId,
                                @Param("attachmentHash") String attachmentHash,
                                @Param("uploadedByAccountId") Long uploadedByAccountId,
                                @Param("uploadedByUserId") Long uploadedByUserId);

    int insertAssemblyDecision(@Param("workOrderId") Long workOrderId,
                               @Param("tenantId") Long tenantId,
                               @Param("packageId") Long packageId);

    Long findAssemblyDecisionPackageId(@Param("workOrderId") Long workOrderId,
                                       @Param("tenantId") Long tenantId);

    int updateAssemblyDecisionResult(@Param("workOrderId") Long workOrderId,
                                     @Param("tenantId") Long tenantId,
                                     @Param("result") String result);

    int nextApprovalPackageVersion(@Param("workOrderId") Long workOrderId);

    int insertApprovalPackage(@Param("workOrderId") Long workOrderId,
                              @Param("tenantId") Long tenantId,
                              @Param("version") int version,
                              @Param("officialDocumentHash") String officialDocumentHash,
                              @Param("mergedPackageHash") String mergedPackageHash,
                              @Param("printedAndAttached") int printedAndAttached,
                              @Param("submittedByUserId") Long submittedByUserId);

    Long findInsertedApprovalPackageId(@Param("workOrderId") Long workOrderId,
                                       @Param("version") int version);

    int insertApprovalAttachment(@Param("packageId") Long packageId,
                                 @Param("attachmentType") String attachmentType,
                                 @Param("attachmentHash") String attachmentHash,
                                 @Param("originalFileName") String originalFileName,
                                 @Param("sortOrder") int sortOrder,
                                 @Param("uploadedByUserId") Long uploadedByUserId);

    Long findActiveApprovalPackageId(@Param("workOrderId") Long workOrderId,
                                     @Param("tenantId") Long tenantId);

    int updateApprovalPackageStatus(@Param("packageId") Long packageId,
                                    @Param("tenantId") Long tenantId,
                                    @Param("status") String status);

    int insertPriceReview(@Param("workOrderId") Long workOrderId,
                          @Param("packageId") Long packageId,
                          @Param("tenantId") Long tenantId,
                          @Param("reviewMode") String reviewMode,
                          @Param("reviewedAmount") BigDecimal reviewedAmount,
                          @Param("reviewReportHash") String reviewReportHash,
                          @Param("conclusion") String conclusion,
                          @Param("opinion") String opinion,
                          @Param("reviewedByUserId") Long reviewedByUserId);

    BigDecimal findLatestApprovedPrice(@Param("workOrderId") Long workOrderId,
                                       @Param("tenantId") Long tenantId);

    int insertGovernanceApproval(@Param("workOrderId") Long workOrderId,
                                 @Param("tenantId") Long tenantId,
                                 @Param("approverUserId") Long approverUserId,
                                 @Param("approverPosition") String approverPosition,
                                 @Param("opinion") String opinion);

    int insertGovernanceSeal(@Param("workOrderId") Long workOrderId,
                             @Param("tenantId") Long tenantId,
                             @Param("sealedByUserId") Long sealedByUserId,
                             @Param("sealType") String sealType,
                             @Param("sealedFileHash") String sealedFileHash,
                             @Param("remark") String remark);

    String findActiveCommitteePosition(@Param("tenantId") Long tenantId,
                                       @Param("userId") Long userId);

    int insertContract(@Param("workOrderId") Long workOrderId,
                       @Param("tenantId") Long tenantId,
                       @Param("supplierDeptId") Long supplierDeptId,
                       @Param("supplierName") String supplierName,
                       @Param("contractAmount") BigDecimal contractAmount,
                       @Param("repairScopeHash") String repairScopeHash,
                       @Param("fundSource") String fundSource,
                       @Param("signingMethod") String signingMethod,
                       @Param("contractFileHash") String contractFileHash,
                       @Param("createdByUserId") Long createdByUserId);

    Long findActiveContractId(@Param("workOrderId") Long workOrderId,
                              @Param("tenantId") Long tenantId);

    boolean recommendedQuoteMatches(@Param("workOrderId") Long workOrderId,
                                    @Param("tenantId") Long tenantId,
                                    @Param("supplierDeptId") Long supplierDeptId,
                                    @Param("supplierName") String supplierName);

    int markRecommendedQuoteContractConfirmed(@Param("workOrderId") Long workOrderId,
                                              @Param("tenantId") Long tenantId);

    int insertContractSignature(@Param("contractId") Long contractId,
                                @Param("signature") RepairContractSignature signature);

    int markContractEffective(@Param("contractId") Long contractId,
                              @Param("finalContractFileHash") String finalContractFileHash);

    Long findEffectiveContractId(@Param("workOrderId") Long workOrderId,
                                 @Param("tenantId") Long tenantId);

    BigDecimal findContractPaymentRemaining(@Param("contractId") Long contractId);

    int insertPaymentRequest(@Param("workOrderId") Long workOrderId,
                             @Param("contractId") Long contractId,
                             @Param("tenantId") Long tenantId,
                             @Param("milestoneType") String milestoneType,
                             @Param("requestedAmount") BigDecimal requestedAmount,
                             @Param("conditionEvidenceHash") String conditionEvidenceHash,
                             @Param("requestedByUserId") Long requestedByUserId);

    Long findLatestPaymentRequestId(@Param("workOrderId") Long workOrderId,
                                    @Param("contractId") Long contractId);

    int deleteAcceptanceScope(@Param("workOrderId") Long workOrderId,
                              @Param("tenantId") Long tenantId);

    int insertAcceptanceScope(@Param("workOrderId") Long workOrderId,
                              @Param("tenantId") Long tenantId,
                              @Param("roomId") Long roomId,
                              @Param("affectedReason") String affectedReason,
                              @Param("createdByUserId") Long createdByUserId);

    boolean roomInAcceptanceScope(@Param("workOrderId") Long workOrderId,
                                  @Param("tenantId") Long tenantId,
                                  @Param("roomId") Long roomId);

    boolean ownerOwnsAcceptanceRoom(@Param("workOrderId") Long workOrderId,
                                    @Param("tenantId") Long tenantId,
                                    @Param("roomId") Long roomId,
                                    @Param("uid") Long uid);

    List<Long> listOwnerAcceptanceRooms(@Param("workOrderId") Long workOrderId,
                                        @Param("tenantId") Long tenantId,
                                        @Param("uid") Long uid);

    int insertAcceptanceRecord(@Param("workOrderId") Long workOrderId,
                               @Param("tenantId") Long tenantId,
                               @Param("record") RepairAcceptanceRecord record);

    RepairAcceptanceSummaryRow summarizeAcceptance(@Param("workOrderId") Long workOrderId,
                                                    @Param("tenantId") Long tenantId);

    int insertEvent(RepairWorkOrderEventRow row);

    List<RepairWorkOrderEventRow> listEvents(@Param("workOrderId") Long workOrderId,
                                             @Param("tenantId") Long tenantId);
}
