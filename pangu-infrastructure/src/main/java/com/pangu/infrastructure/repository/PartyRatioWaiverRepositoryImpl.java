package com.pangu.infrastructure.repository;

import com.pangu.domain.model.waiver.PartyRatioWaiver;
import com.pangu.domain.model.waiver.WaiverStatus;
import com.pangu.domain.repository.PartyRatioWaiverRepository;
import com.pangu.infrastructure.persistence.entity.PartyRatioWaiverRow;
import com.pangu.infrastructure.persistence.mapper.PartyRatioWaiverMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link PartyRatioWaiverRepository} 默认实现：MyBatis row ↔ 聚合根双向翻译。
 *
 * <p>关键约定：
 * <ul>
 *   <li>{@link DuplicateKeyException}（部分唯一索引 {@code uidx_waiver_active_per_subject} 触发）
 *       转译为领域端口的 {@link PartyRatioWaiverRepository.DuplicateActiveWaiverException}；</li>
 *   <li>乐观锁失败（{@code update} 影响行数为 0）转译为
 *       {@link PartyRatioWaiverRepository.OptimisticLockException}；</li>
 *   <li>聚合根 → row 的 status 序列化使用 {@link WaiverStatus#getDbValue()}。</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class PartyRatioWaiverRepositoryImpl implements PartyRatioWaiverRepository {

    private final PartyRatioWaiverMapper mapper;

    @Override
    public Optional<PartyRatioWaiver> findActiveBySubjectIdForUpdate(Long subjectId) {
        return Optional.ofNullable(mapper.selectActiveBySubjectIdForUpdate(subjectId)).map(this::toAggregate);
    }

    @Override
    public Optional<PartyRatioWaiver> findByIdForUpdate(Long waiverId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(waiverId)).map(this::toAggregate);
    }

    @Override
    public Optional<PartyRatioWaiver> findById(Long waiverId) {
        return Optional.ofNullable(mapper.selectById(waiverId)).map(this::toAggregate);
    }

    @Override
    public PartyRatioWaiver insert(PartyRatioWaiver waiver) {
        PartyRatioWaiverRow row = toRow(waiver);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new DuplicateActiveWaiverException(
                    "uidx_waiver_active_per_subject violated for subjectId=" + waiver.getSubjectId(), e);
        }
        // 主键回填到聚合根
        waiver.setWaiverId(row.getWaiverId());
        return waiver;
    }

    @Override
    public void update(PartyRatioWaiver waiver) {
        PartyRatioWaiverRow row = toRow(waiver);
        int affected;
        try {
            affected = mapper.update(row);
        } catch (DuplicateKeyException e) {
            // 状态翻转为活跃 + 同议题已有另一活跃 waiver（极端边界，如人工撤销中再度激活）
            throw new DuplicateActiveWaiverException(
                    "Concurrent active waiver detected on update for subjectId=" + waiver.getSubjectId(), e);
        }
        if (affected == 0) {
            throw new OptimisticLockException(
                    "Optimistic lock failed for waiverId=" + waiver.getWaiverId()
                            + " expectedVersion=" + waiver.getVersion());
        }
        waiver.setVersion(waiver.getVersion() + 1);
    }

    // ===== row ↔ aggregate translators =====

    private PartyRatioWaiverRow toRow(PartyRatioWaiver w) {
        PartyRatioWaiverRow r = new PartyRatioWaiverRow();
        r.setWaiverId(w.getWaiverId());
        r.setSubjectId(w.getSubjectId());
        r.setTenantId(w.getTenantId());
        r.setInitiatorUserId(w.getInitiatorUserId());
        r.setRequestedRatio(w.getRequestedRatio());
        r.setPartyPoolSize(w.getPartyPoolSize());
        r.setTotalEligibleSize(w.getTotalEligibleSize());
        r.setReasonText(w.getReasonText());
        r.setReasonEvidenceKeys(w.getReasonEvidenceKeys());
        r.setStatus(w.getStatus().getDbValue());
        r.setCommitteeApprover(w.getCommitteeApprover());
        r.setCommitteeApprovalAt(w.getCommitteeApprovalAt());
        r.setCommitteeOpinion(w.getCommitteeOpinion());
        r.setCommitteeRejectReasonCode(w.getCommitteeRejectReasonCode());
        r.setCommitteeRejectEvidenceJson(w.getCommitteeRejectEvidenceJson());
        r.setStreetApprover(w.getStreetApprover());
        r.setStreetApprovalAt(w.getStreetApprovalAt());
        r.setStreetOpinion(w.getStreetOpinion());
        r.setStreetRejectReasonCode(w.getStreetRejectReasonCode());
        r.setStreetRejectEvidenceJson(w.getStreetRejectEvidenceJson());
        r.setAppliedAt(w.getAppliedAt());
        r.setLocalPayloadHash(w.getLocalPayloadHash());
        r.setLocalPayloadLockedAt(w.getLocalPayloadLockedAt());
        r.setBlockchainTxHash(w.getBlockchainTxHash());
        r.setBlockchainChainProvider(w.getBlockchainChainProvider());
        r.setChainAttestStatus(w.getChainAttestStatus());
        r.setChainAttestAttempts(w.getChainAttestAttempts());
        r.setChainAttestLastError(w.getChainAttestLastError());
        r.setChainConfirmedAt(w.getChainConfirmedAt());
        r.setVersion(w.getVersion());
        return r;
    }

    private PartyRatioWaiver toAggregate(PartyRatioWaiverRow r) {
        return PartyRatioWaiver.rehydrate(
                r.getWaiverId(), r.getSubjectId(), r.getTenantId(), r.getInitiatorUserId(),
                r.getRequestedRatio(), r.getPartyPoolSize(), r.getTotalEligibleSize(),
                r.getReasonText(), r.getReasonEvidenceKeys(),
                WaiverStatus.fromDbValue(r.getStatus()),
                r.getCommitteeApprover(), r.getCommitteeApprovalAt(), r.getCommitteeOpinion(),
                r.getCommitteeRejectReasonCode(), r.getCommitteeRejectEvidenceJson(),
                r.getStreetApprover(), r.getStreetApprovalAt(), r.getStreetOpinion(),
                r.getStreetRejectReasonCode(), r.getStreetRejectEvidenceJson(),
                r.getAppliedAt(),
                r.getLocalPayloadHash(), r.getLocalPayloadLockedAt(),
                r.getBlockchainTxHash(), r.getBlockchainChainProvider(),
                r.getChainAttestStatus() == null ? 1 : r.getChainAttestStatus(),
                r.getChainAttestAttempts() == null ? 0 : r.getChainAttestAttempts(),
                r.getChainAttestLastError(),
                r.getChainConfirmedAt(),
                r.getVersion());
    }
}
