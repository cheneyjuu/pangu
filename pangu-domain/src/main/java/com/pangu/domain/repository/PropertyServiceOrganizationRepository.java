// 关联业务：定义物业服务组织登记、材料、企业核验和项目部启用的持久化端口。
package com.pangu.domain.repository;

import com.pangu.domain.model.propertyservice.PropertyServiceEnterprise;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganization;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationMaterial;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationVerification;

import java.util.List;
import java.util.Optional;

/**
 * 物业服务组织登记聚合仓储。
 */
public interface PropertyServiceOrganizationRepository {

    List<PropertyServiceOrganization> listByTenant(Long tenantId);

    Optional<PropertyServiceOrganization> findByTenantAndId(Long tenantId, Long organizationId);

    Optional<PropertyServiceOrganization> findByTenantAndIdForUpdate(Long tenantId, Long organizationId);

    Optional<PropertyServiceOrganization> findActiveByTenant(Long tenantId);

    PropertyServiceOrganization insertOrganization(PropertyServiceOrganization organization);

    int updateOrganization(PropertyServiceOrganization organization, int expectedVersion);

    Optional<PropertyServiceEnterprise> findEnterpriseByUscc(String unifiedSocialCreditCode);

    Optional<PropertyServiceEnterprise> findEnterpriseById(Long enterpriseId);

    Optional<PropertyServiceEnterprise> findEnterpriseByUsccForUpdate(String unifiedSocialCreditCode);

    PropertyServiceEnterprise insertEnterprise(PropertyServiceEnterprise enterprise);

    int updateEnterpriseDepartment(Long enterpriseId, Long enterpriseDeptId);

    Long insertEnterpriseDepartment(String enterpriseName);

    Long insertProjectDepartment(Long enterpriseDeptId, String projectDeptName, Long tenantId);

    PropertyServiceOrganizationMaterial insertMaterial(PropertyServiceOrganizationMaterial material);

    Optional<PropertyServiceOrganizationMaterial> findMaterial(Long organizationId, Long materialId);

    List<PropertyServiceOrganizationMaterial> listMaterials(Long organizationId);

    int deactivateMaterial(Long organizationId, Long materialId);

    long countActiveMaterialsByType(Long organizationId, String materialType);

    PropertyServiceOrganizationVerification insertVerification(PropertyServiceOrganizationVerification verification);

    List<PropertyServiceOrganizationVerification> listVerifications(Long organizationId);

    void insertAudit(Long organizationId,
                     Long actorAccountId,
                     Long actorUserId,
                     Long actorDeptId,
                     String eventType,
                     String fromStatus,
                     String toStatus,
                     String payloadJson);

    class DuplicatePropertyServiceOrganizationException extends RuntimeException {
        public DuplicatePropertyServiceOrganizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
