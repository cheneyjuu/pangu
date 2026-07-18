// 关联业务：把小区维修征询规则领域模型映射到备案版本表。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.repair.RepairDecisionRule;
import com.pangu.domain.model.repair.RepairProjectGovernance.NonResponseRule;
import com.pangu.domain.repository.RepairDecisionRuleRepository;
import com.pangu.infrastructure.persistence.entity.RepairDecisionRuleRow;
import com.pangu.infrastructure.persistence.mapper.RepairDecisionRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RepairDecisionRuleRepositoryImpl implements RepairDecisionRuleRepository {

    private final RepairDecisionRuleMapper mapper;

    @Override
    public Optional<RepairDecisionRule> findActive(Long tenantId) {
        return Optional.ofNullable(mapper.findActive(tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<RepairDecisionRule> findById(Long ruleId, Long tenantId) {
        return Optional.ofNullable(mapper.findById(ruleId, tenantId)).map(this::toDomain);
    }

    @Override
    public List<RepairDecisionRule> listByTenant(Long tenantId) {
        return mapper.listByTenant(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public int supersedeActive(Long tenantId) {
        return mapper.supersedeActive(tenantId);
    }

    @Override
    public RepairDecisionRule insert(RepairDecisionRule rule) {
        RepairDecisionRuleRow row = toRow(rule);
        mapper.insert(row);
        return findById(row.getRuleId(), rule.tenantId()).orElseThrow();
    }

    private RepairDecisionRule toDomain(RepairDecisionRuleRow row) {
        return new RepairDecisionRule(
                row.getRuleId(), row.getTenantId(), row.getRuleName(), row.getRuleVersion(),
                row.getEffectiveAt(), row.getDeliveryRule(), NonResponseRule.valueOf(row.getNonResponseRule()),
                row.getObjectKey(), row.getOriginalFileName(), row.getContentType(), row.getFileSize(),
                row.getEtag(), row.getSha256(), RepairDecisionRule.Status.valueOf(row.getStatus()),
                row.getRegisteredByAccountId(), row.getRegisteredByUserId(),
                row.getCreateTime(), row.getUpdateTime());
    }

    private RepairDecisionRuleRow toRow(RepairDecisionRule rule) {
        RepairDecisionRuleRow row = new RepairDecisionRuleRow();
        row.setRuleId(rule.ruleId());
        row.setTenantId(rule.tenantId());
        row.setRuleName(rule.ruleName());
        row.setRuleVersion(rule.ruleVersion());
        row.setEffectiveAt(rule.effectiveAt());
        row.setDeliveryRule(rule.deliveryRule());
        row.setNonResponseRule(rule.nonResponseRule().name());
        row.setObjectKey(rule.objectKey());
        row.setOriginalFileName(rule.originalFileName());
        row.setContentType(rule.contentType());
        row.setFileSize(rule.fileSize());
        row.setEtag(rule.etag());
        row.setSha256(rule.sha256());
        row.setStatus(rule.status().name());
        row.setRegisteredByAccountId(rule.registeredByAccountId());
        row.setRegisteredByUserId(rule.registeredByUserId());
        return row;
    }
}
