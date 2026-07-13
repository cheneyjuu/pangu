// 关联业务：实现物业服务组织登记、材料、核验历史和项目部启用的数据库持久化。
package com.pangu.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.propertyservice.PropertyServiceContractBasis;
import com.pangu.domain.model.propertyservice.PropertyServiceEnterprise;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganization;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationMaterial;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationMaterialType;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationStatus;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationVerification;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationVerificationMethod;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationVerificationResult;
import com.pangu.domain.repository.PropertyServiceOrganizationRepository;
import com.pangu.infrastructure.persistence.mapper.PropertyServiceOrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 物业服务组织登记仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class PropertyServiceOrganizationRepositoryImpl implements PropertyServiceOrganizationRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final PropertyServiceOrganizationMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<PropertyServiceOrganization> listByTenant(Long tenantId) {
        return mapper.selectOrganizations(tenantId).stream().map(this::toOrganization).toList();
    }

    @Override
    public Optional<PropertyServiceOrganization> findByTenantAndId(Long tenantId, Long organizationId) {
        return Optional.ofNullable(mapper.selectOrganization(tenantId, organizationId)).map(this::toOrganization);
    }

    @Override
    public Optional<PropertyServiceOrganization> findByTenantAndIdForUpdate(Long tenantId, Long organizationId) {
        return Optional.ofNullable(mapper.selectOrganizationForUpdate(tenantId, organizationId))
                .map(this::toOrganization);
    }

    @Override
    public Optional<PropertyServiceOrganization> findActiveByTenant(Long tenantId) {
        return Optional.ofNullable(mapper.selectActiveOrganization(tenantId)).map(this::toOrganization);
    }

    @Override
    public PropertyServiceOrganization insertOrganization(PropertyServiceOrganization organization) {
        PropertyServiceOrganizationMapper.OrganizationRow row = toRow(organization);
        try {
            mapper.insertOrganization(row);
        } catch (DuplicateKeyException ex) {
            throw new DuplicatePropertyServiceOrganizationException("物业服务组织登记唯一约束冲突", ex);
        }
        return findByTenantAndId(organization.tenantId(), row.getOrganizationId())
                .orElseThrow(() -> new IllegalStateException("物业服务组织登记创建后未能回读"));
    }

    @Override
    public int updateOrganization(PropertyServiceOrganization organization, int expectedVersion) {
        try {
            return mapper.updateOrganization(toRow(organization), expectedVersion);
        } catch (DuplicateKeyException ex) {
            throw new DuplicatePropertyServiceOrganizationException("物业服务组织启用唯一约束冲突", ex);
        }
    }

    @Override
    public Optional<PropertyServiceEnterprise> findEnterpriseByUscc(String unifiedSocialCreditCode) {
        return Optional.ofNullable(mapper.selectEnterpriseByUscc(unifiedSocialCreditCode)).map(this::toEnterprise);
    }

    @Override
    public Optional<PropertyServiceEnterprise> findEnterpriseById(Long enterpriseId) {
        return Optional.ofNullable(mapper.selectEnterpriseById(enterpriseId)).map(this::toEnterprise);
    }

    @Override
    public Optional<PropertyServiceEnterprise> findEnterpriseByUsccForUpdate(String unifiedSocialCreditCode) {
        return Optional.ofNullable(mapper.selectEnterpriseByUsccForUpdate(unifiedSocialCreditCode))
                .map(this::toEnterprise);
    }

    @Override
    public PropertyServiceEnterprise insertEnterprise(PropertyServiceEnterprise enterprise) {
        PropertyServiceOrganizationMapper.EnterpriseRow row = toRow(enterprise);
        try {
            mapper.insertEnterprise(row);
        } catch (DuplicateKeyException ex) {
            throw new DuplicatePropertyServiceOrganizationException("物业服务企业统一社会信用代码已存在", ex);
        }
        return findEnterpriseById(row.getEnterpriseId())
                .orElseThrow(() -> new IllegalStateException("物业服务企业创建后未能回读"));
    }

    @Override
    public int updateEnterpriseDepartment(Long enterpriseId, Long enterpriseDeptId) {
        return mapper.updateEnterpriseDepartment(enterpriseId, enterpriseDeptId);
    }

    @Override
    public Long insertEnterpriseDepartment(String enterpriseName) {
        PropertyServiceOrganizationMapper.DeptInsertRow row = new PropertyServiceOrganizationMapper.DeptInsertRow();
        row.setParentId(null);
        row.setAncestors("");
        row.setDeptName(enterpriseName);
        row.setDeptType(3);
        row.setDeptCategory("S");
        row.setTenantId(null);
        row.setOrderNum(1);
        mapper.insertEnterpriseDepartment(row);
        return row.getDeptId();
    }

    @Override
    public Long insertProjectDepartment(Long enterpriseDeptId, String projectDeptName, Long tenantId) {
        PropertyServiceOrganizationMapper.DeptInsertRow row = new PropertyServiceOrganizationMapper.DeptInsertRow();
        row.setParentId(enterpriseDeptId);
        row.setAncestors(String.valueOf(enterpriseDeptId));
        row.setDeptName(projectDeptName);
        row.setDeptType(3);
        row.setDeptCategory("S");
        row.setTenantId(tenantId);
        row.setOrderNum(1);
        mapper.insertProjectDepartment(row);
        return row.getDeptId();
    }

    @Override
    public PropertyServiceOrganizationMaterial insertMaterial(PropertyServiceOrganizationMaterial material) {
        PropertyServiceOrganizationMapper.MaterialRow row = toRow(material);
        mapper.insertMaterial(row);
        return toMaterial(row);
    }

    @Override
    public Optional<PropertyServiceOrganizationMaterial> findMaterial(Long organizationId, Long materialId) {
        return Optional.ofNullable(mapper.selectMaterial(organizationId, materialId)).map(this::toMaterial);
    }

    @Override
    public List<PropertyServiceOrganizationMaterial> listMaterials(Long organizationId) {
        return mapper.selectMaterials(organizationId).stream().map(this::toMaterial).toList();
    }

    @Override
    public int deactivateMaterial(Long organizationId, Long materialId) {
        return mapper.deactivateMaterial(organizationId, materialId);
    }

    @Override
    public long countActiveMaterialsByType(Long organizationId, String materialType) {
        return mapper.countActiveMaterialsByType(organizationId, materialType);
    }

    @Override
    public PropertyServiceOrganizationVerification insertVerification(
            PropertyServiceOrganizationVerification verification) {
        PropertyServiceOrganizationMapper.VerificationRow row = toRow(verification);
        mapper.insertVerification(row);
        return toVerification(row);
    }

    @Override
    public List<PropertyServiceOrganizationVerification> listVerifications(Long organizationId) {
        return mapper.selectVerifications(organizationId).stream().map(this::toVerification).toList();
    }

    @Override
    public void insertAudit(Long organizationId,
                            Long actorAccountId,
                            Long actorUserId,
                            Long actorDeptId,
                            String eventType,
                            String fromStatus,
                            String toStatus,
                            String payloadJson) {
        mapper.insertAudit(organizationId, actorAccountId, actorUserId, actorDeptId,
                eventType, fromStatus, toStatus, payloadJson);
    }

    private PropertyServiceEnterprise toEnterprise(PropertyServiceOrganizationMapper.EnterpriseRow row) {
        return new PropertyServiceEnterprise(
                row.getEnterpriseId(), row.getEnterpriseDeptId(), row.getLegalName(),
                row.getUnifiedSocialCreditCode(), row.getCreateTime(), row.getUpdateTime());
    }

    private PropertyServiceOrganization toOrganization(PropertyServiceOrganizationMapper.OrganizationRow row) {
        return new PropertyServiceOrganization(
                row.getOrganizationId(), row.getTenantId(), row.getEnterpriseId(), row.getProjectDeptId(),
                row.getProjectDeptName(), row.getServiceContactName(), row.getServiceContactPhone(),
                PropertyServiceContractBasis.valueOf(row.getServiceBasis()), row.getServiceStartDate(),
                row.getServiceEndDate(), PropertyServiceOrganizationStatus.valueOf(row.getStatus()),
                row.getSubmittedByAccountId(), row.getSubmittedByUserId(), row.getSubmittedAt(),
                row.getVerifiedByAccountId(), row.getVerifiedByUserId(), row.getVerifiedAt(),
                row.getRejectionReason(), row.getVersion() == null ? 0 : row.getVersion(),
                row.getCreateTime(), row.getUpdateTime());
    }

    private PropertyServiceOrganizationMaterial toMaterial(PropertyServiceOrganizationMapper.MaterialRow row) {
        return new PropertyServiceOrganizationMaterial(
                row.getMaterialId(), row.getOrganizationId(),
                PropertyServiceOrganizationMaterialType.valueOf(row.getMaterialType()),
                row.getObjectKey(), row.getOriginalFileName(), row.getContentType(),
                row.getFileSize() == null ? 0 : row.getFileSize(), row.getEtag(), row.getSha256(),
                row.getUploadedByAccountId(), row.getStatus(), row.getCreateTime());
    }

    private PropertyServiceOrganizationVerification toVerification(
            PropertyServiceOrganizationMapper.VerificationRow row) {
        return new PropertyServiceOrganizationVerification(
                row.getVerificationId(), row.getOrganizationId(), row.getLegalNameSnapshot(),
                row.getUnifiedSocialCreditCodeSnapshot(),
                PropertyServiceOrganizationVerificationMethod.valueOf(row.getVerificationMethod()),
                row.getProviderCode(), row.getSourceCode(), row.getProviderRequestId(), row.getProviderResultCode(),
                PropertyServiceOrganizationVerificationResult.valueOf(row.getVerificationResult()),
                row.getBusinessStatus(), row.getResultMessage(), parseStringList(row.getInconsistentFieldsJson()),
                row.getEvidenceReference(), row.getRemark(), row.getOperatorAccountId(), row.getOperatorUserId(),
                row.getOperatorRoleKey(), Boolean.TRUE.equals(row.getSimulated()), row.getVerifiedAt());
    }

    private PropertyServiceOrganizationMapper.EnterpriseRow toRow(PropertyServiceEnterprise enterprise) {
        PropertyServiceOrganizationMapper.EnterpriseRow row = new PropertyServiceOrganizationMapper.EnterpriseRow();
        row.setEnterpriseId(enterprise.enterpriseId());
        row.setEnterpriseDeptId(enterprise.enterpriseDeptId());
        row.setLegalName(enterprise.legalName());
        row.setUnifiedSocialCreditCode(enterprise.unifiedSocialCreditCode());
        row.setCreateTime(enterprise.createdAt());
        row.setUpdateTime(enterprise.updatedAt());
        return row;
    }

    private PropertyServiceOrganizationMapper.OrganizationRow toRow(PropertyServiceOrganization organization) {
        PropertyServiceOrganizationMapper.OrganizationRow row = new PropertyServiceOrganizationMapper.OrganizationRow();
        row.setOrganizationId(organization.organizationId());
        row.setTenantId(organization.tenantId());
        row.setEnterpriseId(organization.enterpriseId());
        row.setProjectDeptId(organization.projectDeptId());
        row.setProjectDeptName(organization.projectDeptName());
        row.setServiceContactName(organization.serviceContactName());
        row.setServiceContactPhone(organization.serviceContactPhone());
        row.setServiceBasis(organization.serviceBasis().name());
        row.setServiceStartDate(organization.serviceStartDate());
        row.setServiceEndDate(organization.serviceEndDate());
        row.setStatus(organization.status().name());
        row.setSubmittedByAccountId(organization.submittedByAccountId());
        row.setSubmittedByUserId(organization.submittedByUserId());
        row.setSubmittedAt(organization.submittedAt());
        row.setVerifiedByAccountId(organization.verifiedByAccountId());
        row.setVerifiedByUserId(organization.verifiedByUserId());
        row.setVerifiedAt(organization.verifiedAt());
        row.setRejectionReason(organization.rejectionReason());
        row.setVersion(organization.version());
        row.setCreateTime(organization.createdAt());
        row.setUpdateTime(organization.updatedAt());
        return row;
    }

    private PropertyServiceOrganizationMapper.MaterialRow toRow(PropertyServiceOrganizationMaterial material) {
        PropertyServiceOrganizationMapper.MaterialRow row = new PropertyServiceOrganizationMapper.MaterialRow();
        row.setMaterialId(material.materialId());
        row.setOrganizationId(material.organizationId());
        row.setMaterialType(material.materialType().name());
        row.setObjectKey(material.objectKey());
        row.setOriginalFileName(material.originalFileName());
        row.setContentType(material.contentType());
        row.setFileSize(material.fileSize());
        row.setEtag(material.etag());
        row.setSha256(material.sha256());
        row.setUploadedByAccountId(material.uploadedByAccountId());
        row.setStatus(material.status());
        row.setCreateTime(material.createdAt());
        return row;
    }

    private PropertyServiceOrganizationMapper.VerificationRow toRow(
            PropertyServiceOrganizationVerification verification) {
        PropertyServiceOrganizationMapper.VerificationRow row = new PropertyServiceOrganizationMapper.VerificationRow();
        row.setVerificationId(verification.verificationId());
        row.setOrganizationId(verification.organizationId());
        row.setLegalNameSnapshot(verification.legalNameSnapshot());
        row.setUnifiedSocialCreditCodeSnapshot(verification.unifiedSocialCreditCodeSnapshot());
        row.setVerificationMethod(verification.verificationMethod().name());
        row.setProviderCode(verification.providerCode());
        row.setSourceCode(verification.sourceCode());
        row.setProviderRequestId(verification.providerRequestId());
        row.setProviderResultCode(verification.providerResultCode());
        row.setVerificationResult(verification.verificationResult().name());
        row.setBusinessStatus(verification.businessStatus());
        row.setResultMessage(verification.resultMessage());
        row.setInconsistentFieldsJson(writeStringList(verification.inconsistentFields()));
        row.setEvidenceReference(verification.evidenceReference());
        row.setRemark(verification.remark());
        row.setOperatorAccountId(verification.operatorAccountId());
        row.setOperatorUserId(verification.operatorUserId());
        row.setOperatorRoleKey(verification.operatorRoleKey());
        row.setSimulated(verification.simulated());
        row.setVerifiedAt(verification.verifiedAt());
        return row;
    }

    private List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("物业服务组织核验不一致字段数据损坏", ex);
        }
    }

    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("物业服务组织核验不一致字段序列化失败", ex);
        }
    }
}
