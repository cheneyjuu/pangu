// 关联业务：执行线上阅读确认、整包提交和纸质协助请求的数据库读写。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.OnlineBallotSubmissionItemRow;
import com.pangu.infrastructure.persistence.entity.OnlineBallotSubmissionRow;
import com.pangu.infrastructure.persistence.entity.OnlinePaperAssistanceRequestRow;
import com.pangu.infrastructure.persistence.entity.OnlineVotingAcknowledgementRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface OnlineVotingMapper {

    int insertAcknowledgement(OnlineVotingAcknowledgementRow row);

    OnlineVotingAcknowledgementRow selectAcknowledgement(
            @Param("packageId") Long packageId,
            @Param("electorateItemId") Long electorateItemId,
            @Param("tenantId") Long tenantId);

    int insertSubmission(OnlineBallotSubmissionRow row);

    int insertSubmissionItem(OnlineBallotSubmissionItemRow row);

    OnlineBallotSubmissionRow selectSubmission(
            @Param("packageId") Long packageId,
            @Param("electorateItemId") Long electorateItemId,
            @Param("tenantId") Long tenantId);

    OnlineBallotSubmissionRow selectSubmissionByIdempotencyKey(
            @Param("packageId") Long packageId,
            @Param("tenantId") Long tenantId,
            @Param("idempotencyKey") String idempotencyKey);

    long countAcceptedSubmissions(@Param("packageId") Long packageId,
                                  @Param("tenantId") Long tenantId);

    List<OnlineBallotSubmissionItemRow> selectSubmissionItems(@Param("submissionId") Long submissionId);

    int insertPaperAssistanceRequest(OnlinePaperAssistanceRequestRow row);

    OnlinePaperAssistanceRequestRow selectPaperAssistanceRequest(
            @Param("packageId") Long packageId,
            @Param("electorateItemId") Long electorateItemId,
            @Param("tenantId") Long tenantId);

    List<OnlinePaperAssistanceRequestRow> selectPaperAssistanceRequests(
            @Param("packageId") Long packageId,
            @Param("tenantId") Long tenantId);

    int reactivatePaperAssistanceRequest(
            @Param("requestId") Long requestId,
            @Param("tenantId") Long tenantId,
            @Param("requestedAt") Instant requestedAt);

    int withdrawPaperAssistanceRequest(
            @Param("requestId") Long requestId,
            @Param("tenantId") Long tenantId,
            @Param("withdrawnAt") Instant withdrawnAt);

    int fulfillPaperAssistanceRequest(
            @Param("requestId") Long requestId,
            @Param("tenantId") Long tenantId,
            @Param("paperDeliveryId") Long paperDeliveryId,
            @Param("fulfilledAt") Instant fulfilledAt);
}
