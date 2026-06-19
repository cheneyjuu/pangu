package com.pangu.infrastructure.repository;

import com.pangu.domain.model.dispute.Dispute;
import com.pangu.domain.model.dispute.DisputeKind;
import com.pangu.domain.model.dispute.DisputeStatus;
import com.pangu.domain.repository.DisputeRepository;
import com.pangu.infrastructure.persistence.entity.OwnerDisputeRow;
import com.pangu.infrastructure.persistence.mapper.OwnerDisputeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link DisputeRepository} 默认实现：MyBatis row ↔ {@link Dispute} 聚合根双向翻译。
 *
 * <ul>
 *   <li>{@code disputeKind} / {@code status} 用枚举名直接保存；</li>
 *   <li>{@code businessPayload} 在 SQL 端用 {@code CAST(? AS JSONB)}，Java 侧用 String 透传；</li>
 *   <li>乐观锁失败（{@code update} 影响行数为 0）→ {@link OptimisticLockException}。</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DisputeRepositoryImpl implements DisputeRepository {

    private final OwnerDisputeMapper mapper;

    @Override
    public Optional<Dispute> findById(Long disputeId) {
        return Optional.ofNullable(mapper.selectById(disputeId)).map(this::toAggregate);
    }

    @Override
    public Optional<Dispute> findByIdForUpdate(Long disputeId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(disputeId)).map(this::toAggregate);
    }

    @Override
    public List<Dispute> findByOwner(Long tenantId, Long ownerId, int limit, int offset) {
        return mapper.selectByOwner(tenantId, ownerId, limit, offset)
                .stream().map(this::toAggregate).toList();
    }

    @Override
    public List<Dispute> findForJurisdiction(Long tenantId, Integer reviewLevel, String status,
                                              int limit, int offset) {
        return mapper.selectForJurisdiction(tenantId, reviewLevel, status, limit, offset)
                .stream().map(this::toAggregate).toList();
    }

    @Override
    public Dispute insert(Dispute dispute) {
        OwnerDisputeRow row = toRow(dispute);
        mapper.insert(row);
        dispute.setDisputeId(row.getDisputeId());
        return dispute;
    }

    @Override
    public void update(Dispute dispute) {
        OwnerDisputeRow row = toRow(dispute);
        int affected = mapper.update(row);
        if (affected == 0) {
            throw new OptimisticLockException(
                    "Optimistic lock failed for disputeId=" + dispute.getDisputeId()
                            + " expectedVersion=" + dispute.getVersion());
        }
        dispute.setVersion(dispute.getVersion() + 1);
    }

    // ===== row ↔ aggregate translators =====

    private OwnerDisputeRow toRow(Dispute d) {
        OwnerDisputeRow r = new OwnerDisputeRow();
        r.setDisputeId(d.getDisputeId());
        r.setTenantId(d.getTenantId());
        r.setRaisedByOwnerId(d.getRaisedByOwnerId());
        r.setDisputeKind(d.getDisputeKind().name());
        r.setRelatedEntityType(d.getRelatedEntityType());
        r.setRelatedEntityId(d.getRelatedEntityId());
        r.setCurrentReviewLevel(d.getCurrentReviewLevel());
        r.setStatus(d.getStatus().name());
        r.setBusinessPayload(d.getBusinessPayloadJson() == null ? "{}" : d.getBusinessPayloadJson());
        r.setRaisedAt(d.getRaisedAt());
        r.setEscalatedAt(d.getEscalatedAt());
        r.setClosedAt(d.getClosedAt());
        r.setLitigationOutcome(d.getLitigationOutcome());
        r.setLitigationJudgementUrl(d.getLitigationJudgementUrl());
        r.setVersion(d.getVersion());
        return r;
    }

    private Dispute toAggregate(OwnerDisputeRow r) {
        return Dispute.rehydrate(
                r.getDisputeId(), r.getTenantId(), r.getRaisedByOwnerId(),
                DisputeKind.valueOf(r.getDisputeKind()),
                r.getRelatedEntityType(), r.getRelatedEntityId(),
                r.getCurrentReviewLevel(),
                DisputeStatus.valueOf(r.getStatus()),
                r.getBusinessPayload(),
                r.getRaisedAt(), r.getEscalatedAt(), r.getClosedAt(),
                r.getLitigationOutcome(), r.getLitigationJudgementUrl(),
                r.getVersion());
    }
}
