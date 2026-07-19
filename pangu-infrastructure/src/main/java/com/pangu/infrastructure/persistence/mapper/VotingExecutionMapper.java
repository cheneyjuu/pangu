// 关联业务：执行正式表决包、冻结名册、送达、票据和审计的数据库读写。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.VotingBallotRecordRow;
import com.pangu.infrastructure.persistence.entity.VotingDeliveryRecordRow;
import com.pangu.infrastructure.persistence.entity.VotingElectorateCandidateRow;
import com.pangu.infrastructure.persistence.entity.VotingElectorateItemRow;
import com.pangu.infrastructure.persistence.entity.VotingElectorateSnapshotRow;
import com.pangu.infrastructure.persistence.entity.VotingExecutionPackageRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface VotingExecutionMapper {

    int insertPackage(VotingExecutionPackageRow row);

    VotingExecutionPackageRow selectPackage(@Param("packageId") Long packageId,
                                             @Param("tenantId") Long tenantId);

    VotingExecutionPackageRow selectPackageForUpdate(@Param("packageId") Long packageId,
                                                      @Param("tenantId") Long tenantId);

    VotingExecutionPackageRow selectPackageBySubjectId(@Param("subjectId") Long subjectId);

    int insertPackageSubject(@Param("packageId") Long packageId,
                             @Param("tenantId") Long tenantId,
                             @Param("subjectId") Long subjectId);

    List<Long> selectSubjectIds(@Param("packageId") Long packageId,
                                @Param("tenantId") Long tenantId);

    List<VotingElectorateCandidateRow> selectElectorateCandidates(
            @Param("tenantId") Long tenantId,
            @Param("scope") int scope,
            @Param("scopeReferenceId") Long scopeReferenceId);

    List<VotingElectorateCandidateRow> selectElectorateCandidatesByRoomIds(
            @Param("tenantId") Long tenantId,
            @Param("roomIds") List<Long> roomIds);

    int insertElectorateSnapshot(VotingElectorateSnapshotRow row);

    int insertElectorateItems(@Param("snapshotId") Long snapshotId,
                              @Param("items") List<VotingElectorateItemRow> items);

    VotingElectorateSnapshotRow selectElectorateSnapshot(@Param("snapshotId") Long snapshotId,
                                                         @Param("tenantId") Long tenantId);

    List<VotingElectorateItemRow> selectElectorateItems(@Param("snapshotId") Long snapshotId);

    VotingElectorateItemRow selectElectorateItemByOpid(@Param("packageId") Long packageId,
                                                       @Param("tenantId") Long tenantId,
                                                       @Param("opid") Long opid);

    int updatePackage(VotingExecutionPackageRow row);

    int insertDelivery(VotingDeliveryRecordRow row);

    boolean deliveryExists(@Param("packageId") Long packageId,
                           @Param("tenantId") Long tenantId,
                           @Param("electorateItemId") Long electorateItemId,
                           @Param("deliveryChannel") int deliveryChannel);

    int insertBallot(VotingBallotRecordRow row);

    VotingBallotRecordRow selectActiveBallot(@Param("subjectId") Long subjectId,
                                              @Param("electorateItemId") Long electorateItemId,
                                              @Param("tenantId") Long tenantId);

    long countAudits(@Param("packageId") Long packageId,
                     @Param("tenantId") Long tenantId,
                     @Param("eventType") String eventType);

    int insertAudit(@Param("packageId") Long packageId,
                    @Param("tenantId") Long tenantId,
                    @Param("eventType") String eventType,
                    @Param("fromStatus") String fromStatus,
                    @Param("toStatus") String toStatus,
                    @Param("actorUserId") Long actorUserId,
                    @Param("detailJson") String detailJson,
                    @Param("occurredAt") Instant occurredAt);
}
