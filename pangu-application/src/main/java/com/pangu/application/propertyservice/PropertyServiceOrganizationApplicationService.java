// 关联业务：编排新小区物业服务组织登记、材料核验、项目部启用和物业角色授权前置条件闭环。
package com.pangu.application.propertyservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.propertyservice.command.ManualPropertyServiceOrganizationVerificationCommand;
import com.pangu.application.propertyservice.command.PlatformPropertyServiceOrganizationVerificationCommand;
import com.pangu.application.propertyservice.command.UploadPropertyServiceOrganizationMaterialCommand;
import com.pangu.application.propertyservice.command.UpsertPropertyServiceOrganizationCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.propertyservice.PropertyServiceContractBasis;
import com.pangu.domain.model.propertyservice.PropertyServiceEnterprise;
import com.pangu.domain.model.propertyservice.PropertyServiceManualVerificationSource;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganization;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationMaterial;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationMaterialType;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationStatus;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationVerification;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationVerificationMethod;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationVerificationResult;
import com.pangu.domain.repository.EnterpriseVerificationProvider;
import com.pangu.domain.repository.PropertyServiceOrganizationMaterialStorage;
import com.pangu.domain.repository.PropertyServiceOrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException.Reason.CONCURRENT_MODIFICATION;
import static com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException.Reason.DUPLICATE_ACTIVE_ORGANIZATION;
import static com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException.Reason.EXTERNAL_VERIFICATION_UNAVAILABLE;
import static com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException.Reason.MATERIAL_REQUIRED;
import static com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException.Reason.STORAGE_UNAVAILABLE;
import static com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException.Reason.UNAUTHORIZED;

/**
 * 物业服务组织登记应用服务。
 *
 * <p>企业主体、其本小区项目部以及物业角色授权保持分层：企业根组织可跨小区复用，
 * 只有通过本小区核验后创建的 tenant 项目部才能承接物业经理和物业员工工作身份。
 */
@Service
@RequiredArgsConstructor
public class PropertyServiceOrganizationApplicationService {

    private static final String READ_PERMISSION = "property:service-organization:read";
    private static final String SUBMIT_PERMISSION = "property:service-organization:submit";
    private static final String VERIFY_PERMISSION = "property:service-organization:verify";
    private static final Pattern USCC_PATTERN = Pattern.compile("[0-9A-HJ-NPQRTUWXY]{18}");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Set<String> MATERIAL_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");
    private static final long MAX_MATERIAL_SIZE = 20L * 1024 * 1024;
    private static final Duration PREVIEW_VALIDITY = Duration.ofMinutes(10);
    private static final Set<String> SUBMITTER_ROLES = Set.of(
            "COMMITTEE_DIRECTOR", "COMMUNITY_ADMIN", "GOV_SUPER_ADMIN");
    private static final Set<String> VERIFIER_ROLES = Set.of("COMMUNITY_ADMIN", "GOV_SUPER_ADMIN");

    private final PropertyServiceOrganizationRepository repository;
    private final PropertyServiceOrganizationMaterialStorage materialStorage;
    private final EnterpriseVerificationProvider verificationProvider;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public List<PropertyServiceOrganizationDetails> list() {
        Long tenantId = requireViewerTenant();
        return repository.listByTenant(tenantId).stream().map(this::details).toList();
    }

    @Transactional(readOnly = true)
    public PropertyServiceOrganizationDetails get(Long organizationId) {
        Long tenantId = requireViewerTenant();
        return details(requireOrganization(tenantId, organizationId));
    }

    @Transactional
    public PropertyServiceOrganizationDetails create(UpsertPropertyServiceOrganizationCommand command) {
        UserContext actor = requireSubmitter();
        Long tenantId = requireTenant(actor);
        NormalizedOrganization normalized = normalize(command);
        PropertyServiceEnterprise enterprise = resolveEnterprise(normalized.legalName(), normalized.uscc());
        Instant now = Instant.now();
        PropertyServiceOrganization created = repository.insertOrganization(new PropertyServiceOrganization(
                null, tenantId, enterprise.enterpriseId(), null, normalized.projectDeptName(),
                normalized.serviceContactName(), normalized.serviceContactPhone(), normalized.serviceBasis(),
                normalized.serviceStartDate(), normalized.serviceEndDate(), PropertyServiceOrganizationStatus.DRAFT,
                null, null, null, null, null, null, null, 0, now, now));
        repository.insertAudit(created.organizationId(), actor.accountId(), actor.userId(), actor.deptId(),
                "ORGANIZATION_CREATED", null, PropertyServiceOrganizationStatus.DRAFT.name(),
                toJson(Map.of("enterpriseId", enterprise.enterpriseId(), "serviceBasis", normalized.serviceBasis().name())));
        return details(created);
    }

    @Transactional
    public PropertyServiceOrganizationDetails revise(
            Long organizationId,
            UpsertPropertyServiceOrganizationCommand command) {
        UserContext actor = requireSubmitter();
        Long tenantId = requireTenant(actor);
        PropertyServiceOrganization current = requireOrganization(tenantId, organizationId);
        int expectedVersion = requireExpectedVersion(command == null ? null : command.expectedVersion());
        assertVersion(current, expectedVersion);
        requireEditable(current);
        NormalizedOrganization normalized = normalize(command);
        PropertyServiceEnterprise enterprise = resolveEnterprise(normalized.legalName(), normalized.uscc());
        Instant now = Instant.now();
        PropertyServiceOrganization revised = new PropertyServiceOrganization(
                current.organizationId(), tenantId, enterprise.enterpriseId(), null, normalized.projectDeptName(),
                normalized.serviceContactName(), normalized.serviceContactPhone(), normalized.serviceBasis(),
                normalized.serviceStartDate(), normalized.serviceEndDate(), PropertyServiceOrganizationStatus.DRAFT,
                null, null, null, null, null, null, null, current.version(), current.createdAt(), now);
        updateOrThrow(revised, expectedVersion);
        repository.insertAudit(current.organizationId(), actor.accountId(), actor.userId(), actor.deptId(),
                "ORGANIZATION_REVISED", current.status().name(), PropertyServiceOrganizationStatus.DRAFT.name(),
                toJson(Map.of("expectedVersion", expectedVersion, "enterpriseId", enterprise.enterpriseId())));
        return details(requireOrganization(tenantId, organizationId));
    }

    @Transactional
    public PropertyServiceOrganizationMaterial uploadMaterial(
            Long organizationId,
            UploadPropertyServiceOrganizationMaterialCommand command) {
        UserContext actor = requireSubmitter();
        Long tenantId = requireTenant(actor);
        PropertyServiceOrganization organization = requireOrganization(tenantId, organizationId);
        requireEditable(organization);
        if (command == null || command.materialType() == null) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "材料类型不能为空");
        }
        String contentType = normalizeContentType(command.contentType());
        byte[] content = command.content() == null ? new byte[0] : command.content();
        if (!MATERIAL_CONTENT_TYPES.contains(contentType)) {
            throw new PropertyServiceOrganizationApplicationException(
                    PARAM_INVALID, "物业服务组织材料仅支持 PDF、JPG、PNG 或 WebP");
        }
        if (content.length == 0 || content.length > MAX_MATERIAL_SIZE) {
            throw new PropertyServiceOrganizationApplicationException(
                    PARAM_INVALID, "物业服务组织材料大小必须大于 0 且不超过 20MB");
        }
        String fileName = normalizeFileName(command.originalFileName());
        String objectKey = materialObjectKey(organization, contentType);
        PropertyServiceOrganizationMaterialStorage.StoredObjectMetadata stored;
        try {
            stored = materialStorage.put(objectKey, content, contentType, digestBase64("MD5", content));
        } catch (RuntimeException ex) {
            throw new PropertyServiceOrganizationApplicationException(STORAGE_UNAVAILABLE, "物业服务组织材料上传失败", ex);
        }
        if (stored.size() != content.length || stored.etag() == null || stored.etag().isBlank()) {
            deleteStoredObjectQuietly(objectKey);
            throw new PropertyServiceOrganizationApplicationException(
                    STORAGE_UNAVAILABLE, "物业服务组织材料对象完整性校验失败");
        }
        try {
            PropertyServiceOrganizationMaterial material = repository.insertMaterial(
                    new PropertyServiceOrganizationMaterial(
                            null, organizationId, command.materialType(), objectKey, fileName, contentType,
                            content.length, stored.etag(), digestHex("SHA-256", content), actor.accountId(),
                            "ACTIVE", Instant.now()));
            repository.insertAudit(organizationId, actor.accountId(), actor.userId(), actor.deptId(),
                    "MATERIAL_UPLOADED", organization.status().name(), organization.status().name(),
                    toJson(Map.of("materialId", material.materialId(),
                            "materialType", material.materialType().name(), "sha256", material.sha256())));
            return material;
        } catch (RuntimeException ex) {
            deleteStoredObjectQuietly(objectKey);
            throw ex;
        }
    }

    @Transactional
    public void deleteMaterial(Long organizationId, Long materialId) {
        UserContext actor = requireSubmitter();
        Long tenantId = requireTenant(actor);
        PropertyServiceOrganization organization = requireOrganization(tenantId, organizationId);
        requireEditable(organization);
        PropertyServiceOrganizationMaterial material = repository.findMaterial(organizationId, materialId)
                .orElseThrow(() -> new PropertyServiceOrganizationApplicationException(NOT_FOUND, "物业服务组织材料不存在"));
        if (repository.deactivateMaterial(organizationId, materialId) != 1) {
            throw new PropertyServiceOrganizationApplicationException(INVALID_STATUS, "物业服务组织材料状态已变化，请刷新后重试");
        }
        try {
            materialStorage.delete(material.objectKey());
        } catch (RuntimeException ex) {
            throw new PropertyServiceOrganizationApplicationException(STORAGE_UNAVAILABLE, "删除物业服务组织材料失败", ex);
        }
        repository.insertAudit(organizationId, actor.accountId(), actor.userId(), actor.deptId(),
                "MATERIAL_REMOVED", organization.status().name(), organization.status().name(),
                toJson(Map.of("materialId", materialId, "sha256", material.sha256())));
    }

    @Transactional(readOnly = true)
    public PropertyServiceOrganizationMaterialPreviewTicket createMaterialPreviewTicket(
            Long organizationId,
            Long materialId) {
        Long tenantId = requireViewerTenant();
        requireOrganization(tenantId, organizationId);
        PropertyServiceOrganizationMaterial material = repository.findMaterial(organizationId, materialId)
                .orElseThrow(() -> new PropertyServiceOrganizationApplicationException(NOT_FOUND, "物业服务组织材料不存在"));
        Instant expiresAt = Instant.now().plus(PREVIEW_VALIDITY);
        try {
            return new PropertyServiceOrganizationMaterialPreviewTicket(
                    material.materialId(), material.originalFileName(), material.contentType(), material.fileSize(),
                    materialStorage.createPreviewUrl(material.objectKey(), material.originalFileName(), PREVIEW_VALIDITY)
                            .toString(),
                    expiresAt);
        } catch (RuntimeException ex) {
            throw new PropertyServiceOrganizationApplicationException(STORAGE_UNAVAILABLE, "生成物业服务组织材料预览地址失败", ex);
        }
    }

    @Transactional
    public PropertyServiceOrganizationDetails submit(Long organizationId, int expectedVersion) {
        UserContext actor = requireSubmitter();
        Long tenantId = requireTenant(actor);
        PropertyServiceOrganization current = requireOrganization(tenantId, organizationId);
        assertVersion(current, expectedVersion);
        requireEditable(current);
        assertRequiredMaterials(current);
        Instant now = Instant.now();
        PropertyServiceOrganization submitted = new PropertyServiceOrganization(
                current.organizationId(), current.tenantId(), current.enterpriseId(), current.projectDeptId(),
                current.projectDeptName(), current.serviceContactName(), current.serviceContactPhone(),
                current.serviceBasis(), current.serviceStartDate(), current.serviceEndDate(),
                PropertyServiceOrganizationStatus.PENDING_VERIFICATION,
                actor.accountId(), actor.userId(), now,
                null, null, null, null, current.version(), current.createdAt(), now);
        updateOrThrow(submitted, expectedVersion);
        repository.insertAudit(organizationId, actor.accountId(), actor.userId(), actor.deptId(),
                "ORGANIZATION_SUBMITTED", current.status().name(), submitted.status().name(),
                toJson(Map.of("materialCount", repository.listMaterials(organizationId).size())));
        return details(requireOrganization(tenantId, organizationId));
    }

    @Transactional(readOnly = true)
    public PropertyServiceEnterpriseVerificationProviderDescriptor providerDescriptor() {
        requireVerifier();
        return new PropertyServiceEnterpriseVerificationProviderDescriptor(
                verificationProvider.providerCode(), verificationProvider.displayName(), verificationProvider.simulated());
    }

    @Transactional
    public PropertyServiceOrganizationDetails verifyManually(
            Long organizationId,
            ManualPropertyServiceOrganizationVerificationCommand command) {
        UserContext actor = requireVerifier();
        Long tenantId = requireTenant(actor);
        PropertyServiceManualVerificationSource source = parseManualSource(command == null ? null : command.sourceCode());
        PropertyServiceOrganizationVerificationResult result = parseManualResult(
                command == null ? null : command.verificationResult());
        String evidenceReference = trimToLength(command == null ? null : command.evidenceReference(), 500);
        String remark = trimToLength(command == null ? null : command.remark(), 500);
        if (source == PropertyServiceManualVerificationSource.OTHER_GOVERNMENT_SOURCE && evidenceReference == null) {
            throw new PropertyServiceOrganizationApplicationException(
                    PARAM_INVALID, "使用其他政府信息来源时必须填写查询来源或凭证编号");
        }
        if (result == PropertyServiceOrganizationVerificationResult.REJECTED && remark == null) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "手工核验不通过时必须填写原因");
        }
        PropertyServiceOrganization current = requireOrganizationForUpdate(tenantId, organizationId);
        assertPendingVerification(current);
        PropertyServiceEnterprise enterprise = requireEnterprise(current.enterpriseId());
        Instant verifiedAt = Instant.now();
        PropertyServiceOrganizationVerification verification = repository.insertVerification(
                new PropertyServiceOrganizationVerification(
                        null, current.organizationId(), enterprise.legalName(), enterprise.unifiedSocialCreditCode(),
                        PropertyServiceOrganizationVerificationMethod.PROPERTY_MANUAL,
                        null, source.name(), null, null, result, null,
                        result == PropertyServiceOrganizationVerificationResult.PASSED
                                ? "已按所选政府信息来源核对企业名称与统一社会信用代码"
                                : "属地人工核验未通过",
                        List.of(), evidenceReference, remark,
                        actor.accountId(), actor.userId(), actor.roleKey(), false, verifiedAt));
        applyVerificationOutcome(current, enterprise, verification, actor, verifiedAt);
        return details(requireOrganization(tenantId, organizationId));
    }

    public PropertyServiceOrganizationDetails verifyWithPlatform(
            Long organizationId,
            PlatformPropertyServiceOrganizationVerificationCommand command) {
        UserContext actor = requireVerifier();
        Long tenantId = requireTenant(actor);
        if (command == null || !command.enterpriseAuthorizationConfirmed()) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "调用企业核验平台前必须确认企业授权");
        }
        PropertyServiceOrganization initial = requireOrganization(tenantId, organizationId);
        assertPendingVerification(initial);
        PropertyServiceEnterprise initialEnterprise = requireEnterprise(initial.enterpriseId());

        EnterpriseVerificationProvider.VerificationResult providerResult;
        try {
            providerResult = verificationProvider.verify(new EnterpriseVerificationProvider.VerificationRequest(
                    tenantId, organizationId, initialEnterprise.legalName(), initialEnterprise.unifiedSocialCreditCode(), true));
            validateProviderResult(providerResult);
        } catch (RuntimeException ex) {
            recordProviderError(actor, initial, initialEnterprise, ex);
            throw new PropertyServiceOrganizationApplicationException(
                    EXTERNAL_VERIFICATION_UNAVAILABLE, "企业核验平台暂时不可用，请稍后重试", ex);
        }

        return requireTransactionResult(transactionTemplate.execute(status -> {
            PropertyServiceOrganization current = requireOrganizationForUpdate(tenantId, organizationId);
            assertPendingVerification(current);
            PropertyServiceEnterprise enterprise = requireEnterprise(current.enterpriseId());
            if (!enterprise.unifiedSocialCreditCode().equals(initialEnterprise.unifiedSocialCreditCode())
                    || !enterprise.legalName().equals(initialEnterprise.legalName())) {
                throw new PropertyServiceOrganizationApplicationException(
                        INVALID_STATUS, "物业服务企业信息已变化，请重新发起平台核验");
            }
            Instant verifiedAt = Instant.now();
            PropertyServiceOrganizationVerificationResult result = providerResult.matched()
                    ? PropertyServiceOrganizationVerificationResult.PASSED
                    : PropertyServiceOrganizationVerificationResult.REJECTED;
            PropertyServiceOrganizationVerification verification = repository.insertVerification(
                    new PropertyServiceOrganizationVerification(
                            null, current.organizationId(), enterprise.legalName(), enterprise.unifiedSocialCreditCode(),
                            PropertyServiceOrganizationVerificationMethod.PLATFORM_API,
                            verificationProvider.providerCode(), null,
                            trimToLength(providerResult.providerRequestId(), 128),
                            trimToLength(providerResult.providerResultCode(), 64), result,
                            trimToLength(providerResult.businessStatus(), 64),
                            trimToLength(providerResult.resultMessage(), 500), providerResult.inconsistentFields(),
                            null,
                            verificationProvider.simulated()
                                    ? "开发测试模拟平台核验，生产环境必须改用真实核验服务"
                                    : "企业授权后发起平台核验",
                            actor.accountId(), actor.userId(), actor.roleKey(), verificationProvider.simulated(), verifiedAt));
            applyVerificationOutcome(current, enterprise, verification, actor, verifiedAt);
            return details(requireOrganization(tenantId, organizationId));
        }));
    }

    private void applyVerificationOutcome(PropertyServiceOrganization current,
                                          PropertyServiceEnterprise enterprise,
                                          PropertyServiceOrganizationVerification verification,
                                          UserContext actor,
                                          Instant verifiedAt) {
        if (verification.verificationResult() == PropertyServiceOrganizationVerificationResult.PASSED) {
            PropertyServiceEnterprise lockedEnterprise = repository.findEnterpriseByUsccForUpdate(
                            enterprise.unifiedSocialCreditCode())
                    .orElseThrow(() -> new PropertyServiceOrganizationApplicationException(
                            NOT_FOUND, "物业服务企业不存在或已变化"));
            Long enterpriseDeptId = lockedEnterprise.enterpriseDeptId();
            if (enterpriseDeptId == null) {
                enterpriseDeptId = repository.insertEnterpriseDepartment(
                        limitDeptName(lockedEnterprise.legalName()));
                if (repository.updateEnterpriseDepartment(lockedEnterprise.enterpriseId(), enterpriseDeptId) != 1) {
                    throw new PropertyServiceOrganizationApplicationException(
                            CONCURRENT_MODIFICATION, "物业服务企业组织节点已被并发更新，请刷新后重试");
                }
            }
            repository.findActiveByTenant(current.tenantId())
                    .filter(active -> !active.organizationId().equals(current.organizationId()))
                    .ifPresent(active -> {
                        throw new PropertyServiceOrganizationApplicationException(
                                DUPLICATE_ACTIVE_ORGANIZATION, "当前小区已存在已启用的物业服务组织");
                    });
            Long projectDeptId = current.projectDeptId();
            if (projectDeptId == null) {
                projectDeptId = repository.insertProjectDepartment(
                        enterpriseDeptId, current.projectDeptName(), current.tenantId());
            }
            PropertyServiceOrganization active = new PropertyServiceOrganization(
                    current.organizationId(), current.tenantId(), lockedEnterprise.enterpriseId(), projectDeptId,
                    current.projectDeptName(), current.serviceContactName(), current.serviceContactPhone(),
                    current.serviceBasis(), current.serviceStartDate(), current.serviceEndDate(),
                    PropertyServiceOrganizationStatus.ACTIVE,
                    current.submittedByAccountId(), current.submittedByUserId(), current.submittedAt(),
                    actor.accountId(), actor.userId(), verifiedAt, null,
                    current.version(), current.createdAt(), verifiedAt);
            updateOrThrow(active, current.version());
            repository.insertAudit(current.organizationId(), actor.accountId(), actor.userId(), actor.deptId(),
                    "VERIFICATION_PASSED_AND_PROJECT_DEPT_ENABLED", current.status().name(), active.status().name(),
                    toJson(Map.of("verificationId", verification.verificationId(),
                            "enterpriseDeptId", enterpriseDeptId, "projectDeptId", projectDeptId,
                            "simulated", verification.simulated())));
            return;
        }

        String rejectionReason = verification.remark() == null
                ? trimToLength(verification.resultMessage(), 500)
                : verification.remark();
        PropertyServiceOrganization rejected = new PropertyServiceOrganization(
                current.organizationId(), current.tenantId(), current.enterpriseId(), current.projectDeptId(),
                current.projectDeptName(), current.serviceContactName(), current.serviceContactPhone(),
                current.serviceBasis(), current.serviceStartDate(), current.serviceEndDate(),
                PropertyServiceOrganizationStatus.REJECTED,
                current.submittedByAccountId(), current.submittedByUserId(), current.submittedAt(),
                actor.accountId(), actor.userId(), verifiedAt, rejectionReason,
                current.version(), current.createdAt(), verifiedAt);
        updateOrThrow(rejected, current.version());
        repository.insertAudit(current.organizationId(), actor.accountId(), actor.userId(), actor.deptId(),
                "VERIFICATION_REJECTED", current.status().name(), rejected.status().name(),
                toJson(Map.of("verificationId", verification.verificationId(),
                        "simulated", verification.simulated())));
    }

    private void recordProviderError(UserContext actor,
                                     PropertyServiceOrganization organization,
                                     PropertyServiceEnterprise enterprise,
                                     RuntimeException cause) {
        try {
            transactionTemplate.executeWithoutResult(status -> repository.insertVerification(
                    new PropertyServiceOrganizationVerification(
                            null, organization.organizationId(), enterprise.legalName(),
                            enterprise.unifiedSocialCreditCode(),
                            PropertyServiceOrganizationVerificationMethod.PLATFORM_API,
                            verificationProvider.providerCode(), null, null, "PROVIDER_ERROR",
                            PropertyServiceOrganizationVerificationResult.ERROR, null,
                            trimToLength(cause.getMessage(), 500), List.of(), null,
                            "企业核验平台调用失败", actor.accountId(), actor.userId(), actor.roleKey(),
                            verificationProvider.simulated(), Instant.now())));
        } catch (RuntimeException auditFailure) {
            cause.addSuppressed(auditFailure);
        }
    }

    private void assertRequiredMaterials(PropertyServiceOrganization organization) {
        requireMaterial(organization.organizationId(), PropertyServiceOrganizationMaterialType.BUSINESS_LICENSE,
                "提交前必须上传营业执照");
        requireMaterial(organization.organizationId(), PropertyServiceOrganizationMaterialType.PROPERTY_SERVICE_CONTRACT,
                "提交前必须上传物业服务合同或前期物业服务协议");
        if (organization.serviceBasis() == PropertyServiceContractBasis.OWNERS_ASSEMBLY_SELECTED) {
            requireMaterial(organization.organizationId(), PropertyServiceOrganizationMaterialType.OWNERS_ASSEMBLY_DECISION,
                    "业主大会选聘物业时必须上传业主大会决定材料");
        }
    }

    private void requireMaterial(Long organizationId,
                                 PropertyServiceOrganizationMaterialType materialType,
                                 String message) {
        if (repository.countActiveMaterialsByType(organizationId, materialType.name()) < 1) {
            throw new PropertyServiceOrganizationApplicationException(MATERIAL_REQUIRED, message);
        }
    }

    private PropertyServiceEnterprise resolveEnterprise(String legalName, String uscc) {
        return repository.findEnterpriseByUscc(uscc).map(existing -> {
            if (!existing.legalName().equals(legalName)) {
                throw new PropertyServiceOrganizationApplicationException(
                        PARAM_INVALID, "统一社会信用代码已关联其他企业名称，请先核对企业主体信息");
            }
            return existing;
        }).orElseGet(() -> {
            Instant now = Instant.now();
            try {
                return repository.insertEnterprise(new PropertyServiceEnterprise(
                        null, null, legalName, uscc, now, now));
            } catch (PropertyServiceOrganizationRepository.DuplicatePropertyServiceOrganizationException ex) {
                return repository.findEnterpriseByUscc(uscc).map(existing -> {
                    if (!existing.legalName().equals(legalName)) {
                        throw new PropertyServiceOrganizationApplicationException(
                                PARAM_INVALID, "统一社会信用代码已关联其他企业名称，请先核对企业主体信息", ex);
                    }
                    return existing;
                }).orElseThrow(() -> ex);
            }
        });
    }

    private PropertyServiceOrganizationDetails details(PropertyServiceOrganization organization) {
        PropertyServiceEnterprise enterprise = requireEnterprise(organization.enterpriseId());
        return new PropertyServiceOrganizationDetails(
                organization,
                enterprise,
                repository.listMaterials(organization.organizationId()),
                repository.listVerifications(organization.organizationId()));
    }

    private PropertyServiceEnterprise requireEnterprise(Long enterpriseId) {
        return repository.findEnterpriseById(enterpriseId)
                .orElseThrow(() -> new PropertyServiceOrganizationApplicationException(
                        NOT_FOUND, "物业服务企业不存在或已被移除"));
    }

    private PropertyServiceOrganization requireOrganization(Long tenantId, Long organizationId) {
        if (organizationId == null) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "organizationId 必填");
        }
        return repository.findByTenantAndId(tenantId, organizationId)
                .orElseThrow(() -> new PropertyServiceOrganizationApplicationException(
                        NOT_FOUND, "物业服务组织不存在或不属于当前小区"));
    }

    private PropertyServiceOrganization requireOrganizationForUpdate(Long tenantId, Long organizationId) {
        if (organizationId == null) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "organizationId 必填");
        }
        return repository.findByTenantAndIdForUpdate(tenantId, organizationId)
                .orElseThrow(() -> new PropertyServiceOrganizationApplicationException(
                        NOT_FOUND, "物业服务组织不存在或不属于当前小区"));
    }

    private void updateOrThrow(PropertyServiceOrganization organization, int expectedVersion) {
        try {
            if (repository.updateOrganization(organization, expectedVersion) != 1) {
                throw new PropertyServiceOrganizationApplicationException(
                        CONCURRENT_MODIFICATION, "物业服务组织登记已被并发更新，请刷新后重试");
            }
        } catch (PropertyServiceOrganizationRepository.DuplicatePropertyServiceOrganizationException ex) {
            throw new PropertyServiceOrganizationApplicationException(
                    DUPLICATE_ACTIVE_ORGANIZATION, "当前小区已存在已启用的物业服务组织", ex);
        }
    }

    private void assertVersion(PropertyServiceOrganization organization, int expectedVersion) {
        if (organization.version() != expectedVersion) {
            throw new PropertyServiceOrganizationApplicationException(
                    CONCURRENT_MODIFICATION, "物业服务组织登记已被并发更新，请刷新后重试");
        }
    }

    private void requireEditable(PropertyServiceOrganization organization) {
        if (!organization.status().editable()) {
            throw new PropertyServiceOrganizationApplicationException(
                    INVALID_STATUS, "只有草稿或核验退回的物业服务组织可以修改材料，当前状态=" + organization.status());
        }
    }

    private void assertPendingVerification(PropertyServiceOrganization organization) {
        if (organization.status() != PropertyServiceOrganizationStatus.PENDING_VERIFICATION) {
            throw new PropertyServiceOrganizationApplicationException(
                    INVALID_STATUS, "只有待核验物业服务组织可以进行企业核验，当前状态=" + organization.status());
        }
    }

    private UserContext requireViewer() {
        UserContext context = userContextHolder.current();
        if (context == null || !context.isSysUser()) {
            throw new PropertyServiceOrganizationApplicationException(UNAUTHORIZED, "请使用管理端工作身份查看物业服务组织");
        }
        if (!context.hasPermission(READ_PERMISSION)) {
            throw new PropertyServiceOrganizationApplicationException(FORBIDDEN, "当前身份无权查看物业服务组织");
        }
        return context;
    }

    private Long requireViewerTenant() {
        return requireTenant(requireViewer());
    }

    private UserContext requireSubmitter() {
        UserContext context = requireViewer();
        if (!context.hasPermission(SUBMIT_PERMISSION) || !SUBMITTER_ROLES.contains(context.roleKey())) {
            throw new PropertyServiceOrganizationApplicationException(FORBIDDEN, "当前身份无权登记物业服务组织");
        }
        return context;
    }

    private UserContext requireVerifier() {
        UserContext context = requireViewer();
        if (!context.hasPermission(VERIFY_PERMISSION) || !VERIFIER_ROLES.contains(context.roleKey())) {
            throw new PropertyServiceOrganizationApplicationException(FORBIDDEN, "当前身份无权核验物业服务企业");
        }
        return context;
    }

    private Long requireTenant(UserContext context) {
        if (context.tenantId() == null) {
            throw new PropertyServiceOrganizationApplicationException(
                    PARAM_INVALID, "请先在管理端选择需要办理的小区数据范围");
        }
        return context.tenantId();
    }

    private NormalizedOrganization normalize(UpsertPropertyServiceOrganizationCommand command) {
        if (command == null) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "物业服务组织参数不能为空");
        }
        String legalName = requireText(command.legalName(), "legalName", 120);
        String uscc = requireText(command.unifiedSocialCreditCode(), "unifiedSocialCreditCode", 18).toUpperCase(Locale.ROOT);
        if (!USCC_PATTERN.matcher(uscc).matches()) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "统一社会信用代码格式不正确");
        }
        String projectDeptName = trimToLength(command.projectDeptName(), 50);
        if (projectDeptName == null) {
            projectDeptName = limitDeptName(legalName + "本小区项目部");
        }
        String contactName = requireText(command.serviceContactName(), "serviceContactName", 50);
        String contactPhone = requireText(command.serviceContactPhone(), "serviceContactPhone", 20);
        if (!MOBILE_PATTERN.matcher(contactPhone).matches()) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "物业服务联系人手机号格式不正确");
        }
        if (command.serviceBasis() == null || command.serviceStartDate() == null) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "服务依据和服务开始日期不能为空");
        }
        LocalDate serviceEndDate = command.serviceEndDate();
        if (serviceEndDate != null && serviceEndDate.isBefore(command.serviceStartDate())) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "服务结束日期不得早于服务开始日期");
        }
        return new NormalizedOrganization(
                legalName, uscc, projectDeptName, contactName, contactPhone,
                command.serviceBasis(), command.serviceStartDate(), serviceEndDate);
    }

    private PropertyServiceManualVerificationSource parseManualSource(String value) {
        try {
            return PropertyServiceManualVerificationSource.valueOf(requireText(value, "sourceCode", 64).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new PropertyServiceOrganizationApplicationException(
                    PARAM_INVALID, "手工核验来源仅支持 GSXT_WEB 或 OTHER_GOVERNMENT_SOURCE", ex);
        }
    }

    private PropertyServiceOrganizationVerificationResult parseManualResult(String value) {
        try {
            PropertyServiceOrganizationVerificationResult result = PropertyServiceOrganizationVerificationResult.valueOf(
                    requireText(value, "verificationResult", 24).toUpperCase(Locale.ROOT));
            if (result == PropertyServiceOrganizationVerificationResult.ERROR) {
                throw new IllegalArgumentException("ERROR 不是手工核验结论");
            }
            return result;
        } catch (IllegalArgumentException ex) {
            throw new PropertyServiceOrganizationApplicationException(
                    PARAM_INVALID, "手工核验结论仅支持 PASSED 或 REJECTED", ex);
        }
    }

    private void validateProviderResult(EnterpriseVerificationProvider.VerificationResult result) {
        if (result == null || trimToLength(result.providerRequestId(), 128) == null
                || trimToLength(result.providerResultCode(), 64) == null) {
            throw new IllegalStateException("企业核验平台返回结果缺少审计标识");
        }
    }

    private int requireExpectedVersion(Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "expectedVersion 必须为非负整数");
        }
        return expectedVersion;
    }

    private String normalizeContentType(String value) {
        String contentType = requireText(value, "contentType", 100).toLowerCase(Locale.ROOT);
        int semicolon = contentType.indexOf(';');
        return semicolon < 0 ? contentType : contentType.substring(0, semicolon).trim();
    }

    private String normalizeFileName(String value) {
        String fileName = requireText(value, "originalFileName", 255);
        return fileName.replaceAll("[\\r\\n]", "_");
    }

    private String materialObjectKey(PropertyServiceOrganization organization, String contentType) {
        return "property-service-organizations/" + organization.tenantId() + "/" + organization.organizationId()
                + "/" + UUID.randomUUID() + fileExtension(contentType);
    }

    private String fileExtension(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    private String digestBase64(String algorithm, byte[] content) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance(algorithm).digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持摘要算法=" + algorithm, ex);
        }
    }

    private String digestHex(String algorithm, byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持摘要算法=" + algorithm, ex);
        }
    }

    private String requireText(String value, String fieldName, int maxLength) {
        String normalized = trimToLength(value, maxLength);
        if (normalized == null) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, fieldName + " 不能为空");
        }
        return normalized;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new PropertyServiceOrganizationApplicationException(
                    PARAM_INVALID, "字段长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String limitDeptName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new PropertyServiceOrganizationApplicationException(PARAM_INVALID, "项目部名称不能为空");
        }
        return normalized.length() <= 50 ? normalized : normalized.substring(0, 50);
    }

    private String toJson(Map<String, ?> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("物业服务组织审计数据序列化失败", ex);
        }
    }

    private void deleteStoredObjectQuietly(String objectKey) {
        try {
            materialStorage.delete(objectKey);
        } catch (RuntimeException ignored) {
            // 数据库未落库或事务已回滚时尽量回收私有对象，原异常仍由调用方处理。
        }
    }

    private <T> T requireTransactionResult(T value) {
        if (value == null) {
            throw new IllegalStateException("物业服务组织事务未返回结果");
        }
        return value;
    }

    private record NormalizedOrganization(
            String legalName,
            String uscc,
            String projectDeptName,
            String serviceContactName,
            String serviceContactPhone,
            PropertyServiceContractBasis serviceBasis,
            LocalDate serviceStartDate,
            LocalDate serviceEndDate
    ) {
    }
}
