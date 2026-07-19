// 关联业务：以专用台账持久化线上阅读确认、整包提交和纸质协助请求。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.OnlineBallotSubmission;
import com.pangu.domain.model.voting.OnlinePaperAssistanceRequest;
import com.pangu.domain.model.voting.OnlineVotingAcknowledgement;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.repository.OnlineVotingRepository;
import com.pangu.infrastructure.persistence.entity.OnlineBallotSubmissionItemRow;
import com.pangu.infrastructure.persistence.entity.OnlineBallotSubmissionRow;
import com.pangu.infrastructure.persistence.entity.OnlinePaperAssistanceRequestRow;
import com.pangu.infrastructure.persistence.entity.OnlineVotingAcknowledgementRow;
import com.pangu.infrastructure.persistence.mapper.OnlineVotingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OnlineVotingRepositoryImpl implements OnlineVotingRepository {

    private final OnlineVotingMapper mapper;

    @Override
    public OnlineVotingAcknowledgement insertAcknowledgement(OnlineVotingAcknowledgement acknowledgement) {
        OnlineVotingAcknowledgementRow row = toRow(acknowledgement);
        mapper.insertAcknowledgement(row);
        return findAcknowledgement(
                acknowledgement.packageId(), acknowledgement.electorateItemId(), acknowledgement.tenantId())
                .orElseThrow();
    }

    @Override
    public Optional<OnlineVotingAcknowledgement> findAcknowledgement(
            Long packageId, Long electorateItemId, Long tenantId) {
        return Optional.ofNullable(mapper.selectAcknowledgement(packageId, electorateItemId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public OnlineBallotSubmission insertSubmission(OnlineBallotSubmission submission) {
        OnlineBallotSubmissionRow row = toRow(submission);
        mapper.insertSubmission(row);
        return new OnlineBallotSubmission(
                row.getSubmissionId(), submission.packageId(), submission.electorateItemId(), submission.tenantId(),
                submission.accountId(), submission.uid(), submission.opid(), submission.idempotencyKey(),
                submission.packageHash(), submission.choiceManifestHash(), submission.confirmationHash(),
                submission.status(), submission.submittedAt(), List.of());
    }

    @Override
    public void insertSubmissionItem(Long submissionId, OnlineBallotSubmission.Item item) {
        OnlineBallotSubmissionItemRow row = toRow(submissionId, item);
        mapper.insertSubmissionItem(row);
    }

    @Override
    public Optional<OnlineBallotSubmission> findSubmission(
            Long packageId, Long electorateItemId, Long tenantId) {
        return Optional.ofNullable(mapper.selectSubmission(packageId, electorateItemId, tenantId))
                .map(this::toDomainWithItems);
    }

    @Override
    public Optional<OnlineBallotSubmission> findSubmissionByIdempotencyKey(
            Long packageId, Long tenantId, String idempotencyKey) {
        return Optional.ofNullable(mapper.selectSubmissionByIdempotencyKey(packageId, tenantId, idempotencyKey))
                .map(this::toDomainWithItems);
    }

    @Override
    public long countAcceptedSubmissions(Long packageId, Long tenantId) {
        return mapper.countAcceptedSubmissions(packageId, tenantId);
    }

    @Override
    public OnlinePaperAssistanceRequest insertPaperAssistanceRequest(OnlinePaperAssistanceRequest request) {
        OnlinePaperAssistanceRequestRow row = toRow(request);
        mapper.insertPaperAssistanceRequest(row);
        return findPaperAssistanceRequest(request.packageId(), request.electorateItemId(), request.tenantId())
                .orElseThrow();
    }

    @Override
    public Optional<OnlinePaperAssistanceRequest> findPaperAssistanceRequest(
            Long packageId, Long electorateItemId, Long tenantId) {
        return Optional.ofNullable(mapper.selectPaperAssistanceRequest(packageId, electorateItemId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public List<OnlinePaperAssistanceRequest> listPaperAssistanceRequests(Long packageId, Long tenantId) {
        return mapper.selectPaperAssistanceRequests(packageId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public int reactivatePaperAssistanceRequest(Long requestId, Long tenantId, Instant requestedAt) {
        return mapper.reactivatePaperAssistanceRequest(requestId, tenantId, requestedAt);
    }

    @Override
    public int withdrawPaperAssistanceRequest(Long requestId, Long tenantId, Instant withdrawnAt) {
        return mapper.withdrawPaperAssistanceRequest(requestId, tenantId, withdrawnAt);
    }

    @Override
    public int fulfillPaperAssistanceRequest(
            Long requestId, Long tenantId, Long paperDeliveryId, Instant fulfilledAt) {
        return mapper.fulfillPaperAssistanceRequest(requestId, tenantId, paperDeliveryId, fulfilledAt);
    }

    private OnlineVotingAcknowledgementRow toRow(OnlineVotingAcknowledgement domain) {
        OnlineVotingAcknowledgementRow row = new OnlineVotingAcknowledgementRow();
        row.setAcknowledgementId(domain.acknowledgementId());
        row.setPackageId(domain.packageId());
        row.setElectorateItemId(domain.electorateItemId());
        row.setTenantId(domain.tenantId());
        row.setAccountId(domain.accountId());
        row.setUid(domain.uid());
        row.setOpid(domain.opid());
        row.setPackageHash(domain.packageHash());
        row.setAcknowledgementHash(domain.acknowledgementHash());
        row.setUnifiedDeliveryId(domain.unifiedDeliveryId());
        row.setAcknowledgedAt(domain.acknowledgedAt());
        return row;
    }

    private OnlineVotingAcknowledgement toDomain(OnlineVotingAcknowledgementRow row) {
        return new OnlineVotingAcknowledgement(
                row.getAcknowledgementId(), row.getPackageId(), row.getElectorateItemId(), row.getTenantId(),
                row.getAccountId(), row.getUid(), row.getOpid(), row.getPackageHash(),
                row.getAcknowledgementHash(), row.getUnifiedDeliveryId(), row.getAcknowledgedAt());
    }

    private OnlineBallotSubmissionRow toRow(OnlineBallotSubmission domain) {
        OnlineBallotSubmissionRow row = new OnlineBallotSubmissionRow();
        row.setSubmissionId(domain.submissionId());
        row.setPackageId(domain.packageId());
        row.setElectorateItemId(domain.electorateItemId());
        row.setTenantId(domain.tenantId());
        row.setAccountId(domain.accountId());
        row.setUid(domain.uid());
        row.setOpid(domain.opid());
        row.setIdempotencyKey(domain.idempotencyKey());
        row.setPackageHash(domain.packageHash());
        row.setChoiceManifestHash(domain.choiceManifestHash());
        row.setConfirmationHash(domain.confirmationHash());
        row.setStatus(domain.status().name());
        row.setSubmittedAt(domain.submittedAt());
        return row;
    }

    private OnlineBallotSubmissionItemRow toRow(Long submissionId, OnlineBallotSubmission.Item domain) {
        OnlineBallotSubmissionItemRow row = new OnlineBallotSubmissionItemRow();
        row.setSubmissionItemId(domain.submissionItemId());
        row.setSubmissionId(submissionId);
        row.setSubjectId(domain.subjectId());
        row.setChoice(domain.choice().getDbValue());
        row.setUnifiedBallotId(domain.unifiedBallotId());
        row.setItemConfirmationHash(domain.itemConfirmationHash());
        return row;
    }

    private OnlineBallotSubmission toDomainWithItems(OnlineBallotSubmissionRow row) {
        return new OnlineBallotSubmission(
                row.getSubmissionId(), row.getPackageId(), row.getElectorateItemId(), row.getTenantId(),
                row.getAccountId(), row.getUid(), row.getOpid(), row.getIdempotencyKey(), row.getPackageHash(),
                row.getChoiceManifestHash(), row.getConfirmationHash(),
                OnlineBallotSubmission.Status.valueOf(row.getStatus()), row.getSubmittedAt(),
                mapper.selectSubmissionItems(row.getSubmissionId()).stream().map(this::toDomain).toList());
    }

    private OnlineBallotSubmission.Item toDomain(OnlineBallotSubmissionItemRow row) {
        return new OnlineBallotSubmission.Item(
                row.getSubmissionItemId(), row.getSubmissionId(), row.getSubjectId(),
                VoteChoice.fromDbValue(row.getChoice()), row.getUnifiedBallotId(), row.getItemConfirmationHash());
    }

    private OnlinePaperAssistanceRequestRow toRow(OnlinePaperAssistanceRequest domain) {
        OnlinePaperAssistanceRequestRow row = new OnlinePaperAssistanceRequestRow();
        row.setRequestId(domain.requestId());
        row.setPackageId(domain.packageId());
        row.setElectorateItemId(domain.electorateItemId());
        row.setTenantId(domain.tenantId());
        row.setAccountId(domain.accountId());
        row.setUid(domain.uid());
        row.setOpid(domain.opid());
        row.setStatus(domain.status().name());
        row.setRequestedAt(domain.requestedAt());
        row.setFulfilledAt(domain.fulfilledAt());
        row.setWithdrawnAt(domain.withdrawnAt());
        row.setPaperDeliveryId(domain.paperDeliveryId());
        return row;
    }

    private OnlinePaperAssistanceRequest toDomain(OnlinePaperAssistanceRequestRow row) {
        return new OnlinePaperAssistanceRequest(
                row.getRequestId(), row.getPackageId(), row.getElectorateItemId(), row.getTenantId(),
                row.getAccountId(), row.getUid(), row.getOpid(),
                OnlinePaperAssistanceRequest.Status.valueOf(row.getStatus()), row.getRequestedAt(),
                row.getFulfilledAt(), row.getWithdrawnAt(), row.getPaperDeliveryId());
    }
}
