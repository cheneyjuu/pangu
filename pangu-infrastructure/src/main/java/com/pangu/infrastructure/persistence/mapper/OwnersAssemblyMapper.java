// 关联业务：映射业主大会会前事项、材料、公示安排、纸质送达和投票记录的数据库访问。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.OwnersAssemblyDeliveryRecordRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyMaterialRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyPackageRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyRuleSnapshotRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblySessionRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblySubjectDraftRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyVoteRecordRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OwnersAssemblyMapper {

    int insertSession(OwnersAssemblySessionRow row);

    OwnersAssemblySessionRow findSession(@Param("sessionId") Long sessionId,
                                         @Param("tenantId") Long tenantId);

    OwnersAssemblySessionRow findSessionForUpdate(@Param("sessionId") Long sessionId,
                                                  @Param("tenantId") Long tenantId);

    List<OwnersAssemblySessionRow> listSessions(@Param("tenantId") Long tenantId);

    int updateSessionStatus(@Param("sessionId") Long sessionId,
                            @Param("tenantId") Long tenantId,
                            @Param("status") String status);

    int insertPackage(OwnersAssemblyPackageRow row);

    int insertRuleSnapshot(OwnersAssemblyRuleSnapshotRow row);

    OwnersAssemblyRuleSnapshotRow findRuleSnapshotBySession(@Param("sessionId") Long sessionId,
                                                             @Param("tenantId") Long tenantId);

    OwnersAssemblyRuleSnapshotRow findRuleSnapshot(@Param("ruleSnapshotId") Long ruleSnapshotId,
                                                    @Param("tenantId") Long tenantId);

    OwnersAssemblyPackageRow findPackage(@Param("packageId") Long packageId,
                                         @Param("tenantId") Long tenantId);

    OwnersAssemblyPackageRow findPackageForUpdate(@Param("packageId") Long packageId,
                                                  @Param("tenantId") Long tenantId);

    OwnersAssemblyPackageRow findLatestPackageBySession(@Param("sessionId") Long sessionId,
                                                         @Param("tenantId") Long tenantId);

    OwnersAssemblyPackageRow findPackageBySubjectId(@Param("subjectId") Long subjectId);

    int insertSubjectLink(@Param("packageId") Long packageId,
                          @Param("tenantId") Long tenantId,
                          @Param("subjectId") Long subjectId);

    List<Long> listSubjectIds(@Param("packageId") Long packageId,
                              @Param("tenantId") Long tenantId);

    int insertSubjectDraft(OwnersAssemblySubjectDraftRow row);

    List<OwnersAssemblySubjectDraftRow> listSubjectDrafts(@Param("sessionId") Long sessionId,
                                                          @Param("tenantId") Long tenantId);

    int insertMaterial(OwnersAssemblyMaterialRow row);

    OwnersAssemblyMaterialRow findMaterial(@Param("materialId") Long materialId,
                                           @Param("sessionId") Long sessionId,
                                           @Param("tenantId") Long tenantId);

    List<OwnersAssemblyMaterialRow> listMaterials(@Param("sessionId") Long sessionId,
                                                   @Param("tenantId") Long tenantId);

    List<OwnersAssemblyMaterialRow> listPackageMaterials(@Param("packageId") Long packageId,
                                                          @Param("tenantId") Long tenantId);

    int insertPackageMaterial(@Param("packageId") Long packageId,
                              @Param("tenantId") Long tenantId,
                              @Param("materialId") Long materialId);

    int lockPackage(@Param("packageId") Long packageId,
                    @Param("tenantId") Long tenantId,
                    @Param("packageHash") String packageHash,
                    @Param("publicNoticeStartAt") LocalDateTime publicNoticeStartAt,
                    @Param("publicNoticeEndAt") LocalDateTime publicNoticeEndAt,
                    @Param("lockedByUserId") Long lockedByUserId);

    int markPackageVoting(@Param("packageId") Long packageId,
                          @Param("tenantId") Long tenantId);

    int markPackageSettled(@Param("packageId") Long packageId,
                           @Param("tenantId") Long tenantId);

    int insertDelivery(OwnersAssemblyDeliveryRecordRow row);

    boolean deliveryExists(@Param("packageId") Long packageId,
                           @Param("tenantId") Long tenantId,
                           @Param("opid") Long opid,
                           @Param("uid") Long uid,
                           @Param("deliveryChannel") String deliveryChannel);

    int insertVoteRecord(OwnersAssemblyVoteRecordRow row);

    OwnersAssemblyVoteRecordRow findActiveVoteRecord(@Param("subjectId") Long subjectId,
                                                     @Param("opid") Long opid);

    LocalDateTime findOwnerParticipationAt(@Param("packageId") Long packageId,
                                            @Param("tenantId") Long tenantId,
                                            @Param("uid") Long uid);

    int invalidateVoteRecordByVoteId(@Param("voteId") Long voteId,
                                     @Param("invalidatedByVoteId") Long invalidatedByVoteId,
                                     @Param("invalidReason") String invalidReason);

    boolean allSubjectsPassed(@Param("packageId") Long packageId,
                              @Param("tenantId") Long tenantId);
}
