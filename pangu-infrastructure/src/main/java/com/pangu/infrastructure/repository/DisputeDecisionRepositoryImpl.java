package com.pangu.infrastructure.repository;

import com.pangu.domain.model.dispute.Decision;
import com.pangu.domain.model.dispute.DecisionKind;
import com.pangu.domain.repository.DisputeDecisionRepository;
import com.pangu.infrastructure.persistence.entity.DisputeDecisionRow;
import com.pangu.infrastructure.persistence.mapper.DisputeDecisionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link DisputeDecisionRepository} 默认实现。
 *
 * <p>{@link DuplicateKeyException}（{@code uk_decision_dispute_level} 唯一索引触发）
 * 转译为领域端口的 {@link DuplicateDecisionException}。
 */
@Repository
@RequiredArgsConstructor
public class DisputeDecisionRepositoryImpl implements DisputeDecisionRepository {

    private final DisputeDecisionMapper mapper;

    @Override
    public Decision insert(Decision decision) {
        DisputeDecisionRow row = toRow(decision);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new DuplicateDecisionException(
                    "uk_decision_dispute_level violated for disputeId=" + decision.disputeId()
                            + " level=" + decision.reviewLevel(), e);
        }
        return new Decision(row.getDecisionId(), decision.disputeId(), decision.reviewLevel(),
                decision.decidedByUserId(), decision.kind(), decision.content(),
                decision.docUrl(), decision.decidedAt());
    }

    @Override
    public List<Decision> findByDisputeId(Long disputeId) {
        return mapper.selectByDisputeId(disputeId).stream().map(this::toValueObject).toList();
    }

    private DisputeDecisionRow toRow(Decision d) {
        DisputeDecisionRow r = new DisputeDecisionRow();
        r.setDecisionId(d.decisionId());
        r.setDisputeId(d.disputeId());
        r.setReviewLevel(d.reviewLevel());
        r.setDecidedByUserId(d.decidedByUserId());
        r.setDecisionKind(d.kind().name());
        r.setDecisionContent(d.content());
        r.setDecisionDocUrl(d.docUrl());
        r.setDecidedAt(d.decidedAt());
        return r;
    }

    private Decision toValueObject(DisputeDecisionRow r) {
        return new Decision(r.getDecisionId(), r.getDisputeId(), r.getReviewLevel(),
                r.getDecidedByUserId(), DecisionKind.valueOf(r.getDecisionKind()),
                r.getDecisionContent(), r.getDecisionDocUrl(), r.getDecidedAt());
    }
}
