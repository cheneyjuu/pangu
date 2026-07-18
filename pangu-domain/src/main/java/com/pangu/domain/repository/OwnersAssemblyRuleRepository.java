// 关联业务：持久化业主大会议事规则版本、其状态迁移及关键审计事件。
package com.pangu.domain.repository;

import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleAudit;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleFieldConfirmation;

import java.util.List;
import java.util.Optional;

public interface OwnersAssemblyRuleRepository {

    Optional<OwnersAssemblyRule> findActive(Long tenantId);

    Optional<OwnersAssemblyRule> findById(Long ruleId, Long tenantId);

    Optional<OwnersAssemblyRule> findByIdForUpdate(Long ruleId, Long tenantId);

    List<OwnersAssemblyRule> listByTenant(Long tenantId);

    /** 锁定小区根记录，避免空规则表时两个确认操作并发产生两个有效版本。 */
    void lockTenantRules(Long tenantId);

    OwnersAssemblyRule insert(OwnersAssemblyRule rule);

    int updateDraft(OwnersAssemblyRule rule);

    int submitForConfirmation(Long ruleId, Long tenantId, Long accountId, Long userId);

    void insertFieldConfirmation(OwnersAssemblyRuleFieldConfirmation confirmation);

    List<OwnersAssemblyRuleFieldConfirmation> listFieldConfirmations(
            Long ruleId, Long tenantId, String configurationSha256);

    int confirmField(Long ruleId,
                     Long tenantId,
                     String configurationSha256,
                     OwnersAssemblyRuleConfiguration.RuleConfigurationField field,
                     Long accountId,
                     Long userId,
                     String committeePosition);

    int supersedeActive(Long tenantId);

    int activate(Long ruleId, Long tenantId, Long accountId, Long userId);

    OwnersAssemblyRuleAudit appendAudit(OwnersAssemblyRuleAudit audit);

    List<OwnersAssemblyRuleAudit> listAudits(Long ruleId, Long tenantId);
}
