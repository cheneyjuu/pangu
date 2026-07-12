// 关联业务：实现维修工单状态机及其表决、报审、盖章、合同和验收数据访问。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.asset.OwnerPropertyDetail;
import com.pangu.domain.model.repair.RepairAcceptanceRecord;
import com.pangu.domain.model.repair.RepairAcceptanceSummary;
import com.pangu.domain.model.repair.RepairApprovalAttachment;
import com.pangu.domain.model.repair.RepairBuildingDecisionSnapshot;
import com.pangu.domain.model.repair.RepairContractSignature;
import com.pangu.domain.model.repair.RepairDecisionRoom;
import com.pangu.domain.model.repair.RepairFrameworkRelation;
import com.pangu.domain.model.repair.RepairLocationOption;
import com.pangu.domain.model.repair.RepairLocalDecision;
import com.pangu.domain.model.repair.RepairLocalDecisionChannel;
import com.pangu.domain.model.repair.RepairLocalDecisionScopeType;
import com.pangu.domain.model.repair.RepairOwnerLocalDecision;
import com.pangu.domain.model.repair.RepairQuoteConfirmationStatus;
import com.pangu.domain.model.repair.RepairQuoteInvitation;
import com.pangu.domain.model.repair.RepairQuoteSubmissionSource;
import com.pangu.domain.model.repair.RepairSource;
import com.pangu.domain.model.repair.RepairPublicAreaScope;
import com.pangu.domain.model.repair.RepairSpaceScope;
import com.pangu.domain.model.repair.RepairSupplierQuote;
import com.pangu.domain.model.repair.RepairSupplierQuoteStatus;
import com.pangu.domain.model.repair.RepairSupplierOrganization;
import com.pangu.domain.model.repair.RepairSupplierRecommendation;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.model.repair.RepairSolitaireEntry;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderEvent;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.repair.SupplierActivationInvitation;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import com.pangu.infrastructure.persistence.entity.OwnerPropertyDetailRow;
import com.pangu.infrastructure.persistence.entity.RepairBuildingDecisionSnapshotRow;
import com.pangu.infrastructure.persistence.entity.RepairFrameworkRelationRow;
import com.pangu.infrastructure.persistence.entity.RepairLocationOptionRow;
import com.pangu.infrastructure.persistence.entity.RepairLocalDecisionRow;
import com.pangu.infrastructure.persistence.entity.RepairSupplierQuoteRow;
import com.pangu.infrastructure.persistence.entity.RepairSupplierOrganizationRow;
import com.pangu.infrastructure.persistence.entity.RepairSupplierRecommendationRow;
import com.pangu.infrastructure.persistence.entity.RepairWorkOrderEventRow;
import com.pangu.infrastructure.persistence.entity.RepairWorkOrderRow;
import com.pangu.infrastructure.persistence.entity.SupplierActivationInvitationRow;
import com.pangu.infrastructure.persistence.mapper.RepairWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RepairWorkOrderRepositoryImpl implements RepairWorkOrderRepository {

    private final RepairWorkOrderMapper mapper;

    @Override
    public Optional<OwnerPropertyDetail> findOwnerProperty(Long uid, Long tenantId, Long opid) {
        return Optional.ofNullable(mapper.selectOwnerProperty(uid, tenantId, opid)).map(this::toOwnerProperty);
    }

    @Override
    public boolean buildingExists(Long tenantId, Long buildingId) {
        return mapper.existsBuilding(tenantId, buildingId);
    }

    @Override
    public boolean roomExists(Long tenantId, Long buildingId, Long roomId) {
        return mapper.existsRoom(tenantId, buildingId, roomId);
    }

    @Override
    public List<RepairLocationOption> listLocationOptions(Long tenantId,
                                                          List<WorkIdentityBuildingScope> authorizedBuildingScopes,
                                                          boolean restrictToScopes) {
        return mapper.selectLocationOptions(tenantId, authorizedBuildingScopes, restrictToScopes).stream()
                .map(this::toLocationOption)
                .toList();
    }

    @Override
    public Optional<RepairWorkOrder> findDuplicate(Long tenantId,
                                                   Long reporterAccountId,
                                                   RepairSpaceScope spaceScope,
                                                   Long roomId,
                                                   Long buildingId,
                                                   String title,
                                                   LocalDateTime since) {
        return Optional.ofNullable(mapper.findDuplicate(
                tenantId, reporterAccountId, spaceScope.name(), roomId, buildingId, title, since)).map(this::toDomain);
    }

    @Override
    public RepairWorkOrder insert(RepairWorkOrder workOrder) {
        RepairWorkOrderRow row = toRow(workOrder);
        mapper.insert(row);
        return findById(row.getWorkOrderId()).orElseThrow();
    }

    @Override
    public Optional<RepairWorkOrder> findById(Long workOrderId) {
        return Optional.ofNullable(mapper.findById(workOrderId)).map(this::toDomain);
    }

    @Override
    public Optional<RepairWorkOrder> findByIdForOwner(Long workOrderId, Long accountId, Long uid, Long tenantId) {
        return Optional.ofNullable(mapper.findByIdForOwner(workOrderId, accountId, uid, tenantId)).map(this::toDomain);
    }

    @Override
    public List<RepairWorkOrder> listForOwner(Long accountId, Long uid, Long tenantId) {
        return mapper.listForOwner(accountId, uid, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<RepairWorkOrder> listForAdmin(Long tenantId,
                                              String roleKey,
                                              Long userId,
                                              List<WorkIdentityBuildingScope> authorizedBuildingScopes,
                                              RepairWorkOrderStatus status,
                                              RepairSpaceScope scope,
                                              String keyword,
                                              int page,
                                              int size) {
        int offset = (Math.max(page, 1) - 1) * size;
        return mapper.listForAdmin(tenantId, roleKey, userId, authorizedBuildingScopes,
                status == null ? null : status.name(),
                scope == null ? null : scope.name(),
                keyword, size, offset).stream().map(this::toDomain).toList();
    }

    @Override
    public long countForAdmin(Long tenantId,
                              String roleKey,
                              Long userId,
                              List<WorkIdentityBuildingScope> authorizedBuildingScopes,
                              RepairWorkOrderStatus status,
                              RepairSpaceScope scope,
                              String keyword) {
        return mapper.countForAdmin(tenantId, roleKey, userId, authorizedBuildingScopes,
                status == null ? null : status.name(),
                scope == null ? null : scope.name(),
                keyword);
    }

    @Override
    public List<RepairWorkOrder> listForSupplier(Long supplierDeptId) {
        return mapper.listForSupplier(supplierDeptId).stream().map(this::toDomain).toList();
    }

    @Override
    public int update(RepairWorkOrder workOrder) {
        return mapper.update(toRow(workOrder));
    }

    @Override
    public void lockQuoteSubmission(Long workOrderId) {
        mapper.lockQuoteSubmission(workOrderId);
    }

    @Override
    public RepairSupplierQuote insertQuote(RepairSupplierQuote quote) {
        RepairSupplierQuoteRow row = toRow(quote);
        mapper.insertQuote(row);
        return findQuote(row.getQuoteId(), quote.workOrderId(), quote.tenantId()).orElseThrow();
    }

    @Override
    public Optional<RepairSupplierQuote> findActiveQuote(Long workOrderId, Long tenantId, Long supplierDeptId) {
        return Optional.ofNullable(mapper.findActiveQuote(workOrderId, tenantId, supplierDeptId))
                .map(this::toDomain);
    }

    @Override
    public Optional<RepairSupplierQuote> findLatestSupplierQuote(Long workOrderId,
                                                                 Long tenantId,
                                                                 Long supplierDeptId) {
        return Optional.ofNullable(mapper.findLatestSupplierQuote(workOrderId, tenantId, supplierDeptId))
                .map(this::toDomain);
    }

    @Override
    public void supersedeQuote(Long quoteId) {
        mapper.supersedeQuote(quoteId);
    }

    @Override
    public void linkSupersededQuote(Long quoteId, Long supersededByQuoteId) {
        mapper.linkSupersededQuote(quoteId, supersededByQuoteId);
    }

    @Override
    public Optional<RepairSupplierQuote> findQuote(Long quoteId, Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findQuote(quoteId, workOrderId, tenantId)).map(this::toDomain);
    }

    @Override
    public int countQuotes(Long workOrderId, Long tenantId) {
        return mapper.countQuotes(workOrderId, tenantId);
    }

    @Override
    public int countQuoteInvitations(Long workOrderId, Long tenantId) {
        return mapper.countQuoteInvitations(workOrderId, tenantId);
    }

    @Override
    public List<RepairQuoteInvitation> listQuoteInvitations(Long workOrderId, Long tenantId) {
        return mapper.listQuoteInvitations(workOrderId, tenantId).stream()
                .map(row -> new RepairQuoteInvitation(
                        row.getQuoteInvitationId(), row.getWorkOrderId(), row.getSupplierDeptId(),
                        row.getSupplierName(), row.getStatus(),
                        row.getInvitationRound() == null ? 1 : row.getInvitationRound(),
                        row.getInvitationType(), row.getRevisionReason(), row.getDeadline(), row.getSentAt()))
                .toList();
    }

    @Override
    public Optional<String> findSupplierLegalName(Long supplierDeptId) {
        return Optional.ofNullable(mapper.findSupplierLegalName(supplierDeptId));
    }

    @Override
    public List<RepairSupplierOrganization> listSupplierOrganizations(Long tenantId) {
        return mapper.listSupplierOrganizations(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<RepairSupplierQuote> listSupplierQuotes(Long workOrderId, Long tenantId) {
        return mapper.listSupplierQuotes(workOrderId, tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<RepairSupplierQuote> listSupplierQuoteHistory(Long workOrderId,
                                                              Long tenantId,
                                                              Long supplierDeptId) {
        return mapper.listSupplierQuoteHistory(workOrderId, tenantId, supplierDeptId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<RepairFrameworkRelation> listActiveFrameworkRelations(Long tenantId, String serviceCategory) {
        return mapper.listActiveFrameworkRelations(tenantId, serviceCategory).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Long registerSupplierOrganization(Long tenantId,
                                             String legalName,
                                             String unifiedSocialCreditCode,
                                             String contactName,
                                             String contactPhone,
                                             Long requestedByUserId) {
        Long supplierDeptId = unifiedSocialCreditCode == null
                ? null
                : mapper.findSupplierDeptIdByUscc(unifiedSocialCreditCode);
        if (supplierDeptId == null) {
            supplierDeptId = mapper.findProvisionalSupplierDeptId(tenantId, legalName);
        }
        if (supplierDeptId == null) {
            supplierDeptId = mapper.nextDeptId();
            mapper.insertSupplierDept(supplierDeptId, legalName);
            mapper.insertSupplierProfile(supplierDeptId, unifiedSocialCreditCode, legalName, contactName, contactPhone);
        } else {
            mapper.completeSupplierProfile(supplierDeptId, unifiedSocialCreditCode, contactName, contactPhone);
        }
        mapper.insertSupplierTenantRelation(tenantId, supplierDeptId, requestedByUserId);
        return supplierDeptId;
    }

    @Override
    public Optional<RepairSupplierOrganization> findSupplierOrganization(Long tenantId, Long supplierDeptId) {
        return Optional.ofNullable(mapper.findSupplierOrganization(tenantId, supplierDeptId))
                .map(this::toDomain);
    }

    @Override
    public Optional<SupplierActivationInvitation> findReusableSupplierActivationInvitation(
            Long supplierDeptId, String contactPhone, LocalDateTime now) {
        return Optional.ofNullable(mapper.findReusableSupplierActivationInvitation(supplierDeptId, contactPhone, now))
                .map(this::toDomain);
    }

    @Override
    public Optional<SupplierActivationInvitation> findSupplierActivationInvitationForUpdate(Long invitationId) {
        return Optional.ofNullable(mapper.findSupplierActivationInvitationForUpdate(invitationId))
                .map(this::toDomain);
    }

    @Override
    public boolean supplierHasActiveIdentity(Long supplierDeptId, String contactPhone) {
        return mapper.supplierHasActiveIdentity(supplierDeptId, contactPhone);
    }

    @Override
    public void cancelPendingSupplierActivationInvitations(Long supplierDeptId, String contactPhone) {
        mapper.cancelPendingSupplierActivationInvitations(supplierDeptId, contactPhone);
    }

    @Override
    public SupplierActivationInvitation insertSupplierActivationInvitation(
            Long tenantId,
            Long supplierDeptId,
            Long workOrderId,
            String contactName,
            String contactPhone,
            String invitationTokenHash,
            LocalDateTime expiresAt,
            Long invitedByUserId) {
        SupplierActivationInvitationRow row = new SupplierActivationInvitationRow();
        row.setTenantId(tenantId);
        row.setSupplierDeptId(supplierDeptId);
        row.setWorkOrderId(workOrderId);
        row.setContactName(contactName);
        row.setContactPhone(contactPhone);
        row.setInvitationTokenHash(invitationTokenHash);
        row.setExpiresAt(expiresAt);
        row.setInvitedByUserId(invitedByUserId);
        mapper.insertSupplierActivationInvitation(row);
        return findSupplierActivationInvitationForUpdate(row.getInvitationId())
                .orElseThrow(() -> new IllegalStateException("供应商激活邀请写入后不可见"));
    }

    @Override
    public void markSupplierActivationInvitationExpired(Long invitationId) {
        mapper.markSupplierActivationInvitationExpired(invitationId);
    }

    @Override
    public void markSupplierActivationInvitationActivated(
            Long invitationId, Long accountId, Long userId, LocalDateTime activatedAt) {
        if (mapper.markSupplierActivationInvitationActivated(invitationId, accountId, userId, activatedAt) != 1) {
            throw new IllegalStateException("供应商激活邀请状态已变化");
        }
    }

    @Override
    public boolean supplierVerified(Long tenantId, Long supplierDeptId) {
        return mapper.supplierVerified(tenantId, supplierDeptId);
    }

    @Override
    public void inviteSuppliers(Long workOrderId,
                                Long tenantId,
                                Long invitedByUserId,
                                List<Long> supplierDeptIds,
                                LocalDateTime deadline) {
        supplierDeptIds.forEach(supplierDeptId -> mapper.insertQuoteInvitation(
                workOrderId, tenantId, supplierDeptId, invitedByUserId, deadline));
    }

    @Override
    public void inviteSupplierRevisions(Long workOrderId,
                                        Long tenantId,
                                        Long invitedByUserId,
                                        List<Long> supplierDeptIds,
                                        LocalDateTime deadline,
                                        String revisionReason) {
        supplierDeptIds.forEach(supplierDeptId -> mapper.insertQuoteRevisionInvitation(
                workOrderId, tenantId, supplierDeptId, invitedByUserId, deadline, revisionReason));
    }

    @Override
    public void markActiveQuoteRevisionRequested(Long workOrderId, Long tenantId, Long supplierDeptId) {
        mapper.markActiveQuoteRevisionRequested(workOrderId, tenantId, supplierDeptId);
    }

    @Override
    public boolean supplierCanAccess(Long workOrderId, Long tenantId, Long supplierDeptId) {
        return mapper.supplierCanAccess(workOrderId, tenantId, supplierDeptId);
    }

    @Override
    public RepairSupplierRecommendation insertRecommendation(RepairSupplierRecommendation recommendation) {
        RepairSupplierRecommendationRow row = toRow(recommendation);
        mapper.insertRecommendation(row);
        return new RepairSupplierRecommendation(
                row.getRecommendationId(),
                recommendation.workOrderId(),
                recommendation.tenantId(),
                recommendation.quoteId(),
                recommendation.recommendedByUserId(),
                recommendation.recommendationReason(),
                recommendation.singleSource(),
                recommendation.singleSourceReason(),
                recommendation.selectionMethod(),
                recommendation.insufficientQuoteReason(),
                recommendation.frameworkRelationId(),
                recommendation.createTime());
    }

    @Override
    public Optional<RepairSupplierRecommendation> findLatestRecommendation(Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findLatestRecommendation(workOrderId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public boolean frameworkRelationActive(Long relationId,
                                           Long tenantId,
                                           Long supplierDeptId,
                                           String serviceCategory) {
        return mapper.frameworkRelationActive(relationId, tenantId, supplierDeptId, serviceCategory);
    }

    @Override
    public Optional<RepairBuildingDecisionSnapshot> loadBuildingDecisionSnapshot(Long tenantId,
                                                                                  Long buildingId,
                                                                                  String unitName) {
        return Optional.ofNullable(mapper.loadBuildingDecisionSnapshot(tenantId, buildingId, unitName))
                .map(this::toDomain);
    }

    @Override
    public List<RepairDecisionRoom> listDecisionRooms(Long tenantId, Long buildingId, String unitName) {
        return mapper.listDecisionRooms(tenantId, buildingId, unitName).stream()
                .map(row -> new RepairDecisionRoom(row.getRoomId(), row.getRoomLabel(), row.getBuildArea()))
                .toList();
    }

    @Override
    public RepairLocalDecision insertLocalDecision(RepairLocalDecision decision) {
        RepairLocalDecisionRow row = toRow(decision);
        mapper.insertLocalDecision(row);
        return findLocalDecision(decision.workOrderId(), decision.tenantId()).orElseThrow();
    }

    @Override
    public Optional<RepairLocalDecision> findLocalDecision(Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findLocalDecision(workOrderId, tenantId)).map(this::toDomain);
    }

    @Override
    public List<RepairOwnerLocalDecision> listOwnerLocalDecisions(Long uid, Long tenantId) {
        return mapper.listOwnerLocalDecisions(uid, tenantId, null, null).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<RepairOwnerLocalDecision> findOwnerLocalDecision(
            Long decisionId, Long opid, Long uid, Long tenantId) {
        return mapper.listOwnerLocalDecisions(uid, tenantId, decisionId, opid).stream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public void submitOnlineDecisionVote(Long decisionId, Long tenantId, Long roomId, Long ownerUid,
                                         Long accountId, String choice, BigDecimal buildArea) {
        mapper.submitOnlineDecisionVote(decisionId, tenantId, roomId, ownerUid, accountId, choice, buildArea);
    }

    @Override
    public List<RepairSolitaireEntry> listSolitaireEntries(Long decisionId, Long tenantId) {
        return mapper.listSolitaireEntries(decisionId, tenantId).stream()
                .map(row -> new RepairSolitaireEntry(row.getRoomId(), row.getOwnerUid(),
                        com.pangu.domain.model.repair.RepairVoteChoice.valueOf(row.getChoice()),
                        row.getBuildArea(), row.getOriginalText()))
                .toList();
    }

    @Override
    public int updateLocalDecisionResult(RepairLocalDecision decision) {
        return mapper.updateLocalDecisionResult(toRow(decision));
    }

    @Override
    public void replaceSolitaireEntries(Long decisionId,
                                        Long tenantId,
                                        Long verifiedByUserId,
                                        List<RepairSolitaireEntry> entries) {
        mapper.deleteSolitaireEntries(decisionId, tenantId);
        entries.forEach(entry -> mapper.insertSolitaireEntry(decisionId, tenantId, verifiedByUserId, entry));
    }

    @Override
    public void insertSolitaireEvidence(Long decisionId,
                                        Long tenantId,
                                        String attachmentHash,
                                        Long uploadedByAccountId,
                                        Long uploadedByUserId) {
        mapper.insertSolitaireEvidence(decisionId, tenantId, attachmentHash,
                uploadedByAccountId, uploadedByUserId);
    }

    @Override
    public void insertAssemblyDecision(Long workOrderId, Long tenantId, Long packageId) {
        mapper.insertAssemblyDecision(workOrderId, tenantId, packageId);
    }

    @Override
    public Optional<Long> findAssemblyDecisionPackageId(Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findAssemblyDecisionPackageId(workOrderId, tenantId));
    }

    @Override
    public int updateAssemblyDecisionResult(Long workOrderId, Long tenantId, String result) {
        return mapper.updateAssemblyDecisionResult(workOrderId, tenantId, result);
    }

    @Override
    public Long insertApprovalPackage(Long workOrderId,
                                      Long tenantId,
                                      String officialDocumentHash,
                                      String mergedPackageHash,
                                      boolean printedAndAttached,
                                      Long submittedByUserId,
                                      List<RepairApprovalAttachment> attachments) {
        int version = mapper.nextApprovalPackageVersion(workOrderId);
        mapper.insertApprovalPackage(workOrderId, tenantId, version, officialDocumentHash,
                mergedPackageHash, flag(printedAndAttached), submittedByUserId);
        Long packageId = mapper.findInsertedApprovalPackageId(workOrderId, version);
        for (RepairApprovalAttachment attachment : attachments) {
            mapper.insertApprovalAttachment(packageId, attachment.attachmentType(), attachment.attachmentHash(),
                    attachment.originalFileName(), attachment.sortOrder(), submittedByUserId);
        }
        return packageId;
    }

    @Override
    public Optional<Long> findActiveApprovalPackageId(Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findActiveApprovalPackageId(workOrderId, tenantId));
    }

    @Override
    public void updateApprovalPackageStatus(Long packageId, Long tenantId, String status) {
        mapper.updateApprovalPackageStatus(packageId, tenantId, status);
    }

    @Override
    public void insertPriceReview(Long workOrderId,
                                  Long packageId,
                                  Long tenantId,
                                  String reviewMode,
                                  BigDecimal reviewedAmount,
                                  String reviewReportHash,
                                  String conclusion,
                                  String opinion,
                                  Long reviewedByUserId) {
        mapper.insertPriceReview(workOrderId, packageId, tenantId, reviewMode, reviewedAmount,
                reviewReportHash, conclusion, opinion, reviewedByUserId);
    }

    @Override
    public Optional<BigDecimal> findLatestApprovedPrice(Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findLatestApprovedPrice(workOrderId, tenantId));
    }

    @Override
    public void insertGovernanceApproval(Long workOrderId,
                                         Long tenantId,
                                         Long approverUserId,
                                         String approverPosition,
                                         String opinion) {
        mapper.insertGovernanceApproval(workOrderId, tenantId, approverUserId, approverPosition, opinion);
    }

    @Override
    public void insertGovernanceSeal(Long workOrderId,
                                     Long tenantId,
                                     Long sealedByUserId,
                                     Long usageId,
                                     String sealType,
                                     String sealedFileHash,
                                     String remark) {
        mapper.insertGovernanceSeal(
                workOrderId, tenantId, sealedByUserId, usageId, sealType, sealedFileHash, remark);
    }

    @Override
    public Optional<String> findActiveCommitteePosition(Long tenantId, Long userId) {
        return Optional.ofNullable(mapper.findActiveCommitteePosition(tenantId, userId));
    }

    @Override
    public Long insertContract(Long workOrderId,
                               Long tenantId,
                               Long supplierDeptId,
                               String supplierName,
                               BigDecimal contractAmount,
                               String repairScopeHash,
                               String fundSource,
                               String signingMethod,
                               String contractFileHash,
                               Long createdByUserId) {
        mapper.insertContract(workOrderId, tenantId, supplierDeptId, supplierName, contractAmount,
                repairScopeHash, fundSource, signingMethod, contractFileHash, createdByUserId);
        return mapper.findActiveContractId(workOrderId, tenantId);
    }

    @Override
    public Optional<Long> findActiveContractId(Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findActiveContractId(workOrderId, tenantId));
    }

    @Override
    public boolean recommendedQuoteMatches(Long workOrderId,
                                           Long tenantId,
                                           Long supplierDeptId,
                                           String supplierName) {
        return mapper.recommendedQuoteMatches(workOrderId, tenantId, supplierDeptId, supplierName);
    }

    @Override
    public void markRecommendedQuoteContractConfirmed(Long workOrderId, Long tenantId) {
        mapper.markRecommendedQuoteContractConfirmed(workOrderId, tenantId);
    }

    @Override
    public void insertContractSignatures(Long contractId, List<RepairContractSignature> signatures) {
        signatures.forEach(signature -> mapper.insertContractSignature(contractId, signature));
    }

    @Override
    public int markContractEffective(Long contractId, String finalContractFileHash) {
        return mapper.markContractEffective(contractId, finalContractFileHash);
    }

    @Override
    public Optional<Long> findEffectiveContractId(Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findEffectiveContractId(workOrderId, tenantId));
    }

    @Override
    public BigDecimal findContractPaymentRemaining(Long contractId) {
        return mapper.findContractPaymentRemaining(contractId);
    }

    @Override
    public Long insertPaymentRequest(Long workOrderId,
                                     Long contractId,
                                     Long tenantId,
                                     String milestoneType,
                                     BigDecimal requestedAmount,
                                     String conditionEvidenceHash,
                                     Long requestedByUserId) {
        mapper.insertPaymentRequest(workOrderId, contractId, tenantId, milestoneType,
                requestedAmount, conditionEvidenceHash, requestedByUserId);
        return mapper.findLatestPaymentRequestId(workOrderId, contractId);
    }

    @Override
    public void replaceAcceptanceScope(Long workOrderId,
                                       Long tenantId,
                                       Long createdByUserId,
                                       List<Long> roomIds,
                                       List<String> affectedReasons) {
        mapper.deleteAcceptanceScope(workOrderId, tenantId);
        for (int index = 0; index < roomIds.size(); index++) {
            mapper.insertAcceptanceScope(workOrderId, tenantId, roomIds.get(index),
                    affectedReasons.get(index), createdByUserId);
        }
    }

    @Override
    public boolean roomInAcceptanceScope(Long workOrderId, Long tenantId, Long roomId) {
        return mapper.roomInAcceptanceScope(workOrderId, tenantId, roomId);
    }

    @Override
    public boolean ownerOwnsAcceptanceRoom(Long workOrderId, Long tenantId, Long roomId, Long uid) {
        return mapper.ownerOwnsAcceptanceRoom(workOrderId, tenantId, roomId, uid);
    }

    @Override
    public List<Long> listOwnerAcceptanceRooms(Long workOrderId, Long tenantId, Long uid) {
        return mapper.listOwnerAcceptanceRooms(workOrderId, tenantId, uid);
    }

    @Override
    public void insertAcceptanceRecord(Long workOrderId, Long tenantId, RepairAcceptanceRecord record) {
        mapper.insertAcceptanceRecord(workOrderId, tenantId, record);
    }

    @Override
    public RepairAcceptanceSummary summarizeAcceptance(Long workOrderId, Long tenantId) {
        var row = mapper.summarizeAcceptance(workOrderId, tenantId);
        return new RepairAcceptanceSummary(
                row.getAffectedRoomCount() == null ? 0 : row.getAffectedRoomCount(),
                row.getPassedAffectedRoomCount() == null ? 0 : row.getPassedAffectedRoomCount(),
                row.getRectificationCount() == null ? 0 : row.getRectificationCount(),
                row.getUnreachableCount() == null ? 0 : row.getUnreachableCount(),
                Boolean.TRUE.equals(row.getOwnerRepresentativePassed()),
                Boolean.TRUE.equals(row.getPropertyRepresentativePassed()));
    }

    @Override
    public void insertEvent(RepairWorkOrderEvent event) {
        mapper.insertEvent(toRow(event));
    }

    @Override
    public List<RepairWorkOrderEvent> listEvents(Long workOrderId, Long tenantId) {
        return mapper.listEvents(workOrderId, tenantId).stream().map(this::toDomain).toList();
    }

    private OwnerPropertyDetail toOwnerProperty(OwnerPropertyDetailRow row) {
        return new OwnerPropertyDetail(
                row.getOpid(),
                row.getBuildingId(),
                row.getRoomId(),
                row.getBuildArea(),
                row.getVotingDelegate() != null && row.getVotingDelegate() == 1,
                row.getAccountStatus());
    }

    private RepairLocationOption toLocationOption(RepairLocationOptionRow row) {
        return new RepairLocationOption(
                row.getTenantId(),
                row.getCommunityName(),
                row.getBuildingId(),
                row.getBuildingName(),
                row.getUnitName(),
                row.getRoomId(),
                row.getRoomName());
    }

    private RepairWorkOrder toDomain(RepairWorkOrderRow row) {
        return new RepairWorkOrder(
                row.getWorkOrderId(),
                row.getOrderNo(),
                row.getTenantId(),
                row.getTitle(),
                row.getDescription(),
                RepairSource.valueOf(row.getSource()),
                RepairSpaceScope.valueOf(row.getSpaceScope()),
                row.getPublicAreaScope() == null ? null : RepairPublicAreaScope.valueOf(row.getPublicAreaScope()),
                RepairWorkOrderStatus.valueOf(row.getStatus()),
                row.getReporterAccountId(),
                row.getReporterUid(),
                row.getReporterUserId(),
                row.getRoomId(),
                row.getBuildingId(),
                row.getLocationText(),
                flag(row.getNeedManualLocation()),
                flag(row.getLocationLocked()),
                row.getAssignedUserId(),
                row.getAssigneeRoleKey(),
                row.getAssigneeDeptId(),
                row.getCategory(),
                row.getRiskLevel(),
                row.getSurveySummary(),
                row.getPlanBudget(),
                row.getPublicCeilingPrice(),
                row.getFundSource(),
                flag(row.getFundGateBlocked()),
                row.getSatisfactionScore(),
                row.getSatisfactionComment(),
                row.getVersion(),
                row.getCreateTime(),
                row.getUpdateTime());
    }

    private RepairWorkOrderRow toRow(RepairWorkOrder domain) {
        RepairWorkOrderRow row = new RepairWorkOrderRow();
        row.setWorkOrderId(domain.workOrderId());
        row.setOrderNo(domain.orderNo());
        row.setTenantId(domain.tenantId());
        row.setTitle(domain.title());
        row.setDescription(domain.description());
        row.setSource(domain.source().name());
        row.setSpaceScope(domain.spaceScope().name());
        row.setPublicAreaScope(domain.publicAreaScope() == null ? null : domain.publicAreaScope().name());
        row.setStatus(domain.status().name());
        row.setReporterAccountId(domain.reporterAccountId());
        row.setReporterUid(domain.reporterUid());
        row.setReporterUserId(domain.reporterUserId());
        row.setRoomId(domain.roomId());
        row.setBuildingId(domain.buildingId());
        row.setLocationText(domain.locationText());
        row.setNeedManualLocation(flag(domain.needManualLocation()));
        row.setLocationLocked(flag(domain.locationLocked()));
        row.setAssignedUserId(domain.assignedUserId());
        row.setAssigneeRoleKey(domain.assigneeRoleKey());
        row.setAssigneeDeptId(domain.assigneeDeptId());
        row.setCategory(domain.category());
        row.setRiskLevel(domain.riskLevel());
        row.setSurveySummary(domain.surveySummary());
        row.setPlanBudget(domain.planBudget());
        row.setPublicCeilingPrice(domain.publicCeilingPrice());
        row.setFundSource(domain.fundSource());
        row.setFundGateBlocked(flag(domain.fundGateBlocked()));
        row.setSatisfactionScore(domain.satisfactionScore());
        row.setSatisfactionComment(domain.satisfactionComment());
        row.setVersion(domain.version());
        return row;
    }

    private RepairWorkOrderEvent toDomain(RepairWorkOrderEventRow row) {
        return new RepairWorkOrderEvent(
                row.getEventId(),
                row.getWorkOrderId(),
                row.getTenantId(),
                row.getAction(),
                row.getFromStatus() == null ? null : RepairWorkOrderStatus.valueOf(row.getFromStatus()),
                row.getToStatus() == null ? null : RepairWorkOrderStatus.valueOf(row.getToStatus()),
                row.getActorAccountId(),
                row.getActorIdentityType(),
                row.getActorIdentityId(),
                row.getRemark(),
                row.getPayloadJson(),
                row.getCreateTime());
    }

    private RepairSupplierQuote toDomain(RepairSupplierQuoteRow row) {
        return new RepairSupplierQuote(
                row.getQuoteId(),
                row.getWorkOrderId(),
                row.getTenantId(),
                row.getSupplierName(),
                row.getQuoteAmount(),
                row.getQuoteSummary(),
                row.getAttachmentId(),
                row.getAttachmentHash(),
                row.getSubmittedByUserId(),
                row.getSubmittedByRoleKey(),
                flag(row.getSubmittedBySupplier()),
                flag(row.getSupplierConfirmed()),
                row.getSupplierDeptId(),
                row.getQuoteInvitationId(),
                row.getSubmissionSource() == null ? null : RepairQuoteSubmissionSource.valueOf(row.getSubmissionSource()),
                row.getConfirmationStatus() == null ? null : RepairQuoteConfirmationStatus.valueOf(row.getConfirmationStatus()),
                row.getOriginalSource(),
                row.getOriginalAttachmentHash(),
                row.getQuoteStatus() == null ? RepairSupplierQuoteStatus.ACTIVE
                        : RepairSupplierQuoteStatus.valueOf(row.getQuoteStatus()),
                row.getRevisionNo() == null ? 1 : row.getRevisionNo(),
                row.getSupersededByQuoteId(),
                row.getCreateTime());
    }

    private RepairSupplierOrganization toDomain(RepairSupplierOrganizationRow row) {
        return new RepairSupplierOrganization(
                row.getSupplierDeptId(),
                row.getUnifiedSocialCreditCode(),
                row.getLegalName(),
                row.getContactName(),
                row.getContactPhone(),
                row.getVerificationStatus(),
                row.getVerificationId(),
                row.getVerificationMethod(),
                row.getVerificationProviderCode(),
                row.getVerificationSourceCode(),
                row.isVerificationSimulated(),
                row.getVerifiedByAccountId(),
                row.getVerifiedByUserId(),
                row.getVerifiedAt(),
                row.getAccountStatus(),
                row.getActiveAccountCount(),
                row.getLoginPhone(),
                row.getActivationInvitationId(),
                row.getActivationInvitationExpiresAt());
    }

    private SupplierActivationInvitation toDomain(SupplierActivationInvitationRow row) {
        return new SupplierActivationInvitation(
                row.getInvitationId(),
                row.getTenantId(),
                row.getSupplierDeptId(),
                row.getWorkOrderId(),
                row.getSupplierLegalName(),
                row.getContactName(),
                row.getContactPhone(),
                row.getDefaultRoleKey(),
                row.getStatus(),
                row.getExpiresAt(),
                row.getActivatedAccountId(),
                row.getActivatedUserId(),
                row.getInvitedByUserId(),
                row.getActivatedAt(),
                row.getCreateTime());
    }

    private RepairFrameworkRelation toDomain(RepairFrameworkRelationRow row) {
        return new RepairFrameworkRelation(
                row.getRelationId(),
                row.getSupplierDeptId(),
                row.getSupplierLegalName(),
                row.getServiceCategory(),
                row.getValidFrom(),
                row.getValidUntil());
    }

    private RepairSupplierQuoteRow toRow(RepairSupplierQuote domain) {
        RepairSupplierQuoteRow row = new RepairSupplierQuoteRow();
        row.setQuoteId(domain.quoteId());
        row.setWorkOrderId(domain.workOrderId());
        row.setTenantId(domain.tenantId());
        row.setSupplierName(domain.supplierName());
        row.setQuoteAmount(domain.quoteAmount());
        row.setQuoteSummary(domain.quoteSummary());
        row.setAttachmentId(domain.attachmentId());
        row.setAttachmentHash(domain.attachmentHash());
        row.setSubmittedByUserId(domain.submittedByUserId());
        row.setSubmittedByRoleKey(domain.submittedByRoleKey());
        row.setSubmittedBySupplier(flag(domain.submittedBySupplier()));
        row.setSupplierConfirmed(flag(domain.supplierConfirmed()));
        row.setSupplierDeptId(domain.supplierDeptId());
        row.setQuoteInvitationId(domain.quoteInvitationId());
        row.setSubmissionSource(domain.submissionSource().name());
        row.setConfirmationStatus(domain.confirmationStatus().name());
        row.setOriginalSource(domain.originalSource());
        row.setOriginalAttachmentHash(domain.originalAttachmentHash());
        row.setQuoteStatus(domain.quoteStatus().name());
        row.setRevisionNo(domain.revisionNo());
        row.setSupersededByQuoteId(domain.supersededByQuoteId());
        return row;
    }

    private RepairSupplierRecommendationRow toRow(RepairSupplierRecommendation domain) {
        RepairSupplierRecommendationRow row = new RepairSupplierRecommendationRow();
        row.setRecommendationId(domain.recommendationId());
        row.setWorkOrderId(domain.workOrderId());
        row.setTenantId(domain.tenantId());
        row.setQuoteId(domain.quoteId());
        row.setRecommendedByUserId(domain.recommendedByUserId());
        row.setRecommendationReason(domain.recommendationReason());
        row.setSingleSource(flag(domain.singleSource()));
        row.setSingleSourceReason(domain.singleSourceReason());
        row.setSelectionMethod(domain.selectionMethod().name());
        row.setInsufficientQuoteReason(domain.insufficientQuoteReason());
        row.setFrameworkRelationId(domain.frameworkRelationId());
        return row;
    }

    private RepairSupplierRecommendation toDomain(RepairSupplierRecommendationRow row) {
        return new RepairSupplierRecommendation(
                row.getRecommendationId(),
                row.getWorkOrderId(),
                row.getTenantId(),
                row.getQuoteId(),
                row.getRecommendedByUserId(),
                row.getRecommendationReason(),
                flag(row.getSingleSource()),
                row.getSingleSourceReason(),
                RepairSupplierSelectionMethod.valueOf(row.getSelectionMethod()),
                row.getInsufficientQuoteReason(),
                row.getFrameworkRelationId(),
                row.getCreateTime());
    }

    private RepairBuildingDecisionSnapshot toDomain(RepairBuildingDecisionSnapshotRow row) {
        return new RepairBuildingDecisionSnapshot(
                row.getTotalOwnerCount() == null ? 0 : row.getTotalOwnerCount(),
                row.getTotalArea());
    }

    private RepairLocalDecision toDomain(RepairLocalDecisionRow row) {
        return new RepairLocalDecision(
                row.getDecisionId(),
                row.getWorkOrderId(),
                row.getTenantId(),
                row.getBuildingId(),
                RepairLocalDecisionScopeType.valueOf(row.getScopeType()),
                RepairLocalDecisionChannel.valueOf(row.getDecisionChannel()),
                row.getUnitName(),
                row.getScopeLabel(),
                row.getTotalOwnerCount() == null ? 0 : row.getTotalOwnerCount(),
                row.getTotalArea(),
                row.getParticipatedOwnerCount(),
                row.getParticipatedArea(),
                row.getAgreeOwnerCount(),
                row.getAgreeArea(),
                row.getDisagreeOwnerCount(),
                row.getDisagreeArea(),
                row.getAbstainOwnerCount(),
                row.getAbstainArea(),
                row.getInvalidOwnerCount(),
                row.getInvalidArea(),
                row.getEvidenceAttachmentHash(),
                flag(row.getPrintedAndAttached()),
                row.getResult(),
                row.getCreateTime(),
                row.getUpdateTime());
    }

    private RepairLocalDecisionRow toRow(RepairLocalDecision domain) {
        RepairLocalDecisionRow row = new RepairLocalDecisionRow();
        row.setDecisionId(domain.decisionId());
        row.setWorkOrderId(domain.workOrderId());
        row.setTenantId(domain.tenantId());
        row.setBuildingId(domain.buildingId());
        row.setScopeType(domain.scopeType().name());
        row.setDecisionChannel(domain.decisionChannel().name());
        row.setUnitName(domain.unitName());
        row.setScopeLabel(domain.scopeLabel());
        row.setTotalOwnerCount(domain.totalOwnerCount());
        row.setTotalArea(domain.totalArea());
        row.setParticipatedOwnerCount(domain.participatedOwnerCount());
        row.setParticipatedArea(domain.participatedArea());
        row.setAgreeOwnerCount(domain.agreeOwnerCount());
        row.setAgreeArea(domain.agreeArea());
        row.setDisagreeOwnerCount(domain.disagreeOwnerCount());
        row.setDisagreeArea(domain.disagreeArea());
        row.setAbstainOwnerCount(domain.abstainOwnerCount());
        row.setAbstainArea(domain.abstainArea());
        row.setInvalidOwnerCount(domain.invalidOwnerCount());
        row.setInvalidArea(domain.invalidArea());
        row.setEvidenceAttachmentHash(domain.evidenceAttachmentHash());
        row.setPrintedAndAttached(flag(domain.printedAndAttached()));
        row.setResult(domain.result());
        return row;
    }

    private RepairOwnerLocalDecision toDomain(
            com.pangu.infrastructure.persistence.entity.RepairOwnerLocalDecisionRow row) {
        return new RepairOwnerLocalDecision(
                row.getDecisionId(), row.getWorkOrderId(), row.getOrderNo(), row.getTitle(),
                row.getLocationText(), row.getSurveySummary(), row.getScopeLabel(), row.getOpid(),
                row.getRoomId(), row.getRoomName(), row.getBuildArea(), row.getSupplierName(),
                row.getQuoteAmount(), row.getQuoteSummary(), row.getQuoteAttachmentId(),
                row.getMyChoice() == null ? null
                        : com.pangu.domain.model.repair.RepairVoteChoice.valueOf(row.getMyChoice()));
    }

    private RepairWorkOrderEventRow toRow(RepairWorkOrderEvent event) {
        RepairWorkOrderEventRow row = new RepairWorkOrderEventRow();
        row.setEventId(event.eventId());
        row.setWorkOrderId(event.workOrderId());
        row.setTenantId(event.tenantId());
        row.setAction(event.action());
        row.setFromStatus(event.fromStatus() == null ? null : event.fromStatus().name());
        row.setToStatus(event.toStatus() == null ? null : event.toStatus().name());
        row.setActorAccountId(event.actorAccountId());
        row.setActorIdentityType(event.actorIdentityType());
        row.setActorIdentityId(event.actorIdentityId());
        row.setRemark(event.remark());
        row.setPayloadJson(event.payloadJson());
        return row;
    }

    private boolean flag(Integer value) {
        return value != null && value == 1;
    }

    private int flag(boolean value) {
        return value ? 1 : 0;
    }
}
