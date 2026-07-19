// 关联业务：定义正式表决包、冻结名册、送达、票据和执行审计的统一持久化端口。
package com.pangu.domain.repository;

import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VotingBallotRecord;
import com.pangu.domain.model.voting.VotingDeliveryRecord;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingScope;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VotingExecutionRepository {

    VotingExecutionPackage insertPackage(VotingExecutionPackage ballotPackage);

    Optional<VotingExecutionPackage> findPackage(Long packageId, Long tenantId);

    Optional<VotingExecutionPackage> findPackageForUpdate(Long packageId, Long tenantId);

    Optional<VotingExecutionPackage> findPackageBySubjectId(Long subjectId);

    void attachSubject(Long packageId, Long tenantId, Long subjectId);

    List<Long> listSubjectIds(Long packageId, Long tenantId);

    List<VotingElectorateSnapshot.Candidate> listElectorateCandidates(
            Long tenantId, VotingScope scope, Long scopeReferenceId);

    VotingElectorateSnapshot insertElectorateSnapshot(VotingElectorateSnapshot snapshot);

    Optional<VotingElectorateSnapshot> findElectorateSnapshot(Long snapshotId, Long tenantId);

    Optional<VotingElectorateSnapshot.Item> findElectorateItem(Long packageId, Long tenantId, Long opid);

    /** 使用聚合当前状态按 version 乐观更新，并由数据库将 version 加一。 */
    int updatePackage(VotingExecutionPackage ballotPackage);

    /** 为每个事项写入与包级名册完全同源的结算分母快照。 */
    Long insertSubjectDenominatorSnapshot(
            Long subjectId, VotingScope scope, Long scopeReferenceId, VotingElectorateSnapshot snapshot);

    VotingDeliveryRecord insertDelivery(VotingDeliveryRecord delivery);

    boolean deliveryExists(Long packageId, Long tenantId, Long electorateItemId, VoteChannel channel);

    VotingBallotRecord insertBallot(VotingBallotRecord ballot);

    void insertAudit(Long packageId,
                     Long tenantId,
                     String eventType,
                     String fromStatus,
                     String toStatus,
                     Long actorUserId,
                     String detailJson,
                     Instant occurredAt);
}
