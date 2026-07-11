package com.pangu.domain.repository;

import com.pangu.domain.model.assembly.OwnersAssemblyDeliveryRecord;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.assembly.OwnersAssemblyVoteRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OwnersAssemblyRepository {

    OwnersAssemblySession insertSession(OwnersAssemblySession session);

    Optional<OwnersAssemblySession> findSession(Long sessionId, Long tenantId);

    OwnersAssemblyPackage insertPackage(OwnersAssemblyPackage ballotPackage);

    Optional<OwnersAssemblyPackage> findPackage(Long packageId, Long tenantId);

    Optional<OwnersAssemblyPackage> findPackageForUpdate(Long packageId, Long tenantId);

    Optional<OwnersAssemblyPackage> findPackageBySubjectId(Long subjectId);

    void linkSubject(Long packageId, Long tenantId, Long subjectId);

    List<Long> listSubjectIds(Long packageId, Long tenantId);

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

    int invalidateVoteRecordByVoteId(Long voteId, Long invalidatedByVoteId, String invalidReason);

    boolean allSubjectsPassed(Long packageId, Long tenantId);
}
