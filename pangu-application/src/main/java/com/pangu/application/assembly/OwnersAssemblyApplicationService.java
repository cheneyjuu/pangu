// 关联业务：编排业主大会的会前事项、材料归档、公示、表决、送达和计票状态机。
package com.pangu.application.assembly;

import com.pangu.application.assembly.command.CreateAssemblySubjectDraftCommand;
import com.pangu.application.assembly.command.CreateOwnersAssemblySessionCommand;
import com.pangu.application.assembly.command.ConfirmAssemblyArrangementCommand;
import com.pangu.application.assembly.command.RecordAssemblyDeliveryWithMaterialCommand;
import com.pangu.application.assembly.command.RegisterAssemblyPaperBallotCommand;
import com.pangu.application.assembly.command.ReviewAssemblyPaperBallotEntryCommand;
import com.pangu.application.assembly.command.ReviewAssemblyPaperDeliveryCommand;
import com.pangu.application.assembly.command.SubmitAssemblyPaperBallotEntryCommand;
import com.pangu.application.assembly.command.UploadOwnersAssemblyMaterialCommand;
import com.pangu.application.assembly.command.VoidAssemblyPaperBallotCommand;
import com.pangu.application.support.PayloadHasher;
import com.pangu.application.voting.OnlineVotingService;
import com.pangu.application.voting.FormalVotingRulePolicy;
import com.pangu.application.voting.PaperVotingException;
import com.pangu.application.voting.PaperVotingService;
import com.pangu.application.voting.VotingExecutionService;
import com.pangu.application.voting.VotingDecisionResultProjector;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial.MaterialType;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleSnapshot;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.assembly.OwnersAssemblySubjectDraft;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.PaperBallot;
import com.pangu.domain.model.voting.PaperBallotEntry;
import com.pangu.domain.model.voting.PaperBallotOutcome;
import com.pangu.domain.model.voting.PaperVotingDelivery;
import com.pangu.domain.model.voting.OnlinePaperAssistanceRequest;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingDecisionRule;
import com.pangu.domain.model.voting.VotingSettlementPolicy;
import com.pangu.domain.model.voting.VotingNonResponsePolicy;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.voting.VotingSubjectActions;
import com.pangu.domain.repository.CommitteePositionRepository;
import com.pangu.domain.repository.OwnersAssemblyMaterialStorage;
import com.pangu.domain.repository.OwnersAssemblyRepository;
import com.pangu.domain.repository.OwnersAssemblyRuleRepository;
import com.pangu.domain.repository.PropertyBindingRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import com.pangu.domain.repository.VotingResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.CONCURRENT_MODIFICATION;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.DELIVERY_REQUIRED;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.NOTICE_NOT_COMPLETED;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.OPID_OUT_OF_SCOPE;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.STORAGE_UNAVAILABLE;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.VOTE_ALREADY_CAST;

@Service
@RequiredArgsConstructor
public class OwnersAssemblyApplicationService {

    /** 当前已经有统一证据链的单次办理方式；线下集中会议的签到和代理仍未建模。 */
    private static final Set<String> SESSION_MODES = Set.of(
            "WRITTEN_DECISION", "INTERNET_DECISION", "ONLINE_AND_OFFLINE");
    private static final Set<String> MATERIAL_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final long MAX_MATERIAL_SIZE = 20L * 1024 * 1024;
    private static final String STATUS_PACKAGE_DRAFT = "PACKAGE_DRAFT";
    private static final String STATUS_PUBLIC_NOTICE = "PUBLIC_NOTICE";
    private static final String STATUS_VOTING = "VOTING";
    private static final String STATUS_SETTLED = "SETTLED";
    private static final String CHANNEL_PAPER = "PAPER";
    private static final String FORMAL_MANAGE_PERMISSION = "owners-assembly:formal:manage";
    private static final Set<String> EXECUTIVE_POSITIONS = Set.of("DIRECTOR", "VICE_DIRECTOR");

    private final OwnersAssemblyRepository ownersAssemblyRepository;
    private final OwnersAssemblyRuleRepository ownersAssemblyRuleRepository;
    private final PropertyBindingRepository propertyBindingRepository;
    private final OwnersAssemblyMaterialStorage ownersAssemblyMaterialStorage;
    private final CommitteePositionRepository committeePositionRepository;
    private final VotingSubjectRepository votingSubjectRepository;
    private final VotingResultRepository votingResultRepository;
    private final VotingDecisionResultProjector votingDecisionResultProjector;
    private final VotingExecutionService votingExecutionService;
    private final PaperVotingService paperVotingService;
    private final OnlineVotingService onlineVotingService;
    private final FormalVotingRulePolicy formalVotingRulePolicy;
    private final UserContextHolder userContextHolder;

    @Transactional
    public OwnersAssemblySession createSession(CreateOwnersAssemblySessionCommand command) {
        requireSysContext(command.tenantId(), command.createdByUserId());
        String title = requireText(command.title(), "title");
        String mode = normalize(command.preparationMode(), "preparationMode");
        if (!SESSION_MODES.contains(mode)) {
            throw new OwnersAssemblyApplicationException(
                    PARAM_INVALID, "请选择纸质书面征询、互联网表决或规则明确允许的纸质与线上并行方式");
        }
        return ownersAssemblyRepository.insertSession(new OwnersAssemblySession(
                null, command.tenantId(), title, mode, "PREPARING", command.createdByUserId(), null));
    }

    @Transactional(readOnly = true)
    public List<OwnersAssemblySession> listSessions(Long tenantId) {
        requireSysContext(tenantId, null);
        return ownersAssemblyRepository.listSessions(tenantId);
    }

    /**
     * 由当前已启用议事规则计算可选办理方式和最早开始时间。管理端只能展示该结果，
     * 不能根据规则字段自行拼装会议形式或默认日期。
     */
    @Transactional(readOnly = true)
    public PreparationOptions preparationOptions(Long tenantId) {
        requireSysContext(tenantId, null);
        OwnersAssemblyRule activeRule = ownersAssemblyRuleRepository.findActive(tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(
                        INVALID_STATUS, "当前小区尚无已确认生效的业主大会议事规则"));
        FormalVotingRulePolicy.PreparationOptions options =
                formalVotingRulePolicy.preparationOptions(activeRule, Instant.now());
        OwnersAssemblyRuleConfiguration configuration = activeRule.configuration();
        return new PreparationOptions(
                activeRule.ruleName(),
                activeRule.ruleVersion(),
                activeRule.effectiveDate(),
                options.ready(),
                options.blockingItems(),
                options.allowedModes().stream().map(this::preparationModeOf).toList(),
                options.earliestVoteStartAt(),
                configuration.planPublicityDays(),
                configuration.meetingNoticeDays(),
                configuration.resultAnnouncementDays(),
                options.validDeliveryMethods(),
                options.paperBallotSealRequired(),
                options.proxyVotingPolicy());
    }

    @Transactional(readOnly = true)
    public OwnersAssemblyWorkspace loadWorkspace(Long sessionId, Long tenantId) {
        requireSysContext(tenantId, null);
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(sessionId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = ownersAssemblyRepository.findLatestPackageBySession(sessionId, tenantId)
                .orElse(null);
        OwnersAssemblyRuleSnapshot ruleSnapshot = ownersAssemblyRepository
                .findRuleSnapshotBySession(sessionId, tenantId)
                .orElse(null);
        List<OwnersAssemblyWorkspace.FormalSubject> formalSubjects = arrangement == null ? List.of()
                : ownersAssemblyRepository.listSubjectIds(arrangement.packageId(), tenantId).stream()
                .map(subjectId -> votingSubjectRepository.findById(subjectId).orElseThrow(
                        () -> new OwnersAssemblyApplicationException(NOT_FOUND, "正式表决事项不存在")))
                .map(this::toWorkspaceSubject)
                .toList();
        return new OwnersAssemblyWorkspace(
                session,
                arrangement,
                ruleSnapshot,
                ownersAssemblyRepository.listSubjectDrafts(sessionId, tenantId),
                formalSubjects,
                ownersAssemblyRepository.listMaterials(sessionId, tenantId));
    }

    @Transactional
    public OwnersAssemblySubjectDraft createSubjectDraft(CreateAssemblySubjectDraftCommand command) {
        requireSysContext(command.tenantId(), command.proposedByUserId());
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        requirePreparing(session);
        if (ownersAssemblyRepository.findLatestPackageBySession(session.sessionId(), session.tenantId()).isPresent()) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "已确认公示与表决安排，不能再新增表决事项");
        }
        if (command.subjectType() == null || command.subjectType() == SubjectType.ELECTION) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "业主大会仅支持一般决议或重大决议事项");
        }
        return ownersAssemblyRepository.insertSubjectDraft(new OwnersAssemblySubjectDraft(
                null,
                session.sessionId(),
                session.tenantId(),
                command.subjectType(),
                VotingScope.COMMUNITY,
                null,
                requireText(command.title(), "title", 200),
                trim(command.content()),
                command.proposedByUserId(),
                null));
    }

    @Transactional
    public OwnersAssemblyMaterial uploadMaterial(UploadOwnersAssemblyMaterialCommand command) {
        UserContext actor = requireSysContext(command.tenantId(), null);
        if (actor.accountId() == null) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到办理账号");
        }
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        if ("VOIDED".equals(session.status()) || STATUS_SETTLED.equals(session.status())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "已结束的业主大会不能继续上传办理材料");
        }
        if (command.materialType() == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "materialType 必填");
        }
        requireMaterialStage(session, command.materialType());
        String fileName = normalizeFileName(command.originalFileName());
        String contentType = normalizeContentType(command.contentType());
        byte[] content = command.content() == null ? new byte[0] : command.content();
        validateMaterialFile(contentType, content.length);
        String objectKey = materialObjectKey(session.tenantId(), session.sessionId(), command.materialType(), contentType);
        OwnersAssemblyMaterialStorage.StoredObjectMetadata metadata;
        try {
            metadata = ownersAssemblyMaterialStorage.put(
                    objectKey, content, contentType, digestBase64("MD5", content));
        } catch (RuntimeException ex) {
            throw new OwnersAssemblyApplicationException(STORAGE_UNAVAILABLE, "上传业主大会原始材料失败", ex);
        }
        validateStoredMaterial(objectKey, contentType, content.length, metadata);
        try {
            return ownersAssemblyRepository.insertMaterial(new OwnersAssemblyMaterial(
                    null,
                    session.sessionId(),
                    session.tenantId(),
                    command.materialType(),
                    objectKey,
                    fileName,
                    contentType,
                    metadata.size(),
                    metadata.etag(),
                    digestHex("SHA-256", content),
                    actor.accountId(),
                    actor.userId(),
                    null));
        } catch (RuntimeException ex) {
            deleteMaterialQuietly(objectKey, ex);
            throw ex;
        }
    }

    @Transactional
    public OwnersAssemblyPackage confirmArrangement(ConfirmAssemblyArrangementCommand command) {
        UserContext actor = requireFormalAssemblyExecutive(command.tenantId());
        // 同一小区的规则启用与本次会议的规则快照必须串行，避免确认过程读到被替换中的 ACTIVE 版本。
        ownersAssemblyRuleRepository.lockTenantRules(command.tenantId());
        OwnersAssemblySession session = ownersAssemblyRepository.findSessionForUpdate(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        requirePreparing(session);
        if (ownersAssemblyRepository.findLatestPackageBySession(session.sessionId(), session.tenantId()).isPresent()) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "本次业主大会已确认公示与表决安排");
        }
        if (ownersAssemblyRepository.findRuleSnapshotBySession(session.sessionId(), session.tenantId()).isPresent()) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "本次业主大会已冻结议事规则快照，不能重复确认安排");
        }
        OwnersAssemblyRule activeRule = ownersAssemblyRuleRepository.findActive(session.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(
                        INVALID_STATUS, "当前小区尚无已确认生效的业主大会议事规则；可继续会前准备，但不能进入正式公示与表决"));
        requireSupportedFormalFlow(session, activeRule.configuration());
        List<OwnersAssemblySubjectDraft> drafts = ownersAssemblyRepository
                .listSubjectDrafts(session.sessionId(), session.tenantId());
        if (drafts.isEmpty()) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "请先新增至少一个表决事项");
        }
        if (command.voteStartAt() == null || command.voteEndAt() == null
                || !command.voteEndAt().isAfter(command.voteStartAt())) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "投票截止时间必须晚于投票开始时间");
        }
        try {
            formalVotingRulePolicy.requireExecutable(
                    activeRule,
                    collectionModeOf(session, activeRule.configuration()),
                    Instant.now(),
                    command.voteStartAt());
        } catch (FormalVotingRulePolicy.UnsupportedRuleException ex) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, ex.getMessage(), ex);
        }
        OwnersAssemblyMaterial publicNotice = requireMaterial(
                command.publicNoticeMaterialId(), session, MaterialType.PUBLIC_NOTICE);
        OwnersAssemblyMaterial ballotTemplate = requireMaterial(
                command.ballotTemplateMaterialId(), session, MaterialType.PAPER_BALLOT_TEMPLATE);
        if (!"application/pdf".equals(ballotTemplate.contentType())) {
            throw new OwnersAssemblyApplicationException(
                    PARAM_INVALID, "正式纸质表决票样必须上传 PDF 原件");
        }
        List<Long> attachmentIds = command.planAttachmentMaterialIds() == null ? List.of()
                : command.planAttachmentMaterialIds().stream().filter(id -> id != null).distinct().toList();
        if (attachmentIds.isEmpty()) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "请至少归档一份方案附件");
        }
        List<OwnersAssemblyMaterial> attachments = attachmentIds.stream()
                .map(materialId -> requireMaterial(materialId, session, MaterialType.PLAN_ATTACHMENT))
                .sorted(Comparator.comparing(OwnersAssemblyMaterial::materialId))
                .toList();
        OwnersAssemblyRuleSnapshot ruleSnapshot = ownersAssemblyRepository.insertRuleSnapshot(
                snapshotOf(activeRule, session, actor));
        OwnersAssemblyPackage arrangement = createPackage(
                session,
                ruleSnapshot,
                publicNotice.contentSha256(),
                buildAttachmentManifestHash(attachments),
                ballotTemplate.contentSha256(),
                command.voteStartAt(),
                command.voteEndAt());
        // 正式表决包只披露此处锁定的原始材料，绝不以会前草稿材料全集替代。
        ownersAssemblyRepository.linkPackageMaterial(
                arrangement.packageId(), arrangement.tenantId(), publicNotice.materialId());
        for (OwnersAssemblyMaterial attachment : attachments) {
            ownersAssemblyRepository.linkPackageMaterial(
                    arrangement.packageId(), arrangement.tenantId(), attachment.materialId());
        }
        ownersAssemblyRepository.linkPackageMaterial(
                arrangement.packageId(), arrangement.tenantId(), ballotTemplate.materialId());
        for (OwnersAssemblySubjectDraft draft : drafts) {
            addDraftSubjectToArrangement(arrangement, draft, actor.userId());
        }
        List<Long> subjectIds = ownersAssemblyRepository.listSubjectIds(
                arrangement.packageId(), arrangement.tenantId());
        VotingScope executionScope = drafts.getFirst().scope() == null
                ? VotingScope.COMMUNITY : drafts.getFirst().scope();
        Long executionScopeReferenceId = drafts.getFirst().scopeReferenceId();
        boolean mixedScope = drafts.stream().anyMatch(draft -> {
            VotingScope draftScope = draft.scope() == null ? VotingScope.COMMUNITY : draft.scope();
            return draftScope != executionScope
                    || !java.util.Objects.equals(draft.scopeReferenceId(), executionScopeReferenceId);
        });
        if (mixedScope) {
            throw new OwnersAssemblyApplicationException(
                    PARAM_INVALID, "同一个正式表决包内的事项必须面向同一批相关业主；不同范围请分别办理");
        }
        VotingExecutionPackage executionPackage = votingExecutionService.create(
                new VotingExecutionService.CreatePackageCommand(
                        arrangement.tenantId(),
                        VotingExecutionPackage.BusinessType.OWNERS_ASSEMBLY,
                        arrangement.packageId(),
                        "OWNERS_ASSEMBLY_PACKAGE",
                        arrangement.packageId(),
                        buildPackageHash(arrangement, ruleSnapshot, subjectIds),
                        "OWNERS_ASSEMBLY_RULE",
                        ruleSnapshot.ruleSnapshotId(),
                        requireText(ruleSnapshot.configurationSha256(), "议事规则配置摘要"),
                        executionScope,
                        executionScopeReferenceId,
                        collectionModeOf(session, ruleSnapshot.configuration()),
                        arrangement.voteStartAt(),
                        arrangement.voteEndAt(),
                        actor.userId()));
        for (Long subjectId : subjectIds) {
            votingExecutionService.attachSubject(
                    executionPackage.getPackageId(), arrangement.tenantId(), subjectId, actor.userId());
        }
        return arrangement;
    }

    /**
     * 正式表决包只能由已验证的会前草案、已归档材料和已冻结规则生成，禁止接受客户端摘要或渠道参数。
     */
    private OwnersAssemblyPackage createPackage(OwnersAssemblySession session,
                                                OwnersAssemblyRuleSnapshot ruleSnapshot,
                                                String announcementHash,
                                                String attachmentManifestHash,
                                                String ballotTemplateHash,
                                                Instant voteStartAt,
                                                Instant voteEndAt) {
        if (voteStartAt == null || voteEndAt == null || !voteEndAt.isAfter(voteStartAt)) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "投票截止时间必须晚于投票开始时间");
        }
        OwnersAssemblyPackage created = ownersAssemblyRepository.insertPackage(new OwnersAssemblyPackage(
                null,
                session.sessionId(),
                session.tenantId(),
                ruleSnapshot.ruleSnapshotId(),
                1,
                STATUS_PACKAGE_DRAFT,
                ruleSnapshot.configuration().votingChannelPolicy().name(),
                requiredPreparationDays(ruleSnapshot.configuration()),
                requireText(announcementHash, "公告材料摘要"),
                requireText(attachmentManifestHash, "方案附件摘要"),
                requireText(ballotTemplateHash, "纸质选票模板摘要"),
                null,
                null,
                null,
                null,
                voteStartAt,
                voteEndAt,
                null,
                null));
        ownersAssemblyRepository.updateSessionStatus(session.sessionId(), session.tenantId(), STATUS_PACKAGE_DRAFT);
        return created;
    }

    /**
     * 会前草案保留原拟定人；确认公示安排的办理人只负责将其转换为正式事项，不能被误当成拟定人。
     */
    private VotingSubject addDraftSubjectToArrangement(OwnersAssemblyPackage arrangement,
                                                        OwnersAssemblySubjectDraft draft,
                                                        Long confirmedByUserId) {
        requireSysContext(arrangement.tenantId(), confirmedByUserId);
        return addSubjectToArrangement(
                arrangement,
                draft.subjectType(),
                draft.scope(),
                draft.scopeReferenceId(),
                draft.title(),
                draft.content(),
                draft.proposedByUserId(),
                null);
    }

    private VotingSubject addSubjectToArrangement(OwnersAssemblyPackage ballotPackage,
                                                   SubjectType subjectType,
                                                   VotingScope requestedScope,
                                                   Long scopeReferenceId,
                                                   String title,
                                                   String content,
                                                   Long proposedByUserId,
                                                   BigDecimal partyRatioFloor) {
        requirePackageStatus(ballotPackage, STATUS_PACKAGE_DRAFT);
        if (subjectType == null || subjectType == SubjectType.ELECTION) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "业主大会仅支持一般决议或重大决议事项");
        }
        VotingScope scope = requestedScope == null ? VotingScope.COMMUNITY : requestedScope;
        VotingSubject subject;
        try {
            subject = VotingSubjectActions.open(
                    ballotPackage.tenantId(),
                    subjectType,
                    scope,
                    scopeReferenceId,
                    requireText(title, "title"),
                    ballotPackage.voteStartAt(),
                    ballotPackage.voteEndAt(),
                    proposedByUserId,
                    partyRatioFloor);
            subject.setContent(trim(content));
        } catch (IllegalArgumentException ex) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, ex.getMessage(), ex);
        }
        VotingSubject inserted = votingSubjectRepository.insert(subject);
        ownersAssemblyRepository.linkSubject(ballotPackage.packageId(), ballotPackage.tenantId(), inserted.getSubjectId());
        return inserted;
    }

    @Transactional
    public OwnersAssemblyPackage lockPackage(Long packageId, Long tenantId) {
        UserContext ctx = requireFormalAssemblyExecutive(tenantId);
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository.findPackageForUpdate(packageId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决包不存在"));
        requirePackageStatus(ballotPackage, STATUS_PACKAGE_DRAFT);
        OwnersAssemblyRuleSnapshot ruleSnapshot = requireRuleSnapshot(ballotPackage);
        List<Long> subjectIds = ownersAssemblyRepository.listSubjectIds(packageId, tenantId);
        if (subjectIds.isEmpty()) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "表决包至少需要一个表决事项");
        }
        String packageHash = buildPackageHash(ballotPackage, ruleSnapshot, subjectIds);
        VotingExecutionPackage executionPackage = requireExecutionPackage(ballotPackage, subjectIds);
        if (!packageHash.equals(executionPackage.getProposalSnapshotHash())) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "当前公示材料或表决事项与已确认安排不一致，请重新核对");
        }
        Instant noticeStartAt = Instant.now();
        Instant noticeEndAt = noticeStartAt.plus(ballotPackage.publicNoticeDays(), ChronoUnit.DAYS);
        if (ballotPackage.voteStartAt().isBefore(noticeEndAt)) {
            throw new OwnersAssemblyApplicationException(
                    NOTICE_NOT_COMPLETED,
                    "计划投票开始时间早于本次规则要求的公示和通知期限，请重新确认安排");
        }
        int updated = ownersAssemblyRepository.lockPackage(
                packageId, tenantId, packageHash, noticeStartAt, noticeEndAt, ctx.userId());
        if (updated != 1) {
            throw new OwnersAssemblyApplicationException(CONCURRENT_MODIFICATION, "表决包已被并发修改");
        }
        votingExecutionService.freeze(
                executionPackage.getPackageId(), tenantId, ctx.userId(), noticeStartAt);
        ownersAssemblyRepository.updateSessionStatus(
                ballotPackage.sessionId(), ballotPackage.tenantId(), STATUS_PUBLIC_NOTICE);
        return ownersAssemblyRepository.findPackage(packageId, tenantId).orElseThrow();
    }

    @Transactional
    public OwnersAssemblyPackage openVoting(Long packageId, Long tenantId) {
        UserContext actor = requireFormalAssemblyExecutive(tenantId);
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository.findPackageForUpdate(packageId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决包不存在"));
        requirePackageStatus(ballotPackage, STATUS_PUBLIC_NOTICE);
        requireRuleSnapshot(ballotPackage);
        Instant now = Instant.now();
        if (ballotPackage.publicNoticeEndAt() == null || now.isBefore(ballotPackage.publicNoticeEndAt())) {
            throw new OwnersAssemblyApplicationException(NOTICE_NOT_COMPLETED, "公示期未满，禁止发起投票");
        }
        if (now.isBefore(ballotPackage.voteStartAt())) {
            throw new OwnersAssemblyApplicationException(NOTICE_NOT_COMPLETED, "尚未到达投票开始时间");
        }
        int updated = ownersAssemblyRepository.markPackageVoting(packageId, tenantId);
        if (updated != 1) {
            throw new OwnersAssemblyApplicationException(CONCURRENT_MODIFICATION, "表决包已被并发修改");
        }
        VotingExecutionPackage executionPackage = requireExecutionPackage(
                ballotPackage, ownersAssemblyRepository.listSubjectIds(packageId, tenantId));
        votingExecutionService.open(executionPackage.getPackageId(), tenantId, actor.userId(), now);
        ownersAssemblyRepository.updateSessionStatus(
                ballotPackage.sessionId(), ballotPackage.tenantId(), STATUS_VOTING);
        return ownersAssemblyRepository.findPackage(packageId, tenantId).orElseThrow();
    }

    @Transactional
    public OwnersAssemblyPackage publishCurrentArrangement(Long sessionId, Long tenantId) {
        requireFormalAssemblyExecutive(tenantId);
        OwnersAssemblyPackage arrangement = requireLatestArrangement(sessionId, tenantId);
        return lockPackage(arrangement.packageId(), tenantId);
    }

    @Transactional
    public OwnersAssemblyPackage startVoting(Long sessionId, Long tenantId) {
        requireFormalAssemblyExecutive(tenantId);
        OwnersAssemblyPackage arrangement = requireLatestArrangement(sessionId, tenantId);
        return openVoting(arrangement.packageId(), tenantId);
    }

    public PaperVotingDelivery recordPaperDelivery(RecordAssemblyDeliveryWithMaterialCommand command) {
        requireSysContext(command.tenantId(), command.deliveredByUserId());
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
        OwnersAssemblyRuleSnapshot ruleSnapshot = requireRuleSnapshot(arrangement);
        requireChannelAllowed(arrangement, CHANNEL_PAPER);
        if (command.deliveryMethod() == null
                || !ruleSnapshot.configuration().validDeliveryMethods().contains(command.deliveryMethod())) {
            throw new OwnersAssemblyApplicationException(
                    PARAM_INVALID, "送达方式不在本次业主大会冻结议事规则认可范围内");
        }
        OwnersAssemblyMaterial evidence = requireMaterial(
                command.evidenceMaterialId(), session, MaterialType.DELIVERY_EVIDENCE);
        VotingExecutionPackage executionPackage = requireExecutionPackage(
                arrangement, ownersAssemblyRepository.listSubjectIds(arrangement.packageId(), arrangement.tenantId()));
        try {
            return paperVotingService.registerDelivery(new PaperVotingService.RegisterDeliveryCommand(
                    executionPackage.getPackageId(), session.tenantId(), command.opid(),
                    command.proxyAuthorizationId(), command.recipientName(),
                    command.deliveryMethod().name(), "OWNERS_ASSEMBLY_MATERIAL", evidence.materialId(),
                    evidence.contentSha256(), command.deliveredByUserId(), command.deliveredAt()));
        } catch (PaperVotingException ex) {
            throw translatePaperVotingFailure(ex);
        }
    }

    public PaperVotingDelivery reviewPaperDelivery(ReviewAssemblyPaperDeliveryCommand command) {
        requireSysContext(command.tenantId(), command.reviewedByUserId());
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
        VotingExecutionPackage executionPackage = requireExecutionPackage(
                arrangement, ownersAssemblyRepository.listSubjectIds(arrangement.packageId(), arrangement.tenantId()));
        try {
            return paperVotingService.reviewDelivery(new PaperVotingService.ReviewDeliveryCommand(
                    executionPackage.getPackageId(), command.paperDeliveryId(), command.tenantId(),
                    command.decision(), command.reviewNote(), command.reviewedByUserId(), command.reviewedAt()));
        } catch (PaperVotingException ex) {
            throw translatePaperVotingFailure(ex);
        }
    }

    public PaperBallot registerPaperBallot(RegisterAssemblyPaperBallotCommand command) {
        requireSysContext(command.tenantId(), command.receivedByUserId());
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
        requireChannelAllowed(arrangement, CHANNEL_PAPER);
        OwnersAssemblyMaterial ballot = requireMaterial(command.ballotMaterialId(), session, MaterialType.PAPER_BALLOT);
        VotingExecutionPackage executionPackage = requireExecutionPackage(
                arrangement, ownersAssemblyRepository.listSubjectIds(arrangement.packageId(), arrangement.tenantId()));
        try {
            return paperVotingService.registerBallot(new PaperVotingService.RegisterBallotCommand(
                    executionPackage.getPackageId(), command.tenantId(), command.opid(),
                    command.proxyAuthorizationId(), command.ballotNumber(),
                    arrangement.ballotTemplateHash(), "OWNERS_ASSEMBLY_MATERIAL", ballot.materialId(),
                    ballot.contentSha256(), command.receivedByUserId(), command.receivedAt()));
        } catch (PaperVotingException ex) {
            throw translatePaperVotingFailure(ex);
        }
    }

    public PaperBallot voidPaperBallot(VoidAssemblyPaperBallotCommand command) {
        requireSysContext(command.tenantId(), command.voidedByUserId());
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
        VotingExecutionPackage executionPackage = requireExecutionPackage(
                arrangement, ownersAssemblyRepository.listSubjectIds(arrangement.packageId(), arrangement.tenantId()));
        try {
            return paperVotingService.voidBallot(new PaperVotingService.VoidBallotCommand(
                    executionPackage.getPackageId(), command.paperBallotId(), command.tenantId(),
                    command.reason(), command.voidedByUserId(), command.voidedAt()));
        } catch (PaperVotingException ex) {
            throw translatePaperVotingFailure(ex);
        }
    }

    public PaperBallotEntry submitPaperBallotEntry(SubmitAssemblyPaperBallotEntryCommand command) {
        requireSysContext(command.tenantId(), command.enteredByUserId());
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
        VotingExecutionPackage executionPackage = requireExecutionPackage(
                arrangement, ownersAssemblyRepository.listSubjectIds(arrangement.packageId(), arrangement.tenantId()));
        try {
            return paperVotingService.submitEntry(new PaperVotingService.SubmitEntryCommand(
                    executionPackage.getPackageId(), command.paperBallotId(), command.tenantId(),
                    arrangement.ballotTemplateHash(), command.items(), command.enteredByUserId(), command.enteredAt()));
        } catch (PaperVotingException ex) {
            throw translatePaperVotingFailure(ex);
        }
    }

    public PaperVotingService.BallotReviewResult reviewPaperBallotEntry(
            ReviewAssemblyPaperBallotEntryCommand command) {
        requireSysContext(command.tenantId(), command.reviewedByUserId());
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
        VotingExecutionPackage executionPackage = requireExecutionPackage(
                arrangement, ownersAssemblyRepository.listSubjectIds(arrangement.packageId(), arrangement.tenantId()));
        try {
            return paperVotingService.reviewEntry(new PaperVotingService.ReviewEntryCommand(
                    executionPackage.getPackageId(), command.paperBallotId(), command.entryId(), command.tenantId(),
                    command.decision(), command.reviewNote(), command.reviewedByUserId(), command.reviewedAt()));
        } catch (PaperVotingException ex) {
            throw translatePaperVotingFailure(ex);
        }
    }

    @Transactional(readOnly = true)
    public VotingWorkbench getVotingWorkbench(Long sessionId, Long tenantId) {
        UserContext actor = requireSysContext(tenantId, null);
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(sessionId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
        VotingExecutionPackage executionPackage = requireExecutionPackage(
                arrangement, ownersAssemblyRepository.listSubjectIds(arrangement.packageId(), arrangement.tenantId()));
        try {
            PaperVotingService.Workbench paper = paperVotingService.getWorkbench(
                    executionPackage.getPackageId(), tenantId);
            List<PaperAssistanceWorkbenchItem> assistance = onlineVotingService
                    .listPaperAssistanceRequests(executionPackage.getPackageId(), tenantId).stream()
                    .map(request -> toPaperAssistanceWorkbenchItem(executionPackage, paper, request))
                    .toList();
            OnlineVotingService.ManagementProgress online = onlineVotingService.managementProgress(
                    executionPackage.getPackageId(), tenantId);
            List<ElectorateWorkbenchItem> electorate = votingExecutionService.findElectorateSnapshot(
                            executionPackage.getPackageId(), tenantId)
                    .orElseThrow(() -> new OwnersAssemblyApplicationException(
                            INVALID_STATUS, "本次表决缺少冻结房屋名册"))
                    .items().stream().map(item -> toElectorateWorkbenchItem(item, tenantId)).toList();
            long duplicatePaperDecisionCount = paper.ballots().stream()
                    .flatMap(item -> item.outcomes().stream())
                    .filter(outcome -> outcome.status() == PaperBallotOutcome.Status.DUPLICATE)
                    .count();
            return new VotingWorkbench(
                    electorate, paper, assistance, online, duplicatePaperDecisionCount, actor.userId());
        } catch (PaperVotingException ex) {
            throw translatePaperVotingFailure(ex);
        }
    }

    private ElectorateWorkbenchItem toElectorateWorkbenchItem(
            VotingElectorateSnapshot.Item item, Long tenantId) {
        PropertyBindingRepository.Roster roster = propertyBindingRepository.findRosterById(item.rosterId());
        if (roster == null
                || !tenantId.equals(roster.tenantId())
                || !item.roomId().equals(roster.roomId())
                || !item.buildingId().equals(roster.buildingId())) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "冻结房屋名册与建筑名册不一致，不能继续办理");
        }
        return new ElectorateWorkbenchItem(
                item.snapshotItemId(), item.roomId(), item.buildingId(), item.certifiedArea(),
                item.representativeOpid(), roster.buildingName(), roster.unitName(), roster.roomName());
    }

    private OwnersAssemblyWorkspace.FormalSubject toWorkspaceSubject(VotingSubject subject) {
        OwnersAssemblyWorkspace.Result result = votingResultRepository.findBySubjectId(subject.getSubjectId())
                .map(votingDecisionResultProjector::project)
                .map(view -> new OwnersAssemblyWorkspace.Result(
                        view.quorumSatisfied(), view.passed(), view.totalArea(), view.totalOwnerCount(),
                        view.participatingArea(), view.participatingOwnerCount(),
                        view.supportArea(), view.supportOwnerCount(),
                        view.againstArea(), view.againstOwnerCount(),
                        view.abstainArea(), view.abstainOwnerCount(), view.nonResponse()))
                .orElse(null);
        return new OwnersAssemblyWorkspace.FormalSubject(
                subject.getSubjectId(), subject.getSubjectType(), subject.getTitle(), subject.getContent(),
                subject.getStatus().name(), result);
    }

    private PaperAssistanceWorkbenchItem toPaperAssistanceWorkbenchItem(
            VotingExecutionPackage executionPackage,
            PaperVotingService.Workbench paper,
            OnlinePaperAssistanceRequest request) {
        VotingElectorateSnapshot.Item electorate = votingExecutionService
                .findElectorateItem(executionPackage.getPackageId(), executionPackage.getTenantId(), request.opid())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(
                        INVALID_STATUS, "纸质办理申请与冻结表决名册不一致"));
        PaperAssistanceStage stage;
        if (request.status() == OnlinePaperAssistanceRequest.Status.WITHDRAWN) {
            stage = PaperAssistanceStage.WITHDRAWN;
        } else if (paper.ballots().stream()
                .anyMatch(item -> item.ballot().electorateItemId().equals(electorate.snapshotItemId())
                        && item.ballot().status() == PaperBallot.Status.COMPLETED)) {
            stage = PaperAssistanceStage.COMPLETED;
        } else if (request.status() == OnlinePaperAssistanceRequest.Status.FULFILLED) {
            stage = PaperAssistanceStage.PAPER_PROCESSING;
        } else {
            stage = PaperAssistanceStage.PENDING_PAPER_PROVISION;
        }
        return new PaperAssistanceWorkbenchItem(
                request.requestId(), request.opid(), electorate.buildingId(), electorate.roomId(), stage,
                request.requestedAt(), request.fulfilledAt(), request.withdrawnAt(), request.paperDeliveryId());
    }

    @Transactional
    public OwnersAssemblyPackage settlePackage(Long packageId, Long tenantId) {
        UserContext actor = requireFormalAssemblyExecutive(tenantId);
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository.findPackageForUpdate(packageId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决包不存在"));
        requirePackageStatus(ballotPackage, STATUS_VOTING);
        OwnersAssemblyRuleSnapshot ruleSnapshot = requireRuleSnapshot(ballotPackage);
        Instant now = Instant.now();
        if (now.isBefore(ballotPackage.voteEndAt())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "尚未到达投票截止时间，禁止结算");
        }
        List<Long> subjectIds = ownersAssemblyRepository.listSubjectIds(packageId, tenantId);
        VotingExecutionPackage executionPackage = requireExecutionPackage(ballotPackage, subjectIds);
        try {
            votingExecutionService.closeAndSettle(
                    executionPackage.getPackageId(), tenantId, actor.userId(), now,
                    subject -> settlementPolicyFor(subject, ruleSnapshot));
        } catch (VotingExecutionService.VotingExecutionException ex) {
            throw translateExecutionFailure(ex);
        }
        int updated = ownersAssemblyRepository.markPackageSettled(packageId, tenantId);
        if (updated != 1) {
            throw new OwnersAssemblyApplicationException(CONCURRENT_MODIFICATION, "表决包已被并发修改");
        }
        ownersAssemblyRepository.updateSessionStatus(
                ballotPackage.sessionId(), ballotPackage.tenantId(), STATUS_SETTLED);
        return ownersAssemblyRepository.findPackage(packageId, tenantId).orElseThrow();
    }

    @Transactional
    public OwnersAssemblyPackage settleCurrentArrangement(Long sessionId, Long tenantId) {
        requireFormalAssemblyExecutive(tenantId);
        OwnersAssemblyPackage arrangement = requireLatestArrangement(sessionId, tenantId);
        return settlePackage(arrangement.packageId(), tenantId);
    }

    /**
     * 从当前 ACTIVE 规则生成会次专属快照。正式办理后只读取该副本，不能被规则替代动作改变。
     */
    private OwnersAssemblyRuleSnapshot snapshotOf(OwnersAssemblyRule activeRule,
                                                   OwnersAssemblySession session,
                                                   UserContext actor) {
        OwnersAssemblyRuleConfiguration configuration = activeRule.configuration();
        if (configuration == null) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "当前有效议事规则缺少结构化配置，不能进入正式公示与表决");
        }
        return new OwnersAssemblyRuleSnapshot(
                null,
                session.sessionId(),
                session.tenantId(),
                activeRule.ruleId(),
                activeRule.ruleName(),
                activeRule.ruleVersion(),
                activeRule.effectiveDate(),
                activeRule.originalFileName(),
                activeRule.sha256(),
                configuration,
                activeRule.configurationSha256(),
                actor.accountId(),
                actor.userId(),
                null);
    }

    /** 本次实际方式必须同时得到有效规则中的会议形式和渠道规则支持。 */
    private void requireSupportedFormalFlow(OwnersAssemblySession session,
                                            OwnersAssemblyRuleConfiguration configuration) {
        collectionModeOf(session, configuration);
        requireSupportedFrozenRule(configuration);
    }

    /**
     * 对已冻结快照复核当前状态机真正具备的渠道、送达和计票能力，避免旧表决包绕过新规则闸门。
     */
    private void requireSupportedFrozenRule(OwnersAssemblyRuleConfiguration configuration) {
        if (configuration == null) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "表决包缺少冻结议事规则配置");
        }
        if (configuration.planPublicityDays() == null || configuration.planPublicityDays() < 0) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "冻结议事规则未明确有效的方案公示期限");
        }
        if (configuration.meetingNoticeDays() == null || configuration.meetingNoticeDays() < 0) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "冻结议事规则未明确有效的会议通知期限");
        }
        if (configuration.resultAnnouncementDays() == null || configuration.resultAnnouncementDays() < 0) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "冻结议事规则未明确有效的结果公示期限");
        }
        validateSupportedChannelRule(configuration);
        if (configuration.nonResponsePolicy() == null) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "冻结议事规则未明确未反馈表决票的认定方式");
        }
        if (configuration.proxyVotingPolicy() == null) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "冻结议事规则未明确是否允许书面委托");
        }
        if (configuration.validDeliveryMethods() == null || configuration.validDeliveryMethods().isEmpty()) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "冻结议事规则未明确有效送达方式");
        }
        for (OwnersAssemblyRuleConfiguration.DecisionType type
                : OwnersAssemblyRuleConfiguration.DecisionType.values()) {
            OwnersAssemblyRuleConfiguration.CountingRule countingRule = configuration.countingRules().get(type);
            if (countingRule == null) {
                throw new OwnersAssemblyApplicationException(INVALID_STATUS, "冻结议事规则缺少 " + type + " 事项计票口径");
            }
            try {
                countingRule.toVotingDecisionRule().requireExecutable();
            } catch (IllegalStateException ex) {
                throw new OwnersAssemblyApplicationException(
                        INVALID_STATUS, "冻结议事规则的 " + type + " 事项计票口径不完整", ex);
            }
        }
    }

    /**
     * 将会次选择映射为统一收票方式。互联网方式中的纸质票是按业主请求提供的协助渠道，
     * 不能误映射成默认向所有人开放的纸电并行。
     */
    private VotingExecutionPackage.CollectionMode collectionModeOf(
            OwnersAssemblySession session,
            OwnersAssemblyRuleConfiguration configuration) {
        if (configuration == null) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "当前有效议事规则缺少结构化配置");
        }
        return switch (session.preparationMode()) {
            case "WRITTEN_DECISION" -> requireMode(
                    configuration,
                    OwnersAssemblyRuleConfiguration.MeetingForm.WRITTEN_CONSULTATION,
                    OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_ONLY,
                    VotingExecutionPackage.CollectionMode.PAPER,
                    "当前有效议事规则未确认纸质书面征询");
            case "INTERNET_DECISION" -> requireMode(
                    configuration,
                    OwnersAssemblyRuleConfiguration.MeetingForm.INTERNET,
                    OwnersAssemblyRuleConfiguration.VotingChannelPolicy.ONLINE_ONLY,
                    VotingExecutionPackage.CollectionMode.ONLINE_WITH_PAPER_ASSISTANCE,
                    "当前有效议事规则未确认互联网表决及实名核验");
            case "ONLINE_AND_OFFLINE" -> requireMode(
                    configuration,
                    OwnersAssemblyRuleConfiguration.MeetingForm.ONLINE_AND_OFFLINE,
                    OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_AND_ONLINE,
                    VotingExecutionPackage.CollectionMode.PAPER_AND_ONLINE,
                    "当前有效议事规则未明确允许纸质与线上并行");
            default -> throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "本次会议形式尚无可执行的表决证据链");
        };
    }

    private String preparationModeOf(VotingExecutionPackage.CollectionMode mode) {
        return switch (mode) {
            case PAPER -> "WRITTEN_DECISION";
            case ONLINE_WITH_PAPER_ASSISTANCE -> "INTERNET_DECISION";
            case PAPER_AND_ONLINE -> "ONLINE_AND_OFFLINE";
        };
    }

    /** 方案公示与会议通知可以同期完成，但必须满足两者中较长的期限。 */
    private int requiredPreparationDays(OwnersAssemblyRuleConfiguration configuration) {
        return Math.max(configuration.planPublicityDays(), configuration.meetingNoticeDays());
    }

    private VotingExecutionPackage.CollectionMode requireMode(
            OwnersAssemblyRuleConfiguration configuration,
            OwnersAssemblyRuleConfiguration.MeetingForm meetingForm,
            OwnersAssemblyRuleConfiguration.VotingChannelPolicy channelPolicy,
            VotingExecutionPackage.CollectionMode collectionMode,
            String message) {
        if (!configuration.allowedMeetingForms().contains(meetingForm)
                || !allowsChannel(configuration.votingChannelPolicy(), channelPolicy)) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, message);
        }
        return collectionMode;
    }

    /** 当前规则可覆盖多个办理形式，但每次会次只冻结一种实际收票方式。 */
    private boolean allowsChannel(
            OwnersAssemblyRuleConfiguration.VotingChannelPolicy configured,
            OwnersAssemblyRuleConfiguration.VotingChannelPolicy requested) {
        return configured == requested
                || configured == OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_AND_ONLINE
                && (requested == OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_ONLY
                || requested == OwnersAssemblyRuleConfiguration.VotingChannelPolicy.ONLINE_ONLY);
    }

    private void validateSupportedChannelRule(OwnersAssemblyRuleConfiguration configuration) {
        FormalVotingRulePolicy.ChannelCapability capability =
                formalVotingRulePolicy.assessChannelCapability(configuration);
        if (!capability.blockingItems().isEmpty()) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS,
                    capability.blockingItems().stream()
                            .map(FormalVotingRulePolicy.ReadinessIssue::message)
                            .collect(Collectors.joining("；")));
        }
        if (capability.allowedModes().isEmpty()) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "当前表决依据没有系统可办理的表决方式");
        }
    }

    private OwnersAssemblyRuleSnapshot requireRuleSnapshot(OwnersAssemblyPackage ballotPackage) {
        if (ballotPackage.ruleSnapshotId() == null) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "缺少冻结议事规则快照；历史表决包不得按平台默认规则继续办理，请依据当时有效规则完成治理迁移后再处理");
        }
        OwnersAssemblyRuleSnapshot snapshot = ownersAssemblyRepository
                .findRuleSnapshot(ballotPackage.ruleSnapshotId(), ballotPackage.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(
                        INVALID_STATUS, "表决包关联的议事规则快照不存在，不能继续办理"));
        if (!ballotPackage.sessionId().equals(snapshot.sessionId())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "表决包与议事规则快照不属于同一次业主大会");
        }
        requireSupportedFrozenRule(snapshot.configuration());
        return snapshot;
    }

    /**
     * 结算必须使用该会次冻结版本中的对应事项阈值，不能退回通用投票引擎默认规则。
     */
    private VotingSettlementPolicy settlementPolicyFor(VotingSubject subject,
                                                       OwnersAssemblyRuleSnapshot ruleSnapshot) {
        OwnersAssemblyRuleConfiguration.DecisionType decisionType = switch (subject.getSubjectType()) {
            case GENERAL -> OwnersAssemblyRuleConfiguration.DecisionType.GENERAL;
            case MAJOR -> OwnersAssemblyRuleConfiguration.DecisionType.MAJOR;
            case ELECTION -> throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "业主大会选举事项尚未接入本次议事规则结算模型");
        };
        OwnersAssemblyRuleConfiguration.CountingRule countingRule = ruleSnapshot.configuration()
                .countingRules().get(decisionType);
        if (countingRule == null) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "冻结议事规则缺少 " + decisionType + " 事项计票口径");
        }
        VotingDecisionRule decisionRule = countingRule.toVotingDecisionRule();
        try {
            decisionRule.requireExecutable();
        } catch (IllegalStateException ex) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "冻结议事规则的 " + decisionType + " 事项计票口径不完整", ex);
        }
        return new VotingSettlementPolicy(
                decisionRule,
                ruleSnapshot.ruleSnapshotId(),
                ruleSnapshot.configurationSha256(),
                VotingNonResponsePolicy.valueOf(
                        ruleSnapshot.configuration().nonResponsePolicy().name()),
                ruleSnapshot.configuration().validDeliveryMethods().stream()
                        .map(Enum::name)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    private void requirePreparing(OwnersAssemblySession session) {
        if (!"PREPARING".equals(session.status())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "本次业主大会已进入正式办理阶段，不能修改会前事项");
        }
    }

    /**
     * 材料的可上传阶段与业务动作绑定：会前材料不得在公示后替换，送达和回收凭证也不得倒灌到会前。
     */
    private void requireMaterialStage(OwnersAssemblySession session, MaterialType materialType) {
        switch (materialType) {
            case PUBLIC_NOTICE, PLAN_ATTACHMENT, PAPER_BALLOT_TEMPLATE -> requirePreparing(session);
            case DELIVERY_EVIDENCE -> {
                OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
                requireRuleSnapshot(arrangement);
                if (!STATUS_VOTING.equals(arrangement.status())) {
                    throw new OwnersAssemblyApplicationException(
                            INVALID_STATUS, "仅在投票期间可以归档纸质选票送达凭证");
                }
            }
            case PAPER_BALLOT -> {
                OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
                requirePackageStatus(arrangement, STATUS_VOTING);
            }
        }
    }

    private OwnersAssemblyPackage requireLatestArrangement(Long sessionId, Long tenantId) {
        return ownersAssemblyRepository.findLatestPackageBySession(sessionId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(
                        NOT_FOUND, "请先确认公示与表决安排"));
    }

    private OwnersAssemblyMaterial requireMaterial(Long materialId,
                                                    OwnersAssemblySession session,
                                                    MaterialType expectedType) {
        if (materialId == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "办理材料必填");
        }
        OwnersAssemblyMaterial material = ownersAssemblyRepository
                .findMaterial(materialId, session.sessionId(), session.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "办理材料不存在或不属于本次业主大会"));
        if (material.materialType() != expectedType) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "办理材料类型不匹配");
        }
        return material;
    }

    private String buildAttachmentManifestHash(List<OwnersAssemblyMaterial> attachments) {
        return PayloadHasher.sha256Hex(attachments.stream()
                .map(OwnersAssemblyMaterial::contentSha256)
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(PARAM_INVALID, "请至少归档一份方案附件")));
    }

    private String normalizeFileName(String value) {
        String normalized = requireText(value, "originalFileName", 255).replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    }

    private String normalizeContentType(String value) {
        return value == null ? "" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private void validateMaterialFile(String contentType, long size) {
        if (!MATERIAL_CONTENT_TYPES.contains(contentType)) {
            throw new OwnersAssemblyApplicationException(
                    PARAM_INVALID, "业主大会材料仅支持 PDF、图片、DOC 或 DOCX 文件");
        }
        if (size <= 0 || size > MAX_MATERIAL_SIZE) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "业主大会材料大小必须在 20MB 以内");
        }
    }

    private void validateStoredMaterial(String objectKey,
                                        String contentType,
                                        long expectedSize,
                                        OwnersAssemblyMaterialStorage.StoredObjectMetadata metadata) {
        if (metadata == null || metadata.size() != expectedSize
                || !contentType.equals(normalizeContentType(metadata.contentType()))
                || trim(metadata.etag()) == null) {
            OwnersAssemblyApplicationException failure = new OwnersAssemblyApplicationException(
                    STORAGE_UNAVAILABLE, "材料存储返回的大小、类型或 ETag 与原文件不一致");
            deleteMaterialQuietly(objectKey, failure);
            throw failure;
        }
    }

    private String materialObjectKey(Long tenantId, Long sessionId, MaterialType materialType, String contentType) {
        return "owners-assemblies/tenant-" + tenantId + "/session-" + sessionId + "/"
                + LocalDate.now() + "/" + materialType.name().toLowerCase(Locale.ROOT) + "-"
                + UUID.randomUUID() + materialExtension(contentType);
    }

    private String materialExtension(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
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

    private void deleteMaterialQuietly(String objectKey, RuntimeException failure) {
        try {
            ownersAssemblyMaterialStorage.delete(objectKey);
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private OwnersAssemblyPackage loadVotingPackage(Long packageId, Long tenantId) {
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository.findPackageForUpdate(packageId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决包不存在"));
        requirePackageStatus(ballotPackage, STATUS_VOTING);
        requireRuleSnapshot(ballotPackage);
        Instant now = Instant.now();
        if (now.isBefore(ballotPackage.voteStartAt()) || now.isAfter(ballotPackage.voteEndAt())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "表决包不在投票时间窗口内");
        }
        if (trim(ballotPackage.packageHash()) == null) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "表决包未锁定版本哈希");
        }
        return ballotPackage;
    }

    /** 纸质选票在方案公示期结束后发放，送达凭证只允许在已开启投票的会次归档。 */
    private OwnersAssemblyPackage loadDeliveryPackage(Long packageId, Long tenantId) {
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository.findPackageForUpdate(packageId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决安排不存在"));
        requireRuleSnapshot(ballotPackage);
        if (!STATUS_VOTING.equals(ballotPackage.status())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "公示期结束并开始投票后才可登记纸质选票送达记录");
        }
        if (trim(ballotPackage.packageHash()) == null) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "表决安排尚未发布");
        }
        return ballotPackage;
    }

    private void validateSubjectInPackage(OwnersAssemblyPackage ballotPackage, Long subjectId) {
        if (subjectId == null || !ownersAssemblyRepository
                .listSubjectIds(ballotPackage.packageId(), ballotPackage.tenantId()).contains(subjectId)) {
            throw new OwnersAssemblyApplicationException(NOT_FOUND, "表决事项不属于该表决包");
        }
    }

    /**
     * 业主大会旧表决安排与统一表决包必须一一对应，不能仅凭任一事项碰巧有关联就继续办理。
     */
    private VotingExecutionPackage requireExecutionPackage(OwnersAssemblyPackage ballotPackage,
                                                            List<Long> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "表决安排至少需要一个表决事项");
        }
        VotingExecutionPackage executionPackage = votingExecutionService.findPackageBySubjectId(subjectIds.getFirst())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(
                        INVALID_STATUS, "本次表决安排尚未建立统一收票记录，不能继续办理"));
        if (!ballotPackage.tenantId().equals(executionPackage.getTenantId())
                || executionPackage.getBusinessType() != VotingExecutionPackage.BusinessType.OWNERS_ASSEMBLY
                || !ballotPackage.packageId().equals(executionPackage.getBusinessReferenceId())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "表决安排与统一收票记录不一致");
        }
        for (Long subjectId : subjectIds) {
            VotingExecutionPackage subjectPackage = votingExecutionService.findPackageBySubjectId(subjectId)
                    .orElseThrow(() -> new OwnersAssemblyApplicationException(
                            INVALID_STATUS, "表决事项尚未加入统一收票记录 subjectId=" + subjectId));
            if (!executionPackage.getPackageId().equals(subjectPackage.getPackageId())) {
                throw new OwnersAssemblyApplicationException(
                        INVALID_STATUS, "同一表决安排的事项被拆分到了不同收票记录");
            }
        }
        return executionPackage;
    }

    private OwnersAssemblyApplicationException translateExecutionFailure(
            VotingExecutionService.VotingExecutionException failure) {
        OwnersAssemblyApplicationException.Reason reason = switch (failure.getReason()) {
            case NOT_FOUND -> NOT_FOUND;
            case INVALID_STATUS -> INVALID_STATUS;
            case ELECTORATE_NOT_FOUND -> OPID_OUT_OF_SCOPE;
            case DUPLICATE_BALLOT -> VOTE_ALREADY_CAST;
            case CHANNEL_NOT_ALLOWED -> FORBIDDEN;
            case DELIVERY_REQUIRED -> DELIVERY_REQUIRED;
            case CONCURRENT_MODIFICATION -> CONCURRENT_MODIFICATION;
            case INVALID_COMMAND -> PARAM_INVALID;
        };
        return new OwnersAssemblyApplicationException(reason, failure.getMessage(), failure);
    }

    private OwnersAssemblyApplicationException translatePaperVotingFailure(PaperVotingException failure) {
        OwnersAssemblyApplicationException.Reason reason = switch (failure.getReason()) {
            case NOT_FOUND -> NOT_FOUND;
            case INVALID_STATUS -> INVALID_STATUS;
            case INVALID_ARGUMENT -> PARAM_INVALID;
            case DUPLICATE -> VOTE_ALREADY_CAST;
            case CONCURRENT_MODIFICATION -> CONCURRENT_MODIFICATION;
        };
        return new OwnersAssemblyApplicationException(reason, failure.getMessage(), failure);
    }

    private UserContext requireSysContext(Long tenantId, Long expectedUserId) {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isSysUser() || ctx.userId() == null) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到管理端工作身份");
        }
        if (tenantId == null || ctx.tenantId() == null || !tenantId.equals(ctx.tenantId())) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到当前小区租户上下文");
        }
        if (expectedUserId != null && !expectedUserId.equals(ctx.userId())) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "操作人身份不匹配");
        }
        return ctx;
    }

    /**
     * 正式业主大会的安排、发布、开票和结算由当前届主任或副主任办理。
     * 物业可以协助录入会前材料，但不能以协办权限替代业主自治方的正式决定。
     */
    private UserContext requireFormalAssemblyExecutive(Long tenantId) {
        UserContext actor = requireSysContext(tenantId, null);
        if (!actor.hasPermission(FORMAL_MANAGE_PERMISSION)) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "当前角色无权办理业主大会正式环节");
        }
        String position = committeePositionRepository.findActivePosition(tenantId, actor.userId()).orElse(null);
        if (!EXECUTIVE_POSITIONS.contains(position)) {
            throw new OwnersAssemblyApplicationException(
                    FORBIDDEN, "业主大会正式环节仅限当前届主任或副主任办理");
        }
        return actor;
    }

    private void requirePackageStatus(OwnersAssemblyPackage ballotPackage, String status) {
        if (!status.equals(ballotPackage.status())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS,
                    "表决包状态不允许该动作 status=" + ballotPackage.status());
        }
    }

    private void requireChannelAllowed(OwnersAssemblyPackage ballotPackage, String channel) {
        if (!CHANNEL_PAPER.equals(channel)) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "不支持的纸质办理动作");
        }
        if (CHANNEL_PAPER.equals(channel) && !ballotPackage.paperAllowed()) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "该表决包未启用纸质投票");
        }
    }

    private String buildPackageHash(OwnersAssemblyPackage ballotPackage,
                                    OwnersAssemblyRuleSnapshot ruleSnapshot,
                                    List<Long> subjectIds) {
        return PayloadHasher.sha256Hex(String.join("|",
                ballotPackage.packageId().toString(),
                ruleSnapshot.ruleSnapshotId().toString(),
                requireText(ruleSnapshot.configurationSha256(), "议事规则配置摘要"),
                ballotPackage.packageVersion().toString(),
                ballotPackage.votingChannelPolicy(),
                ballotPackage.publicNoticeDays().toString(),
                ballotPackage.announcementHash(),
                ballotPackage.attachmentManifestHash(),
                ballotPackage.ballotTemplateHash(),
                ballotPackage.electronicSealHash() == null ? "" : ballotPackage.electronicSealHash(),
                ballotPackage.voteStartAt().toString(),
                ballotPackage.voteEndAt().toString(),
                subjectIds.toString()));
    }

    /** 管理端办理台只暴露纸票记录、协助进度和线上汇总，不返回线上票面选择。 */
    public record VotingWorkbench(
            List<ElectorateWorkbenchItem> electorate,
            PaperVotingService.Workbench paper,
            List<PaperAssistanceWorkbenchItem> paperAssistance,
            OnlineVotingService.ManagementProgress online,
            long duplicatePaperDecisionCount,
            Long currentActorUserId
    ) {
        public VotingWorkbench {
            electorate = electorate == null ? List.of() : List.copyOf(electorate);
            paperAssistance = paperAssistance == null ? List.of() : List.copyOf(paperAssistance);
        }
    }

    public record ElectorateWorkbenchItem(
            Long snapshotItemId,
            Long roomId,
            Long buildingId,
            BigDecimal certifiedArea,
            Long representativeOpid,
            String buildingName,
            String unitName,
            String roomName
    ) {
    }

    public record PreparationOptions(
            String ruleName,
            String ruleVersion,
            LocalDate effectiveDate,
            boolean ready,
            List<FormalVotingRulePolicy.ReadinessIssue> blockingItems,
            List<String> allowedPreparationModes,
            Instant earliestVoteStartAt,
            Integer planPublicityDays,
            Integer meetingNoticeDays,
            Integer resultAnnouncementDays,
            Set<OwnersAssemblyRuleConfiguration.DeliveryMethod> validDeliveryMethods,
            Boolean paperBallotSealRequired,
            OwnersAssemblyRuleConfiguration.ProxyVotingPolicy proxyVotingPolicy
    ) {
        public PreparationOptions {
            blockingItems = blockingItems == null ? List.of() : List.copyOf(blockingItems);
            allowedPreparationModes = allowedPreparationModes == null
                    ? List.of() : List.copyOf(allowedPreparationModes);
            validDeliveryMethods = validDeliveryMethods == null
                    ? Set.of() : Set.copyOf(validDeliveryMethods);
        }
    }

    public record PaperAssistanceWorkbenchItem(
            Long requestId,
            Long opid,
            Long buildingId,
            Long roomId,
            PaperAssistanceStage stage,
            Instant requestedAt,
            Instant fulfilledAt,
            Instant withdrawnAt,
            Long paperDeliveryId
    ) {
    }

    public enum PaperAssistanceStage {
        PENDING_PAPER_PROVISION,
        PAPER_PROCESSING,
        COMPLETED,
        WITHDRAWN
    }

    private String normalize(String value, String field) {
        String trimmed = requireText(value, field);
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String field) {
        String trimmed = trim(value);
        if (trimmed == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, field + " 必填");
        }
        return trimmed;
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = requireText(value, field);
        if (normalized.length() > maxLength) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, field + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
