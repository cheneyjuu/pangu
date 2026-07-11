package com.pangu.application.repair;

import com.pangu.application.repair.command.UploadRepairAttachmentCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairAttachment;
import com.pangu.domain.model.repair.RepairAttachmentKind;
import com.pangu.domain.model.repair.RepairAttachmentStatus;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.domain.repository.RepairAttachmentRepository;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.STORAGE_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class RepairAttachmentService {

    private static final Duration DOWNLOAD_URL_VALIDITY = Duration.ofMinutes(10);
    private static final long MAX_IMAGE_SIZE = 8L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 20L * 1024 * 1024;
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
    private static final Set<String> VIDEO_CONTENT_TYPES = Set.of("video/mp4", "video/quicktime");
    private static final Set<String> FIELD_ROLES = Set.of(
            "PROPERTY_STAFF", "PROPERTY_MANAGER", "GRID_MEMBER", "VOLUNTEER", "OWNER_REPRESENTATIVE");

    private final RepairAttachmentRepository attachmentRepository;
    private final RepairWorkOrderRepository workOrderRepository;
    private final RepairEvidenceObjectStorage objectStorage;
    private final UserContextHolder userContextHolder;

    @Transactional
    public RepairAttachment upload(Long workOrderId, UploadRepairAttachmentCommand command) {
        UserContext ctx = requireFieldContext();
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        RepairAttachmentKind kind = parseKind(command.attachmentKind());
        assertUploadStatus(order, kind);
        String contentType = normalizeContentType(command.contentType());
        byte[] content = command.content() == null ? new byte[0] : command.content();
        long fileSize = content.length;
        validateMedia(kind, contentType, fileSize);
        assertUploadCount(order, kind);

        String objectKey = objectKey(order, kind, contentType);
        RepairEvidenceObjectStorage.StoredObjectMetadata metadata;
        try {
            metadata = objectStorage.put(objectKey, content, contentType, contentMd5(content));
        } catch (RuntimeException ex) {
            throw storageException("Java OSS SDK 上传附件失败", ex);
        }
        if (metadata.size() != fileSize) {
            deleteUploadedObject(objectKey);
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "OSS 对象大小与上传文件不一致");
        }
        if (metadata.contentType() == null
                || !normalizeContentType(metadata.contentType()).equals(contentType)) {
            deleteUploadedObject(objectKey);
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "OSS 对象类型与上传文件不一致");
        }
        if (metadata.etag() == null || metadata.etag().isBlank()) {
            deleteUploadedObject(objectKey);
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "OSS 对象缺少 ETag 完整性标识");
        }
        try {
            return attachmentRepository.insert(new RepairAttachment(
                    null, order.workOrderId(), order.tenantId(), kind, objectKey,
                    normalizeFileName(command.originalFileName()), contentType, fileSize,
                    metadata.size(), metadata.etag(), RepairAttachmentStatus.READY, ctx.accountId(),
                    null, null, LocalDateTime.now()));
        } catch (RuntimeException ex) {
            try {
                objectStorage.delete(objectKey);
            } catch (RuntimeException cleanupError) {
                ex.addSuppressed(cleanupError);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public RepairAttachmentDownloadTicket createDownloadTicket(Long workOrderId, Long attachmentId) {
        UserContext ctx = requireFieldContext();
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        RepairAttachment attachment = loadAttachment(order, attachmentId);
        if (attachment.status() == RepairAttachmentStatus.PENDING) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "附件尚未完成上传确认");
        }
        Instant expiresAt = Instant.now().plus(DOWNLOAD_URL_VALIDITY);
        try {
            return new RepairAttachmentDownloadTicket(attachmentId,
                    objectStorage.createDownloadUrl(attachment.objectKey(), DOWNLOAD_URL_VALIDITY).toString(),
                    expiresAt);
        } catch (RuntimeException ex) {
            throw storageException("生成 OSS 下载地址失败", ex);
        }
    }

    @Transactional
    public void deleteUnbound(Long workOrderId, Long attachmentId) {
        UserContext ctx = requireFieldContext();
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        RepairAttachment attachment = loadAttachment(order, attachmentId);
        if (attachment.status() == RepairAttachmentStatus.BOUND) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "已绑定审计流水的附件不可删除");
        }
        if (attachmentRepository.deleteUnbound(attachmentId, workOrderId, order.tenantId()) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "附件状态已变化，请刷新后重试");
        }
        try {
            objectStorage.delete(attachment.objectKey());
        } catch (RuntimeException ex) {
            throw storageException("删除 OSS 附件失败", ex);
        }
    }

    private void assertUploadCount(RepairWorkOrder order, RepairAttachmentKind kind) {
        int limit = kind == RepairAttachmentKind.SURVEY_VIDEO ? 1 : 3;
        if (attachmentRepository.countActive(order.workOrderId(), order.tenantId(), kind) >= limit) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    kind == RepairAttachmentKind.SURVEY_VIDEO ? "现场初勘最多上传 1 段视频" : "现场证据图片最多上传 3 张");
        }
    }

    private void validateMedia(RepairAttachmentKind kind, String contentType, long fileSize) {
        boolean video = kind == RepairAttachmentKind.SURVEY_VIDEO;
        Set<String> allowed = video ? VIDEO_CONTENT_TYPES : IMAGE_CONTENT_TYPES;
        long limit = video ? MAX_VIDEO_SIZE : MAX_IMAGE_SIZE;
        if (!allowed.contains(contentType)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    video ? "视频仅支持 MP4 或 MOV" : "图片仅支持 JPEG、PNG、WebP、HEIC");
        }
        if (fileSize <= 0 || fileSize > limit) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    video ? "视频大小必须在 20MB 以内" : "单张图片大小必须在 8MB 以内");
        }
    }

    private void assertUploadStatus(RepairWorkOrder order, RepairAttachmentKind kind) {
        boolean allowed = switch (kind) {
            case LOCATION_IMAGE -> Set.of(
                    RepairWorkOrderStatus.SUBMITTED,
                    RepairWorkOrderStatus.NEED_MANUAL_LOCATION,
                    RepairWorkOrderStatus.PENDING_VERIFY).contains(order.status());
            case SURVEY_IMAGE, SURVEY_VIDEO -> order.status() == RepairWorkOrderStatus.SURVEYING;
        };
        if (!allowed) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS,
                    "当前工单状态不允许上传该类现场证据 status=" + order.status());
        }
    }

    private RepairAttachmentKind parseKind(String value) {
        try {
            return RepairAttachmentKind.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "不支持的维修附件类型");
        }
    }

    private String normalizeContentType(String value) {
        if (value == null) {
            return "";
        }
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String contentMd5(byte[] content) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 不支持 MD5", ex);
        }
    }

    private void deleteUploadedObject(String objectKey) {
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException ignored) {
            // Keep the original validation error; orphan cleanup is handled operationally.
        }
    }

    private String normalizeFileName(String value) {
        String normalized = value == null ? "现场证据" : value.trim();
        normalized = normalized.replaceAll("[\\r\\n\\t/\\\\]", "_");
        if (normalized.isBlank()) {
            normalized = "现场证据";
        }
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String objectKey(RepairWorkOrder order, RepairAttachmentKind kind, String contentType) {
        LocalDate today = LocalDate.now();
        return "repair/%d/%d/%s/%04d/%02d/%s.%s".formatted(
                order.tenantId(), order.workOrderId(), kind.name().toLowerCase(Locale.ROOT),
                today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension(contentType));
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/heic" -> "heic";
            case "image/heif" -> "heif";
            case "video/quicktime" -> "mov";
            default -> "mp4";
        };
    }

    private RepairAttachment loadAttachment(RepairWorkOrder order, Long attachmentId) {
        return attachmentRepository.findById(attachmentId, order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "附件不存在或不属于当前工单"));
    }

    private RepairWorkOrder loadVisible(UserContext ctx, Long workOrderId) {
        RepairWorkOrder order = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "工单不存在"));
        boolean ownerGroup = "OWNER_GROUP".equals(
                ctx.dataScopeType() == null ? null : ctx.dataScopeType().getValue());
        if (!ownerGroup && ctx.tenantId() != null && !ctx.tenantId().equals(order.tenantId())) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "工单不在当前租户范围内");
        }
        if (ownerGroup) {
            boolean assigned = ctx.userId() != null && ctx.userId().equals(order.assignedUserId());
            boolean buildingAllowed = order.buildingId() != null
                    && ctx.authorizedBuildingScopes().contains(
                    new WorkIdentityBuildingScope(order.tenantId(), order.buildingId()));
            if (!assigned && !buildingAllowed) {
                throw new RepairWorkOrderApplicationException(NOT_FOUND, "工单不在当前责任田范围内");
            }
        }
        return order;
    }

    private UserContext requireFieldContext() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isSysUser() || ctx.roleKey() == null || !FIELD_ROLES.contains(ctx.roleKey())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前身份无权处理维修现场附件");
        }
        return ctx;
    }

    private RepairWorkOrderApplicationException storageException(String message, RuntimeException cause) {
        return new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, message, cause);
    }
}
