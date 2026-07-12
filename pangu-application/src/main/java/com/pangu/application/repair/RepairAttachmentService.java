package com.pangu.application.repair;

import com.pangu.application.repair.command.UploadRepairAttachmentCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairAttachment;
import com.pangu.domain.model.repair.RepairAttachmentKind;
import com.pangu.domain.model.repair.RepairAttachmentStatus;
import com.pangu.domain.model.repair.RepairSource;
import com.pangu.domain.model.repair.RepairSpaceScope;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.domain.repository.RepairAttachmentRepository;
import com.pangu.domain.repository.RepairDocumentPreviewConverter;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
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
@Slf4j
public class RepairAttachmentService {

    private static final Duration DOWNLOAD_URL_VALIDITY = Duration.ofMinutes(10);
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String EXCEL_PREVIEW_RENDER_VERSION = "v1";
    private static final String PDF_PAGE_RENDER_VERSION = "v1";
    private static final long MAX_IMAGE_SIZE = 8L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 20L * 1024 * 1024;
    private static final long MAX_DOCUMENT_SIZE = 20L * 1024 * 1024;
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
    private static final Set<String> VIDEO_CONTENT_TYPES = Set.of("video/mp4", "video/quicktime");
    private static final Set<String> DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final Set<String> EXCEL_CONTENT_TYPES = Set.of(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final Set<String> FIELD_ROLES = Set.of(
            "PROPERTY_STAFF", "PROPERTY_MANAGER", "GRID_MEMBER", "VOLUNTEER", "OWNER_REPRESENTATIVE");
    private static final Set<String> SUPPLIER_ROLES = Set.of("SERVICE_PROVIDER_ADMIN", "SERVICE_PROVIDER_STAFF");

    private final RepairAttachmentRepository attachmentRepository;
    private final RepairWorkOrderRepository workOrderRepository;
    private final RepairEvidenceObjectStorage objectStorage;
    private final RepairDocumentPreviewConverter documentPreviewConverter;
    private final UserContextHolder userContextHolder;

    @Transactional
    public RepairAttachment upload(Long workOrderId, UploadRepairAttachmentCommand command) {
        RepairAttachmentKind kind = parseKind(command.attachmentKind());
        UserContext ctx = requireAttachmentContext(kind);
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
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

    @Transactional
    public RepairAttachment uploadIntakeAttachment(Long workOrderId, UploadRepairAttachmentCommand command) {
        UserContext ctx = requireAttachmentContext(RepairAttachmentKind.INTAKE_ATTACHMENT);
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        if (order.source() != RepairSource.ADMIN_PC || order.spaceScope() != RepairSpaceScope.PUBLIC) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "仅物业登记的公共报修可补充登记附件");
        }
        RepairAttachment attachment = upload(workOrderId, new UploadRepairAttachmentCommand(
                RepairAttachmentKind.INTAKE_ATTACHMENT.name(), command.originalFileName(),
                command.contentType(), command.content()));
        int bound = attachmentRepository.markBound(
                List.of(attachment.attachmentId()), order.workOrderId(), order.tenantId(), "ADMIN_REGISTER_PUBLIC");
        if (bound != 1) {
            deleteUploadedObject(attachment.objectKey());
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "登记附件绑定失败，请刷新后重试");
        }
        return attachmentRepository.findById(attachment.attachmentId(), order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "登记附件不存在"));
    }

    @Transactional(readOnly = true)
    public RepairAttachmentDownloadTicket createDownloadTicket(Long workOrderId, Long attachmentId) {
        RepairAttachment attachment = loadAccessibleAttachment(workOrderId, attachmentId);
        Instant expiresAt = Instant.now().plus(DOWNLOAD_URL_VALIDITY);
        try {
            return new RepairAttachmentDownloadTicket(attachmentId,
                    objectStorage.createDownloadUrl(attachment.objectKey(), DOWNLOAD_URL_VALIDITY).toString(),
                    expiresAt);
        } catch (RuntimeException ex) {
            throw storageException("生成 OSS 下载地址失败", ex);
        }
    }

    @Transactional(readOnly = true)
    public RepairAttachmentPreviewTicket createPreviewTicket(Long workOrderId, Long attachmentId) {
        RepairAttachment attachment = loadAccessibleAttachment(workOrderId, attachmentId);
        return buildPreviewTicket(attachment);
    }

    @Transactional(readOnly = true)
    public RepairAttachmentPreviewTicket createOwnerDecisionQuotePreviewTicket(Long decisionId, Long opid) {
        UserContext ctx = requireOwnerAttachmentContext();
        if (decisionId == null || opid == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "decisionId 和 opid 必填");
        }
        var decision = workOrderRepository.findOwnerLocalDecision(
                        decisionId, opid, ctx.uid(), ctx.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "表决不存在、已结束或房屋不在表决范围内"));
        if (decision.quoteAttachmentId() == null) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "推荐报价未上传附件");
        }
        RepairWorkOrder order = workOrderRepository.findById(decision.workOrderId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "工单不存在"));
        RepairAttachment attachment = loadAttachment(order, decision.quoteAttachmentId());
        if (attachment.kind() != RepairAttachmentKind.QUOTE_DOCUMENT
                || attachment.status() == RepairAttachmentStatus.PENDING) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "推荐报价附件不可预览");
        }
        return buildPreviewTicket(attachment);
    }

    private RepairAttachmentPreviewTicket buildPreviewTicket(RepairAttachment attachment) {
        Instant expiresAt = Instant.now().plus(DOWNLOAD_URL_VALIDITY);
        try {
            PreviewTarget preview = resolvePreviewTarget(attachment);
            String previewUrl = objectStorage.createPreviewUrl(
                    preview.objectKey(), preview.fileName(), DOWNLOAD_URL_VALIDITY).toString();
            return new RepairAttachmentPreviewTicket(
                    attachment.attachmentId(),
                    attachment.originalFileName(),
                    preview.contentType(),
                    attachment.actualSize(),
                    previewUrl,
                    createPagePreviewUrls(preview),
                    preview.converted(),
                    expiresAt);
        } catch (RepairWorkOrderApplicationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw storageException("生成 OSS 预览地址失败", ex);
        }
    }

    private List<String> createPagePreviewUrls(PreviewTarget preview) {
        if (!PDF_CONTENT_TYPE.equals(preview.contentType())) {
            return List.of(objectStorage.createPreviewUrl(
                    preview.objectKey(), preview.fileName(), DOWNLOAD_URL_VALIDITY).toString());
        }
        List<byte[]> pages = documentPreviewConverter.renderPdfPages(objectStorage.read(preview.objectKey()));
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<String> urls = new java.util.ArrayList<>(pages.size());
        for (int index = 0; index < pages.size(); index++) {
            byte[] page = pages.get(index);
            String pageObjectKey = preview.objectKey() + ".pages-" + PDF_PAGE_RENDER_VERSION
                    + "/" + (index + 1) + ".png";
            if (!objectStorage.exists(pageObjectKey)) {
                objectStorage.put(pageObjectKey, page, "image/png", contentMd5(page));
            }
            urls.add(objectStorage.createPreviewUrl(
                    pageObjectKey, "page-" + (index + 1) + ".png", DOWNLOAD_URL_VALIDITY).toString());
        }
        return List.copyOf(urls);
    }

    @Transactional
    public void deleteUnbound(Long workOrderId, Long attachmentId) {
        UserContext ctx = requireAttachmentContext(null);
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        RepairAttachment attachment = loadAttachment(order, attachmentId);
        assertSupplierAttachmentAccess(ctx, attachment);
        if (attachment.status() == RepairAttachmentStatus.BOUND) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "已绑定审计流水的附件不可删除");
        }
        if (attachmentRepository.deleteUnbound(attachmentId, workOrderId, order.tenantId()) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "附件状态已变化，请刷新后重试");
        }
        try {
            deleteDerivedPreview(attachment);
            objectStorage.delete(attachment.objectKey());
        } catch (RuntimeException ex) {
            throw storageException("删除 OSS 附件失败", ex);
        }
    }

    private PreviewTarget resolvePreviewTarget(RepairAttachment attachment) {
        if (!EXCEL_CONTENT_TYPES.contains(attachment.contentType())) {
            return new PreviewTarget(
                    attachment.objectKey(), attachment.originalFileName(), attachment.contentType(), false);
        }
        String previewObjectKey = previewObjectKey(attachment.objectKey());
        if (!objectStorage.exists(previewObjectKey)) {
            byte[] source = objectStorage.read(attachment.objectKey());
            byte[] pdf;
            try {
                pdf = documentPreviewConverter.convertExcelToPdf(
                        attachment.originalFileName(), attachment.contentType(), source);
            } catch (RuntimeException ex) {
                throw storageException("Excel 报价单转 PDF 失败，原件仍可下载", ex);
            }
            RepairEvidenceObjectStorage.StoredObjectMetadata metadata = objectStorage.put(
                    previewObjectKey, pdf, PDF_CONTENT_TYPE, contentMd5(pdf));
            if (metadata.size() != pdf.length
                    || !PDF_CONTENT_TYPE.equals(normalizeContentType(metadata.contentType()))
                    || metadata.etag() == null
                    || metadata.etag().isBlank()) {
                try {
                    objectStorage.delete(previewObjectKey);
                } catch (RuntimeException cleanupError) {
                    log.warn("Failed to clean invalid Excel preview object key={}", previewObjectKey, cleanupError);
                }
                throw new IllegalStateException("Excel 预览 PDF 对象完整性校验失败");
            }
        }
        return new PreviewTarget(
                previewObjectKey, replaceExtension(attachment.originalFileName(), "pdf"), PDF_CONTENT_TYPE, true);
    }

    private void deleteDerivedPreview(RepairAttachment attachment) {
        if (!EXCEL_CONTENT_TYPES.contains(attachment.contentType())) {
            return;
        }
        try {
            objectStorage.delete(previewObjectKey(attachment.objectKey()));
        } catch (RuntimeException ex) {
            log.warn("Failed to delete derived Excel preview attachmentId={}", attachment.attachmentId(), ex);
        }
    }

    private String previewObjectKey(String originalObjectKey) {
        return originalObjectKey + ".preview-" + EXCEL_PREVIEW_RENDER_VERSION + ".pdf";
    }

    private String replaceExtension(String fileName, String extension) {
        int separator = fileName.lastIndexOf('.');
        String baseName = separator > 0 ? fileName.substring(0, separator) : fileName;
        return baseName + "." + extension;
    }

    private RepairAttachment loadAccessibleAttachment(Long workOrderId, Long attachmentId) {
        UserContext ctx = requireAttachmentContext(null);
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        RepairAttachment attachment = loadAttachment(order, attachmentId);
        assertSupplierAttachmentAccess(ctx, attachment);
        if (attachment.status() == RepairAttachmentStatus.PENDING) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "附件尚未完成上传确认");
        }
        return attachment;
    }

    private void assertUploadCount(RepairWorkOrder order, RepairAttachmentKind kind) {
        int limit = switch (kind) {
            case SURVEY_VIDEO -> 1;
            case QUOTE_DOCUMENT -> 20;
            case APPROVAL_DOCUMENT -> 3;
            case SOLITAIRE_SCREENSHOT -> 3;
            case INTAKE_ATTACHMENT -> 5;
            default -> 3;
        };
        if (attachmentRepository.countActive(order.workOrderId(), order.tenantId(), kind) >= limit) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    switch (kind) {
                        case SURVEY_VIDEO -> "现场初勘最多上传 1 段视频";
                        case QUOTE_DOCUMENT -> "单个工单最多保留 20 份待提交报价原件";
                        case APPROVAL_DOCUMENT -> "单个工单最多保留 3 份待提交报审文件";
                        case SOLITAIRE_SCREENSHOT -> "微信接龙截图最多上传 3 张";
                        case INTAKE_ATTACHMENT -> "登记工单最多上传 5 个附件";
                        default -> "现场证据图片最多上传 3 张";
                    });
        }
    }

    private void validateMedia(RepairAttachmentKind kind, String contentType, long fileSize) {
        if (kind == RepairAttachmentKind.INTAKE_ATTACHMENT) {
            boolean supported = IMAGE_CONTENT_TYPES.contains(contentType) || "application/pdf".equals(contentType);
            if (!supported) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "登记附件仅支持图片或 PDF 文件");
            }
            if (fileSize <= 0 || fileSize > MAX_DOCUMENT_SIZE) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "单个登记附件大小必须在 20MB 以内");
            }
            return;
        }
        if (kind == RepairAttachmentKind.QUOTE_DOCUMENT || kind == RepairAttachmentKind.APPROVAL_DOCUMENT) {
            if (!DOCUMENT_CONTENT_TYPES.contains(contentType)) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                        kind == RepairAttachmentKind.APPROVAL_DOCUMENT
                                ? "报审文件仅支持 PDF、图片、Word 或 Excel 文件"
                                : "报价原件仅支持 PDF、图片、Word 或 Excel 文件");
            }
            if (fileSize <= 0 || fileSize > MAX_DOCUMENT_SIZE) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                        kind == RepairAttachmentKind.APPROVAL_DOCUMENT
                                ? "报审文件大小必须在 20MB 以内"
                                : "报价原件大小必须在 20MB 以内");
            }
            return;
        }
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
            case INTAKE_ATTACHMENT -> Set.of(
                    RepairWorkOrderStatus.SUBMITTED,
                    RepairWorkOrderStatus.NEED_MANUAL_LOCATION,
                    RepairWorkOrderStatus.PENDING_VERIFY).contains(order.status());
            case LOCATION_IMAGE -> Set.of(
                    RepairWorkOrderStatus.SUBMITTED,
                    RepairWorkOrderStatus.NEED_MANUAL_LOCATION,
                    RepairWorkOrderStatus.PENDING_VERIFY).contains(order.status());
            case SURVEY_IMAGE, SURVEY_VIDEO -> Set.of(
                    RepairWorkOrderStatus.SUBMITTED,
                    RepairWorkOrderStatus.NEED_MANUAL_LOCATION,
                    RepairWorkOrderStatus.PENDING_VERIFY,
                    RepairWorkOrderStatus.VERIFIED,
                    RepairWorkOrderStatus.ASSIGNED,
                    RepairWorkOrderStatus.SURVEYING).contains(order.status());
            case QUOTE_DOCUMENT -> Set.of(
                    RepairWorkOrderStatus.QUOTE_COLLECTING,
                    RepairWorkOrderStatus.QUOTE_SUBMITTED).contains(order.status());
            case APPROVAL_DOCUMENT -> Set.of(
                    RepairWorkOrderStatus.LOCAL_DECISION_PASSED,
                    RepairWorkOrderStatus.APPROVAL_DOCUMENT_PREPARING).contains(order.status());
            case SOLITAIRE_SCREENSHOT -> Set.of(
                    RepairWorkOrderStatus.LOCAL_DECISION_PENDING,
                    RepairWorkOrderStatus.LOCAL_DECISION_PASSED,
                    RepairWorkOrderStatus.APPROVAL_DOCUMENT_PREPARING).contains(order.status());
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
        String normalized = value == null ? "维修附件" : value.trim();
        normalized = normalized.replaceAll("[\\r\\n\\t/\\\\]", "_");
        if (normalized.isBlank()) {
            normalized = "维修附件";
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
            case "video/mp4" -> "mp4";
            case "application/pdf" -> "pdf";
            case "application/msword" -> "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.ms-excel" -> "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            default -> "bin";
        };
    }

    private RepairAttachment loadAttachment(RepairWorkOrder order, Long attachmentId) {
        return attachmentRepository.findById(attachmentId, order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "附件不存在或不属于当前工单"));
    }

    private void assertSupplierAttachmentAccess(UserContext ctx, RepairAttachment attachment) {
        if (SUPPLIER_ROLES.contains(ctx.roleKey())
                && (attachment.kind() != RepairAttachmentKind.QUOTE_DOCUMENT
                || !ctx.accountId().equals(attachment.uploadedByAccountId()))) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "附件不存在或不属于当前供应商");
        }
    }

    private RepairWorkOrder loadVisible(UserContext ctx, Long workOrderId) {
        RepairWorkOrder order = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "工单不存在"));
        if (SUPPLIER_ROLES.contains(ctx.roleKey())) {
            if (ctx.deptId() == null
                    || !workOrderRepository.supplierCanAccess(workOrderId, order.tenantId(), ctx.deptId())) {
                throw new RepairWorkOrderApplicationException(NOT_FOUND, "工单不在当前供应商邀价范围内");
            }
            return order;
        }
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

    private UserContext requireAttachmentContext(RepairAttachmentKind kind) {
        UserContext ctx = userContextHolder.current();
        boolean fieldUser = ctx != null && ctx.isSysUser() && FIELD_ROLES.contains(ctx.roleKey());
        boolean supplierUser = ctx != null && ctx.isSysUser() && SUPPLIER_ROLES.contains(ctx.roleKey());
        if (!fieldUser && !supplierUser) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前身份无权处理维修现场附件");
        }
        if (supplierUser && kind != null && kind != RepairAttachmentKind.QUOTE_DOCUMENT) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "供应商只能上传报价原件");
        }
        return ctx;
    }

    private UserContext requireOwnerAttachmentContext() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isCUser() || ctx.uid() == null || ctx.tenantId() == null
                || ctx.authLevel() == null || ctx.authLevel().getValue() < 2) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "仅实名业主可查看楼栋维修报价");
        }
        return ctx;
    }

    private RepairWorkOrderApplicationException storageException(String message, RuntimeException cause) {
        return new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, message, cause);
    }

    private record PreviewTarget(
            String objectKey, String fileName, String contentType, boolean converted) {
    }
}
