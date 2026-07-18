// 关联业务：读写小区维修征询规则备案版本并锁定唯一当前有效规则。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.RepairDecisionRuleRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RepairDecisionRuleMapper {

    RepairDecisionRuleRow findActive(@Param("tenantId") Long tenantId);

    RepairDecisionRuleRow findById(@Param("ruleId") Long ruleId, @Param("tenantId") Long tenantId);

    List<RepairDecisionRuleRow> listByTenant(@Param("tenantId") Long tenantId);

    int supersedeActive(@Param("tenantId") Long tenantId);

    int insert(RepairDecisionRuleRow row);
}
