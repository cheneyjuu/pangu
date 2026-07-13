// 关联业务：编排物业管理模式从业委会主任申报、业主大会决议留档到街道办审核执行的闭环。
package com.pangu.application.community;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.community.command.PropertyManagementModeChangeVersionCommand;
import com.pangu.application.community.command.ReviewPropertyManagementModeChangeCommand;
import com.pangu.application.community.command.UploadPropertyManagementModeChangeMaterialCommand;
import com.pangu.application.community.command.UpsertPropertyManagementModeChangeCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.community.PropertyManagementMode;
import com.pangu.domain.model.community.PropertyManagementModeChangeDecision;
import com.pangu.domain.model.community.PropertyManagementModeChangeMaterial;
import com.pangu.domain.model.community.PropertyManagementModeChangeMaterialType;
import com.pangu.domain.model.community.PropertyManagementModeChangeRequest;
import com.pangu.domain.model.community.PropertyManagementModeChangeStatus;
import com.pangu.domain.model.community.TenantCommunity;
import com.pangu.domain.repository.CommunitySettingsRepository;
import com.pangu.domain.repository.PropertyManagementModeChangeMaterialStorage;
import com.pangu.domain.repository.PropertyManagementModeChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.pangu.application.community.PropertyManagementModeChangeApplicationException.Reason.CONCURRENT_MODIFICATION;
import static com.pangu.application.community.PropertyManagementModeChangeApplicationException.Reason.DUPLICATE_ACTIVE_REQUEST;
import static com.pangu.application.community.PropertyManagementModeChangeApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.community.PropertyManagementModeChangeApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.community.PropertyManagementModeChangeApplicationException.Reason.MATERIAL_REQUIRED;
import static com.pangu.application.community.PropertyManagementModeChangeApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.community.PropertyManagementModeChangeApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.community.PropertyManagementModeChangeApplicationException.Reason.STORAGE_UNAVAILABLE;
import static com.pangu.application.community.PropertyManagementModeChangeApplicationException.Reason.UNAUTHORIZED;

/**
 * 物业管理模式变更应用服务。
 *
 * <p>本服务不提供直接修改当前模式的入口。业委会主任只能依据业主大会决议发起申请；
 * 街道办审核后以租户当前模式为并发前提执行，材料、审核意见和最终生效态均可回溯。
 */
@Service
@RequiredArgsConstructor
public class PropertyManagementModeChangeApplicationService {

    private static final String READ_PERMISSION = "property:management-mode:read";
    private static final String SUBMIT_PERMISSION = "property:management-mode:submit";
    private static final String REVIEW_PERMISSION = "property:management-mode:review";
    private static final String EXECUTE_PERMISSION = "property:management-mode:execute";
    private static final String COMMITTEE_DIRECTOR = "COMMITTEE_DIRECTOR";
    private static final String GOV_SUPER_ADMIN = "GOV_SUPER_ADMIN";
    private static final Set<String> MATERIAL_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");
    private static final long MAX_MATERIAL_SIZE = 20L * 1024 * 1024;
    private static final Duration PREVIEW_VALIDITY = Duration.ofMinutes(10);

    private final PropertyManagementModeChangeRepository repository;
    private final PropertyManagementModeChangeMaterialStorage materialStorage;
    private final CommunitySettingsRepository communitySettingsRepository;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<PropertyManagementModeChangeDetails> list() {
        Long tenantId = requireViewerTenant();
        PropertyManagementMode effectiveMode = currentMode(tenantId);
        return repository.listByTenant(tenantId).stream()
                .map(request -> details(effectiveMode, request))
                .toList();
    }

    @Transactional(readOnly = true)
    public PropertyManagementModeChangeDetails get(Long requestId) {
        Long tenantId = requireViewerTenant();
        return details(currentMode(tenantId), requireRequest(tenantId, requestId));
    }

    @Transactional
    public PropertyManagementModeChangeDetails create(UpsertPropertyManagementModeChangeCommand command) {
        UserContext actor = requireApplicant();
        Long tenantId = requireTenant(actor);
        PropertyManagementMode currentMode = currentMode(tenantId);
        NormalizedChange normalized = normalize(command);
        assertDifferentMode(currentMode, normalized.requestedPropertyMode());
        Instant now = Instant.now();
        PropertyManagementModeChangeRequest draft = new PropertyManagementModeChangeRequest(
                null, tenantId, currentMode, normalized.requestedPropertyMode(),
                normalized.ownersAssemblyResolutionReference(), normalized.changeReason(),
                PropertyManagementModeChangeStatus.DRAFT,
                actor.accountId(), actor.userId(), actor.deptId(),
                null, null, null, null, null, null, null,
                0, now, now);
        PropertyManagementModeChangeRequest created;
        try {
            created = repository.insertRequest(draft);
        } catch (PropertyManagementModeChangeRepository.DuplicateActiveRequestException ex) {
            throw new PropertyManagementModeChangeApplicationException(
                    DUPLICATE_ACTIVE_REQUEST, "该小区已有待处理的物业管理模式变更申请", ex);
        }
        repository.insertAudit(created.requestId(), actor.accountId(), actor.userId(), actor.deptId(),
                "REQUEST_CREATED", null, created.status().name(),
                toJson(createdAuditPayload(currentMode, created)));
        return details(currentMode, created);
    }

    @Transactional
    public PropertyManagementModeChangeDetails revise(
            Long requestId,
            UpsertPropertyManagementModeChangeCommand command) {
        UserContext actor = requireApplicant();
        Long tenantId = requireTenant(actor);
        PropertyManagementModeChangeRequest current = requireRequest(tenantId, requestId);
        int expectedVersion = requireExpectedVersion(command == null ? null : command.expectedVersion());
        assertVersion(current, expectedVersion);
        requireEditable(current);
        NormalizedChange normalized = normalize(command);
        assertDifferentMode(current.currentPropertyMode(), normalized.requestedPropertyMode());
        Instant now = Instant.now();
        PropertyManagementModeChangeRequest revised = new PropertyManagementModeChangeRequest(
                current.requestId(), current.tenantId(), current.currentPropertyMode(),
                normalized.requestedPropertyMode(), normalized.ownersAssemblyResolutionReference(),
                normalized.changeReason(), current.status(),
                current.applicantAccountId(), current.applicantUserId(), current.applicantDeptId(),
                current.submittedAt(), current.reviewerAccountId(), current.reviewerUserId(),
                current.reviewerDeptId(), current.reviewComment(), current.reviewedAt(), current.executedAt(),
                current.version(), current.createdAt(), now);
        updateOrThrow(revised, expectedVersion);
        repository.insertAudit(requestId, actor.accountId(), actor.userId(), actor.deptId(),
                "REQUEST_REVISED", current.status().name(), current.status().name(),
                toJson(Map.of(
                        "requestedPropertyMode", revised.requestedPropertyMode().name(),
                        "ownersAssemblyResolutionReference", revised.ownersAssemblyResolutionReference(),
                        "expectedVersion", expectedVersion)));
        return details(currentMode(tenantId), requireRequest(tenantId, requestId));
    }

    @Transactional
    public PropertyManagementModeChangeMaterial uploadMaterial(
            Long requestId,
            UploadPropertyManagementModeChangeMaterialCommand command) {
        UserContext actor = requireApplicant();
        Long tenantId = requireTenant(actor);
        PropertyManagementModeChangeRequest request = requireRequest(tenantId, requestId);
        requireEditable(request);
        if (command == null || command.materialType() == null) {
            throw new PropertyManagementModeChangeApplicationException(PARAM_INVALID, "材料类型不能为空");
        }
        String contentType = normalizeContentType(command.contentType());
        byte[] content = command.content() == null ? new byte[0] : command.content();
        if (!MATERIAL_CONTENT_TYPES.contains(contentType)) {
            throw new PropertyManagementModeChangeApplicationException(
                    PARAM_INVALID, "物业管理模式变更材料仅支持 PDF、JPG、PNG 或 WebP");
        }
        if (content.length == 0 || content.length > MAX_MATERIAL_SIZE) {
            throw new PropertyManagementModeChangeApplicationException(
                    PARAM_INVALID, "物业管理模式变更材料大小必须大于 0 且不超过 20MB");
        }
        String fileName = normalizeFileName(command.originalFileName());
        String objectKey = materialObjectKey(request, contentType);
        PropertyManagementModeChangeMaterialStorage.StoredObjectMetadata stored;
        try {
            stored = materialStorage.put(objectKey, content, contentType, digestBase64("MD5", content));
        } catch (RuntimeException ex) {
            throw new PropertyManagementModeChangeApplicationException(STORAGE_UNAVAILABLE, "物业管理模式变更材料上传失败", ex);
        }
        if (stored.size() != content.length || stored.etag() == null || stored.etag().isBlank()) {
            deleteStoredObjectQuietly(objectKey);
            throw new PropertyManagementModeChangeApplicationException(
                    STORAGE_UNAVAILABLE, "物业管理模式变更材料对象完整性校验失败");
        }
        try {
            PropertyManagementModeChangeMaterial material = repository.insertMaterial(
                    new PropertyManagementModeChangeMaterial(
                            null, requestId, command.materialType(), objectKey, fileName, contentType,
                            content.length, stored.etag(), digestHex("SHA-256", content),
                            actor.accountId(), "ACTIVE", Instant.now()));
            repository.insertAudit(requestId, actor.accountId(), actor.userId(), actor.deptId(),
                    "MATERIAL_UPLOADED", request.status().name(), request.status().name(),
                    toJson(Map.of(
                            "materialId", material.materialId(),
                            "materialType", material.materialType().name(),
                            "sha256", material.sha256())));
            return material;
        } catch (RuntimeException ex) {
            deleteStoredObjectQuietly(objectKey);
            throw ex;
        }
    }

    @Transactional
    public void deleteMaterial(Long requestId, Long materialId) {
        UserContext actor = requireApplicant();
        Long tenantId = requireTenant(actor);
        PropertyManagementModeChangeRequest request = requireRequest(tenantId, requestId);
        requireEditable(request);
        PropertyManagementModeChangeMaterial material = repository.findMaterial(requestId, materialId)
                .orElseThrow(() -> new PropertyManagementModeChangeApplicationException(NOT_FOUND, "物业管理模式变更材料不存在"));
        if (repository.deactivateMaterial(requestId, materialId) != 1) {
            throw new PropertyManagementModeChangeApplicationException(
                    INVALID_STATUS, "物业管理模式变更材料状态已变化，请刷新后重试");
        }
        try {
            materialStorage.delete(material.objectKey());
        } catch (RuntimeException ex) {
            throw new PropertyManagementModeChangeApplicationException(STORAGE_UNAVAILABLE, "删除物业管理模式变更材料失败", ex);
        }
        repository.insertAudit(requestId, actor.accountId(), actor.userId(), actor.deptId(),
                "MATERIAL_REMOVED", request.status().name(), request.status().name(),
                toJson(Map.of("materialId", materialId, "sha256", material.sha256())));
    }

    @Transactional(readOnly = true)
    public PropertyManagementModeChangeMaterialPreviewTicket createMaterialPreviewTicket(
            Long requestId,
            Long materialId) {
        Long tenantId = requireViewerTenant();
        requireRequest(tenantId, requestId);
        PropertyManagementModeChangeMaterial material = repository.findMaterial(requestId, materialId)
                .orElseThrow(() -> new PropertyManagementModeChangeApplicationException(NOT_FOUND, "物业管理模式变更材料不存在"));
        Instant expiresAt = Instant.now().plus(PREVIEW_VALIDITY);
        try {
            return new PropertyManagementModeChangeMaterialPreviewTicket(
                    material.materialId(), material.originalFileName(), material.contentType(), material.fileSize(),
                    materialStorage.createPreviewUrl(material.objectKey(), material.originalFileName(), PREVIEW_VALIDITY)
                            .toString(),
                    expiresAt);
        } catch (RuntimeException ex) {
            throw new PropertyManagementModeChangeApplicationException(STORAGE_UNAVAILABLE, "生成物业管理模式变更材料预览地址失败", ex);
        }
    }

    @Transactional
    public PropertyManagementModeChangeDetails submit(
            Long requestId,
            PropertyManagementModeChangeVersionCommand command) {
        UserContext actor = requireApplicant();
        Long tenantId = requireTenant(actor);
        PropertyManagementModeChangeRequest current = requireRequestForUpdate(tenantId, requestId);
        int expectedVersion = requireExpectedVersion(command == null ? null : command.expectedVersion());
        assertVersion(current, expectedVersion);
        requireEditable(current);
        if (!Objects.equals(current.currentPropertyMode(), currentMode(tenantId))) {
            throw new PropertyManagementModeChangeApplicationException(
                    CONCURRENT_MODIFICATION, "小区当前物业管理模式已变化，请重新发起申请");
        }
        if (repository.countActiveMaterialsByType(
                requestId, PropertyManagementModeChangeMaterialType.OWNERS_ASSEMBLY_RESOLUTION.name()) < 1) {
            throw new PropertyManagementModeChangeApplicationException(
                    MATERIAL_REQUIRED, "提交前必须上传业主大会决议材料");
        }
        Instant now = Instant.now();
        PropertyManagementModeChangeRequest submitted = new PropertyManagementModeChangeRequest(
                current.requestId(), current.tenantId(), current.currentPropertyMode(),
                current.requestedPropertyMode(), current.ownersAssemblyResolutionReference(), current.changeReason(),
                PropertyManagementModeChangeStatus.SUBMITTED,
                current.applicantAccountId(), current.applicantUserId(), current.applicantDeptId(),
                now, null, null, null, null, null, null,
                current.version(), current.createdAt(), now);
        updateOrThrow(submitted, expectedVersion);
        repository.insertAudit(requestId, actor.accountId(), actor.userId(), actor.deptId(),
                "REQUEST_SUBMITTED", current.status().name(), submitted.status().name(),
                toJson(Map.of(
                        "ownersAssemblyResolutionReference", current.ownersAssemblyResolutionReference(),
                        "materialCount", repository.listMaterials(requestId).size())));
        return details(currentMode(tenantId), requireRequest(tenantId, requestId));
    }

    @Transactional
    public PropertyManagementModeChangeDetails review(
            Long requestId,
            ReviewPropertyManagementModeChangeCommand command) {
        PropertyManagementModeChangeDecision decision = command == null ? null : command.decision();
        UserContext actor = requireGovernmentReviewer(decision);
        Long tenantId = requireTenant(actor);
        PropertyManagementModeChangeRequest current = requireRequestForUpdate(tenantId, requestId);
        int expectedVersion = requireExpectedVersion(command == null ? null : command.expectedVersion());
        assertVersion(current, expectedVersion);
        if (current.status() != PropertyManagementModeChangeStatus.SUBMITTED) {
            throw new PropertyManagementModeChangeApplicationException(
                    INVALID_STATUS, "仅已提交的物业管理模式变更申请可以由街道办处理");
        }
        String reviewComment = trimOptional(command.reviewComment(), 1000);
        if (decision != PropertyManagementModeChangeDecision.EXECUTE && reviewComment == null) {
            throw new PropertyManagementModeChangeApplicationException(PARAM_INVALID, "退回或驳回时必须填写审核意见");
        }
        Instant now = Instant.now();
        PropertyManagementModeChangeStatus targetStatus = switch (decision) {
            case RETURN -> PropertyManagementModeChangeStatus.RETURNED;
            case REJECT -> PropertyManagementModeChangeStatus.REJECTED;
            case EXECUTE -> PropertyManagementModeChangeStatus.EXECUTED;
        };
        if (decision == PropertyManagementModeChangeDecision.EXECUTE) {
            int applied = repository.applyMode(
                    tenantId, current.currentPropertyMode(), current.requestedPropertyMode(),
                    modeHistoryJson(current, actor, now));
            if (applied != 1) {
                throw new PropertyManagementModeChangeApplicationException(
                        CONCURRENT_MODIFICATION, "小区当前物业管理模式已变化，不能执行该申请");
            }
        }
        PropertyManagementModeChangeRequest reviewed = new PropertyManagementModeChangeRequest(
                current.requestId(), current.tenantId(), current.currentPropertyMode(),
                current.requestedPropertyMode(), current.ownersAssemblyResolutionReference(), current.changeReason(),
                targetStatus, current.applicantAccountId(), current.applicantUserId(), current.applicantDeptId(),
                current.submittedAt(), actor.accountId(), actor.userId(), actor.deptId(), reviewComment,
                now, decision == PropertyManagementModeChangeDecision.EXECUTE ? now : null,
                current.version(), current.createdAt(), now);
        updateOrThrow(reviewed, expectedVersion);
        repository.insertAudit(requestId, actor.accountId(), actor.userId(), actor.deptId(),
                switch (decision) {
                    case RETURN -> "REQUEST_RETURNED";
                    case REJECT -> "REQUEST_REJECTED";
                    case EXECUTE -> "MODE_EXECUTED";
                }, current.status().name(), targetStatus.name(),
                toJson(reviewAuditPayload(current, decision, reviewComment)));
        return details(currentMode(tenantId), requireRequest(tenantId, requestId));
    }

    private PropertyManagementModeChangeDetails details(
            PropertyManagementMode effectiveMode,
            PropertyManagementModeChangeRequest request) {
        return new PropertyManagementModeChangeDetails(
                effectiveMode,
                request,
                repository.listMaterials(request.requestId()),
                repository.listAudits(request.requestId()));
    }

    private PropertyManagementModeChangeRequest requireRequest(Long tenantId, Long requestId) {
        if (requestId == null || requestId <= 0) {
            throw new PropertyManagementModeChangeApplicationException(PARAM_INVALID, "物业管理模式变更申请编号非法");
        }
        return repository.findByTenantAndId(tenantId, requestId)
                .orElseThrow(() -> new PropertyManagementModeChangeApplicationException(
                        NOT_FOUND, "物业管理模式变更申请不存在或不属于当前小区"));
    }

    private PropertyManagementModeChangeRequest requireRequestForUpdate(Long tenantId, Long requestId) {
        if (requestId == null || requestId <= 0) {
            throw new PropertyManagementModeChangeApplicationException(PARAM_INVALID, "物业管理模式变更申请编号非法");
        }
        return repository.findByTenantAndIdForUpdate(tenantId, requestId)
                .orElseThrow(() -> new PropertyManagementModeChangeApplicationException(
                        NOT_FOUND, "物业管理模式变更申请不存在或不属于当前小区"));
    }

    private PropertyManagementMode currentMode(Long tenantId) {
        TenantCommunity community = communitySettingsRepository.findCommunity(tenantId)
                .orElseThrow(() -> new PropertyManagementModeChangeApplicationException(
                        NOT_FOUND, "当前小区不存在或未启用"));
        return community.propertyManagementMode();
    }

    private UserContext requireViewer() {
        UserContext context = userContextHolder.current();
        if (context == null || !context.isSysUser()) {
            throw new PropertyManagementModeChangeApplicationException(UNAUTHORIZED, "请先登录管理端");
        }
        if (!context.hasPermission(READ_PERMISSION)) {
            throw new PropertyManagementModeChangeApplicationException(FORBIDDEN, "当前身份无权查看物业管理模式变更记录");
        }
        return context;
    }

    private Long requireViewerTenant() {
        return requireTenant(requireViewer());
    }

    private UserContext requireApplicant() {
        UserContext context = userContextHolder.current();
        if (context == null || !context.isSysUser()) {
            throw new PropertyManagementModeChangeApplicationException(UNAUTHORIZED, "请先登录管理端");
        }
        if (!COMMITTEE_DIRECTOR.equals(context.roleKey()) || !context.hasPermission(SUBMIT_PERMISSION)) {
            throw new PropertyManagementModeChangeApplicationException(
                    FORBIDDEN, "仅业委会主任可以依据业主大会决议发起物业管理模式变更申请");
        }
        return context;
    }

    private UserContext requireGovernmentReviewer(PropertyManagementModeChangeDecision decision) {
        if (decision == null) {
            throw new PropertyManagementModeChangeApplicationException(PARAM_INVALID, "街道办处理决定不能为空");
        }
        UserContext context = userContextHolder.current();
        if (context == null || !context.isSysUser()) {
            throw new PropertyManagementModeChangeApplicationException(UNAUTHORIZED, "请先登录管理端");
        }
        if (!GOV_SUPER_ADMIN.equals(context.roleKey()) || !context.hasPermission(REVIEW_PERMISSION)) {
            throw new PropertyManagementModeChangeApplicationException(
                    FORBIDDEN, "仅街道办管理员可以审核物业管理模式变更申请");
        }
        if (decision == PropertyManagementModeChangeDecision.EXECUTE && !context.hasPermission(EXECUTE_PERMISSION)) {
            throw new PropertyManagementModeChangeApplicationException(
                    FORBIDDEN, "当前街道办身份无权执行物业管理模式变更");
        }
        return context;
    }

    private Long requireTenant(UserContext context) {
        if (context == null || context.tenantId() == null) {
            throw new PropertyManagementModeChangeApplicationException(
                    PARAM_INVALID, "请先切换到需要办理的小区");
        }
        return context.tenantId();
    }

    private NormalizedChange normalize(UpsertPropertyManagementModeChangeCommand command) {
        if (command == null || command.requestedPropertyMode() == null) {
            throw new PropertyManagementModeChangeApplicationException(PARAM_INVALID, "目标物业管理模式不能为空");
        }
        return new NormalizedChange(
                command.requestedPropertyMode(),
                trimRequired(command.ownersAssemblyResolutionReference(), "业主大会决议依据", 2, 128),
                trimRequired(command.changeReason(), "变更原因", 5, 1000));
    }

    private void assertDifferentMode(PropertyManagementMode current, PropertyManagementMode requested) {
        if (current != null && current == requested) {
            throw new PropertyManagementModeChangeApplicationException(
                    PARAM_INVALID, "目标物业管理模式与当前已生效模式一致，无需发起变更申请");
        }
    }

    private void requireEditable(PropertyManagementModeChangeRequest request) {
        if (!request.status().editable()) {
            throw new PropertyManagementModeChangeApplicationException(
                    INVALID_STATUS, "当前状态不允许修改物业管理模式变更申请或材料");
        }
    }

    private int requireExpectedVersion(Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new PropertyManagementModeChangeApplicationException(PARAM_INVALID, "申请版本号不能为空");
        }
        return expectedVersion;
    }

    private void assertVersion(PropertyManagementModeChangeRequest request, int expectedVersion) {
        if (request.version() != expectedVersion) {
            throw new PropertyManagementModeChangeApplicationException(
                    CONCURRENT_MODIFICATION, "物业管理模式变更申请已被更新，请刷新后重试");
        }
    }

    private void updateOrThrow(PropertyManagementModeChangeRequest request, int expectedVersion) {
        if (repository.updateRequest(request, expectedVersion) != 1) {
            throw new PropertyManagementModeChangeApplicationException(
                    CONCURRENT_MODIFICATION, "物业管理模式变更申请已被并发更新，请刷新后重试");
        }
    }

    private String normalizeContentType(String contentType) {
        String normalized = trimOptional(contentType, 100);
        if (normalized == null) {
            throw new PropertyManagementModeChangeApplicationException(PARAM_INVALID, "材料内容类型不能为空");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeFileName(String originalFileName) {
        String fileName = trimOptional(originalFileName, 255);
        if (fileName == null) {
            throw new PropertyManagementModeChangeApplicationException(PARAM_INVALID, "材料文件名不能为空");
        }
        return fileName.replaceAll("[\\r\\n]", "_");
    }

    private String materialObjectKey(PropertyManagementModeChangeRequest request, String contentType) {
        return "property-management-mode-change/" + request.tenantId() + "/" + request.requestId()
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

    private void deleteStoredObjectQuietly(String objectKey) {
        try {
            materialStorage.delete(objectKey);
        } catch (RuntimeException ignored) {
            // 原始存储失败会通过调用方的稳定错误契约返回；清理失败不覆盖该根因。
        }
    }

    private String digestBase64(String algorithm, byte[] content) {
        return Base64.getEncoder().encodeToString(digest(algorithm, content));
    }

    private String digestHex(String algorithm, byte[] content) {
        return HexFormat.of().formatHex(digest(algorithm, content));
    }

    private byte[] digest(String algorithm, byte[] content) {
        try {
            return MessageDigest.getInstance(algorithm).digest(content);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持摘要算法：" + algorithm, ex);
        }
    }

    private String modeHistoryJson(
            PropertyManagementModeChangeRequest request,
            UserContext actor,
            Instant executedAt) {
        Map<String, Object> history = new LinkedHashMap<>();
        history.put("from", modeName(request.currentPropertyMode()));
        history.put("to", request.requestedPropertyMode().name());
        history.put("source", "GOVERNMENT_EXECUTED_CHANGE");
        history.put("requestId", request.requestId());
        history.put("ownersAssemblyResolutionReference", request.ownersAssemblyResolutionReference());
        history.put("reviewerAccountId", actor.accountId());
        history.put("reviewerUserId", actor.userId());
        history.put("reviewerDeptId", actor.deptId());
        history.put("executedAt", executedAt.toString());
        return toJson(List.of(history));
    }

    private Map<String, Object> reviewAuditPayload(
            PropertyManagementModeChangeRequest request,
            PropertyManagementModeChangeDecision decision,
            String reviewComment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decision", decision.name());
        payload.put("currentPropertyMode", modeName(request.currentPropertyMode()));
        payload.put("requestedPropertyMode", request.requestedPropertyMode().name());
        payload.put("ownersAssemblyResolutionReference", request.ownersAssemblyResolutionReference());
        payload.put("reviewComment", reviewComment);
        return payload;
    }

    private Map<String, Object> createdAuditPayload(
            PropertyManagementMode currentMode,
            PropertyManagementModeChangeRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currentPropertyMode", modeName(currentMode));
        payload.put("requestedPropertyMode", request.requestedPropertyMode().name());
        payload.put("ownersAssemblyResolutionReference", request.ownersAssemblyResolutionReference());
        return payload;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("物业管理模式变更审计序列化失败", ex);
        }
    }

    private String modeName(PropertyManagementMode mode) {
        return mode == null ? null : mode.name();
    }

    private String trimRequired(String value, String field, int minLength, int maxLength) {
        String normalized = trimOptional(value, maxLength);
        if (normalized == null || normalized.length() < minLength) {
            throw new PropertyManagementModeChangeApplicationException(
                    PARAM_INVALID, field + "长度必须在 " + minLength + " 到 " + maxLength + " 个字符之间");
        }
        return normalized;
    }

    private String trimOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new PropertyManagementModeChangeApplicationException(
                    PARAM_INVALID, "输入内容不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private record NormalizedChange(
            PropertyManagementMode requestedPropertyMode,
            String ownersAssemblyResolutionReference,
            String changeReason
    ) {
    }
}
