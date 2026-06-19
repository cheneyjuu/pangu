package com.pangu.domain.repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 公示差分审计仓储端口（Hexagonal Port）。
 *
 * <p>application 层通过本接口落 {@code t_disclosure_audit_compare} 行——纯写入，
 * 不暴露聚合根（没有领域行为可言）。读 / 列表查询本期不交付。
 */
public interface DisclosureCompareRepository {

    /** 查询某对 (prev, curr) 是否已审计过；幂等性兜底。 */
    Optional<CompareRecord> findByPair(Long prevSnapshotId, Long currSnapshotId);

    /** 写入一条审计差分记录。 */
    CompareRecord insert(CompareRecord record);

    /**
     * 审计记录值对象（与 t_disclosure_audit_compare 行 1:1）。
     * 不进 domain.model 包，因为它没有任何领域行为，仅供 repository 端到端搬运。
     */
    record CompareRecord(
            Long compareId,
            Long tenantId,
            Long prevSnapshotId,
            Long currSnapshotId,
            String diffJson,
            int writeCount,
            int readCount,
            int noChangeCount,
            Long auditedByUserId,
            Instant auditedAt,
            Instant createTime) {
    }
}
