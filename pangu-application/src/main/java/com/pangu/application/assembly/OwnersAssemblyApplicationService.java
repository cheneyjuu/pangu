// 关联业务：编排业主大会的会前事项、材料归档、公示、表决、送达和计票状态机。
package com.pangu.application.assembly;

import com.pangu.application.assembly.command.AddAssemblySubjectCommand;
import com.pangu.application.assembly.command.CastAssemblyOnlineVoteCommand;
import com.pangu.application.assembly.command.CastAssemblyPaperVoteCommand;
import com.pangu.application.assembly.command.CreateBallotPackageCommand;
import com.pangu.application.assembly.command.CreateAssemblySubjectDraftCommand;
import com.pangu.application.assembly.command.CreateOwnersAssemblySessionCommand;
import com.pangu.application.assembly.command.ConfirmAssemblyArrangementCommand;
import com.pangu.application.assembly.command.CastAssemblyPaperVoteWithMaterialCommand;
import com.pangu.application.assembly.command.RecordAssemblyDeliveryCommand;
import com.pangu.application.assembly.command.RecordAssemblyDeliveryWithMaterialCommand;
import com.pangu.application.assembly.command.UploadOwnersAssemblyMaterialCommand;
import com.pangu.application.support.PayloadHasher;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.VotingApplicationService;
import com.pangu.application.voting.command.SettleSubjectCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.assembly.OwnersAssemblyDeliveryRecord;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial.MaterialType;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.assembly.OwnersAssemblySubjectDraft;
import com.pangu.domain.model.assembly.OwnersAssemblyVoteRecord;
import com.pangu.domain.model.asset.OwnerPropertyVotingView;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.voting.VotingSubjectActions;
import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.EvaluationResult;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.domain.repository.OwnersAssemblyMaterialStorage;
import com.pangu.domain.repository.OwnersAssemblyRepository;
import com.pangu.domain.repository.VoteItemRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.AUTH_LEVEL_INSUFFICIENT;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.CONCURRENT_MODIFICATION;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.DELIVERY_REQUIRED;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.NOTICE_NOT_COMPLETED;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.OPID_NOT_OWNED;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.OPID_OUT_OF_SCOPE;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.STORAGE_UNAVAILABLE;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.VOTE_ALREADY_CAST;

@Service
@RequiredArgsConstructor
public class OwnersAssemblyApplicationService {

    /**
     * 新办理页只允许可线下留痕的会议形式；旧 FULL/QUICK 值仅为兼容历史会次，
     * 线上或混合表决不能在身份绑定和电子签署闭环未验证前由此流程新建。
     */
    private static final Set<String> SESSION_MODES = Set.of(
            "FULL", "QUICK", "WRITTEN_DECISION", "OFFLINE_MEETING");
    private static final Set<String> PACKAGE_POLICIES = Set.of("PAPER_ONLY", "ONLINE_ONLY", "PAPER_AND_ONLINE");
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
    private static final String CHANNEL_ONLINE = "ONLINE";

    private final OwnersAssemblyRepository ownersAssemblyRepository;
    private final OwnersAssemblyMaterialStorage ownersAssemblyMaterialStorage;
    private final VotingSubjectRepository votingSubjectRepository;
    private final VoteItemRepository voteItemRepository;
    private final OwnerPropertyVotingRepository ownerPropertyVotingRepository;
    private final VotingApplicationService votingApplicationService;
    private final AbacPolicyEngine abacPolicyEngine;
    private final UserContextHolder userContextHolder;

    @Transactional
    public OwnersAssemblySession createSession(CreateOwnersAssemblySessionCommand command) {
        requireSysContext(command.tenantId(), command.createdByUserId());
        String title = requireText(command.title(), "title");
        String mode = normalize(command.preparationMode(), "preparationMode");
        if (!SESSION_MODES.contains(mode)) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "不支持的业主大会会议形式");
        }
        return ownersAssemblyRepository.insertSession(new OwnersAssemblySession(
                null, command.tenantId(), title, mode, "PREPARING", command.createdByUserId(), null));
    }

    @Transactional(readOnly = true)
    public List<OwnersAssemblySession> listSessions(Long tenantId) {
        requireSysContext(tenantId, null);
        return ownersAssemblyRepository.listSessions(tenantId);
    }

    @Transactional(readOnly = true)
    public OwnersAssemblyWorkspace loadWorkspace(Long sessionId, Long tenantId) {
        requireSysContext(tenantId, null);
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(sessionId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = ownersAssemblyRepository.findLatestPackageBySession(sessionId, tenantId)
                .orElse(null);
        List<VotingSubject> formalSubjects = arrangement == null ? List.of()
                : ownersAssemblyRepository.listSubjectIds(arrangement.packageId(), tenantId).stream()
                .map(subjectId -> votingSubjectRepository.findById(subjectId).orElseThrow(
                        () -> new OwnersAssemblyApplicationException(NOT_FOUND, "正式表决事项不存在")))
                .toList();
        return new OwnersAssemblyWorkspace(
                session,
                arrangement,
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
        UserContext actor = requireSysContext(command.tenantId(), null);
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        requirePreparing(session);
        if (ownersAssemblyRepository.findLatestPackageBySession(session.sessionId(), session.tenantId()).isPresent()) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "本次业主大会已确认公示与表决安排");
        }
        List<OwnersAssemblySubjectDraft> drafts = ownersAssemblyRepository
                .listSubjectDrafts(session.sessionId(), session.tenantId());
        if (drafts.isEmpty()) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "请先新增至少一个表决事项");
        }
        int noticeDays = command.publicNoticeDays() == null ? 7 : command.publicNoticeDays();
        if (noticeDays < 7) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "业主大会表决事项公示期至少 7 天");
        }
        if (command.voteStartAt() == null || command.voteEndAt() == null
                || !command.voteEndAt().isAfter(command.voteStartAt())) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "投票截止时间必须晚于投票开始时间");
        }
        OwnersAssemblyMaterial publicNotice = requireMaterial(
                command.publicNoticeMaterialId(), session, MaterialType.PUBLIC_NOTICE);
        OwnersAssemblyMaterial ballotTemplate = requireMaterial(
                command.ballotTemplateMaterialId(), session, MaterialType.PAPER_BALLOT_TEMPLATE);
        List<Long> attachmentIds = command.planAttachmentMaterialIds() == null ? List.of()
                : command.planAttachmentMaterialIds().stream().filter(id -> id != null).distinct().toList();
        if (attachmentIds.isEmpty()) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "请至少归档一份方案附件");
        }
        List<OwnersAssemblyMaterial> attachments = attachmentIds.stream()
                .map(materialId -> requireMaterial(materialId, session, MaterialType.PLAN_ATTACHMENT))
                .sorted(Comparator.comparing(OwnersAssemblyMaterial::materialId))
                .toList();
        OwnersAssemblyPackage arrangement = createPackage(new CreateBallotPackageCommand(
                session.sessionId(),
                session.tenantId(),
                "PAPER_ONLY",
                noticeDays,
                publicNotice.contentSha256(),
                buildAttachmentManifestHash(attachments),
                ballotTemplate.contentSha256(),
                null,
                command.voteStartAt(),
                command.voteEndAt()));
        for (OwnersAssemblySubjectDraft draft : drafts) {
            addDraftSubjectToArrangement(arrangement, draft, actor.userId());
        }
        return arrangement;
    }

    @Transactional
    public OwnersAssemblyPackage createPackage(CreateBallotPackageCommand command) {
        UserContext ctx = requireSysContext(command.tenantId(), null);
        OwnersAssemblySession session = ownersAssemblyRepository
                .findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        String policy = normalize(command.votingChannelPolicy(), "votingChannelPolicy");
        if (!PACKAGE_POLICIES.contains(policy)) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "votingChannelPolicy 不合法");
        }
        int noticeDays = command.publicNoticeDays() == null ? 7 : command.publicNoticeDays();
        if (noticeDays < 7) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "业主大会表决事项公示期至少 7 天");
        }
        if (command.voteStartAt() == null || command.voteEndAt() == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "voteStartAt/voteEndAt 必填");
        }
        if (!command.voteEndAt().isAfter(command.voteStartAt())) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "voteEndAt 必须晚于 voteStartAt");
        }
        String electronicSealHash = trim(command.electronicSealHash());
        if (!"PAPER_ONLY".equals(policy) && electronicSealHash == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "线上表决必须锁定电子公章摘要");
        }
        OwnersAssemblyPackage created = ownersAssemblyRepository.insertPackage(new OwnersAssemblyPackage(
                null,
                session.sessionId(),
                command.tenantId(),
                1,
                STATUS_PACKAGE_DRAFT,
                policy,
                noticeDays,
                requireText(command.announcementHash(), "announcementHash"),
                requireText(command.attachmentManifestHash(), "attachmentManifestHash"),
                requireText(command.ballotTemplateHash(), "ballotTemplateHash"),
                electronicSealHash,
                null,
                null,
                null,
                command.voteStartAt(),
                command.voteEndAt(),
                ctx.userId(),
                null));
        ownersAssemblyRepository.updateSessionStatus(session.sessionId(), session.tenantId(), STATUS_PACKAGE_DRAFT);
        return created;
    }

    @Transactional
    public VotingSubject addSubject(AddAssemblySubjectCommand command) {
        requireSysContext(command.tenantId(), command.proposedByUserId());
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository
                .findPackageForUpdate(command.packageId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决包不存在"));
        return addSubjectToArrangement(
                ballotPackage,
                command.subjectType(),
                command.scope(),
                command.scopeReferenceId(),
                command.title(),
                command.content(),
                command.proposedByUserId(),
                command.partyRatioFloor());
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
        UserContext ctx = requireSysContext(tenantId, null);
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository.findPackageForUpdate(packageId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决包不存在"));
        requirePackageStatus(ballotPackage, STATUS_PACKAGE_DRAFT);
        List<Long> subjectIds = ownersAssemblyRepository.listSubjectIds(packageId, tenantId);
        if (subjectIds.isEmpty()) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "表决包至少需要一个表决事项");
        }
        String packageHash = buildPackageHash(ballotPackage, subjectIds);
        Instant noticeStartAt = Instant.now();
        Instant noticeEndAt = noticeStartAt.plus(ballotPackage.publicNoticeDays(), ChronoUnit.DAYS);
        int updated = ownersAssemblyRepository.lockPackage(
                packageId, tenantId, packageHash, noticeStartAt, noticeEndAt, ctx.userId());
        if (updated != 1) {
            throw new OwnersAssemblyApplicationException(CONCURRENT_MODIFICATION, "表决包已被并发修改");
        }
        for (Long subjectId : subjectIds) {
            publishSubject(subjectId);
        }
        ownersAssemblyRepository.updateSessionStatus(
                ballotPackage.sessionId(), ballotPackage.tenantId(), STATUS_PUBLIC_NOTICE);
        return ownersAssemblyRepository.findPackage(packageId, tenantId).orElseThrow();
    }

    @Transactional
    public OwnersAssemblyPackage openVoting(Long packageId, Long tenantId) {
        requireSysContext(tenantId, null);
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository.findPackageForUpdate(packageId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决包不存在"));
        requirePackageStatus(ballotPackage, STATUS_PUBLIC_NOTICE);
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
        for (Long subjectId : ownersAssemblyRepository.listSubjectIds(packageId, tenantId)) {
            openSubject(subjectId, now);
        }
        ownersAssemblyRepository.updateSessionStatus(
                ballotPackage.sessionId(), ballotPackage.tenantId(), STATUS_VOTING);
        return ownersAssemblyRepository.findPackage(packageId, tenantId).orElseThrow();
    }

    @Transactional
    public OwnersAssemblyPackage publishCurrentArrangement(Long sessionId, Long tenantId) {
        requireSysContext(tenantId, null);
        OwnersAssemblyPackage arrangement = requireLatestArrangement(sessionId, tenantId);
        return lockPackage(arrangement.packageId(), tenantId);
    }

    @Transactional
    public OwnersAssemblyPackage startVoting(Long sessionId, Long tenantId) {
        requireSysContext(tenantId, null);
        OwnersAssemblyPackage arrangement = requireLatestArrangement(sessionId, tenantId);
        return openVoting(arrangement.packageId(), tenantId);
    }

    @Transactional
    public OwnersAssemblyDeliveryRecord recordPaperDelivery(RecordAssemblyDeliveryWithMaterialCommand command) {
        requireSysContext(command.tenantId(), command.deliveredByUserId());
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
        OwnersAssemblyMaterial evidence = requireMaterial(
                command.evidenceMaterialId(), session, MaterialType.DELIVERY_EVIDENCE);
        return recordDelivery(new RecordAssemblyDeliveryCommand(
                arrangement.packageId(),
                session.tenantId(),
                command.opid(),
                CHANNEL_PAPER,
                command.deliveryMethod(),
                evidence.contentSha256(),
                command.deliveredByUserId()));
    }

    @Transactional
    public OwnersAssemblyVoteRecord castPaperVoteWithMaterial(CastAssemblyPaperVoteWithMaterialCommand command) {
        requireSysContext(command.tenantId(), command.enteredByUserId());
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
        OwnersAssemblyMaterial ballot = requireMaterial(command.ballotMaterialId(), session, MaterialType.PAPER_BALLOT);
        return castPaperVote(new CastAssemblyPaperVoteCommand(
                arrangement.packageId(),
                command.subjectId(),
                session.tenantId(),
                command.opid(),
                command.choice(),
                ballot.contentSha256(),
                command.enteredByUserId()));
    }

    @Transactional
    public OwnersAssemblyDeliveryRecord recordDelivery(RecordAssemblyDeliveryCommand command) {
        requireSysContext(command.tenantId(), command.deliveredByUserId());
        OwnersAssemblyPackage ballotPackage = loadDeliveryPackage(command.packageId(), command.tenantId());
        String channel = normalize(command.deliveryChannel(), "deliveryChannel");
        requireChannelAllowed(ballotPackage, channel);
        OwnerPropertyVotingView owner = loadOwner(command.opid(), command.tenantId(), null);
        return ownersAssemblyRepository.insertDelivery(new OwnersAssemblyDeliveryRecord(
                null,
                ballotPackage.packageId(),
                ballotPackage.tenantId(),
                owner.opid(),
                owner.uid(),
                channel,
                requireText(command.deliveryMethod(), "deliveryMethod"),
                requireText(command.evidenceHash(), "evidenceHash"),
                command.deliveredByUserId(),
                Instant.now()));
    }

    @Transactional
    public OwnersAssemblyVoteRecord castPaperVote(CastAssemblyPaperVoteCommand command) {
        requireSysContext(command.tenantId(), command.enteredByUserId());
        OwnersAssemblyPackage ballotPackage = loadVotingPackage(command.packageId(), command.tenantId());
        requireChannelAllowed(ballotPackage, CHANNEL_PAPER);
        validateSubjectInPackage(ballotPackage, command.subjectId());
        VotingSubject subject = loadVotingSubject(command.subjectId(), command.tenantId());
        OwnerPropertyVotingView owner = loadOwner(command.opid(), command.tenantId(), null);
        validateOwnerScope(subject, owner);
        requireDelivery(ballotPackage, owner, CHANNEL_PAPER);
        if (voteItemRepository.findActiveVote(subject.getSubjectId(), owner.opid(), null).isPresent()) {
            throw new OwnersAssemblyApplicationException(VOTE_ALREADY_CAST, "该房产已有有效票，不能重复录入纸票");
        }
        long voteId = insertVote(subject, owner, command.choice(), VoteChannel.PAPER, null);
        return ownersAssemblyRepository.insertVoteRecord(new OwnersAssemblyVoteRecord(
                null,
                ballotPackage.packageId(),
                subject.getSubjectId(),
                voteId,
                ballotPackage.tenantId(),
                owner.opid(),
                owner.uid(),
                CHANNEL_PAPER,
                ballotPackage.packageHash(),
                requireText(command.ballotFileHash(), "ballotFileHash"),
                null,
                true,
                null,
                null,
                null));
    }

    @Transactional
    public OwnersAssemblyVoteRecord castOnlineVote(CastAssemblyOnlineVoteCommand command) {
        OwnersAssemblyPackage ballotPackage = loadVotingPackage(command.packageId(), command.tenantId());
        requireOwnerContext(command.tenantId(), command.uid());
        requireChannelAllowed(ballotPackage, CHANNEL_ONLINE);
        validateSubjectInPackage(ballotPackage, command.subjectId());
        VotingSubject subject = loadVotingSubject(command.subjectId(), command.tenantId());
        OwnerPropertyVotingView owner = loadOwner(command.opid(), command.tenantId(), command.uid());
        validateOwnerScope(subject, owner);
        requireDelivery(ballotPackage, owner, CHANNEL_ONLINE);
        requireOnlineAuthentication(owner.uid(), command.tenantId());
        String ballotFileHash = requireText(command.ballotFileHash(), "ballotFileHash");
        String signatureHash = requireText(command.signatureHash(), "signatureHash");

        Optional<VoteItemRepository.StoredVote> activeVote =
                voteItemRepository.findActiveVote(subject.getSubjectId(), owner.opid(), null);
        if (activeVote.isPresent()) {
            if (activeVote.get().voteChannel() != VoteChannel.PAPER || Instant.now().isAfter(ballotPackage.voteEndAt())) {
                throw new OwnersAssemblyApplicationException(VOTE_ALREADY_CAST, "该房产已有有效票，不能重复投票");
            }
            voteItemRepository.invalidateVote(activeVote.get().voteId(), "ONLINE_REAL_NAME_VOTE_SUPERSEDES_PAPER");
        }
        long voteId = insertVote(subject, owner, command.choice(), VoteChannel.ONLINE, signatureHash);
        activeVote.ifPresent(vote -> ownersAssemblyRepository.invalidateVoteRecordByVoteId(
                vote.voteId(), voteId, "ONLINE_REAL_NAME_VOTE_SUPERSEDES_PAPER"));
        return ownersAssemblyRepository.insertVoteRecord(new OwnersAssemblyVoteRecord(
                null,
                ballotPackage.packageId(),
                subject.getSubjectId(),
                voteId,
                ballotPackage.tenantId(),
                owner.opid(),
                owner.uid(),
                CHANNEL_ONLINE,
                ballotPackage.packageHash(),
                ballotFileHash,
                signatureHash,
                true,
                null,
                null,
                null));
    }

    @Transactional
    public OwnersAssemblyPackage settlePackage(Long packageId, Long tenantId) {
        requireSysContext(tenantId, null);
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository.findPackageForUpdate(packageId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决包不存在"));
        requirePackageStatus(ballotPackage, STATUS_VOTING);
        if (Instant.now().isBefore(ballotPackage.voteEndAt())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "尚未到达投票截止时间，禁止结算");
        }
        for (Long subjectId : ownersAssemblyRepository.listSubjectIds(packageId, tenantId)) {
            votingApplicationService.settle(new SettleSubjectCommand(subjectId, "OWNERS_ASSEMBLY"));
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
        requireSysContext(tenantId, null);
        OwnersAssemblyPackage arrangement = requireLatestArrangement(sessionId, tenantId);
        return settlePackage(arrangement.packageId(), tenantId);
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
                if (!STATUS_PUBLIC_NOTICE.equals(arrangement.status()) && !STATUS_VOTING.equals(arrangement.status())) {
                    throw new OwnersAssemblyApplicationException(
                            INVALID_STATUS, "仅在公示或投票期间可以归档送达凭证");
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
        Instant now = Instant.now();
        if (now.isBefore(ballotPackage.voteStartAt()) || now.isAfter(ballotPackage.voteEndAt())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "表决包不在投票时间窗口内");
        }
        if (trim(ballotPackage.packageHash()) == null) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "表决包未锁定版本哈希");
        }
        return ballotPackage;
    }

    /**
     * 纸质材料送达发生在公示至投票期间，不能错误地要求必须已经开始投票。
     */
    private OwnersAssemblyPackage loadDeliveryPackage(Long packageId, Long tenantId) {
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository.findPackageForUpdate(packageId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决安排不存在"));
        if (!STATUS_PUBLIC_NOTICE.equals(ballotPackage.status()) && !STATUS_VOTING.equals(ballotPackage.status())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "当前阶段不能登记送达记录");
        }
        if (trim(ballotPackage.packageHash()) == null) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "表决安排尚未发布");
        }
        return ballotPackage;
    }

    private void publishSubject(Long subjectId) {
        VotingSubject subject = votingSubjectRepository.findByIdForUpdate(subjectId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决事项不存在"));
        if (subject.getStatus() != SubjectStatus.DRAFT) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "表决事项不在草稿状态 subjectId=" + subjectId);
        }
        VotingSubjectActions.publish(subject);
        int updated = votingSubjectRepository.updateStatus(
                subjectId, SubjectStatus.PUBLISHED.getDbValue(), subject.getVersion());
        if (updated != 1) {
            throw new OwnersAssemblyApplicationException(CONCURRENT_MODIFICATION, "表决事项已被并发修改");
        }
    }

    private void openSubject(Long subjectId, Instant now) {
        VotingSubject subject = votingSubjectRepository.findByIdForUpdate(subjectId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决事项不存在"));
        try {
            VotingSubjectActions.openVoting(subject, now);
        } catch (VotingSubjectActions.IllegalSubjectTransitionException ex) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, ex.getMessage(), ex);
        }
        int updated = votingSubjectRepository.updateStatus(
                subjectId, SubjectStatus.VOTING.getDbValue(), subject.getVersion());
        if (updated != 1) {
            throw new OwnersAssemblyApplicationException(CONCURRENT_MODIFICATION, "表决事项已被并发修改");
        }
    }

    private VotingSubject loadVotingSubject(Long subjectId, Long tenantId) {
        VotingSubject subject = votingSubjectRepository.findById(subjectId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决事项不存在"));
        if (!tenantId.equals(subject.getTenantId()) || subject.getStatus() != SubjectStatus.VOTING) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "表决事项不在投票中");
        }
        return subject;
    }

    private void validateSubjectInPackage(OwnersAssemblyPackage ballotPackage, Long subjectId) {
        if (subjectId == null || !ownersAssemblyRepository
                .listSubjectIds(ballotPackage.packageId(), ballotPackage.tenantId()).contains(subjectId)) {
            throw new OwnersAssemblyApplicationException(NOT_FOUND, "表决事项不属于该表决包");
        }
    }

    private OwnerPropertyVotingView loadOwner(Long opid, Long tenantId, Long uid) {
        OwnerPropertyVotingView owner = ownerPropertyVotingRepository.findByOpid(opid)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(OPID_NOT_OWNED, "房产身份不存在"));
        if (!tenantId.equals(owner.tenantId()) || (uid != null && !uid.equals(owner.uid()))) {
            throw new OwnersAssemblyApplicationException(OPID_NOT_OWNED, "房产身份与当前业主或租户不匹配");
        }
        if (!owner.isValidForVoting()) {
            throw new OwnersAssemblyApplicationException(OPID_OUT_OF_SCOPE, "该房产当前不具备投票资格");
        }
        return owner;
    }

    private void validateOwnerScope(VotingSubject subject, OwnerPropertyVotingView owner) {
        if (subject.getScope() == VotingScope.BUILDING && !subject.getScopeReferenceId().equals(owner.buildingId())) {
            throw new OwnersAssemblyApplicationException(OPID_OUT_OF_SCOPE, "房产不在该表决事项楼栋范围内");
        }
        if (subject.getScope() == VotingScope.UNIT) {
            throw new OwnersAssemblyApplicationException(OPID_OUT_OF_SCOPE, "UNIT 范围暂未实现");
        }
    }

    private void requireDelivery(OwnersAssemblyPackage ballotPackage,
                                 OwnerPropertyVotingView owner,
                                 String channel) {
        if (!ownersAssemblyRepository.deliveryExists(
                ballotPackage.packageId(), ballotPackage.tenantId(), owner.opid(), owner.uid(), channel)) {
            throw new OwnersAssemblyApplicationException(DELIVERY_REQUIRED, "投票前必须完成对应通道送达留痕");
        }
    }

    private long insertVote(VotingSubject subject,
                            OwnerPropertyVotingView owner,
                            VoteChoice choice,
                            VoteChannel channel,
                            String signatureHash) {
        if (choice == null) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "choice 必填");
        }
        try {
            return voteItemRepository.insert(subject.getSubjectId(), VoteItem.builder()
                    .opid(owner.opid())
                    .uid(owner.uid())
                    .propertyArea(owner.buildArea())
                    .choice(choice)
                    .voteChannel(channel)
                    .build(), signatureHash);
        } catch (VoteItemRepository.DuplicateVoteException ex) {
            throw new OwnersAssemblyApplicationException(VOTE_ALREADY_CAST, "该房产已有有效票", ex);
        }
    }

    private void requireOnlineAuthentication(Long uid, Long tenantId) {
        UserContext ctx = userContextHolder.current();
        AuthenticationLevel level = ctx == null ? null : ctx.authLevel();
        EvaluationResult result = abacPolicyEngine.evaluateVoting(uid, tenantId, level);
        if (!result.isAllowed()) {
            throw new OwnersAssemblyApplicationException(AUTH_LEVEL_INSUFFICIENT,
                    result.getMessage() == null ? "线上表决需要完成人脸实名认证" : result.getMessage());
        }
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

    private void requireOwnerContext(Long tenantId, Long uid) {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isCUser() || ctx.uid() == null) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到业主身份");
        }
        if (!tenantId.equals(ctx.tenantId()) || !uid.equals(ctx.uid())) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "业主身份与当前租户不匹配");
        }
    }

    private void requirePackageStatus(OwnersAssemblyPackage ballotPackage, String status) {
        if (!status.equals(ballotPackage.status())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS,
                    "表决包状态不允许该动作 status=" + ballotPackage.status());
        }
    }

    private void requireChannelAllowed(OwnersAssemblyPackage ballotPackage, String channel) {
        if (!CHANNEL_PAPER.equals(channel) && !CHANNEL_ONLINE.equals(channel)) {
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "deliveryChannel/voteChannel 仅支持 PAPER/ONLINE");
        }
        if (CHANNEL_PAPER.equals(channel) && !ballotPackage.paperAllowed()) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "该表决包未启用纸质投票");
        }
        if (CHANNEL_ONLINE.equals(channel) && !ballotPackage.onlineAllowed()) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "该表决包未启用线上投票");
        }
    }

    private String buildPackageHash(OwnersAssemblyPackage ballotPackage, List<Long> subjectIds) {
        return PayloadHasher.sha256Hex(String.join("|",
                ballotPackage.packageId().toString(),
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
