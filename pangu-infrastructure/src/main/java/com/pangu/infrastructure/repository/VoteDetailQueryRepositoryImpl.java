package com.pangu.infrastructure.repository;

import com.pangu.domain.common.Page;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.repository.VoteDetailQueryRepository;
import com.pangu.infrastructure.persistence.mapper.VoteItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link VoteDetailQueryRepository} 默认实现。
 *
 * <p>先 count 后 select，组装统一 {@link Page}；scope 转 dbValue 并拒绝 UNIT（同分母只读读取器）。
 * 不挂 {@code @DataScope}——边界由 tenant 过滤 + endpoint {@code @PreAuthorize} 保证（同 M4-1 范式）。
 */
@Repository
@RequiredArgsConstructor
public class VoteDetailQueryRepositoryImpl implements VoteDetailQueryRepository {

    private final VoteItemMapper voteItemMapper;

    @Override
    public Page<VoteDetailRow> page(Long tenantId, Long subjectId, VotingScope scope,
                                    Long scopeReferenceId, int page, int size) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId 不可为空");
        }
        VotingScope effectiveScope = scope != null ? scope : VotingScope.COMMUNITY;
        if (effectiveScope == VotingScope.UNIT) {
            throw new IllegalStateException(
                    "UNIT 范围逐户明细未实现：需先在 c_owner_property 增加 unit_id 字段后再开放");
        }
        if (effectiveScope == VotingScope.BUILDING && scopeReferenceId == null) {
            throw new IllegalArgumentException("BUILDING 范围必须提供 scopeReferenceId(=building_id)");
        }

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int scopeDb = effectiveScope.getDbValue();

        long total = voteItemMapper.countVoteDetailPage(tenantId, scopeDb, scopeReferenceId);
        if (total == 0) {
            return new Page<>(List.of(), 0, safePage, safeSize);
        }

        int offset = (safePage - 1) * safeSize;
        List<VoteDetailRow> rows = voteItemMapper
                .selectVoteDetailPage(tenantId, subjectId, scopeDb, scopeReferenceId, safeSize, offset)
                .stream()
                .map(this::toDomain)
                .toList();
        return new Page<>(rows, total, safePage, safeSize);
    }

    private VoteDetailRow toDomain(com.pangu.infrastructure.persistence.entity.VoteDetailRow r) {
        boolean voted = r.getVoteId() != null;
        VoteChoice choice = r.getChoice() == null ? null : VoteChoice.fromDbValue(r.getChoice());
        return new VoteDetailRow(
                r.getOpid(),
                r.getUid(),
                r.getBuildingId(),
                r.getRoomId(),
                r.getPropertyArea(),
                r.getAuthLevel(),
                voted,
                choice,
                r.getVotedAt());
    }
}
