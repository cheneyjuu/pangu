package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.OwnersAssemblyDeliveryRecordRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyPackageRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblySessionRow;
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

    int insertPackage(OwnersAssemblyPackageRow row);

    OwnersAssemblyPackageRow findPackage(@Param("packageId") Long packageId,
                                         @Param("tenantId") Long tenantId);

    OwnersAssemblyPackageRow findPackageForUpdate(@Param("packageId") Long packageId,
                                                  @Param("tenantId") Long tenantId);

    OwnersAssemblyPackageRow findPackageBySubjectId(@Param("subjectId") Long subjectId);

    int insertSubjectLink(@Param("packageId") Long packageId,
                          @Param("tenantId") Long tenantId,
                          @Param("subjectId") Long subjectId);

    List<Long> listSubjectIds(@Param("packageId") Long packageId,
                              @Param("tenantId") Long tenantId);

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

    int invalidateVoteRecordByVoteId(@Param("voteId") Long voteId,
                                     @Param("invalidatedByVoteId") Long invalidatedByVoteId,
                                     @Param("invalidReason") String invalidReason);

    boolean allSubjectsPassed(@Param("packageId") Long packageId,
                              @Param("tenantId") Long tenantId);
}
