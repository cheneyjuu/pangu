// 关联业务：定义正式表决包、冻结名册、送达、票据和执行审计的统一持久化端口。
package com.pangu.domain.repository;

import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VotingBallotRecord;
import com.pangu.domain.model.voting.VotingDeliveryRecord;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingNonResponseDerivation;
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

    /**
     * 按业务已经冻结的精确房屋集合读取当前有效名册；只供上层在冻结前核对业务快照，
     * 不允许以楼栋或小区实时查询替代该集合。
     */
    List<VotingElectorateSnapshot.Candidate> listElectorateCandidatesByRoomIds(
            Long tenantId, List<Long> roomIds);

    VotingElectorateSnapshot insertElectorateSnapshot(VotingElectorateSnapshot snapshot);

    Optional<VotingElectorateSnapshot> findElectorateSnapshot(Long snapshotId, Long tenantId);

    Optional<VotingElectorateSnapshot.Item> findElectorateItem(Long packageId, Long tenantId, Long opid);

    /** 串行化同一表决包、同一专有部分的跨渠道收票，避免数据库到达顺序替代冻结规则。 */
    void lockElectorateItem(Long packageId, Long tenantId, Long electorateItemId);

    /** 使用聚合当前状态按 version 乐观更新，并由数据库将 version 加一。 */
    int updatePackage(VotingExecutionPackage ballotPackage);

    /** 为每个事项写入与包级名册完全同源的结算分母快照。 */
    Long insertSubjectDenominatorSnapshot(
            Long subjectId, VotingScope scope, Long scopeReferenceId, VotingElectorateSnapshot snapshot);

    VotingDeliveryRecord insertDelivery(VotingDeliveryRecord delivery);

    boolean deliveryExists(Long packageId, Long tenantId, Long electorateItemId, VoteChannel channel);

    /** 结算时读取本次冻结名册的全部有效送达证据，按送达主键稳定排序。 */
    List<VotingDeliveryRecord> listDeliveries(Long packageId, Long tenantId);

    VotingBallotRecord insertBallot(VotingBallotRecord ballot);

    Optional<VotingBallotRecord> findActiveBallot(Long subjectId, Long electorateItemId, Long tenantId);

    int invalidateBallot(Long ballotId, String invalidReason, Instant invalidatedAt);

    /** 一次读取事项全部有效票据，避免按名册逐户查询。 */
    List<VotingBallotRecord> listActiveBallots(Long subjectId, Long tenantId);

    /** 原子写入一事项的不可变未反馈认定记录。 */
    void insertNonResponseDerivations(List<VotingNonResponseDerivation> derivations);

    /** 审计读取一事项的未反馈认定记录，不包含实际票。 */
    List<VotingNonResponseDerivation> listNonResponseDerivations(Long subjectId, Long tenantId);

    long countAudits(Long packageId, Long tenantId, String eventType);

    void insertAudit(Long packageId,
                     Long tenantId,
                     String eventType,
                     String fromStatus,
                     String toStatus,
                     Long actorUserId,
                     String detailJson,
                     Instant occurredAt);
}
