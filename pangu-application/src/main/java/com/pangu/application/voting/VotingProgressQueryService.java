package com.pangu.application.voting;

import com.pangu.domain.common.Page;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingProgress;
import com.pangu.domain.model.voting.VotingProgressCalculator;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VoteDetailQueryRepository;
import com.pangu.domain.repository.VoteItemRepository;
import com.pangu.domain.repository.VotingDenominatorReader;
import com.pangu.domain.repository.VotingDenominatorReader.DenominatorTotals;
import com.pangu.domain.repository.VotingDenominatorReader.FrozenDenominatorSnapshot;
import com.pangu.domain.repository.VotingResultRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 议题进度 / 逐户明细只读查询服务（M4-2）。
 *
 * <p>与写侧 {@link VotingApplicationService} 分离：本服务全程只读，服务管理端进度看板。
 *
 * <p>进度数据来源分支：
 * <ul>
 *   <li>{@code SETTLED} → 直接读法定结算快照（participating/quorum/passed 精确；
 *       support 因快照表无该列而置空）；</li>
 *   <li>其余状态 → 实时计算：只读分母预览 + 有效投票 → {@link VotingProgressCalculator}
 *       （口径与结算引擎一致，但非法定值）。</li>
 * </ul>
 *
 * <p>跨租户防护：subject 的 tenantId 与调用方上下文不符时，按 {@code SUBJECT_NOT_FOUND}
 * 处理（不泄漏「存在但无权」信号，防跨租户探测）。
 */
@Service
@RequiredArgsConstructor
public class VotingProgressQueryService {

    private final VotingSubjectRepository subjectRepository;
    private final VotingResultRepository resultRepository;
    private final VoteItemRepository voteItemRepository;
    private final VotingDenominatorReader denominatorReader;
    private final VoteDetailQueryRepository voteDetailQueryRepository;
    private final VotingProgressCalculator progressCalculator;

    /** 查询议题双过半进度（实时或法定快照）。 */
    @Transactional(readOnly = true)
    public VotingProgress queryProgress(Long subjectId, Long tenantId) {
        VotingSubject subject = loadOwnedSubject(subjectId, tenantId);

        if (subject.getStatus() == SubjectStatus.SETTLED) {
            VotingResultRepository.Snapshot snapshot = resultRepository.findBySubjectId(subjectId)
                    .orElseThrow(() -> new VotingApplicationException(
                            VotingApplicationException.Reason.SUBJECT_ALREADY_SETTLED,
                            "议题状态为 SETTLED 但结果快照缺失，数据损坏 subjectId=" + subjectId));
            Optional<FrozenDenominatorSnapshot> frozen = denominatorReader.findFrozenSnapshot(subjectId);
            return new VotingProgress(
                    subject.getSubjectId(),
                    subject.getStatus(),
                    subject.getScope(),
                    subject.getScopeReferenceId(),
                    snapshot.totalArea(),
                    snapshot.totalOwnerCount(),
                    snapshot.participatingArea(),
                    snapshot.participatingOwnerCount(),
                    null,
                    null,
                    snapshot.quorumSatisfied(),
                    true,
                    snapshot.passed(),
                    snapshot.denominatorSnapshotId(),
                    frozen.map(FrozenDenominatorSnapshot::merkleRoot).orElse(null));
        }

        DenominatorTotals totals = denominatorReader.findFrozenSnapshot(subjectId)
                .map(frozen -> new DenominatorTotals(
                        frozen.totalArea(), frozen.totalOwnerCount(), frozen.snapshotId(), frozen.merkleRoot()))
                .orElseGet(() -> denominatorReader.previewTotals(
                        tenantId, subject.getScope(), subject.getScopeReferenceId()));
        List<VoteItem> votes = voteItemRepository.findValidVotes(subjectId);
        return progressCalculator.compute(subject, totals, votes);
    }

    /** 分页查询议题逐户投票明细（应投房产全量铺开，含未投）。 */
    @Transactional(readOnly = true)
    public Page<VoteDetailQueryRepository.VoteDetailRow> pageVoteDetails(
            Long subjectId, Long tenantId, int page, int size) {
        VotingSubject subject = loadOwnedSubject(subjectId, tenantId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return voteDetailQueryRepository.page(
                tenantId, subjectId, subject.getScope(), subject.getScopeReferenceId(), safePage, safeSize);
    }

    /** 加载并校验议题归属当前租户；不存在或跨租户一律按 SUBJECT_NOT_FOUND。 */
    private VotingSubject loadOwnedSubject(Long subjectId, Long tenantId) {
        VotingSubject subject = subjectRepository.findById(subjectId)
                .filter(s -> s.getTenantId() != null && s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + subjectId));
        return subject;
    }
}
