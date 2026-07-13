// 关联业务：聚合返回物业管理模式当前生效态、申请、材料和审计记录，供管理端按权限展示。
package com.pangu.application.community;

import com.pangu.domain.model.community.PropertyManagementMode;
import com.pangu.domain.model.community.PropertyManagementModeChangeAudit;
import com.pangu.domain.model.community.PropertyManagementModeChangeMaterial;
import com.pangu.domain.model.community.PropertyManagementModeChangeRequest;

import java.util.List;

/**
 * 物业管理模式变更申请详情。
 */
public record PropertyManagementModeChangeDetails(
        PropertyManagementMode effectivePropertyMode,
        PropertyManagementModeChangeRequest request,
        List<PropertyManagementModeChangeMaterial> materials,
        List<PropertyManagementModeChangeAudit> audits
) {
    public PropertyManagementModeChangeDetails {
        materials = materials == null ? List.of() : List.copyOf(materials);
        audits = audits == null ? List.of() : List.copyOf(audits);
    }
}
