// 关联业务：定义物业管理模式变更申请、材料、审计及生效模式原子切换的持久化端口。
package com.pangu.domain.repository;

import com.pangu.domain.model.community.PropertyManagementMode;
import com.pangu.domain.model.community.PropertyManagementModeChangeAudit;
import com.pangu.domain.model.community.PropertyManagementModeChangeMaterial;
import com.pangu.domain.model.community.PropertyManagementModeChangeRequest;

import java.util.List;
import java.util.Optional;

/**
 * 物业管理模式变更申请仓储。
 */
public interface PropertyManagementModeChangeRepository {

    List<PropertyManagementModeChangeRequest> listByTenant(Long tenantId);

    Optional<PropertyManagementModeChangeRequest> findByTenantAndId(Long tenantId, Long requestId);

    Optional<PropertyManagementModeChangeRequest> findByTenantAndIdForUpdate(Long tenantId, Long requestId);

    PropertyManagementModeChangeRequest insertRequest(PropertyManagementModeChangeRequest request);

    int updateRequest(PropertyManagementModeChangeRequest request, int expectedVersion);

    PropertyManagementModeChangeMaterial insertMaterial(PropertyManagementModeChangeMaterial material);

    Optional<PropertyManagementModeChangeMaterial> findMaterial(Long requestId, Long materialId);

    List<PropertyManagementModeChangeMaterial> listMaterials(Long requestId);

    int deactivateMaterial(Long requestId, Long materialId);

    long countActiveMaterialsByType(Long requestId, String materialType);

    List<PropertyManagementModeChangeAudit> listAudits(Long requestId);

    void insertAudit(Long requestId,
                     Long actorAccountId,
                     Long actorUserId,
                     Long actorDeptId,
                     String eventType,
                     String fromStatus,
                     String toStatus,
                     String payloadJson);

    int applyMode(Long tenantId,
                  PropertyManagementMode expectedCurrentMode,
                  PropertyManagementMode requestedMode,
                  String historyJson);

    class DuplicateActiveRequestException extends RuntimeException {
        public DuplicateActiveRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
