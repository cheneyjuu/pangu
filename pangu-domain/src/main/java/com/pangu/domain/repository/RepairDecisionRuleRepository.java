// 关联业务：持久化小区维修征询规则版本，并为项目治理读取唯一当前有效规则。
package com.pangu.domain.repository;

import com.pangu.domain.model.repair.RepairDecisionRule;

import java.util.List;
import java.util.Optional;

public interface RepairDecisionRuleRepository {

    Optional<RepairDecisionRule> findActive(Long tenantId);

    Optional<RepairDecisionRule> findById(Long ruleId, Long tenantId);

    List<RepairDecisionRule> listByTenant(Long tenantId);

    int supersedeActive(Long tenantId);

    RepairDecisionRule insert(RepairDecisionRule rule);
}
