// 关联业务：定义业主大会会前事项、材料、公示安排、送达和投票记录的持久化端口。
package com.pangu.domain.repository;

import com.pangu.domain.model.assembly.OwnersAssemblyDeliveryRecord;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleSnapshot;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.assembly.OwnersAssemblySubjectDraft;
import com.pangu.domain.model.assembly.OwnersAssemblyVoteRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OwnersAssemblyRepository {

    OwnersAssemblySession insertSession(OwnersAssemblySession session);

    Optional<OwnersAssemblySession> findSession(Long sessionId, Long tenantId);

    Optional<OwnersAssemblySession> findSessionForUpdate(Long sessionId, Long tenantId);

    List<OwnersAssemblySession> listSessions(Long tenantId);

    int updateSessionStatus(Long sessionId, Long tenantId, String status);

    OwnersAssemblyPackage insertPackage(OwnersAssemblyPackage ballotPackage);

    OwnersAssemblyRuleSnapshot insertRuleSnapshot(OwnersAssemblyRuleSnapshot ruleSnapshot);

    Optional<OwnersAssemblyRuleSnapshot> findRuleSnapshotBySession(Long sessionId, Long tenantId);

    Optional<OwnersAssemblyRuleSnapshot> findRuleSnapshot(Long ruleSnapshotId, Long tenantId);

    Optional<OwnersAssemblyPackage> findPackage(Long packageId, Long tenantId);

    Optional<OwnersAssemblyPackage> findPackageForUpdate(Long packageId, Long tenantId);

    Optional<OwnersAssemblyPackage> findLatestPackageBySession(Long sessionId, Long tenantId);

    Optional<OwnersAssemblyPackage> findPackageBySubjectId(Long subjectId);

    void linkSubject(Long packageId, Long tenantId, Long subjectId);

    List<Long> listSubjectIds(Long packageId, Long tenantId);

    OwnersAssemblySubjectDraft insertSubjectDraft(OwnersAssemblySubjectDraft draft);

    List<OwnersAssemblySubjectDraft> listSubjectDrafts(Long sessionId, Long tenantId);

    OwnersAssemblyMaterial insertMaterial(OwnersAssemblyMaterial material);

    Optional<OwnersAssemblyMaterial> findMaterial(Long materialId, Long sessionId, Long tenantId);

    List<OwnersAssemblyMaterial> listMaterials(Long sessionId, Long tenantId);

    /** 返回正式表决包确认时锁定的可公开材料，不能以会次全部草稿材料替代。 */
    List<OwnersAssemblyMaterial> listPackageMaterials(Long packageId, Long tenantId);

    /** 将本会次已核验材料关联至正式表决包，作为后续公示与 C 端披露的唯一材料清单。 */
    void linkPackageMaterial(Long packageId, Long tenantId, Long materialId);

    int lockPackage(Long packageId,
                    Long tenantId,
                    String packageHash,
                    Instant publicNoticeStartAt,
                    Instant publicNoticeEndAt,
                    Long lockedByUserId);

    int markPackageVoting(Long packageId, Long tenantId);

    int markPackageSettled(Long packageId, Long tenantId);

    OwnersAssemblyDeliveryRecord insertDelivery(OwnersAssemblyDeliveryRecord delivery);

    boolean deliveryExists(Long packageId, Long tenantId, Long opid, Long uid, String deliveryChannel);

    OwnersAssemblyVoteRecord insertVoteRecord(OwnersAssemblyVoteRecord voteRecord);

    Optional<OwnersAssemblyVoteRecord> findActiveVoteRecord(Long subjectId, Long opid);

    /** 仅返回当前业主是否已参与纸质表决及首次参与时间，不返回任何选择内容。 */
    Optional<Instant> findOwnerParticipationAt(Long packageId, Long tenantId, Long uid);

    int invalidateVoteRecordByVoteId(Long voteId, Long invalidatedByVoteId, String invalidReason);

    boolean allSubjectsPassed(Long packageId, Long tenantId);
}
