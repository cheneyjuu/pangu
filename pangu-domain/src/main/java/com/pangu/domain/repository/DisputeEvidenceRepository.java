package com.pangu.domain.repository;

import com.pangu.domain.model.dispute.DisputeEvidence;

import java.util.List;

/**
 * 异议证据附属表仓储端口。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/DisputeEvidenceRepositoryImpl}。
 */
public interface DisputeEvidenceRepository {

    /** 新增证据；返回带主键的值对象。 */
    DisputeEvidence insert(DisputeEvidence evidence);

    /** 列出某 dispute 的所有证据。 */
    List<DisputeEvidence> findByDisputeId(Long disputeId);
}
