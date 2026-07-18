package com.pangu.infrastructure.repository;

import com.pangu.domain.model.disclosure.DisclosureStatus;
import com.pangu.domain.model.disclosure.DisclosureType;
import com.pangu.domain.model.disclosure.FinanceDisclosureSnapshot;
import com.pangu.domain.repository.FinanceDisclosureRepository;
import com.pangu.infrastructure.persistence.entity.FinanceDisclosureRow;
import com.pangu.infrastructure.persistence.mapper.FinanceDisclosureMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link FinanceDisclosureRepository} 默认实现：MyBatis row ↔ 聚合根双向翻译。
 *
 * <p>关键约定（与 GovernanceLockRepositoryImpl 保持一致）：
 * <ul>
 *   <li>{@link DuplicateKeyException}（{@code uk_disc_period} / {@code uidx_disc_latest} 唯一索引触发）
 *       转译为领域端口的 {@link FinanceDisclosureRepository.DuplicateSnapshotException}；</li>
 *   <li>乐观锁失败转译为 {@link FinanceDisclosureRepository.OptimisticLockException}；</li>
 *   <li>聚合根 ↔ row 的 status 序列化使用 {@link DisclosureStatus#getDbValue()}；
 *       disclosureType 直接保存枚举名。</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class FinanceDisclosureRepositoryImpl implements FinanceDisclosureRepository {

    private final FinanceDisclosureMapper mapper;

    @Override
    public Optional<FinanceDisclosureSnapshot> findById(Long snapshotId) {
        return Optional.ofNullable(mapper.selectById(snapshotId)).map(this::toAggregate);
    }

    @Override
    public Optional<FinanceDisclosureSnapshot> findByIdForUpdate(Long snapshotId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(snapshotId)).map(this::toAggregate);
    }

    @Override
    public Optional<FinanceDisclosureSnapshot> findLatestByPeriod(
            Long tenantId, DisclosureType disclosureType, String period) {
        FinanceDisclosureRow row = mapper.selectLatestByPeriod(tenantId, disclosureType.name(), period);
        return Optional.ofNullable(row).map(this::toAggregate);
    }

    @Override
    public Optional<FinanceDisclosureSnapshot> findLatestPublished(
            Long tenantId, DisclosureType disclosureType) {
        FinanceDisclosureRow row = mapper.selectLatestPublished(
                tenantId,
                disclosureType.name(),
                DisclosureStatus.PUBLISHED.getDbValue());
        return Optional.ofNullable(row).map(this::toAggregate);
    }

    @Override
    public FinanceDisclosureSnapshot insert(FinanceDisclosureSnapshot snapshot) {
        FinanceDisclosureRow row = toRow(snapshot);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new DuplicateSnapshotException(
                    "uk_disc_period / uidx_disc_latest violated for tenantId=" + snapshot.getTenantId()
                            + " type=" + snapshot.getDisclosureType()
                            + " period=" + snapshot.getPeriod()
                            + " statisticsVersion=" + snapshot.getStatisticsVersion(), e);
        }
        snapshot.setSnapshotId(row.getSnapshotId());
        return snapshot;
    }

    @Override
    public void update(FinanceDisclosureSnapshot snapshot) {
        FinanceDisclosureRow row = toRow(snapshot);
        int affected = mapper.update(row);
        if (affected == 0) {
            throw new OptimisticLockException(
                    "Optimistic lock failed for snapshotId=" + snapshot.getSnapshotId()
                            + " expectedVersion=" + snapshot.getVersion());
        }
        snapshot.setVersion(snapshot.getVersion() + 1);
    }

    // ===== row ↔ aggregate translators =====

    private FinanceDisclosureRow toRow(FinanceDisclosureSnapshot s) {
        FinanceDisclosureRow r = new FinanceDisclosureRow();
        r.setSnapshotId(s.getSnapshotId());
        r.setTenantId(s.getTenantId());
        r.setPeriod(s.getPeriod());
        r.setDisclosureType(s.getDisclosureType().name());
        r.setStatus(s.getStatus().getDbValue());
        r.setDataPayload(s.getDataPayload());
        r.setStatisticsVersion(s.getStatisticsVersion());
        r.setPayloadHash(s.getPayloadHash());
        r.setComposedByUserId(s.getComposedByUserId());
        r.setComposedAt(s.getComposedAt());
        r.setGovernanceLockId(s.getGovernanceLockId());
        r.setLockedAt(s.getLockedAt());
        r.setPublishedAt(s.getPublishedAt());
        r.setVersion(s.getVersion());
        return r;
    }

    private FinanceDisclosureSnapshot toAggregate(FinanceDisclosureRow r) {
        return FinanceDisclosureSnapshot.rehydrate(
                r.getSnapshotId(), r.getTenantId(), r.getPeriod(),
                DisclosureType.fromDbValue(r.getDisclosureType()),
                DisclosureStatus.fromDbValue(r.getStatus()),
                r.getDataPayload(), r.getStatisticsVersion(), r.getPayloadHash(),
                r.getComposedByUserId(), r.getComposedAt(),
                r.getGovernanceLockId(), r.getLockedAt(), r.getPublishedAt(),
                r.getVersion());
    }
}
