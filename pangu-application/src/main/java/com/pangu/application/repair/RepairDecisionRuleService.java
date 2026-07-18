// 关联业务：登记、替代、查询并受控预览小区维修征询规则备案版本。
package com.pangu.application.repair;

import com.pangu.application.repair.command.RegisterRepairDecisionRuleCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairDecisionRule;
import com.pangu.domain.repository.RepairDecisionRuleDocumentStorage;
import com.pangu.domain.repository.RepairDecisionRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.STORAGE_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class RepairDecisionRuleService {

    private static final String POLICY_WRITE = "community:settings:policy:write";
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Duration PREVIEW_VALIDITY = Duration.ofMinutes(10);
    private static final Set<String> CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final RepairDecisionRuleRepository repository;
    private final RepairDecisionRuleDocumentStorage storage;
    private final UserContextHolder userContextHolder;

    @Transactional(readOnly = true)
    public List<RepairDecisionRule> list(Long requestedTenantId) {
        UserContext actor = requireActor();
        return repository.listByTenant(resolveTenant(actor, requestedTenantId));
    }

    @Transactional(readOnly = true)
    public RepairDecisionRule active(Long requestedTenantId) {
        UserContext actor = requireActor();
        Long tenantId = resolveTenant(actor, requestedTenantId);
        return repository.findActive(tenantId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        NOT_FOUND, "当前小区尚未备案有效的维修征询规则"));
    }

    @Transactional
    public RepairDecisionRule register(Long requestedTenantId, RegisterRepairDecisionRuleCommand command) {
        UserContext actor = requireActor();
        if (!actor.hasPermission(POLICY_WRITE)) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前角色无权备案维修征询规则");
        }
        Long tenantId = resolveTenant(actor, requestedTenantId);
        if (command == null || command.nonResponseRule() == null || command.effectiveDate() == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "规则、生效日期和未表态规则均为必填项");
        }
        if (command.effectiveDate().isAfter(LocalDate.now())) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "只能启用已经生效的备案规则版本");
        }
        String ruleName = requireText(command.ruleName(), "ruleName", 200);
        String ruleVersion = requireText(command.ruleVersion(), "ruleVersion", 64);
        String deliveryRule = requireText(command.deliveryRule(), "deliveryRule", 1000);
        String fileName = normalizeFileName(command.originalFileName());
        String contentType = normalizeContentType(command.contentType());
        byte[] content = command.content() == null ? new byte[0] : command.content();
        validateFile(contentType, content.length);
        String objectKey = objectKey(tenantId, contentType);
        RepairDecisionRuleDocumentStorage.StoredObjectMetadata metadata;
        try {
            metadata = storage.put(objectKey, content, contentType, digestBase64("MD5", content));
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "上传维修征询规则原件失败", ex);
        }
        validateStoredObject(objectKey, contentType, content.length, metadata);
        try {
            repository.supersedeActive(tenantId);
            return repository.insert(new RepairDecisionRule(
                    null, tenantId, ruleName, ruleVersion, command.effectiveDate().atStartOfDay(),
                    deliveryRule, command.nonResponseRule(), objectKey, fileName, contentType,
                    (long) content.length, metadata.etag(), digestHex("SHA-256", content),
                    RepairDecisionRule.Status.ACTIVE, actor.accountId(), actor.userId(), null, null));
        } catch (RuntimeException ex) {
            deleteQuietly(objectKey, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public RepairDecisionRulePreviewTicket createPreviewTicket(Long ruleId, Long requestedTenantId) {
        UserContext actor = requireActor();
        Long tenantId = resolveTenant(actor, requestedTenantId);
        RepairDecisionRule rule = repository.findById(ruleId, tenantId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "维修征询规则不存在"));
        Instant expiresAt = Instant.now().plus(PREVIEW_VALIDITY);
        try {
            return new RepairDecisionRulePreviewTicket(
                    rule.ruleId(),
                    storage.createPreviewUrl(rule.objectKey(), rule.originalFileName(), PREVIEW_VALIDITY).toString(),
                    expiresAt);
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "生成规则原件预览地址失败", ex);
        }
    }

    private Long resolveTenant(UserContext actor, Long requestedTenantId) {
        Long tenantId = requestedTenantId == null ? actor.tenantId() : requestedTenantId;
        if (tenantId == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "未指定小区租户");
        }
        if (actor.tenantId() != null && !actor.tenantId().equals(tenantId)) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "不能跨小区读取或备案维修征询规则");
        }
        return tenantId;
    }

    private UserContext requireActor() {
        UserContext actor = userContextHolder.current();
        if (actor == null || !actor.isSysUser() || actor.accountId() == null || actor.userId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到管理端工作身份");
        }
        return actor;
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, field + " 必填");
        }
        if (normalized.length() > maxLength) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, field + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String normalizeFileName(String value) {
        String normalized = requireText(value, "originalFileName", 255).replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    }

    private String normalizeContentType(String value) {
        return value == null ? "" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private void validateFile(String contentType, long size) {
        if (!CONTENT_TYPES.contains(contentType)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "备案规则原件仅支持 PDF、DOC 或 DOCX 文件");
        }
        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "备案规则原件大小必须在 20MB 以内");
        }
    }

    private void validateStoredObject(String objectKey, String contentType, long expectedSize,
                                      RepairDecisionRuleDocumentStorage.StoredObjectMetadata metadata) {
        if (metadata == null || metadata.size() != expectedSize
                || !contentType.equals(normalizeContentType(metadata.contentType()))
                || metadata.etag() == null || metadata.etag().isBlank()) {
            RepairWorkOrderApplicationException failure = new RepairWorkOrderApplicationException(
                    PARAM_INVALID, "规则原件存储返回的大小、类型或 ETag 与原文件不一致");
            deleteQuietly(objectKey, failure);
            throw failure;
        }
    }

    private String objectKey(Long tenantId, String contentType) {
        return "repair-decision-rules/tenant-" + tenantId + "/" + LocalDate.now()
                + "/" + UUID.randomUUID() + extension(contentType);
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            default -> "";
        };
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
            throw new IllegalStateException("JVM 缺少文件摘要算法 " + algorithm, ex);
        }
    }

    private void deleteQuietly(String objectKey, RuntimeException failure) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }
}
