package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.repository.VoteItemRepository;
import com.pangu.infrastructure.persistence.entity.VoteItemRow;
import com.pangu.infrastructure.persistence.mapper.VoteItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link VoteItemRepository} 默认实现。
 *
 * <p>双重去重交给 {@code AbstractVotingEngine.settle}，本层不在 SQL 内做去重以避免
 * 与领域去重规则脱钩；M3-2 起补 {@link #insert} 用于业主投票提交。
 */
@Repository
@RequiredArgsConstructor
public class VoteItemRepositoryImpl implements VoteItemRepository {

    private final VoteItemMapper mapper;

    @Override
    public List<VoteItem> findValidVotes(Long subjectId) {
        return mapper.selectBySubjectId(subjectId).stream().map(this::toDomain).toList();
    }

    @Override
    public long insert(Long subjectId, VoteItem item, String signatureHash) {
        VoteItemRow row = new VoteItemRow();
        row.setSubjectId(subjectId);
        row.setOpid(item.getOpid());
        row.setUid(item.getUid());
        row.setTargetId(item.getTargetId());
        row.setPropertyArea(item.getPropertyArea());
        row.setChoice(item.getChoice() == null ? null : choiceToDb(item.getChoice()));
        row.setSignatureHash(signatureHash);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new DuplicateVoteException(
                    "重复投票 subjectId=" + subjectId + " opid=" + item.getOpid()
                            + " targetId=" + item.getTargetId(), e);
        }
        return row.getVoteId();
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

    private int choiceToDb(VoteChoice c) {
        return switch (c) {
            case SUPPORT -> 1;
            case AGAINST -> 2;
            case ABSTAIN -> 3;
        };
    }
}

