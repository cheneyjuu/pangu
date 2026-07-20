// 关联业务：登记、核对确认、启用和受控预览业主大会实际生效的议事规则版本。
package com.pangu.application.assembly;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pangu.application.assembly.command.CreateOwnersAssemblyRuleDraftCommand;
import com.pangu.application.assembly.command.UpdateOwnersAssemblyRuleDraftCommand;
import com.pangu.application.voting.FormalVotingRulePolicy;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleAudit;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleFieldConfirmation;
import com.pangu.domain.model.voting.VotingThreshold;
import com.pangu.domain.repository.CommitteePositionRepository;
import com.pangu.domain.repository.OwnersAssemblyRuleDocumentStorage;
import com.pangu.domain.repository.OwnersAssemblyRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.STORAGE_UNAVAILABLE;

/**
 * 业主大会议事规则版本的受控生命周期。
 *
 * <p>系统只保存和执行主任或副主任确认后的结构化配置；不以示范文本、平台默认天数或 OCR 结果代替
 * 小区实际备案规则。会议快照和正式办理闸门会在后续聚合接入本服务的 ACTIVE 版本。
 */
@Service
@RequiredArgsConstructor
public class OwnersAssemblyRuleService {

    private static final String RULE_READ = "owners-assembly:rule:read";
    private static final String RULE_DRAFT = "owners-assembly:rule:draft";
    private static final String RULE_ACTIVATE = "owners-assembly:rule:activate";
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Duration PREVIEW_VALIDITY = Duration.ofMinutes(10);
    private static final Set<String> CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final Set<String> EXECUTIVE_POSITIONS = Set.of("DIRECTOR", "VICE_DIRECTOR");
    private static final Set<OwnersAssemblyRuleConfiguration.RuleConfigurationField> REQUIRED_SOURCE_FIELDS =
            EnumSet.allOf(OwnersAssemblyRuleConfiguration.RuleConfigurationField.class);

    private final OwnersAssemblyRuleRepository repository;
    private final OwnersAssemblyRuleDocumentStorage storage;
    private final CommitteePositionRepository committeePositionRepository;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;
    private final FormalVotingRulePolicy formalVotingRulePolicy;

    @Transactional(readOnly = true)
    public List<OwnersAssemblyRule> list(Long requestedTenantId) {
        UserContext actor = requireActor(RULE_READ, "当前角色无权查看业主大会议事规则");
        return repository.listByTenant(resolveTenant(actor, requestedTenantId));
    }

    @Transactional(readOnly = true)
    public OwnersAssemblyRule active(Long requestedTenantId) {
        UserContext actor = requireActor(RULE_READ, "当前角色无权查看业主大会议事规则");
        Long tenantId = resolveTenant(actor, requestedTenantId);
        return repository.findActive(tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(
                        NOT_FOUND, "当前小区尚未确认有效的业主大会议事规则"));
    }

    @Transactional(readOnly = true)
    public List<OwnersAssemblyRuleAudit> audits(Long ruleId, Long requestedTenantId) {
        UserContext actor = requireActor(RULE_READ, "当前角色无权查看业主大会议事规则审计记录");
        Long tenantId = resolveTenant(actor, requestedTenantId);
        requireRule(ruleId, tenantId);
        return repository.listAudits(ruleId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<OwnersAssemblyRuleFieldConfirmation> fieldConfirmations(Long ruleId, Long requestedTenantId) {
        UserContext actor = requireActor(RULE_READ, "当前角色无权查看业主大会议事规则逐项核对记录");
        Long tenantId = resolveTenant(actor, requestedTenantId);
        OwnersAssemblyRule rule = requireRule(ruleId, tenantId);
        return repository.listFieldConfirmations(rule.ruleId(), tenantId, rule.configurationSha256());
    }

    @Transactional
    public OwnersAssemblyRule createDraft(Long requestedTenantId, CreateOwnersAssemblyRuleDraftCommand command) {
        UserContext actor = requireActor(RULE_DRAFT, "当前角色无权登记业主大会议事规则草稿");
        Long tenantId = resolveTenant(actor, requestedTenantId);
        validateDraftCommand(command);

        String contentType = normalizeContentType(command.contentType());
        String originalFileName = normalizeFileName(command.originalFileName());
        byte[] content = command.content() == null ? new byte[0] : command.content();
        validateFile(contentType, content.length);
        String objectKey = objectKey(tenantId, contentType);
        OwnersAssemblyRuleDocumentStorage.StoredObjectMetadata metadata;
        try {
            metadata = storage.put(objectKey, content, contentType, digestBase64("MD5", content));
        } catch (RuntimeException ex) {
            throw new OwnersAssemblyApplicationException(STORAGE_UNAVAILABLE, "上传业主大会议事规则原件失败", ex);
        }
        validateStoredObject(objectKey, contentType, content.length, metadata);

        try {
            String configurationSha256 = configurationSha256(command.configuration());
            OwnersAssemblyRule created = repository.insert(new OwnersAssemblyRule(
                    null,
                    tenantId,
                    requireText(command.ruleName(), "ruleName", 200),
                    requireText(command.ruleVersion(), "ruleVersion", 64),
                    command.effectiveDate(),
                    requireText(command.changeReason(), "changeReason", 1000),
                    command.configuration(),
                    configurationSha256,
                    objectKey,
                    originalFileName,
                    contentType,
                    (long) content.length,
                    metadata.etag(),
                    digestHex("SHA-256", content),
                    OwnersAssemblyRule.Status.DRAFT,
                    actor.accountId(),
                    actor.userId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));
            appendAudit(created, OwnersAssemblyRuleAudit.EventType.DRAFT_CREATED, actor);
            return created;
        } catch (RuntimeException ex) {
            deleteQuietly(objectKey, ex);
            throw ex;
        }
    }

    @Transactional
    public OwnersAssemblyRule updateDraft(Long ruleId,
                                          Long requestedTenantId,
                                          UpdateOwnersAssemblyRuleDraftCommand command) {
        UserContext actor = requireActor(RULE_DRAFT, "当前角色无权更新业主大会议事规则草稿");
        Long tenantId = resolveTenant(actor, requestedTenantId);
        validateDraftCommand(command);

        OwnersAssemblyRule existing = requireRuleForUpdate(ruleId, tenantId);
        requireStatus(existing, OwnersAssemblyRule.Status.DRAFT, "只有草稿状态的议事规则可以修改");
        OwnersAssemblyRule updated = new OwnersAssemblyRule(
                existing.ruleId(),
                existing.tenantId(),
                requireText(command.ruleName(), "ruleName", 200),
                requireText(command.ruleVersion(), "ruleVersion", 64),
                command.effectiveDate(),
                requireText(command.changeReason(), "changeReason", 1000),
                command.configuration(),
                configurationSha256(command.configuration()),
                existing.objectKey(),
                existing.originalFileName(),
                existing.contentType(),
                existing.fileSize(),
                existing.etag(),
                existing.sha256(),
                existing.status(),
                existing.draftedByAccountId(),
                existing.draftedByUserId(),
                existing.submittedByAccountId(),
                existing.submittedByUserId(),
                existing.submittedAt(),
                existing.activatedByAccountId(),
                existing.activatedByUserId(),
                existing.activatedAt(),
                existing.createTime(),
                existing.updateTime());
        if (repository.updateDraft(updated) != 1) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "议事规则草稿已被其他操作更新，请刷新后重试");
        }
        OwnersAssemblyRule persisted = requireRule(ruleId, tenantId);
        appendAudit(persisted, OwnersAssemblyRuleAudit.EventType.DRAFT_UPDATED, actor);
        return persisted;
    }

    @Transactional
    public OwnersAssemblyRule submitForConfirmation(Long ruleId, Long requestedTenantId) {
        UserContext actor = requireActor(RULE_DRAFT, "当前角色无权提交业主大会议事规则确认");
        Long tenantId = resolveTenant(actor, requestedTenantId);
        OwnersAssemblyRule rule = requireRuleForUpdate(ruleId, tenantId);
        requireStatus(rule, OwnersAssemblyRule.Status.DRAFT, "只有草稿状态的议事规则可以提交确认");
        validateReadyConfiguration(rule);
        if (repository.submitForConfirmation(ruleId, tenantId, actor.accountId(), actor.userId()) != 1) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "议事规则状态已变化，请刷新后重试");
        }
        OwnersAssemblyRule persisted = requireRule(ruleId, tenantId);
        createPendingFieldConfirmations(persisted);
        appendAudit(persisted, OwnersAssemblyRuleAudit.EventType.SUBMITTED_FOR_CONFIRMATION, actor);
        return persisted;
    }

    @Transactional
    public OwnersAssemblyRuleFieldConfirmation confirmField(
            Long ruleId,
            Long requestedTenantId,
            OwnersAssemblyRuleConfiguration.RuleConfigurationField field) {
        UserContext actor = requireActor(RULE_ACTIVATE, "当前角色无权核对业主大会议事规则字段");
        Long tenantId = resolveTenant(actor, requestedTenantId);
        String executivePosition = requireCommitteeExecutive(actor, tenantId);
        if (field == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "请选择需要核对的议事规则字段");
        }

        OwnersAssemblyRule rule = requireRuleForUpdate(ruleId, tenantId);
        requireStatus(rule, OwnersAssemblyRule.Status.PENDING_CONFIRMATION, "只有待确认的议事规则可以逐项核对");
        List<OwnersAssemblyRuleFieldConfirmation> confirmations = repository.listFieldConfirmations(
                rule.ruleId(), tenantId, rule.configurationSha256());
        OwnersAssemblyRuleFieldConfirmation confirmation = confirmations.stream()
                .filter(item -> item.field() == field)
                .findFirst()
                .orElseThrow(() -> new OwnersAssemblyApplicationException(
                        INVALID_STATUS, "该字段尚未进入本次规则核对清单"));
        if (confirmation.status() == OwnersAssemblyRuleFieldConfirmation.Status.CONFIRMED) {
            return confirmation;
        }
        if (repository.confirmField(
                rule.ruleId(), tenantId, rule.configurationSha256(), field,
                actor.accountId(), actor.userId(), executivePosition) != 1) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "该字段核对状态已变化，请刷新后重试");
        }
        return repository.listFieldConfirmations(rule.ruleId(), tenantId, rule.configurationSha256()).stream()
                .filter(item -> item.field() == field)
                .findFirst()
                .orElseThrow(() -> new OwnersAssemblyApplicationException(
                        INVALID_STATUS, "字段核对记录未能保存"));
    }

    @Transactional
    public OwnersAssemblyRule activate(Long ruleId, Long requestedTenantId) {
        UserContext actor = requireActor(RULE_ACTIVATE, "当前角色无权启用业主大会议事规则");
        Long tenantId = resolveTenant(actor, requestedTenantId);
        String executivePosition = requireCommitteeExecutive(actor, tenantId);

        // 同一小区的确认操作串行化：锁住小区根记录，再替代唯一 ACTIVE 版本。
        repository.lockTenantRules(tenantId);
        OwnersAssemblyRule rule = requireRuleForUpdate(ruleId, tenantId);
        requireStatus(rule, OwnersAssemblyRule.Status.PENDING_CONFIRMATION, "只有待确认的议事规则可以启用");
        validateReadyConfiguration(rule);
        requireAllFieldsConfirmed(rule);
        if (rule.effectiveDate().isAfter(LocalDate.now())) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "只能启用已经生效的议事规则版本");
        }

        repository.findActive(tenantId).ifPresent(active -> {
            if (repository.supersedeActive(tenantId) != 1) {
                throw new OwnersAssemblyApplicationException(INVALID_STATUS, "当前有效规则已变化，请刷新后重试");
            }
            appendAudit(active, OwnersAssemblyRuleAudit.EventType.SUPERSEDED, actor, executivePosition);
        });
        if (repository.activate(ruleId, tenantId, actor.accountId(), actor.userId()) != 1) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "议事规则状态已变化，请刷新后重试");
        }
        OwnersAssemblyRule persisted = requireRule(ruleId, tenantId);
        appendAudit(persisted, OwnersAssemblyRuleAudit.EventType.ACTIVATED, actor, executivePosition);
        return persisted;
    }

    @Transactional(readOnly = true)
    public OwnersAssemblyRulePreviewTicket createPreviewTicket(Long ruleId, Long requestedTenantId) {
        UserContext actor = requireActor(RULE_READ, "当前角色无权预览业主大会议事规则原件");
        Long tenantId = resolveTenant(actor, requestedTenantId);
        OwnersAssemblyRule rule = requireRule(ruleId, tenantId);
        Instant expiresAt = Instant.now().plus(PREVIEW_VALIDITY);
        try {
            return new OwnersAssemblyRulePreviewTicket(
                    rule.ruleId(),
                    rule.originalFileName(),
                    rule.contentType(),
                    rule.fileSize(),
                    storage.createPreviewUrl(rule.objectKey(), rule.originalFileName(), PREVIEW_VALIDITY).toString(),
                    expiresAt);
        } catch (RuntimeException ex) {
            throw new OwnersAssemblyApplicationException(STORAGE_UNAVAILABLE, "生成议事规则原件预览地址失败", ex);
        }
    }

    private void validateDraftCommand(CreateOwnersAssemblyRuleDraftCommand command) {
        if (command == null || command.configuration() == null || command.effectiveDate() == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "规则名称、版本、生效日期、变更原因和结构化配置均为必填项");
        }
        requireText(command.ruleName(), "ruleName", 200);
        requireText(command.ruleVersion(), "ruleVersion", 64);
        requireText(command.changeReason(), "changeReason", 1000);
    }

    private void validateDraftCommand(UpdateOwnersAssemblyRuleDraftCommand command) {
        if (command == null || command.configuration() == null || command.effectiveDate() == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "规则名称、版本、生效日期、变更原因和结构化配置均为必填项");
        }
        requireText(command.ruleName(), "ruleName", 200);
        requireText(command.ruleVersion(), "ruleVersion", 64);
        requireText(command.changeReason(), "changeReason", 1000);
    }

    private void validateReadyConfiguration(OwnersAssemblyRule rule) {
        if (rule.objectKey() == null || rule.objectKey().isBlank() || rule.sha256() == null || rule.sha256().isBlank()) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "议事规则原件未完整归档，不能提交确认");
        }
        OwnersAssemblyRuleConfiguration configuration = rule.configuration();
        if (configuration == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "请先填写结构化议事规则配置");
        }
        if (configuration.allowedMeetingForms().isEmpty()) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "请明确规则允许的会议形式");
        }
        requireNonNegative(configuration.planPublicityDays(), "方案公示期限");
        requireNonNegative(configuration.meetingNoticeDays(), "会议通知期限");
        if (configuration.validDeliveryMethods().isEmpty()) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "请明确规则认可的有效送达方式");
        }
        requirePresent(configuration.nonResponsePolicy(), "未表态处理规则");
        requirePresent(configuration.proxyVotingPolicy(), "委托代理规则");
        requirePresent(configuration.votingChannelPolicy(), "表决渠道规则");
        requirePresent(configuration.onlineIdentityVerificationRequired(), "线上身份核验要求");
        requirePresent(configuration.paperBallotSealRequired(), "纸质选票用印要求");
        requirePresent(configuration.duplicateVotePolicy(), "重复投票处理规则");
        validateVotingChannel(configuration);
        validateCountingRules(configuration.countingRules());
        requireNonNegative(configuration.resultAnnouncementDays(), "结果公告期限");
        validateSourceReferences(configuration.sourceClauseReferences());
    }

    private void createPendingFieldConfirmations(OwnersAssemblyRule rule) {
        OwnersAssemblyRuleConfiguration configuration = rule.configuration();
        for (OwnersAssemblyRuleConfiguration.RuleConfigurationField field : REQUIRED_SOURCE_FIELDS) {
            OwnersAssemblyRuleConfiguration.RuleSourceReference sourceReference =
                    configuration.sourceClauseReferences().get(field);
            repository.insertFieldConfirmation(new OwnersAssemblyRuleFieldConfirmation(
                    null,
                    rule.ruleId(),
                    rule.tenantId(),
                    rule.configurationSha256(),
                    field,
                    sourceReference.pageNumber(),
                    sourceReference.clause(),
                    OwnersAssemblyRuleFieldConfirmation.Status.PENDING,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));
        }
    }

    private void requireAllFieldsConfirmed(OwnersAssemblyRule rule) {
        List<OwnersAssemblyRuleFieldConfirmation> confirmations = repository.listFieldConfirmations(
                rule.ruleId(), rule.tenantId(), rule.configurationSha256());
        if (confirmations.size() != REQUIRED_SOURCE_FIELDS.size()
                || confirmations.stream().anyMatch(item -> item.status()
                != OwnersAssemblyRuleFieldConfirmation.Status.CONFIRMED)) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "请由当前届业委会主任或副主任逐项核对全部议事规则字段后再启用");
        }
    }

    private void validateVotingChannel(OwnersAssemblyRuleConfiguration configuration) {
        FormalVotingRulePolicy.ChannelCapability capability =
                formalVotingRulePolicy.assessChannelCapability(configuration);
        if (!capability.blockingItems().isEmpty()) {
            throw new OwnersAssemblyApplicationException(
                    PARAM_INVALID,
                    capability.blockingItems().stream()
                            .map(FormalVotingRulePolicy.ReadinessIssue::message)
                            .collect(java.util.stream.Collectors.joining("；")));
        }
        if (capability.allowedModes().isEmpty()) {
            throw new OwnersAssemblyApplicationException(
                    PARAM_INVALID, "当前规则没有系统可办理的表决方式");
        }
    }

    private void validateCountingRules(Map<OwnersAssemblyRuleConfiguration.DecisionType,
            OwnersAssemblyRuleConfiguration.CountingRule> countingRules) {
        for (OwnersAssemblyRuleConfiguration.DecisionType type
                : EnumSet.allOf(OwnersAssemblyRuleConfiguration.DecisionType.class)) {
            OwnersAssemblyRuleConfiguration.CountingRule rule = countingRules.get(type);
            if (rule == null) {
                throw new OwnersAssemblyApplicationException(PARAM_INVALID, "请明确 " + type + " 事项的计票阈值");
            }
            validateThreshold(rule.participationOwnerThreshold(), type + " 参与人数比例");
            validateThreshold(rule.participationAreaThreshold(), type + " 参与面积比例");
            validateThreshold(rule.approvalOwnerThreshold(), type + " 同意人数比例");
            validateThreshold(rule.approvalAreaThreshold(), type + " 同意面积比例");
        }
    }

    private void validateSourceReferences(Map<OwnersAssemblyRuleConfiguration.RuleConfigurationField,
            OwnersAssemblyRuleConfiguration.RuleSourceReference> sourceClauseReferences) {
        if (!sourceClauseReferences.keySet().containsAll(REQUIRED_SOURCE_FIELDS)) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "请为每项结构化规则填写原件页码和条款依据");
        }
        for (OwnersAssemblyRuleConfiguration.RuleConfigurationField field : REQUIRED_SOURCE_FIELDS) {
            OwnersAssemblyRuleConfiguration.RuleSourceReference reference = sourceClauseReferences.get(field);
            if (reference == null || reference.pageNumber() == null || reference.pageNumber() <= 0) {
                throw new OwnersAssemblyApplicationException(PARAM_INVALID, field + " 缺少有效原件页码");
            }
            requireText(reference.clause(), field + " 原件条款", 1000);
        }
    }

    private void validateThreshold(VotingThreshold threshold, String field) {
        if (threshold == null || threshold.numerator() == null || threshold.denominator() == null
                || threshold.comparison() == null || threshold.numerator() < 0
                || threshold.denominator() <= 0 || threshold.numerator() > threshold.denominator()) {
            throw new OwnersAssemblyApplicationException(
                    PARAM_INVALID, field + " 必须填写有效分子、分母和比较方式");
        }
    }

    private void requireNonNegative(Integer value, String field) {
        if (value == null || value < 0) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, field + " 必须明确且不能小于 0");
        }
    }

    private void requirePresent(Object value, String field) {
        if (value == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "请明确" + field);
        }
    }

    private String requireCommitteeExecutive(UserContext actor, Long tenantId) {
        String position = committeePositionRepository.findActivePosition(tenantId, actor.userId()).orElse(null);
        if (!EXECUTIVE_POSITIONS.contains(position)) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "只有当前届业委会主任或副主任可以确认启用议事规则");
        }
        return position;
    }

    private OwnersAssemblyRule requireRule(Long ruleId, Long tenantId) {
        return repository.findById(ruleId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会议事规则不存在"));
    }

    private OwnersAssemblyRule requireRuleForUpdate(Long ruleId, Long tenantId) {
        return repository.findByIdForUpdate(ruleId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会议事规则不存在"));
    }

    private void requireStatus(OwnersAssemblyRule rule, OwnersAssemblyRule.Status expected, String message) {
        if (rule.status() != expected) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, message);
        }
    }

    private void appendAudit(OwnersAssemblyRule rule, OwnersAssemblyRuleAudit.EventType eventType, UserContext actor) {
        appendAudit(rule, eventType, actor, null);
    }

    private void appendAudit(OwnersAssemblyRule rule,
                             OwnersAssemblyRuleAudit.EventType eventType,
                             UserContext actor,
                             String committeePosition) {
        repository.appendAudit(new OwnersAssemblyRuleAudit(
                null,
                rule.ruleId(),
                rule.tenantId(),
                eventType,
                rule.configurationSha256(),
                rule.changeReason(),
                actor.accountId(),
                actor.userId(),
                actor.roleKey(),
                committeePosition,
                null));
    }

    private Long resolveTenant(UserContext actor, Long requestedTenantId) {
        Long tenantId = requestedTenantId == null ? actor.tenantId() : requestedTenantId;
        if (tenantId == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "未指定小区租户");
        }
        if (actor.tenantId() != null && !actor.tenantId().equals(tenantId)) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "不能跨小区操作业主大会议事规则");
        }
        return tenantId;
    }

    private UserContext requireActor(String permission, String deniedMessage) {
        UserContext actor = userContextHolder.current();
        if (actor == null || !actor.isSysUser() || actor.accountId() == null || actor.userId() == null
                || actor.roleKey() == null || actor.roleKey().isBlank()) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到管理端工作身份");
        }
        if (!actor.hasPermission(permission)) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, deniedMessage);
        }
        return actor;
    }

    private String configurationSha256(OwnersAssemblyRuleConfiguration configuration) {
        try {
            ObjectMapper canonicalMapper = objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return digestHex("SHA-256", canonicalMapper.writeValueAsBytes(configuration));
        } catch (JsonProcessingException ex) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "结构化议事规则配置无法序列化", ex);
        }
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, field + " 必填");
        }
        if (normalized.length() > maxLength) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, field + " 长度不能超过 " + maxLength);
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
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "议事规则原件仅支持 PDF、DOC 或 DOCX 文件");
        }
        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "议事规则原件大小必须在 20MB 以内");
        }
    }

    private void validateStoredObject(String objectKey,
                                      String contentType,
                                      long expectedSize,
                                      OwnersAssemblyRuleDocumentStorage.StoredObjectMetadata metadata) {
        if (metadata == null || metadata.size() != expectedSize
                || !contentType.equals(normalizeContentType(metadata.contentType()))
                || metadata.etag() == null || metadata.etag().isBlank()) {
            OwnersAssemblyApplicationException failure = new OwnersAssemblyApplicationException(
                    PARAM_INVALID, "议事规则原件存储返回的大小、类型或 ETag 与原文件不一致");
            deleteQuietly(objectKey, failure);
            throw failure;
        }
    }

    private String objectKey(Long tenantId, String contentType) {
        return "owners-assembly-rules/tenant-" + tenantId + "/" + LocalDate.now()
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
