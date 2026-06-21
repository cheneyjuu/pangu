package com.pangu.infrastructure.voting;

import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.repository.VotingDenominatorReader;
import com.pangu.infrastructure.persistence.entity.DenominatorItemRow;
import com.pangu.infrastructure.persistence.mapper.VotingDenominatorSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link VotingDenominatorReader} 默认实现：只读复用 {@code selectDenominatorItems} 的 room_id 双重去重查询。
 *
 * <p>与写侧 {@link DefaultVotingDenominatorResolver} 共用同一条去重 SQL，但<strong>不落快照、不算 Merkle</strong>，
 * 故进度看板的实时分母与最终结算分母口径零偏差（仅在投票期内房产数据变动时可能出现可解释的微差）。
 *
 * <p>UNIT scope 拒绝（镜像 resolver：当前 schema 无 unit_id）；范围内无数据返回零总量（进度页空态）。
 */
@Component
@RequiredArgsConstructor
public class VotingDenominatorReaderImpl implements VotingDenominatorReader {

    private final VotingDenominatorSnapshotMapper snapshotMapper;

    @Override
    public DenominatorTotals previewTotals(Long tenantId, VotingScope scope, Long scopeReferenceId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId 不可为空");
        }
        VotingScope effectiveScope = scope != null ? scope : VotingScope.COMMUNITY;
        if (effectiveScope == VotingScope.UNIT) {
            throw new IllegalStateException(
                    "UNIT 范围分母预览未实现：需先在 c_owner_property 增加 unit_id 字段后再开放");
        }
        if (effectiveScope == VotingScope.BUILDING && scopeReferenceId == null) {
            throw new IllegalArgumentException("BUILDING 范围必须提供 scopeReferenceId(=building_id)");
        }

        List<DenominatorItemRow> items = snapshotMapper.selectDenominatorItems(
                tenantId, effectiveScope.getDbValue(), scopeReferenceId);
        if (items == null || items.isEmpty()) {
            return new DenominatorTotals(BigDecimal.ZERO, 0L);
        }

        BigDecimal totalArea = BigDecimal.ZERO;
        for (DenominatorItemRow item : items) {
            totalArea = totalArea.add(item.getCertifiedArea());
        }
        long totalOwnerCount = items.stream()
                .map(DenominatorItemRow::getPrimaryOwnerUid)
                .distinct()
                .count();

        return new DenominatorTotals(totalArea, totalOwnerCount);
    }
}
