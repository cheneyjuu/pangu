package com.pangu.infrastructure.voting;

import com.pangu.domain.model.voting.Denominator;
import com.pangu.domain.model.voting.VotingDenominatorResolver;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.infrastructure.crypto.MerkleHashCalculator;
import com.pangu.infrastructure.persistence.entity.DenominatorItemRow;
import com.pangu.infrastructure.persistence.entity.DenominatorSnapshotRow;
import com.pangu.infrastructure.persistence.mapper.VotingDenominatorSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link VotingDenominatorResolver} 默认实现：基于 c_owner_property 的双重去重 + 快照落定。
 *
 * <p>核心契约：
 * <ul>
 *   <li>面积按 room_id 去重——使用窗口函数 ROW_NUMBER 选出每个 room 的代表 owner，
 *       从根上消除一户多房 / 共有产权被乘以 N 倍的「应过未过」事故；</li>
 *   <li>人头按 primary_owner_uid 去重——优先 is_voting_delegate=1，次按 opid 升序兜底；</li>
 *   <li>同步落定 {@code t_voting_denominator_snapshot}（议题维度 upsert）+
 *       {@code t_voting_denominator_item_snapshot}（行级明细，先删后插，幂等）；</li>
 *   <li>aggregate_hash 为 row_hash 的 Merkle root，提供「行级可还原 + 整体防篡改」凭证。</li>
 * </ul>
 *
 * <p>本期不支持 UNIT scope：当前 schema 无 unit_id，强行按 room_id 分段会引入数据编码假设；
 * UNIT 范围议题需先在迁移中补 {@code c_owner_property.unit_id} 后再开放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultVotingDenominatorResolver implements VotingDenominatorResolver {

    private final VotingDenominatorSnapshotMapper snapshotMapper;

    @Override
    @Transactional
    public Denominator resolve(VotingSubject subject) {
        if (subject != null && subject.getSubjectId() != null) {
            DenominatorSnapshotRow existing = snapshotMapper.selectSnapshotBySubjectId(subject.getSubjectId());
            if (existing != null) {
                return new Denominator(existing.getTotalArea(), existing.getTotalOwnerCount(),
                        existing.getAggregateHash(), existing.getSnapshotId());
            }
        }
        VotingScope scope = validateAndNormalizeScope(subject);

        List<DenominatorItemRow> items = snapshotMapper.selectDenominatorItems(
                subject.getTenantId(), scope.getDbValue(), subject.getScopeReferenceId());
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException(
                    "投票分母解析失败：未查询到可计入的房产数据 subjectId=" + subject.getSubjectId()
                            + ", scope=" + scope + ", scopeReferenceId=" + subject.getScopeReferenceId());
        }

        // 1. 行级 hash + 累计面积；人头去重在 SQL 层已保证 primary_owner_uid 仍可能跨 room 重复，
        //    故再次以 Set 语义统计 distinct uid。
        BigDecimal totalArea = BigDecimal.ZERO;
        for (DenominatorItemRow item : items) {
            item.setRowHash(computeRowHash(item));
            totalArea = totalArea.add(item.getCertifiedArea());
        }
        long totalOwnerCount = items.stream()
                .map(DenominatorItemRow::getPrimaryOwnerUid)
                .distinct()
                .count();

        if (totalArea.signum() <= 0 || totalOwnerCount <= 0) {
            throw new IllegalStateException(
                    "投票分母解析失败：totalArea=" + totalArea + ", totalOwnerCount=" + totalOwnerCount
                            + " 必须均为正数 subjectId=" + subject.getSubjectId());
        }

        // 2. Merkle root：行级 hash 顺序按楼栋 + room 升序（SQL ORDER BY），保证可重放
        List<String> rowHashes = items.stream()
                .map(DenominatorItemRow::getRowHash)
                .collect(Collectors.toList());
        String aggregateHash = MerkleHashCalculator.merkleRoot(rowHashes);

        // 3. 议题维度幂等 upsert + 行级明细先删后插
        Long snapshotId = snapshotMapper.insertSnapshotIfAbsent(
                subject.getSubjectId(), scope.getDbValue(), subject.getScopeReferenceId(),
                totalArea, totalOwnerCount, (long) items.size(), aggregateHash);
        if (snapshotId == null) {
            DenominatorSnapshotRow existing = snapshotMapper.selectSnapshotBySubjectId(subject.getSubjectId());
            if (existing == null) {
                throw new IllegalStateException("分母快照并发插入后读取失败 subjectId=" + subject.getSubjectId());
            }
            return new Denominator(existing.getTotalArea(), existing.getTotalOwnerCount(),
                    existing.getAggregateHash(), existing.getSnapshotId());
        }
        snapshotMapper.deleteItemsBySnapshotId(snapshotId);
        snapshotMapper.insertItems(snapshotId, items);

        log.info("分母快照落定 subjectId={} scope={} totalArea={} totalOwnerCount={} itemCount={} merkle={}",
                subject.getSubjectId(), scope, totalArea, totalOwnerCount, items.size(), aggregateHash);

        return new Denominator(totalArea, totalOwnerCount, aggregateHash, snapshotId);
    }

    private VotingScope validateAndNormalizeScope(VotingSubject subject) {
        if (subject == null || subject.getSubjectId() == null || subject.getTenantId() == null) {
            throw new IllegalArgumentException("subject / subjectId / tenantId 不可为空");
        }
        VotingScope scope = subject.getScope() != null ? subject.getScope() : VotingScope.COMMUNITY;
        if (scope == VotingScope.UNIT) {
            // 显式拒绝：当前 schema 无 unit_id，按 room_id 推导属于未文档化假设
            throw new IllegalStateException(
                    "UNIT 范围分母解析未实现：需先在 c_owner_property 增加 unit_id 字段后再开放 subjectId="
                            + subject.getSubjectId());
        }
        if (scope == VotingScope.REPAIR_ALLOCATION) {
            throw new IllegalStateException(
                    "维修方案分母必须由统一表决包的精确房屋名册冻结，不能从实时产权关系重新计算 subjectId="
                            + subject.getSubjectId());
        }
        if (scope == VotingScope.BUILDING && subject.getScopeReferenceId() == null) {
            throw new IllegalArgumentException(
                    "BUILDING 范围必须提供 scopeReferenceId(=building_id) subjectId=" + subject.getSubjectId());
        }
        return scope;
    }

    /**
     * 行级 SHA256：roomId|buildingId|certifiedArea(2 位小数)|primaryOwnerUid|eligibilityFlag。
     *
     * <p>面积固定保留 2 位小数避免 BigDecimal toString 在 trailing zero 上的不稳定，
     * 保证同一行多次重算结果一致。
     */
    private String computeRowHash(DenominatorItemRow item) {
        String input = item.getRoomId()
                + "|" + item.getBuildingId()
                + "|" + item.getCertifiedArea().setScale(2, RoundingMode.HALF_UP).toPlainString()
                + "|" + item.getPrimaryOwnerUid()
                + "|" + item.getEligibilityFlag();
        return MerkleHashCalculator.sha256Hex(input);
    }
}
