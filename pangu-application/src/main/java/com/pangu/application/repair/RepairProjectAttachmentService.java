// 关联业务：校验、私有存储并受控下载维修工程项目级原始附件。
package com.pangu.application.repair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.repair.command.UploadRepairProjectAttachmentCommand;
import com.pangu.application.support.OwnerAccessibleFile;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairAcceptancePartyRole;
import com.pangu.domain.model.repair.RepairProjectSourcing.Invitation;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import com.pangu.domain.repository.RepairProjectExecutionRepository;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairProjectSourcingRepository;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.STORAGE_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class RepairProjectAttachmentService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Duration DOWNLOAD_URL_VALIDITY = Duration.ofMinutes(10);
    private static final Set<String> CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final RepairProjectRepository projectRepository;
    private final RepairProjectExecutionRepository executionRepository;
    private final RepairProjectSourcingRepository sourcingRepository;
    private final RepairEvidenceObjectStorage objectStorage;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;
    private final OwnerRepairProjectQueryService ownerProjectQueryService;
    private final OwnerRepairProjectVotingService ownerVotingService;

    @Transactional
    public Attachment upload(Long projectId, UploadRepairProjectAttachmentCommand command) {
        UserContext actor = requireActor();
        Long tenantId = resolveTenant(actor, projectId);
        RepairProject project = projectRepository.findProject(projectId, tenantId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "维修工程项目不存在"));
        assertProjectAccess(actor, project, true, null);
        return store(project, actor, command);
    }

    /** 受影响业主仅可在本人验收任务进行中上传验收证据，不能借此写入其他工程附件。 */
    @Transactional
    public Attachment uploadOwnerAcceptanceEvidence(
            Long projectId, UploadRepairProjectAttachmentCommand command) {
        UserContext owner = userContextHolder.current();
        if (owner == null || !owner.isCUser() || owner.accountId() == null
                || owner.uid() == null || owner.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到当前业主身份");
        }
        RepairProject project = projectRepository.findProject(projectId, owner.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "维修工程项目不存在"));
        if (project.status() != Status.PENDING_ACCEPTANCE) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前工程没有可提交的业主验收任务");
        }
        var policy = executionRepository.findAcceptancePolicy(projectId, owner.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(FORBIDDEN, "当前工程没有锁定验收安排"));
        boolean ownerRequired = policy.requirements().stream()
                .anyMatch(requirement -> requirement.eligibleRoles()
                        .contains(RepairAcceptancePartyRole.AFFECTED_OWNER));
        if (!ownerRequired || executionRepository.listAffectedOwnerRoomIds(
                policy.policyId(), owner.tenantId(), owner.uid()).isEmpty()) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前业主不在本工程验收范围内");
        }
        return store(project, owner, command);
    }

    private Attachment store(
            RepairProject project, UserContext actor, UploadRepairProjectAttachmentCommand command) {
        if (command == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "附件不能为空");
        }
        String fileName = normalizeFileName(command.originalFileName());
        String contentType = normalizeContentType(command.contentType());
        byte[] content = command.content() == null ? new byte[0] : command.content();
        validateContent(contentType, content.length);
        String objectKey = objectKey(project, contentType);
        RepairEvidenceObjectStorage.StoredObjectMetadata metadata;
        try {
            metadata = objectStorage.put(objectKey, content, contentType, digestBase64("MD5", content));
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "上传维修工程附件失败", ex);
        }
        validateStoredObject(objectKey, contentType, content.length, metadata);
        try {
            Attachment attachment = projectRepository.insertAttachment(new Attachment(
                    null, project.projectId(), project.tenantId(), objectKey, fileName, contentType,
                    (long) content.length, metadata.etag(), digestHex("SHA-256", content),
                    actor.accountId(), actor.userId(), null));
            if (actor.isCUser()) {
                projectRepository.insertOwnerEvent(
                        project.projectId(), project.tenantId(), "PROJECT_ATTACHMENT_UPLOADED",
                        actor.accountId(), actor.uid(), eventPayload(attachment.attachmentId()));
            } else {
                projectRepository.insertEvent(
                        project.projectId(), project.tenantId(), "PROJECT_ATTACHMENT_UPLOADED",
                        actor.accountId(), actor.userId(), eventPayload(attachment.attachmentId()));
            }
            return attachment;
        } catch (RuntimeException ex) {
            deleteQuietly(objectKey, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public RepairAttachmentDownloadTicket createDownloadTicket(Long projectId, Long attachmentId) {
        UserContext actor = requireActor();
        Long tenantId = resolveTenant(actor, projectId);
        RepairProject project = projectRepository.findProject(projectId, tenantId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "维修工程项目不存在"));
        Attachment attachment = projectRepository.findAttachment(attachmentId, projectId, tenantId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "维修工程附件不存在"));
        assertProjectAccess(actor, project, false, attachment);
        return createTicket(attachment);
    }

    @Transactional(readOnly = true)
    public RepairAttachmentDownloadTicket createOwnerDownloadTicket(Long workOrderId, Long attachmentId) {
        return createTicket(findOwnerDownloadAttachment(workOrderId, attachmentId));
    }

    @Transactional(readOnly = true)
    public OwnerAccessibleFile readOwnerDownloadAttachment(Long workOrderId, Long attachmentId) {
        return readContent(findOwnerDownloadAttachment(workOrderId, attachmentId));
    }

    private Attachment findOwnerDownloadAttachment(Long workOrderId, Long attachmentId) {
        OwnerRepairProjectDisclosure disclosure = ownerProjectQueryService.findPublishedByWorkOrder(workOrderId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        NOT_FOUND, "关联维修工程尚无可披露的锁定方案"));
        boolean published = disclosure.plan().attachments().stream()
                .anyMatch(attachment -> attachment.attachmentId().equals(attachmentId));
        if (!published) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "维修工程附件不在当前披露方案中");
        }
        UserContext owner = requireOwner();
        return projectRepository.findAttachment(
                        attachmentId, disclosure.projectId(), owner.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        NOT_FOUND, "维修工程附件不存在"));
    }

    @Transactional(readOnly = true)
    public RepairAttachmentDownloadTicket createOwnerDecisionDownloadTicket(
            Long decisionId, Long attachmentId) {
        return createTicket(findOwnerDecisionAttachment(decisionId, attachmentId));
    }

    @Transactional(readOnly = true)
    public OwnerAccessibleFile readOwnerDecisionAttachment(Long decisionId, Long attachmentId) {
        return readContent(findOwnerDecisionAttachment(decisionId, attachmentId));
    }

    private Attachment findOwnerDecisionAttachment(Long decisionId, Long attachmentId) {
        OwnerRepairProjectDisclosure disclosure = ownerProjectQueryService.findPublishedByDecision(decisionId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        NOT_FOUND, "在线表决不存在、已结束或当前业主无权查看方案"));
        boolean published = disclosure.plan().attachments().stream()
                .anyMatch(attachment -> attachment.attachmentId().equals(attachmentId));
        if (!published) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "维修工程附件不在当前披露方案中");
        }
        UserContext owner = requireOwner();
        return projectRepository.findAttachment(
                        attachmentId, disclosure.projectId(), owner.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        NOT_FOUND, "维修工程附件不存在"));
    }

    /** 表决期间只允许下载本次冻结方案已经列出的附件。 */
    @Transactional(readOnly = true)
    public RepairAttachmentDownloadTicket createOwnerVotingDownloadTicket(
            Long projectId, Long attachmentId) {
        return createTicket(findOwnerVotingAttachment(projectId, attachmentId));
    }

    /** 表决期间通过已备案 API 域名返回原文件，避免小程序依赖私有 OSS 域名。 */
    @Transactional(readOnly = true)
    public OwnerAccessibleFile readOwnerVotingAttachment(Long projectId, Long attachmentId) {
        return readContent(findOwnerVotingAttachment(projectId, attachmentId));
    }

    private Attachment findOwnerVotingAttachment(Long projectId, Long attachmentId) {
        OwnerRepairProjectVotingService.Disclosure disclosure = ownerVotingService.disclosure(projectId);
        boolean published = disclosure.attachments().stream()
                .anyMatch(attachment -> attachment.attachmentId().equals(attachmentId));
        if (!published) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "维修工程附件不在本次表决方案中");
        }
        UserContext owner = requireOwner();
        return projectRepository.findAttachment(
                        attachmentId, disclosure.projectId(), owner.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        NOT_FOUND, "维修工程附件不存在"));
    }

    private OwnerAccessibleFile readContent(Attachment attachment) {
        byte[] content;
        try {
            content = objectStorage.read(attachment.objectKey());
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "读取维修工程附件失败", ex);
        }
        if (content == null
                || attachment.fileSize() == null
                || attachment.fileSize() != content.length
                || attachment.sha256() == null
                || !attachment.sha256().equals(digestHex("SHA-256", content))) {
            throw new RepairWorkOrderApplicationException(
                    STORAGE_UNAVAILABLE, "维修工程附件内容与上传记录不一致");
        }
        return new OwnerAccessibleFile(
                attachment.originalFileName(), attachment.contentType(), content);
    }

    private RepairAttachmentDownloadTicket createTicket(Attachment attachment) {
        Instant expiresAt = Instant.now().plus(DOWNLOAD_URL_VALIDITY);
        try {
            return new RepairAttachmentDownloadTicket(
                    attachment.attachmentId(),
                    objectStorage.createDownloadUrl(attachment.objectKey(), DOWNLOAD_URL_VALIDITY).toString(),
                    expiresAt);
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "生成维修工程附件下载地址失败", ex);
        }
    }

    private void validateContent(String contentType, long size) {
        if (!CONTENT_TYPES.contains(contentType)) {
            throw new RepairWorkOrderApplicationException(
                    PARAM_INVALID, "项目附件仅支持 PDF、图片、Word 或 Excel 文件");
        }
        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "项目附件大小必须在 20MB 以内");
        }
    }

    private void validateStoredObject(
            String objectKey,
            String contentType,
            long expectedSize,
            RepairEvidenceObjectStorage.StoredObjectMetadata metadata) {
        if (metadata == null || metadata.size() != expectedSize
                || metadata.contentType() == null
                || !contentType.equals(normalizeContentType(metadata.contentType()))
                || metadata.etag() == null || metadata.etag().isBlank()) {
            RepairWorkOrderApplicationException failure = new RepairWorkOrderApplicationException(
                    PARAM_INVALID, "附件存储返回的大小、类型或 ETag 与原文件不一致");
            deleteQuietly(objectKey, failure);
            throw failure;
        }
    }

    private String objectKey(RepairProject project, String contentType) {
        return "repair-projects/tenant-" + project.tenantId()
                + "/project-" + project.projectId()
                + "/" + LocalDate.now()
                + "/" + UUID.randomUUID() + extension(contentType);
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            case "application/vnd.ms-excel" -> ".xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx";
            default -> "";
        };
    }

    private String normalizeFileName(String value) {
        if (value == null || value.isBlank()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "originalFileName 必填");
        }
        String normalized = value.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "originalFileName 长度不合法");
        }
        return normalized;
    }

    private String normalizeContentType(String value) {
        if (value == null) {
            return "";
        }
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
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
            objectStorage.delete(objectKey);
        } catch (RuntimeException cleanupError) {
            failure.addSuppressed(cleanupError);
        }
    }

    private UserContext requireActor() {
        UserContext actor = userContextHolder.current();
        if (actor == null || !actor.isSysUser() || actor.userId() == null
                || actor.accountId() == null
                || (actor.tenantId() == null && !isSupplier(actor))) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到当前小区管理端工作身份");
        }
        return actor;
    }

    private UserContext requireOwner() {
        UserContext owner = userContextHolder.current();
        if (owner == null || !owner.isCUser() || owner.uid() == null || owner.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到当前业主身份");
        }
        return owner;
    }

    private Long resolveTenant(UserContext actor, Long projectId) {
        if (actor.tenantId() != null) {
            return actor.tenantId();
        }
        if (isSupplier(actor) && actor.deptId() != null) {
            return sourcingRepository.findLatestSupplierProjectInvitation(projectId, actor.deptId())
                    .map(Invitation::tenantId)
                    .orElseThrow(() -> new RepairWorkOrderApplicationException(
                            FORBIDDEN, "当前供应商未获该维修工程邀价"));
        }
        throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到维修工程所属小区");
    }

    private boolean isSupplier(UserContext actor) {
        return Set.of("SERVICE_PROVIDER_MANAGER", "SERVICE_PROVIDER_STAFF").contains(actor.roleKey());
    }

    private void assertProjectAccess(
            UserContext actor, RepairProject project, boolean writing, Attachment attachment) {
        String role = actor.roleKey();
        if (Set.of("PROPERTY_MANAGER", "PROPERTY_STAFF", "COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER")
                .contains(role)) {
            return;
        }
        if ("OWNER_REPRESENTATIVE".equals(role)) {
            boolean ownsBuilding = project.buildingId() != null
                    && (actor.authorizedBuildingIds().contains(project.buildingId())
                    || actor.authorizedBuildingScopes().stream().anyMatch(scope ->
                    scope.tenantId().equals(project.tenantId())
                            && scope.buildingId().equals(project.buildingId())));
            if (ownsBuilding) {
                return;
            }
        }
        if (isSupplier(actor)) {
            Long draftPlanId = projectRepository.listPlans(project.projectId(), project.tenantId()).stream()
                    .filter(plan -> plan.status() == RepairProject.PlanStatus.DRAFT)
                    .map(RepairProject.PlanVersion::planId)
                    .findFirst()
                    .orElse(null);
            boolean invitedForDraft = Set.of(Status.DRAFT, Status.PLAN_LOCKED).contains(project.status())
                    && draftPlanId != null
                    && actor.deptId() != null
                    && sourcingRepository.supplierInvited(
                    project.projectId(), draftPlanId, project.tenantId(), actor.deptId());
            boolean ownDraftAttachment = attachment == null
                    || actor.accountId().equals(attachment.uploadedByAccountId());
            if (invitedForDraft && (writing || ownDraftAttachment)) {
                return;
            }
            boolean selectedSupplier = project.activePlanId() != null
                    && sourcingRepository.findCurrentSelection(
                    project.projectId(), project.activePlanId(), project.tenantId())
                    .map(selection -> actor.deptId() != null
                            && actor.deptId().equals(selection.supplierDeptId()))
                    .orElse(false);
            if (selectedSupplier && project.status() == Status.AUTHORIZED
                    && (writing || ownDraftAttachment)) {
                return;
            }
            boolean contractSupplier = executionRepository.findContract(project.projectId(), project.tenantId())
                    .map(contract -> actor.deptId() != null && actor.deptId().equals(contract.supplierDeptId()))
                    .orElse(false);
            boolean executionStage = Set.of(
                    Status.CONTRACT_EFFECTIVE, Status.IN_PROGRESS, Status.PENDING_ACCEPTANCE)
                    .contains(project.status())
                    || (!writing && project.status() == Status.COMPLETED);
            if (contractSupplier && executionStage) {
                return;
            }
        }
        throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前工作身份无权访问该维修工程附件");
    }

    private String eventPayload(Long attachmentId) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("attachmentId", attachmentId));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("维修工程附件审计事件序列化失败", ex);
        }
    }
}
