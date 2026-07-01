package com.pangu.domain.model.voting;

import com.pangu.domain.repository.VotingDenominatorReader.DenominatorTotals;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 投票进度纯领域计算服务（无状态、framework-light）。
 *
 * <p>职责：在「投票进行中（未结算）」场景下，依据与 {@link AbstractVotingEngine#settle} <strong>完全一致</strong>
 * 的去重 / 双 2/3 门槛口径，计算实时 {@link VotingProgress}：
 * <ul>
 *   <li>参与面积：按 {@code (uid, opid)} 去重累加 {@link VoteItem#getPropertyArea()}；</li>
 *   <li>参与人数：按 {@code uid} 去重计数；</li>
 *   <li>赞成面积 / 人数：在 {@code choice == SUPPORT} 子集上同法计算；</li>
 *   <li>法定门槛：面积与人数同时满足 {@code 3 * 参与 >= 2 * 总量}。</li>
 * </ul>
 *
 * <p>本服务<strong>不修改引擎</strong>、不落快照；实时值仅供看板用途，结算快照才是唯一法定结论。
 */
public class VotingProgressCalculator {

    /**
     * 计算实时进度。
     *
     * @param subject 议题（提供 status / scope / scopeReferenceId 等身份字段）
     * @param totals  分母总量（来自只读分母预览）
     * @param votes   有效投票原始列表（不要求预去重，本服务内部按引擎口径去重）
     * @return 实时进度快照（{@code settled=false}）
     */
    public VotingProgress compute(VotingSubject subject, DenominatorTotals totals, List<VoteItem> votes) {
        if (subject == null) {
            throw new IllegalArgumentException("subject must not be null");
        }
        if (totals == null) {
            throw new IllegalArgumentException("totals must not be null");
        }
        if (votes == null) {
            throw new IllegalArgumentException("votes must not be null");
        }

        BigDecimal totalArea = totals.totalArea() == null ? BigDecimal.ZERO : totals.totalArea();
        long totalOwnerCount = totals.totalOwnerCount();

        Set<Long> participatingUids = new HashSet<>();
        Set<String> participatingUidOpid = new HashSet<>();
        BigDecimal participatingArea = BigDecimal.ZERO;

        Set<Long> supportUids = new HashSet<>();
        Set<String> supportUidOpid = new HashSet<>();
        BigDecimal supportArea = BigDecimal.ZERO;

        for (VoteItem vote : votes) {
            String uidOpid = vote.getUid() + "-" + vote.getOpid();
            if (participatingUidOpid.add(uidOpid)) {
                participatingArea = participatingArea.add(vote.getPropertyArea());
            }
            participatingUids.add(vote.getUid());

            if (vote.getChoice() == VoteChoice.SUPPORT) {
                if (supportUidOpid.add(uidOpid)) {
                    supportArea = supportArea.add(vote.getPropertyArea());
                }
                supportUids.add(vote.getUid());
            }
        }

        long participatingOwnerCount = participatingUids.size();
        boolean quorumSatisfied = checkQuorum(participatingArea, totalArea,
                participatingOwnerCount, totalOwnerCount);

        return new VotingProgress(
                subject.getSubjectId(),
                subject.getStatus(),
                subject.getScope(),
                subject.getScopeReferenceId(),
                totalArea,
                totalOwnerCount,
                participatingArea,
                participatingOwnerCount,
                supportArea,
                (long) supportUids.size(),
                quorumSatisfied,
                false,
                false,
                totals.denominatorSnapshotId(),
                totals.denominatorMerkleRoot());
    }

    /** 双 2/3 门槛：面积与人数同时满足 {@code 3 * 参与 >= 2 * 总量}（口径同引擎 checkQuorum）。 */
    private boolean checkQuorum(BigDecimal participatingArea, BigDecimal totalArea,
                                 long participatingOwnerCount, long totalOwnerCount) {
        if (totalArea.signum() <= 0 || totalOwnerCount <= 0) {
            return false;
        }
        boolean areaQuorum = participatingArea.multiply(new BigDecimal("3"))
                .compareTo(totalArea.multiply(new BigDecimal("2"))) >= 0;
        boolean ownerQuorum = participatingOwnerCount * 3 >= totalOwnerCount * 2;
        return areaQuorum && ownerQuorum;
    }
}
