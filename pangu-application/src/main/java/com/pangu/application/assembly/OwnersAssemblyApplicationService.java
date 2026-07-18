// 关联业务：编排业主大会的会前事项、材料归档、公示、表决、送达和计票状态机。
package com.pangu.application.assembly;

import com.pangu.application.assembly.command.CastAssemblyPaperVoteCommand;
import com.pangu.application.assembly.command.CreateAssemblySubjectDraftCommand;
import com.pangu.application.assembly.command.CreateOwnersAssemblySessionCommand;
import com.pangu.application.assembly.command.ConfirmAssemblyArrangementCommand;
import com.pangu.application.assembly.command.CastAssemblyPaperVoteWithMaterialCommand;
import com.pangu.application.assembly.command.RecordAssemblyDeliveryWithMaterialCommand;
import com.pangu.application.assembly.command.UploadOwnersAssemblyMaterialCommand;
import com.pangu.application.support.PayloadHasher;
import com.pangu.application.voting.VotingApplicationService;
import com.pangu.application.voting.command.SettleSubjectCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.assembly.OwnersAssemblyDeliveryRecord;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial.MaterialType;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleSnapshot;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.assembly.OwnersAssemblySubjectDraft;
import com.pangu.domain.model.assembly.OwnersAssemblyVoteRecord;
import com.pangu.domain.model.asset.OwnerPropertyVotingView;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingDecisionRule;
import com.pangu.domain.model.voting.VotingSettlementPolicy;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.voting.VotingSubjectActions;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.domain.repository.CommitteePositionRepository;
import com.pangu.domain.repository.OwnersAssemblyMaterialStorage;
import com.pangu.domain.repository.OwnersAssemblyRepository;
import com.pangu.domain.repository.OwnersAssemblyRuleRepository;
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
import java.util.Set;
import java.util.UUID;

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
     * 当前正式办理只实现了经规则确认的书面征求意见和纸质选票闭环。
     * 线下会议、线上及混合表决所需的签到、代理、身份和重复票证据链尚未建模，不能伪装为可用流程。
     */
    private static final Set<String> SESSION_MODES = Set.of("WRITTEN_DECISION");
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
    private final OwnersAssemblyMaterialStorage ownersAssemblyMaterialStorage;
    private final CommitteePositionRepository committeePositionRepository;
    private final VotingSubjectRepository votingSubjectRepository;
    private final VoteItemRepository voteItemRepository;
    private final OwnerPropertyVotingRepository ownerPropertyVotingRepository;
    private final VotingApplicationService votingApplicationService;
    private final UserContextHolder userContextHolder;

    @Transactional
    public OwnersAssemblySession createSession(CreateOwnersAssemblySessionCommand command) {
        requireSysContext(command.tenantId(), command.createdByUserId());
        String title = requireText(command.title(), "title");
        String mode = normalize(command.preparationMode(), "preparationMode");
        if (!SESSION_MODES.contains(mode)) {
            throw new OwnersAssemblyApplicationException(
                    PARAM_INVALID, "当前系统仅支持经规则确认的书面征求意见；线下会议、线上或混合表决的"
                    + "签到、身份、代理与证据链尚未实现");
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
        OwnersAssemblyRuleSnapshot ruleSnapshot = ownersAssemblyRepository
                .findRuleSnapshotBySession(sessionId, tenantId)
                .orElse(null);
        List<VotingSubject> formalSubjects = arrangement == null ? List.of()
                : ownersAssemblyRepository.listSubjectIds(arrangement.packageId(), tenantId).stream()
                .map(subjectId -> votingSubjectRepository.findById(subjectId).orElseThrow(
                        () -> new OwnersAssemblyApplicationException(NOT_FOUND, "正式表决事项不存在")))
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
                OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_ONLY.name(),
                ruleSnapshot.configuration().planPublicityDays(),
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
        requireFormalAssemblyExecutive(tenantId);
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
        for (Long subjectId : ownersAssemblyRepository.listSubjectIds(packageId, tenantId)) {
            openSubject(subjectId, now);
        }
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

    @Transactional
    public OwnersAssemblyDeliveryRecord recordPaperDelivery(RecordAssemblyDeliveryWithMaterialCommand command) {
        requireSysContext(command.tenantId(), command.deliveredByUserId());
        OwnersAssemblySession session = ownersAssemblyRepository.findSession(command.sessionId(), command.tenantId())
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在"));
        OwnersAssemblyPackage arrangement = requireLatestArrangement(session.sessionId(), session.tenantId());
        OwnersAssemblyMaterial evidence = requireMaterial(
                command.evidenceMaterialId(), session, MaterialType.DELIVERY_EVIDENCE);
        return recordPaperDelivery(
                arrangement.packageId(),
                session.tenantId(),
                command.opid(),
                command.deliveryMethod(),
                evidence.contentSha256(),
                command.deliveredByUserId());
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

    /**
     * 纸质选票送达只能从本会次已归档的凭证触发，且送达方式必须来自冻结规则，不能由接口传入任意字符串。
     */
    private OwnersAssemblyDeliveryRecord recordPaperDelivery(Long packageId,
                                                              Long tenantId,
                                                              Long opid,
                                                              OwnersAssemblyRuleConfiguration.DeliveryMethod deliveryMethod,
                                                              String evidenceHash,
                                                              Long deliveredByUserId) {
        OwnersAssemblyPackage ballotPackage = loadDeliveryPackage(packageId, tenantId);
        OwnersAssemblyRuleSnapshot ruleSnapshot = requireRuleSnapshot(ballotPackage);
        requireChannelAllowed(ballotPackage, CHANNEL_PAPER);
        if (deliveryMethod == null || !ruleSnapshot.configuration().validDeliveryMethods().contains(deliveryMethod)) {
            throw new OwnersAssemblyApplicationException(
                    PARAM_INVALID, "送达方式不在本次业主大会冻结议事规则认可范围内");
        }
        OwnerPropertyVotingView owner = loadOwner(opid, tenantId, null);
        return ownersAssemblyRepository.insertDelivery(new OwnersAssemblyDeliveryRecord(
                null,
                ballotPackage.packageId(),
                ballotPackage.tenantId(),
                owner.opid(),
                owner.uid(),
                CHANNEL_PAPER,
                deliveryMethod.name(),
                requireText(evidenceHash, "evidenceHash"),
                deliveredByUserId,
                Instant.now()));
    }

    private OwnersAssemblyVoteRecord castPaperVote(CastAssemblyPaperVoteCommand command) {
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
    public OwnersAssemblyPackage settlePackage(Long packageId, Long tenantId) {
        requireFormalAssemblyExecutive(tenantId);
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository.findPackageForUpdate(packageId, tenantId)
                .orElseThrow(() -> new OwnersAssemblyApplicationException(NOT_FOUND, "表决包不存在"));
        requirePackageStatus(ballotPackage, STATUS_VOTING);
        OwnersAssemblyRuleSnapshot ruleSnapshot = requireRuleSnapshot(ballotPackage);
        if (Instant.now().isBefore(ballotPackage.voteEndAt())) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "尚未到达投票截止时间，禁止结算");
        }
        for (Long subjectId : ownersAssemblyRepository.listSubjectIds(packageId, tenantId)) {
            VotingSubject subject = loadVotingSubject(subjectId, tenantId);
            votingApplicationService.settle(
                    new SettleSubjectCommand(subjectId, "OWNERS_ASSEMBLY"),
                    settlementPolicyFor(subject, ruleSnapshot));
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

    /**
     * 本期只开放真实材料已覆盖的纸质书面征求意见。不能把尚无状态与证据模型的能力降级为默认行为。
     */
    private void requireSupportedFormalFlow(OwnersAssemblySession session,
                                            OwnersAssemblyRuleConfiguration configuration) {
        if (!"WRITTEN_DECISION".equals(session.preparationMode())) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "当前会议形式没有对应的正式办理状态机；仅支持书面征求意见");
        }
        if (configuration == null
                || !configuration.allowedMeetingForms().contains(
                OwnersAssemblyRuleConfiguration.MeetingForm.WRITTEN_CONSULTATION)) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "当前有效议事规则未确认书面征求意见，不能进入正式办理");
        }
        requireSupportedFrozenRule(configuration);
        if (configuration.meetingNoticeDays() == null || configuration.meetingNoticeDays() != 0) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "当前系统尚未建模独立的会议通知阶段；规则要求会议通知期限时不能进入正式办理");
        }
        if (configuration.resultAnnouncementDays() == null || configuration.resultAnnouncementDays() != 0) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "当前系统尚未建模独立的结果公告阶段；规则要求结果公告期限时不能进入正式办理");
        }
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
        if (configuration.votingChannelPolicy()
                != OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_ONLY) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "当前系统只实现纸质选票闭环；线上或混合表决规则不能进入正式办理");
        }
        if (configuration.nonResponsePolicy()
                != OwnersAssemblyRuleConfiguration.NonResponsePolicy.NOT_PARTICIPATED) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "当前系统仅支持未表态不计入参与；其他未表态规则尚无可审计计票模型");
        }
        if (configuration.proxyVotingPolicy()
                != OwnersAssemblyRuleConfiguration.ProxyVotingPolicy.NOT_ALLOWED) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "当前系统尚未建模代理授权、核验与存证，不能按允许委托的规则办理");
        }
        if (configuration.duplicateVotePolicy()
                != OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.NOT_APPLICABLE) {
            throw new OwnersAssemblyApplicationException(
                    INVALID_STATUS, "纸质单渠道表决的重复投票规则必须为不适用，不能使用平台默认替代");
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
                ruleSnapshot.configurationSha256());
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
            throw new OwnersAssemblyApplicationException(PARAM_INVALID, "当前业主大会仅支持纸质选票通道");
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
