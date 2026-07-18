// 关联业务：读写业主大会议事规则版本并保证同一小区的启用替代过程可审计。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.OwnersAssemblyRuleAuditRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyRuleFieldConfirmationRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyRuleRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OwnersAssemblyRuleMapper {

    OwnersAssemblyRuleRow findActive(@Param("tenantId") Long tenantId);

    OwnersAssemblyRuleRow findById(@Param("ruleId") Long ruleId, @Param("tenantId") Long tenantId);

    OwnersAssemblyRuleRow findByIdForUpdate(@Param("ruleId") Long ruleId, @Param("tenantId") Long tenantId);

    List<OwnersAssemblyRuleRow> listByTenant(@Param("tenantId") Long tenantId);

    List<Long> lockTenantRules(@Param("tenantId") Long tenantId);

    int insert(OwnersAssemblyRuleRow row);

    int updateDraft(OwnersAssemblyRuleRow row);

    int submitForConfirmation(@Param("ruleId") Long ruleId,
                              @Param("tenantId") Long tenantId,
                              @Param("accountId") Long accountId,
                              @Param("userId") Long userId);

    int insertFieldConfirmation(OwnersAssemblyRuleFieldConfirmationRow row);

    List<OwnersAssemblyRuleFieldConfirmationRow> listFieldConfirmations(
            @Param("ruleId") Long ruleId,
            @Param("tenantId") Long tenantId,
            @Param("configurationSha256") String configurationSha256);

    int confirmField(@Param("ruleId") Long ruleId,
                     @Param("tenantId") Long tenantId,
                     @Param("configurationSha256") String configurationSha256,
                     @Param("fieldKey") String fieldKey,
                     @Param("accountId") Long accountId,
                     @Param("userId") Long userId,
                     @Param("committeePosition") String committeePosition);

    int supersedeActive(@Param("tenantId") Long tenantId);

    int activate(@Param("ruleId") Long ruleId,
                 @Param("tenantId") Long tenantId,
                 @Param("accountId") Long accountId,
                 @Param("userId") Long userId);

    int insertAudit(OwnersAssemblyRuleAuditRow row);

    List<OwnersAssemblyRuleAuditRow> listAudits(@Param("ruleId") Long ruleId, @Param("tenantId") Long tenantId);
}
