// 关联业务：定义线上阅读确认、整包提交、本人回执和纸质协助请求的持久化端口。
package com.pangu.domain.repository;

import com.pangu.domain.model.voting.OnlineBallotSubmission;
import com.pangu.domain.model.voting.OnlinePaperAssistanceRequest;
import com.pangu.domain.model.voting.OnlineVotingAcknowledgement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OnlineVotingRepository {

    OnlineVotingAcknowledgement insertAcknowledgement(OnlineVotingAcknowledgement acknowledgement);

    Optional<OnlineVotingAcknowledgement> findAcknowledgement(
            Long packageId, Long electorateItemId, Long tenantId);

    OnlineBallotSubmission insertSubmission(OnlineBallotSubmission submission);

    void insertSubmissionItem(Long submissionId, OnlineBallotSubmission.Item item);

    Optional<OnlineBallotSubmission> findSubmission(Long packageId, Long electorateItemId, Long tenantId);

    Optional<OnlineBallotSubmission> findSubmissionByIdempotencyKey(
            Long packageId, Long tenantId, String idempotencyKey);

    long countAcceptedSubmissions(Long packageId, Long tenantId);

    OnlinePaperAssistanceRequest insertPaperAssistanceRequest(OnlinePaperAssistanceRequest request);

    Optional<OnlinePaperAssistanceRequest> findPaperAssistanceRequest(
            Long packageId, Long electorateItemId, Long tenantId);

    List<OnlinePaperAssistanceRequest> listPaperAssistanceRequests(Long packageId, Long tenantId);

    int reactivatePaperAssistanceRequest(Long requestId, Long tenantId, Instant requestedAt);

    int withdrawPaperAssistanceRequest(Long requestId, Long tenantId, Instant withdrawnAt);

    int fulfillPaperAssistanceRequest(
            Long requestId, Long tenantId, Long paperDeliveryId, Instant fulfilledAt);
}
