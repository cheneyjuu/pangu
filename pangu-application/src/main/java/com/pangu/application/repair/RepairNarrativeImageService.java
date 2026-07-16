// 关联业务：上传、绑定、签名展示并清理维修实施方案正文中的私有图片。
package com.pangu.application.repair;

import com.pangu.application.repair.command.UploadRepairProjectAttachmentCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairNarrativeImage;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import com.pangu.domain.repository.RepairNarrativeImageRepository;
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
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.STORAGE_UNAVAILABLE;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepairNarrativeImageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final Duration PREVIEW_URL_VALIDITY = Duration.ofMinutes(10);
    private static final Duration DRAFT_RETENTION = Duration.ofHours(24);
    private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Pattern IMAGE_TAG = Pattern.compile(
            "<img\\b[^>]*data-repair-image-id=\\\"(\\d+)\\\"[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALT_ATTRIBUTE = Pattern.compile("\\salt=\\\"([^\\\"]*)\\\"", Pattern.CASE_INSENSITIVE);

    private final RepairNarrativeImageRepository imageRepository;
    private final RepairEvidenceObjectStorage objectStorage;
    private final UserContextHolder userContextHolder;

    @Transactional
    public UploadResult upload(UploadRepairProjectAttachmentCommand command) {
        UserContext actor = requireActor();
        if (command == null) {
            throw invalid("正文图片不能为空");
        }
        String fileName = normalizeFileName(command.originalFileName());
        String contentType = normalizeContentType(command.contentType());
        byte[] content = command.content() == null ? new byte[0] : command.content();
        validateContent(contentType, content.length);
        String objectKey = objectKey(actor.tenantId(), contentType);
        RepairEvidenceObjectStorage.StoredObjectMetadata metadata;
        try {
            metadata = objectStorage.put(objectKey, content, contentType, digestBase64("MD5", content));
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "上传维修方案正文图片失败", ex);
        }
        validateStoredObject(objectKey, contentType, content.length, metadata);
        try {
            RepairNarrativeImage image = imageRepository.insert(new RepairNarrativeImage(
                    null, actor.tenantId(), null, null, objectKey, fileName, contentType,
                    (long) content.length, metadata.etag(), digestHex("SHA-256", content),
                    actor.accountId(), actor.userId(), RepairNarrativeImage.Status.DRAFT,
                    null, null));
            return new UploadResult(image, ticket(image));
        } catch (RuntimeException ex) {
            deleteQuietly(objectKey, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public PreviewTicket previewTicket(Long imageId) {
        UserContext actor = requireActor();
        RepairNarrativeImage image = imageRepository.findById(imageId, actor.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "正文图片不存在"));
        if (image.status() != RepairNarrativeImage.Status.DRAFT
                || !actor.accountId().equals(image.uploadedByAccountId())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "无权预览该正文图片");
        }
        return ticket(image);
    }

    @Transactional
    public void deleteDraft(Long imageId) {
        UserContext actor = requireActor();
        RepairNarrativeImage image = imageRepository.findById(imageId, actor.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "正文图片不存在"));
        if (image.status() != RepairNarrativeImage.Status.DRAFT
                || !actor.accountId().equals(image.uploadedByAccountId())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "无权删除该正文图片");
        }
        try {
            objectStorage.delete(image.objectKey());
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "删除维修方案正文图片失败", ex);
        }
        if (imageRepository.deleteDraft(imageId, actor.tenantId(), actor.accountId()) != 1) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "正文图片已绑定或已删除");
        }
    }

    @Transactional(readOnly = true)
    public void assertDraftImagesUsable(Collection<Long> imageIds, UserContext actor) {
        Set<Long> normalized = normalizedIds(imageIds);
        if (normalized.isEmpty()) {
            return;
        }
        var images = imageRepository.findByIds(normalized, actor.tenantId());
        boolean valid = images.size() == normalized.size() && images.stream().allMatch(image ->
                image.status() == RepairNarrativeImage.Status.DRAFT
                        && actor.accountId().equals(image.uploadedByAccountId()));
        if (!valid) {
            throw invalid("正文包含不存在、已绑定或不属于当前账号的图片");
        }
    }

    @Transactional
    public void bindDraftImages(Collection<Long> imageIds, UserContext actor, Long projectId, Long planId) {
        Set<Long> normalized = normalizedIds(imageIds);
        if (normalized.isEmpty()) {
            return;
        }
        int changed = imageRepository.bindDraftImages(
                normalized, actor.tenantId(), actor.accountId(), projectId, planId);
        if (changed != normalized.size()) {
            throw invalid("正文图片绑定失败，请刷新后重新上传");
        }
    }

    /** 将可信图片引用替换为短期签名地址；数据库与快照仍保留稳定 imageId。 */
    @Transactional(readOnly = true)
    public String resolveForPlan(Long planId, Long tenantId, String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        Set<Long> imageIds = extractImageIds(html);
        if (imageIds.isEmpty()) {
            return html;
        }
        Map<Long, RepairNarrativeImage> images = imageRepository.findByIds(imageIds, tenantId).stream()
                .filter(image -> image.status() == RepairNarrativeImage.Status.BOUND)
                .filter(image -> planId.equals(image.planId()))
                .collect(Collectors.toMap(RepairNarrativeImage::imageId, Function.identity()));
        Matcher matcher = IMAGE_TAG.matcher(html);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            Long imageId = Long.valueOf(matcher.group(1));
            RepairNarrativeImage image = images.get(imageId);
            String replacement = image == null ? "" : resolvedTag(image, matcher.group());
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    @Transactional
    public int cleanupExpiredDrafts() {
        LocalDateTime cutoff = LocalDateTime.now().minus(DRAFT_RETENTION);
        int removed = 0;
        for (RepairNarrativeImage image : imageRepository.listExpiredDrafts(cutoff, CLEANUP_BATCH_SIZE)) {
            try {
                objectStorage.delete(image.objectKey());
                removed += imageRepository.deleteExpiredDraft(image.imageId());
            } catch (RuntimeException ex) {
                log.warn("清理过期维修方案正文图片失败 imageId={}", image.imageId(), ex);
            }
        }
        return removed;
    }

    public Set<Long> extractImageIds(String html) {
        Set<Long> imageIds = new LinkedHashSet<>();
        if (html == null || html.isBlank()) {
            return imageIds;
        }
        Matcher matcher = IMAGE_TAG.matcher(html);
        while (matcher.find()) {
            imageIds.add(Long.valueOf(matcher.group(1)));
        }
        return imageIds;
    }

    private PreviewTicket ticket(RepairNarrativeImage image) {
        Instant expiresAt = Instant.now().plus(PREVIEW_URL_VALIDITY);
        try {
            return new PreviewTicket(
                    image.imageId(), objectStorage.createDownloadUrl(
                            image.objectKey(), PREVIEW_URL_VALIDITY).toString(), expiresAt);
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "生成正文图片预览地址失败", ex);
        }
    }

    private String resolvedTag(RepairNarrativeImage image, String canonicalTag) {
        Matcher altMatcher = ALT_ATTRIBUTE.matcher(canonicalTag);
        String alt = altMatcher.find() ? altMatcher.group(1) : image.originalFileName();
        String src;
        try {
            src = objectStorage.createDownloadUrl(image.objectKey(), PREVIEW_URL_VALIDITY).toString();
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "生成正文图片展示地址失败", ex);
        }
        return "<img src=\"" + escapeAttribute(src) + "\" alt=\"" + escapeAttribute(alt) + "\">";
    }

    private Set<Long> normalizedIds(Collection<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long imageId : imageIds) {
            if (imageId == null || imageId <= 0) {
                throw invalid("正文图片标识不合法");
            }
            normalized.add(imageId);
        }
        return normalized;
    }

    private void validateContent(String contentType, long size) {
        if (!CONTENT_TYPES.contains(contentType)) {
            throw invalid("正文图片仅支持 JPG、PNG 或 WebP");
        }
        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw invalid("正文图片大小必须在 5MB 以内");
        }
    }

    private void validateStoredObject(String objectKey, String contentType, long expectedSize,
                                      RepairEvidenceObjectStorage.StoredObjectMetadata metadata) {
        if (metadata == null || metadata.size() != expectedSize
                || metadata.contentType() == null
                || !contentType.equals(normalizeContentType(metadata.contentType()))
                || metadata.etag() == null || metadata.etag().isBlank()) {
            RepairWorkOrderApplicationException failure = invalid("正文图片存储校验失败");
            deleteQuietly(objectKey, failure);
            throw failure;
        }
    }

    private String objectKey(Long tenantId, String contentType) {
        return "repair-plan-images/tenant-" + tenantId + "/draft/" + LocalDate.now()
                + "/" + UUID.randomUUID() + extension(contentType);
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    private String normalizeFileName(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("originalFileName 必填");
        }
        String normalized = value.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw invalid("originalFileName 长度不合法");
        }
        return normalized;
    }

    private String normalizeContentType(String value) {
        return value == null ? "" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
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

    private String escapeAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private void deleteQuietly(String objectKey, RuntimeException failure) {
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException cleanupError) {
            failure.addSuppressed(cleanupError);
        }
    }

    private UserContext requireActor() {
        UserContext actor = userContextHolder.current();
        if (actor == null || !actor.isSysUser() || actor.userId() == null
                || actor.accountId() == null || actor.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到当前小区管理端工作身份");
        }
        return actor;
    }

    private RepairWorkOrderApplicationException invalid(String message) {
        return new RepairWorkOrderApplicationException(PARAM_INVALID, message);
    }

    public record PreviewTicket(Long imageId, String previewUrl, Instant expiresAt) {
    }

    public record UploadResult(RepairNarrativeImage image, PreviewTicket preview) {
    }
}
