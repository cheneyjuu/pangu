// 关联业务：将业主大会议事规则领域对象映射到版本与审计表，隔离 JSON 和 MyBatis 细节。
package com.pangu.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleAudit;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleFieldConfirmation;
import com.pangu.domain.repository.OwnersAssemblyRuleRepository;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyRuleAuditRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyRuleFieldConfirmationRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyRuleRow;
import com.pangu.infrastructure.persistence.mapper.OwnersAssemblyRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OwnersAssemblyRuleRepositoryImpl implements OwnersAssemblyRuleRepository {

    private final OwnersAssemblyRuleMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<OwnersAssemblyRule> findActive(Long tenantId) {
        return Optional.ofNullable(mapper.findActive(tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<OwnersAssemblyRule> findById(Long ruleId, Long tenantId) {
        return Optional.ofNullable(mapper.findById(ruleId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<OwnersAssemblyRule> findByIdForUpdate(Long ruleId, Long tenantId) {
        return Optional.ofNullable(mapper.findByIdForUpdate(ruleId, tenantId)).map(this::toDomain);
    }

    @Override
    public List<OwnersAssemblyRule> listByTenant(Long tenantId) {
        return mapper.listByTenant(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public void lockTenantRules(Long tenantId) {
        mapper.lockTenantRules(tenantId);
    }

    @Override
    public OwnersAssemblyRule insert(OwnersAssemblyRule rule) {
        OwnersAssemblyRuleRow row = toRow(rule);
        mapper.insert(row);
        return findById(row.getRuleId(), row.getTenantId()).orElseThrow();
    }

    @Override
    public int updateDraft(OwnersAssemblyRule rule) {
        return mapper.updateDraft(toRow(rule));
    }

    @Override
    public int submitForConfirmation(Long ruleId, Long tenantId, Long accountId, Long userId) {
        return mapper.submitForConfirmation(ruleId, tenantId, accountId, userId);
    }

    @Override
    public void insertFieldConfirmation(OwnersAssemblyRuleFieldConfirmation confirmation) {
        mapper.insertFieldConfirmation(toFieldConfirmationRow(confirmation));
    }

    @Override
    public List<OwnersAssemblyRuleFieldConfirmation> listFieldConfirmations(
            Long ruleId, Long tenantId, String configurationSha256) {
        return mapper.listFieldConfirmations(ruleId, tenantId, configurationSha256).stream()
                .map(this::toFieldConfirmationDomain)
                .toList();
    }

    @Override
    public int confirmField(Long ruleId,
                            Long tenantId,
                            String configurationSha256,
                            OwnersAssemblyRuleConfiguration.RuleConfigurationField field,
                            Long accountId,
                            Long userId,
                            String committeePosition) {
        return mapper.confirmField(
                ruleId, tenantId, configurationSha256, field.name(), accountId, userId, committeePosition);
    }

    @Override
    public int supersedeActive(Long tenantId) {
        return mapper.supersedeActive(tenantId);
    }

    @Override
    public int activate(Long ruleId, Long tenantId, Long accountId, Long userId) {
        return mapper.activate(ruleId, tenantId, accountId, userId);
    }

    @Override
    public OwnersAssemblyRuleAudit appendAudit(OwnersAssemblyRuleAudit audit) {
        OwnersAssemblyRuleAuditRow row = toAuditRow(audit);
        mapper.insertAudit(row);
        return toAuditDomain(row);
    }

    @Override
    public List<OwnersAssemblyRuleAudit> listAudits(Long ruleId, Long tenantId) {
        return mapper.listAudits(ruleId, tenantId).stream().map(this::toAuditDomain).toList();
    }

    private OwnersAssemblyRule toDomain(OwnersAssemblyRuleRow row) {
        return new OwnersAssemblyRule(
                row.getRuleId(),
                row.getTenantId(),
                row.getRuleName(),
                row.getRuleVersion(),
                row.getEffectiveDate(),
                row.getChangeReason(),
                configurationFromJson(row.getConfigurationJson()),
                row.getConfigurationSha256(),
                row.getObjectKey(),
                row.getOriginalFileName(),
                row.getContentType(),
                row.getFileSize(),
                row.getEtag(),
                row.getSha256(),
                OwnersAssemblyRule.Status.valueOf(row.getStatus()),
                row.getDraftedByAccountId(),
                row.getDraftedByUserId(),
                row.getSubmittedByAccountId(),
                row.getSubmittedByUserId(),
                row.getSubmittedAt(),
                row.getActivatedByAccountId(),
                row.getActivatedByUserId(),
                row.getActivatedAt(),
                row.getCreateTime(),
                row.getUpdateTime());
    }

    private OwnersAssemblyRuleRow toRow(OwnersAssemblyRule rule) {
        OwnersAssemblyRuleRow row = new OwnersAssemblyRuleRow();
        row.setRuleId(rule.ruleId());
        row.setTenantId(rule.tenantId());
        row.setRuleName(rule.ruleName());
        row.setRuleVersion(rule.ruleVersion());
        row.setEffectiveDate(rule.effectiveDate());
        row.setChangeReason(rule.changeReason());
        row.setConfigurationJson(configurationToJson(rule.configuration()));
        row.setConfigurationSha256(rule.configurationSha256());
        row.setObjectKey(rule.objectKey());
        row.setOriginalFileName(rule.originalFileName());
        row.setContentType(rule.contentType());
        row.setFileSize(rule.fileSize());
        row.setEtag(rule.etag());
        row.setSha256(rule.sha256());
        row.setStatus(rule.status().name());
        row.setDraftedByAccountId(rule.draftedByAccountId());
        row.setDraftedByUserId(rule.draftedByUserId());
        row.setSubmittedByAccountId(rule.submittedByAccountId());
        row.setSubmittedByUserId(rule.submittedByUserId());
        row.setSubmittedAt(rule.submittedAt());
        row.setActivatedByAccountId(rule.activatedByAccountId());
        row.setActivatedByUserId(rule.activatedByUserId());
        row.setActivatedAt(rule.activatedAt());
        row.setCreateTime(rule.createTime());
        row.setUpdateTime(rule.updateTime());
        return row;
    }

    private OwnersAssemblyRuleAudit toAuditDomain(OwnersAssemblyRuleAuditRow row) {
        return new OwnersAssemblyRuleAudit(
                row.getAuditId(),
                row.getRuleId(),
                row.getTenantId(),
                OwnersAssemblyRuleAudit.EventType.valueOf(row.getEventType()),
                row.getConfigurationSha256(),
                row.getChangeReason(),
                row.getActorAccountId(),
                row.getActorUserId(),
                row.getActorRoleKey(),
                row.getActorCommitteePosition(),
                row.getCreateTime());
    }

    private OwnersAssemblyRuleAuditRow toAuditRow(OwnersAssemblyRuleAudit audit) {
        OwnersAssemblyRuleAuditRow row = new OwnersAssemblyRuleAuditRow();
        row.setAuditId(audit.auditId());
        row.setRuleId(audit.ruleId());
        row.setTenantId(audit.tenantId());
        row.setEventType(audit.eventType().name());
        row.setConfigurationSha256(audit.configurationSha256());
        row.setChangeReason(audit.changeReason());
        row.setActorAccountId(audit.actorAccountId());
        row.setActorUserId(audit.actorUserId());
        row.setActorRoleKey(audit.actorRoleKey());
        row.setActorCommitteePosition(audit.actorCommitteePosition());
        row.setCreateTime(audit.createTime());
        return row;
    }

    private OwnersAssemblyRuleFieldConfirmation toFieldConfirmationDomain(
            OwnersAssemblyRuleFieldConfirmationRow row) {
        return new OwnersAssemblyRuleFieldConfirmation(
                row.getConfirmationId(),
                row.getRuleId(),
                row.getTenantId(),
                row.getConfigurationSha256(),
                OwnersAssemblyRuleConfiguration.RuleConfigurationField.valueOf(row.getFieldKey()),
                row.getSourcePageNumber(),
                row.getSourceClause(),
                OwnersAssemblyRuleFieldConfirmation.Status.valueOf(row.getStatus()),
                row.getConfirmedByAccountId(),
                row.getConfirmedByUserId(),
                row.getConfirmedByCommitteePosition(),
                row.getConfirmedAt(),
                row.getCreateTime(),
                row.getUpdateTime());
    }

    private OwnersAssemblyRuleFieldConfirmationRow toFieldConfirmationRow(
            OwnersAssemblyRuleFieldConfirmation confirmation) {
        OwnersAssemblyRuleFieldConfirmationRow row = new OwnersAssemblyRuleFieldConfirmationRow();
        row.setConfirmationId(confirmation.confirmationId());
        row.setRuleId(confirmation.ruleId());
        row.setTenantId(confirmation.tenantId());
        row.setConfigurationSha256(confirmation.configurationSha256());
        row.setFieldKey(confirmation.field().name());
        row.setSourcePageNumber(confirmation.sourcePageNumber());
        row.setSourceClause(confirmation.sourceClause());
        row.setStatus(confirmation.status().name());
        row.setConfirmedByAccountId(confirmation.confirmedByAccountId());
        row.setConfirmedByUserId(confirmation.confirmedByUserId());
        row.setConfirmedByCommitteePosition(confirmation.confirmedByCommitteePosition());
        row.setConfirmedAt(confirmation.confirmedAt());
        row.setCreateTime(confirmation.createTime());
        row.setUpdateTime(confirmation.updateTime());
        return row;
    }

    private OwnersAssemblyRuleConfiguration configurationFromJson(String json) {
        try {
            return objectMapper.readValue(json, OwnersAssemblyRuleConfiguration.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("业主大会议事规则配置数据无法读取", ex);
        }
    }

    private String configurationToJson(OwnersAssemblyRuleConfiguration configuration) {
        try {
            return objectMapper.writeValueAsString(configuration);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("业主大会议事规则配置数据无法保存", ex);
        }
    }
}
