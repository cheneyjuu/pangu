// 关联业务：办理正式表决书面委托的原件登记、异人核验、撤销和纸票绑定校验。
package com.pangu.application.voting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.support.PayloadHasher;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleSnapshot;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingProxyAuthorization;
import com.pangu.domain.repository.OwnersAssemblyRuleRepository;
import com.pangu.domain.repository.OwnersAssemblyRepository;
import com.pangu.domain.repository.VotingExecutionRepository;
import com.pangu.domain.repository.VotingProxyAuthorizationRepository;
import com.pangu.domain.repository.VotingProxyAuthorizationStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.pangu.application.voting.VotingProxyAuthorizationException.Reason.CONCURRENT_MODIFICATION;
import static com.pangu.application.voting.VotingProxyAuthorizationException.Reason.DUPLICATE;
import static com.pangu.application.voting.VotingProxyAuthorizationException.Reason.FORBIDDEN;
import static com.pangu.application.voting.VotingProxyAuthorizationException.Reason.INVALID_ARGUMENT;
import static com.pangu.application.voting.VotingProxyAuthorizationException.Reason.INVALID_STATUS;
import static com.pangu.application.voting.VotingProxyAuthorizationException.Reason.NOT_FOUND;
import static com.pangu.application.voting.VotingProxyAuthorizationException.Reason.STORAGE_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class VotingProxyAuthorizationService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Duration PREVIEW_VALIDITY = Duration.ofMinutes(10);
    private static final String AUDIT_PERMISSION = "voting:subject:audit";
    private static final Set<String> CONTENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private final VotingProxyAuthorizationRepository repository;
    private final VotingProxyAuthorizationStorage storage;
    private final VotingExecutionRepository votingExecutionRepository;
    private final OwnersAssemblyRuleRepository ruleRepository;
    private final OwnersAssemblyRepository ownersAssemblyRepository;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;

    @Transactional
    public VotingProxyAuthorization register(RegisterCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        UserContext actor = requireActor(command.tenantId(), command.registeredByUserId());
        VotingExecutionPackage ballotPackage = requirePackage(command.packageId(), command.tenantId());
        requireRegistrationStage(ballotPackage);
        requireWrittenAuthorizationRule(ballotPackage);
        VotingElectorateSnapshot.Item electorate = votingExecutionRepository.findElectorateItem(
                        command.packageId(), command.tenantId(), requirePositive(command.principalOpid(), "委托业主房屋"))
                .orElseThrow(() -> failure(NOT_FOUND, "所选房屋不在本次表决范围内"));
        requireValidity(command.validFrom(), command.validUntil(), ballotPackage);
        Instant registeredAt = requireInstant(command.registeredAt(), "登记时间");

        String agentName = requireText(command.agentName(), "代理人姓名", 100);
        VotingProxyAuthorization.IdentityDocumentType identityType = command.agentIdentityDocumentType();
        if (identityType == null) {
            throw failure(INVALID_ARGUMENT, "请选择代理人证件类型");
        }
        String identityNumber = requireText(command.agentIdentityNumber(), "代理人证件号码", 64);
        byte[] content = command.content() == null ? new byte[0] : command.content();
        String contentType = normalizeContentType(command.contentType());
        String originalFileName = normalizeFileName(command.originalFileName());
        validateFile(contentType, content);

        String contentSha256 = digestHex("SHA-256", content);
        String authorizationHash = PayloadHasher.sha256Hex(String.join("|",
                String.valueOf(ballotPackage.getPackageId()),
                String.valueOf(electorate.snapshotItemId()),
                String.valueOf(electorate.representativeOpid()),
                String.valueOf(electorate.representativeUid()),
                agentName,
                identityType.name(),
                identityNumber,
                command.validFrom().toString(),
                command.validUntil().toString(),
                contentSha256));
        String objectKey = objectKey(command.tenantId(), command.packageId(), contentType);
        VotingProxyAuthorizationStorage.StoredObjectMetadata metadata;
        try {
            metadata = storage.put(objectKey, content, contentType, digestBase64("MD5", content));
        } catch (RuntimeException ex) {
            throw new VotingProxyAuthorizationException(STORAGE_UNAVAILABLE, "上传书面委托原件失败", ex);
        }
        try {
            requireStoredObject(contentType, content.length, metadata);
            VotingProxyAuthorization inserted = repository.insert(new VotingProxyAuthorization(
                    null, command.packageId(), electorate.snapshotItemId(), command.tenantId(),
                    electorate.representativeOpid(), electorate.representativeUid(), agentName, identityType,
                    identityNumber, command.validFrom(), command.validUntil(), objectKey, originalFileName,
                    contentType, (long) content.length, metadata.etag(), contentSha256, authorizationHash,
                    VotingProxyAuthorization.Status.PENDING_REVIEW, actor.userId(), registeredAt,
                    null, null, null, null, null, null, 0L));
            audit(inserted, "PROXY_AUTHORIZATION_REGISTERED", actor.userId(),
                    Map.of("authorizationId", inserted.authorizationId(),
                            "principalOpid", inserted.principalOpid(),
                            "status", inserted.status().name()), registeredAt);
            return inserted;
        } catch (DataIntegrityViolationException ex) {
            safeDelete(objectKey);
            throw new VotingProxyAuthorizationException(
                    DUPLICATE, "该房屋已有待核对或已确认的书面委托，不能重复登记", ex);
        } catch (RuntimeException ex) {
            safeDelete(objectKey);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<VotingProxyAuthorization> list(Long packageId, Long tenantId) {
        requireActor(tenantId, null);
        requirePackage(packageId, tenantId);
        return repository.listByPackage(packageId, tenantId);
    }

    @Transactional
    public VotingProxyAuthorization review(ReviewCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        UserContext actor = requireActor(command.tenantId(), command.reviewedByUserId());
        VotingExecutionPackage ballotPackage = requirePackage(command.packageId(), command.tenantId());
        requireRegistrationStage(ballotPackage);
        VotingProxyAuthorization authorization = repository.findByIdForUpdate(
                        command.authorizationId(), command.packageId(), command.tenantId())
                .orElseThrow(() -> failure(NOT_FOUND, "书面委托登记不存在"));
        if (authorization.status() != VotingProxyAuthorization.Status.PENDING_REVIEW) {
            throw failure(INVALID_STATUS, "该书面委托已经完成核对");
        }
        if (actor.userId().equals(authorization.registeredByUserId())) {
            throw failure(INVALID_ARGUMENT, "书面委托登记人不能核对自己的登记");
        }
        ReviewDecision decision = command.decision();
        if (decision == null) {
            throw failure(INVALID_ARGUMENT, "请选择核对结果");
        }
        String note = requireText(command.reviewNote(), "核对意见", 500);
        Instant reviewedAt = requireInstant(command.reviewedAt(), "核对时间");
        int updated = switch (decision) {
            case CONFIRM -> repository.confirm(authorization.authorizationId(), authorization.tenantId(),
                    actor.userId(), reviewedAt, note, authorization.version());
            case REJECT -> repository.reject(authorization.authorizationId(), authorization.tenantId(),
                    actor.userId(), reviewedAt, note, authorization.version());
        };
        if (updated != 1) {
            throw failure(CONCURRENT_MODIFICATION, "书面委托已被其他工作人员处理，请刷新后重试");
        }
        VotingProxyAuthorization reviewed = repository.findById(
                        authorization.authorizationId(), authorization.packageId(), authorization.tenantId())
                .orElseThrow();
        audit(reviewed,
                decision == ReviewDecision.CONFIRM
                        ? "PROXY_AUTHORIZATION_CONFIRMED" : "PROXY_AUTHORIZATION_REJECTED",
                actor.userId(), Map.of("authorizationId", reviewed.authorizationId(),
                        "principalOpid", reviewed.principalOpid(), "status", reviewed.status().name()),
                reviewedAt);
        return reviewed;
    }

    @Transactional
    public VotingProxyAuthorization revoke(RevokeCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        UserContext actor = requireActor(command.tenantId(), command.revokedByUserId());
        VotingExecutionPackage ballotPackage = requirePackage(command.packageId(), command.tenantId());
        if (ballotPackage.getStatus() == VotingExecutionPackage.Status.CLOSED
                || ballotPackage.getStatus() == VotingExecutionPackage.Status.SETTLED
                || ballotPackage.getStatus() == VotingExecutionPackage.Status.VOIDED) {
            throw failure(INVALID_STATUS, "本次表决已经结束，不能撤销书面委托");
        }
        VotingProxyAuthorization authorization = repository.findByIdForUpdate(
                        command.authorizationId(), command.packageId(), command.tenantId())
                .orElseThrow(() -> failure(NOT_FOUND, "书面委托登记不存在"));
        if (authorization.status() != VotingProxyAuthorization.Status.CONFIRMED) {
            throw failure(INVALID_STATUS, "只有已确认且尚未使用的书面委托可以撤销");
        }
        if (repository.isUsedByPaperRecord(authorization.authorizationId(), authorization.tenantId())) {
            throw failure(INVALID_STATUS, "该书面委托已经用于提交纸质表决票，不能撤销");
        }
        Instant revokedAt = requireInstant(command.revokedAt(), "撤销时间");
        if (repository.revoke(authorization.authorizationId(), authorization.tenantId(), actor.userId(),
                revokedAt, requireText(command.reason(), "撤销原因", 500), authorization.version()) != 1) {
            throw failure(CONCURRENT_MODIFICATION, "书面委托已被其他工作人员处理，请刷新后重试");
        }
        VotingProxyAuthorization revoked = repository.findById(
                        authorization.authorizationId(), authorization.packageId(), authorization.tenantId())
                .orElseThrow();
        audit(revoked, "PROXY_AUTHORIZATION_REVOKED", actor.userId(),
                Map.of("authorizationId", revoked.authorizationId(),
                        "principalOpid", revoked.principalOpid(), "status", revoked.status().name()),
                revokedAt);
        return revoked;
    }

    @Transactional(readOnly = true)
    public PreviewTicket createPreviewTicket(Long packageId, Long authorizationId, Long tenantId) {
        requireActor(tenantId, null);
        VotingProxyAuthorization authorization = repository.findById(authorizationId, packageId, tenantId)
                .orElseThrow(() -> failure(NOT_FOUND, "书面委托登记不存在"));
        Instant expiresAt = Instant.now().plus(PREVIEW_VALIDITY);
        try {
            return new PreviewTicket(storage.createPreviewUrl(
                    authorization.documentObjectKey(), authorization.originalFileName(), PREVIEW_VALIDITY).toString(),
                    expiresAt);
        } catch (RuntimeException ex) {
            throw new VotingProxyAuthorizationException(STORAGE_UNAVAILABLE, "生成书面委托原件预览地址失败", ex);
        }
    }

    /** 纸质送达和收票只引用已核验且覆盖本次表决窗口的授权，不改变原业主计票归属。 */
    @Transactional(readOnly = true)
    public VotingProxyAuthorization requireUsableForPaperRecord(
            Long packageId, Long tenantId, Long principalOpid, Long authorizationId, Instant handledAt) {
        if (authorizationId == null) {
            return null;
        }
        VotingExecutionPackage ballotPackage = requirePackage(packageId, tenantId);
        requireWrittenAuthorizationRule(ballotPackage);
        VotingProxyAuthorization authorization = repository.findById(authorizationId, packageId, tenantId)
                .orElseThrow(() -> failure(NOT_FOUND, "所选书面委托登记不存在"));
        if (!Objects.equals(authorization.principalOpid(), principalOpid)) {
            throw failure(INVALID_ARGUMENT, "书面委托与所选房屋不一致");
        }
        if (!authorization.usableAt(requireInstant(handledAt, "办理时间"))) {
            throw failure(INVALID_STATUS, "书面委托尚未确认、已撤销或不在有效期内");
        }
        if (authorization.validFrom().isAfter(ballotPackage.getVoteStartAt())
                || authorization.validUntil().isBefore(ballotPackage.getVoteEndAt())) {
            throw failure(INVALID_STATUS, "书面委托有效期没有覆盖本次完整表决时间");
        }
        return authorization;
    }

    private UserContext requireActor(Long tenantId, Long expectedUserId) {
        UserContext actor = userContextHolder.current();
        if (actor == null || !actor.isSysUser() || actor.userId() == null
                || !Objects.equals(actor.tenantId(), tenantId) || !actor.hasPermission(AUDIT_PERMISSION)) {
            throw failure(FORBIDDEN, "当前工作身份无权办理书面委托");
        }
        if (expectedUserId != null && !expectedUserId.equals(actor.userId())) {
            throw failure(FORBIDDEN, "经办人与当前工作身份不一致");
        }
        return actor;
    }

    private VotingExecutionPackage requirePackage(Long packageId, Long tenantId) {
        return votingExecutionRepository.findPackage(requirePositive(packageId, "表决包"), requirePositive(tenantId, "小区"))
                .orElseThrow(() -> failure(NOT_FOUND, "正式表决安排不存在"));
    }

    private void requireRegistrationStage(VotingExecutionPackage ballotPackage) {
        if (ballotPackage.getStatus() != VotingExecutionPackage.Status.FROZEN
                && ballotPackage.getStatus() != VotingExecutionPackage.Status.VOTING) {
            throw failure(INVALID_STATUS, "只有表决安排确认后至表决截止前可以办理书面委托");
        }
    }

    private void requireWrittenAuthorizationRule(VotingExecutionPackage ballotPackage) {
        OwnersAssemblyRuleConfiguration configuration = switch (ballotPackage.getRuleSnapshotType()) {
            case "OWNERS_ASSEMBLY_RULE_VERSION" -> ruleRepository.findById(
                            ballotPackage.getRuleSnapshotId(), ballotPackage.getTenantId())
                    .filter(candidate -> Objects.equals(
                            candidate.configurationSha256(), ballotPackage.getRuleSnapshotHash()))
                    .map(OwnersAssemblyRule::configuration)
                    .orElseThrow(() -> failure(INVALID_STATUS, "本次表决所依据的议事规则版本无法核对"));
            case "OWNERS_ASSEMBLY_RULE" -> ownersAssemblyRepository.findRuleSnapshot(
                            ballotPackage.getRuleSnapshotId(), ballotPackage.getTenantId())
                    .filter(candidate -> Objects.equals(
                            candidate.configurationSha256(), ballotPackage.getRuleSnapshotHash()))
                    .map(OwnersAssemblyRuleSnapshot::configuration)
                    .orElseThrow(() -> failure(INVALID_STATUS, "本次表决所依据的议事规则快照无法核对"));
            default -> throw failure(INVALID_STATUS, "本次表决没有可核对的议事规则依据");
        };
        if (configuration.proxyVotingPolicy()
                != OwnersAssemblyRuleConfiguration.ProxyVotingPolicy.WRITTEN_AUTHORIZATION_REQUIRED) {
            throw failure(INVALID_STATUS, "本次表决规则不允许委托他人代办纸质投票");
        }
    }

    private void requireValidity(Instant validFrom, Instant validUntil, VotingExecutionPackage ballotPackage) {
        requireInstant(validFrom, "委托生效时间");
        requireInstant(validUntil, "委托截止时间");
        if (validUntil.isBefore(validFrom)) {
            throw failure(INVALID_ARGUMENT, "委托截止时间不能早于生效时间");
        }
        if (validFrom.isAfter(ballotPackage.getVoteStartAt()) || validUntil.isBefore(ballotPackage.getVoteEndAt())) {
            throw failure(INVALID_ARGUMENT, "书面委托有效期必须覆盖本次完整表决时间");
        }
    }

    private void validateFile(String contentType, byte[] content) {
        if (!CONTENT_TYPES.contains(contentType)) {
            throw failure(INVALID_ARGUMENT, "书面委托原件仅支持 PDF、JPG 或 PNG");
        }
        if (content.length == 0 || content.length > MAX_FILE_SIZE) {
            throw failure(INVALID_ARGUMENT, "书面委托原件不能为空且不能超过 20 MB");
        }
        boolean signatureMatches = switch (contentType) {
            case "application/pdf" -> startsWith(content, new byte[]{'%', 'P', 'D', 'F', '-'});
            case "image/jpeg" -> startsWith(content, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/png" -> startsWith(content,
                    new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'});
            default -> false;
        };
        if (!signatureMatches) {
            throw failure(INVALID_ARGUMENT, "书面委托原件格式与文件内容不一致");
        }
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private void requireStoredObject(String contentType, int contentLength,
                                     VotingProxyAuthorizationStorage.StoredObjectMetadata metadata) {
        if (metadata == null || metadata.size() != contentLength
                || !contentType.equals(metadata.contentType())) {
            throw failure(STORAGE_UNAVAILABLE, "书面委托原件上传后校验失败");
        }
    }

    private void safeDelete(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException ignored) {
            // 原始业务异常优先返回；对象存储残留由运维巡检按未引用对象清理。
        }
    }

    private void audit(VotingProxyAuthorization authorization, String eventType, Long actorUserId,
                       Map<String, Object> detail, Instant occurredAt) {
        try {
            votingExecutionRepository.insertAudit(
                    authorization.packageId(), authorization.tenantId(), eventType, null,
                    authorization.status().name(), actorUserId, objectMapper.writeValueAsString(detail), occurredAt);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("书面委托审计信息生成失败", ex);
        }
    }

    private String normalizeContentType(String contentType) {
        String normalized = requireText(contentType, "文件类型", 100).toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private String normalizeFileName(String fileName) {
        String normalized = requireText(fileName, "文件名", 255);
        return normalized.replace("\\", "_").replace("/", "_");
    }

    private String objectKey(Long tenantId, Long packageId, String contentType) {
        String suffix = switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> "";
        };
        return "voting-proxy-authorizations/" + tenantId + "/" + packageId + "/" + UUID.randomUUID() + suffix;
    }

    private String requireText(String value, String label, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maxLength) {
            throw failure(INVALID_ARGUMENT, label + "不能为空且长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private Long requirePositive(Long value, String label) {
        if (value == null || value <= 0) {
            throw failure(INVALID_ARGUMENT, label + "不能为空");
        }
        return value;
    }

    private Instant requireInstant(Instant value, String label) {
        if (value == null) {
            throw failure(INVALID_ARGUMENT, label + "不能为空");
        }
        return value;
    }

    private String digestHex(String algorithm, byte[] content) {
        return HexFormat.of().formatHex(digest(algorithm, content));
    }

    private String digestBase64(String algorithm, byte[] content) {
        return Base64.getEncoder().encodeToString(digest(algorithm, content));
    }

    private byte[] digest(String algorithm, byte[] content) {
        try {
            return MessageDigest.getInstance(algorithm).digest(content);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 缺少摘要算法 " + algorithm, ex);
        }
    }

    private VotingProxyAuthorizationException failure(
            VotingProxyAuthorizationException.Reason reason, String message) {
        return new VotingProxyAuthorizationException(reason, message);
    }

    public record RegisterCommand(
            Long packageId,
            Long tenantId,
            Long principalOpid,
            String agentName,
            VotingProxyAuthorization.IdentityDocumentType agentIdentityDocumentType,
            String agentIdentityNumber,
            Instant validFrom,
            Instant validUntil,
            String originalFileName,
            String contentType,
            byte[] content,
            Long registeredByUserId,
            Instant registeredAt
    ) {
        public RegisterCommand {
            content = content == null ? null : content.clone();
        }

        @Override
        public byte[] content() {
            return content == null ? null : content.clone();
        }
    }

    public record ReviewCommand(
            Long packageId,
            Long authorizationId,
            Long tenantId,
            ReviewDecision decision,
            String reviewNote,
            Long reviewedByUserId,
            Instant reviewedAt
    ) {
    }

    public record RevokeCommand(
            Long packageId,
            Long authorizationId,
            Long tenantId,
            String reason,
            Long revokedByUserId,
            Instant revokedAt
    ) {
    }

    public enum ReviewDecision {
        CONFIRM,
        REJECT
    }

    public record PreviewTicket(String url, Instant expiresAt) {
    }
}
