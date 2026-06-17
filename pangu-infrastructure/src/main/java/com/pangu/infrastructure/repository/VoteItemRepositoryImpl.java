package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.repository.VoteItemRepository;
import com.pangu.infrastructure.persistence.entity.VoteItemRow;
import com.pangu.infrastructure.persistence.mapper.VoteItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link VoteItemRepository} 默认实现：仅做 row → 领域翻译。
 *
 * <p>双重去重交给 {@code AbstractVotingEngine.settle}，本层不在 SQL 内做去重以避免
 * 与领域去重规则脱钩。
 */
@Repository
@RequiredArgsConstructor
public class VoteItemRepositoryImpl implements VoteItemRepository {

    private final VoteItemMapper mapper;

    @Override
    public List<VoteItem> findValidVotes(Long subjectId) {
        return mapper.selectBySubjectId(subjectId).stream().map(this::toDomain).toList();
    }

    private VoteItem toDomain(VoteItemRow r) {
        return VoteItem.builder()
                .opid(r.getOpid())
                .uid(r.getUid())
                .targetId(r.getTargetId())
                .propertyArea(r.getPropertyArea())
                .choice(r.getChoice() == null ? null : choiceFromDb(r.getChoice()))
                .build();
    }

    private VoteChoice choiceFromDb(int dbValue) {
        return switch (dbValue) {
            case 1 -> VoteChoice.SUPPORT;
            case 2 -> VoteChoice.AGAINST;
            case 3 -> VoteChoice.ABSTAIN;
            default -> throw new IllegalArgumentException("Unknown VoteChoice dbValue: " + dbValue);
        };
    }
}
