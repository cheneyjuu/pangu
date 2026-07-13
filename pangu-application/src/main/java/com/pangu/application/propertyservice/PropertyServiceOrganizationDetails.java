// 关联业务：汇总小区物业服务组织、企业主体、登记材料和核验审计供管理端展示。
package com.pangu.application.propertyservice;

import com.pangu.domain.model.propertyservice.PropertyServiceEnterprise;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganization;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationMaterial;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationVerification;

import java.util.List;

/**
 * 物业服务组织登记详情。
 */
public record PropertyServiceOrganizationDetails(
        PropertyServiceOrganization organization,
        PropertyServiceEnterprise enterprise,
        List<PropertyServiceOrganizationMaterial> materials,
        List<PropertyServiceOrganizationVerification> verifications
) {
    public PropertyServiceOrganizationDetails {
        materials = materials == null ? List.of() : List.copyOf(materials);
        verifications = verifications == null ? List.of() : List.copyOf(verifications);
    }
}
