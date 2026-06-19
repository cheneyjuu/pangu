package com.pangu.infrastructure.repository;

import com.pangu.domain.repository.DisclosureCompareRepository;
import com.pangu.infrastructure.persistence.entity.DisclosureCompareRow;
import com.pangu.infrastructure.persistence.mapper.DisclosureCompareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link DisclosureCompareRepository} 默认实现：纯 CRUD（仅写入 + 按对查询）。
 */
@Repository
@RequiredArgsConstructor
public class DisclosureCompareRepositoryImpl implements DisclosureCompareRepository {

    private final DisclosureCompareMapper mapper;

    @Override
    public Optional<CompareRecord> findByPair(Long prevSnapshotId, Long currSnapshotId) {
        DisclosureCompareRow row = mapper.selectByPair(prevSnapshotId, currSnapshotId);
        return Optional.ofNullable(row).map(this::toRecord);
    }

    @Override
    public CompareRecord insert(CompareRecord record) {
        DisclosureCompareRow row = toRow(record);
        mapper.insert(row);
        return toRecord(row);
    }

    private DisclosureCompareRow toRow(CompareRecord r) {
        DisclosureCompareRow row = new DisclosureCompareRow();
        row.setCompareId(r.compareId());
        row.setTenantId(r.tenantId());
        row.setPrevSnapshotId(r.prevSnapshotId());
        row.setCurrSnapshotId(r.currSnapshotId());
        row.setDiffJson(r.diffJson());
        row.setWriteCount(r.writeCount());
        row.setReadCount(r.readCount());
        row.setNoChangeCount(r.noChangeCount());
        row.setAuditedByUserId(r.auditedByUserId());
        row.setAuditedAt(r.auditedAt());
        row.setCreateTime(r.createTime());
        return row;
    }

    private CompareRecord toRecord(DisclosureCompareRow row) {
        return new CompareRecord(
                row.getCompareId(), row.getTenantId(),
                row.getPrevSnapshotId(), row.getCurrSnapshotId(),
                row.getDiffJson(),
                row.getWriteCount() == null ? 0 : row.getWriteCount(),
                row.getReadCount() == null ? 0 : row.getReadCount(),
                row.getNoChangeCount() == null ? 0 : row.getNoChangeCount(),
                row.getAuditedByUserId(), row.getAuditedAt(), row.getCreateTime());
    }
}
