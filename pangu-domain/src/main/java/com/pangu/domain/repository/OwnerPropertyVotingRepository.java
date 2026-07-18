package com.pangu.domain.repository;

import com.pangu.domain.model.asset.OwnerPropertyVotingView;

import java.util.List;
import java.util.Optional;

/**
 * 业主房产投票视图仓储端口（M3-2 引入）。
 *
 * <p>不并入 {@code PropertyGateway} 是因为后者职责为"统计欠费 / 列举所有产权"，
 * 与投票场景需要的"按 opid 精准取一行 + uid/building/area 全字段"形态不一致。
 */
public interface OwnerPropertyVotingRepository {

    /** 根据 opid 取出投票视图。返回的 tenantId / uid 由 application 层做归属与租户校验。 */
    Optional<OwnerPropertyVotingView> findByOpid(Long opid);

    /** 列出某业主在某租户下涉及的全部楼栋 ID（"我的议题" ABAC 用）。 */
    List<Long> findBuildingIdsByUid(Long uid, Long tenantId);

    /**
     * 列出当前业主在本小区的房产投票视图。
     *
     * <p>业主大会 C 端用它区分“可查看已发布材料”和“当前是否具备有效投票资格”，
     * 不以任意一处房产存在就推定该业主可投票。
     */
    List<OwnerPropertyVotingView> listByUid(Long uid, Long tenantId);
}
