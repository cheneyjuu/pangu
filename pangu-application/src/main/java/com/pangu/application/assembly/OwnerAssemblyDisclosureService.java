// 关联业务：向当前业主披露已发布业主大会材料，以及线上、纸质或混合表决的本人办理状态。
package com.pangu.application.assembly;

import com.pangu.application.support.PayloadHasher;
import com.pangu.application.voting.OnlineVotingService;
import com.pangu.application.voting.VotingDecisionResultProjector;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial.MaterialType;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleSnapshot;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.asset.OwnerPropertyVotingView;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.voting.PaperBallot;
import com.pangu.domain.model.voting.PaperVotingDelivery;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.domain.repository.OwnersAssemblyMaterialStorage;
import com.pangu.domain.repository.OwnersAssemblyRepository;
import com.pangu.domain.repository.PaperVotingRepository;
import com.pangu.domain.repository.PropertyBindingRepository;
import com.pangu.domain.repository.VotingExecutionRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import com.pangu.domain.repository.VotingResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.STORAGE_UNAVAILABLE;

/**
 * C 端业主大会只读服务。
 *
 * <p>本服务不以“未回复”等状态推定业主选择，也不向业主回显已经提交的具体选择。
 * 所有材料和办理渠道均以正式表决包锁定的清单与规则快照为准，不能回退到会前草稿材料全集。
 */
@Service
@RequiredArgsConstructor
public class OwnerAssemblyDisclosureService {

    private static final Set<String> DISCLOSABLE_STATUSES = Set.of("PUBLIC_NOTICE", "VOTING", "SETTLED");
    private static final Set<MaterialType> DOWNLOADABLE_MATERIAL_TYPES = Set.of(
            MaterialType.PUBLIC_NOTICE, MaterialType.PLAN_ATTACHMENT, MaterialType.PAPER_BALLOT_TEMPLATE);
    private static final Duration DOWNLOAD_VALIDITY = Duration.ofMinutes(10);

    private final OwnersAssemblyRepository ownersAssemblyRepository;
    private final VotingSubjectRepository votingSubjectRepository;
    private final VotingResultRepository votingResultRepository;
    private final VotingDecisionResultProjector votingDecisionResultProjector;
    private final OwnerPropertyVotingRepository ownerPropertyVotingRepository;
    private final VotingExecutionRepository votingExecutionRepository;
    private final PaperVotingRepository paperVotingRepository;
    private final PropertyBindingRepository propertyBindingRepository;
    private final OnlineVotingService onlineVotingService;
    private final OwnersAssemblyMaterialStorage materialStorage;
    private final UserContextHolder userContextHolder;

    @Transactional(readOnly = true)
    public OwnerAssemblyDisclosure disclosure(Long packageId) {
        VisibleAssembly visible = loadVisibleAssembly(packageId);
        return toDisclosure(visible);
    }

    @Transactional(readOnly = true)
    public OwnerAssemblyMaterialDownloadTicket createMaterialDownloadTicket(Long packageId, Long materialId) {
        VisibleAssembly visible = loadVisibleAssembly(packageId);
        OwnersAssemblyMaterial material = visible.materials().stream()
                .filter(candidate -> candidate.materialId().equals(materialId))
                .filter(candidate -> DOWNLOADABLE_MATERIAL_TYPES.contains(candidate.materialType()))
                .findFirst()
                .orElseThrow(this::notVisible);
        Instant expiresAt = Instant.now().plus(DOWNLOAD_VALIDITY);
        try {
            return new OwnerAssemblyMaterialDownloadTicket(
                    material.materialId(),
                    material.originalFileName(),
                    material.contentType(),
                    material.fileSize(),
                    materialStorage.createDownloadUrl(material.objectKey(), DOWNLOAD_VALIDITY).toString(),
                    expiresAt);
        } catch (RuntimeException ex) {
            throw new OwnersAssemblyApplicationException(
                    STORAGE_UNAVAILABLE, "生成业主大会材料下载地址失败", ex);
        }
    }

    private VisibleAssembly loadVisibleAssembly(Long packageId) {
        UserContext owner = requireOwner();
        if (packageId == null) {
            throw notVisible();
        }
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository
                .findPackage(packageId, owner.tenantId())
                .filter(candidate -> DISCLOSABLE_STATUSES.contains(candidate.status()))
                .orElseThrow(this::notVisible);
        List<OwnerPropertyVotingView> ownerProperties = ownerPropertyVotingRepository.listByUid(
                owner.uid(), owner.tenantId());
        if (ownerProperties.isEmpty()) {
            throw notVisible();
        }
        OwnersAssemblySession session = ownersAssemblyRepository
                .findSession(ballotPackage.sessionId(), owner.tenantId())
                .orElseThrow(this::notVisible);
        OwnersAssemblyRuleSnapshot snapshot = ownersAssemblyRepository
                .findRuleSnapshot(ballotPackage.ruleSnapshotId(), owner.tenantId())
                .orElseThrow(this::notVisible);
        List<OwnersAssemblyMaterial> materials = ownersAssemblyRepository
                .listPackageMaterials(ballotPackage.packageId(), owner.tenantId());
        validateLockedPublicMaterials(ballotPackage, materials);
        List<VotingSubject> subjects = ownersAssemblyRepository
                .listSubjectIds(ballotPackage.packageId(), owner.tenantId()).stream()
                .map(subjectId -> votingSubjectRepository.findById(subjectId).orElseThrow(this::notVisible))
                .filter(subject -> owner.tenantId().equals(subject.getTenantId()))
                .toList();
        if (subjects.isEmpty()) {
            throw notVisible();
        }
        return new VisibleAssembly(owner, ballotPackage, session, snapshot, materials, subjects,
                ownerProperties.stream().filter(OwnerPropertyVotingView::isValidForVoting).toList());
    }

    private OwnerAssemblyDisclosure toDisclosure(VisibleAssembly visible) {
        OwnersAssemblyPackage ballotPackage = visible.ballotPackage();
        OwnersAssemblyRuleConfiguration configuration = visible.ruleSnapshot().configuration();
        OwnersAssemblyMaterial publicNotice = requiredMaterial(
                visible.materials(), MaterialType.PUBLIC_NOTICE, ballotPackage.announcementHash());
        OwnersAssemblyMaterial paperBallotTemplate = requiredMaterial(
                visible.materials(), MaterialType.PAPER_BALLOT_TEMPLATE, ballotPackage.ballotTemplateHash());
        List<OwnersAssemblyMaterial> attachments = visible.materials().stream()
                .filter(material -> material.materialType() == MaterialType.PLAN_ATTACHMENT)
                .sorted(Comparator.comparing(OwnersAssemblyMaterial::materialId))
                .toList();
        Instant participatedAt = ownersAssemblyRepository.findOwnerParticipationAt(
                ballotPackage.packageId(), visible.owner().tenantId(), visible.owner().uid()).orElse(null);
        OwnerAssemblyDisclosure.Participation participation = participationOf(visible, participatedAt);
        return new OwnerAssemblyDisclosure(
                ballotPackage.packageId(),
                visible.session().title(),
                stageOf(ballotPackage),
                new OwnerAssemblyDisclosure.PublicNotice(
                        publicNotice.materialId(),
                        publicNotice.originalFileName(),
                        publicNotice.contentType(),
                        publicNotice.fileSize(),
                        publicNotice.contentSha256(),
                        ballotPackage.publicNoticeStartAt()),
                attachments.stream().map(this::toMaterial).toList(),
                toMaterial(paperBallotTemplate),
                visible.subjects().stream()
                        .map(subject -> new OwnerAssemblyDisclosure.Subject(
                                subject.getSubjectId(), subject.getSubjectType(),
                                subject.getTitle(), subject.getContent(), subject.getStatus().name(),
                                votingResultRepository.findBySubjectId(subject.getSubjectId())
                                        .map(this::toResult).orElse(null)))
                        .toList(),
                new OwnerAssemblyDisclosure.Rule(
                        meetingFormOf(visible.session()),
                        configuration.votingChannelPolicy().name(),
                        configuration.planPublicityDays(),
                        configuration.meetingNoticeDays(),
                        configuration.resultAnnouncementDays(),
                        configuration.validDeliveryMethods(),
                        configuration.nonResponsePolicy(),
                        configuration.paperBallotSealRequired(),
                        configuration.countingRules()),
                ballotPackage.publicNoticeStartAt(),
                ballotPackage.publicNoticeEndAt(),
                ballotPackage.voteStartAt(),
                ballotPackage.voteEndAt(),
                votingInstruction(ballotPackage, configuration),
                participation);
    }

    private OwnerAssemblyDisclosure.Participation participationOf(VisibleAssembly visible, Instant participatedAt) {
        int eligiblePropertyCount = visible.eligibleProperties().size();
        int expectedDecisionCount = eligiblePropertyCount * visible.subjects().size();
        if (eligiblePropertyCount == 0) {
            return new OwnerAssemblyDisclosure.Participation(
                    false, false, null, 0, 0, 0, null,
                    new OwnerAssemblyDisclosure.PaperProgress("NOT_APPLICABLE", "NOT_APPLICABLE"), List.of());
        }
        VotingExecutionPackage executionPackage = votingExecutionRepository
                .findPackageBySubjectId(visible.subjects().getFirst().getSubjectId())
                .filter(candidate -> candidate.getBusinessType()
                        == VotingExecutionPackage.BusinessType.OWNERS_ASSEMBLY)
                .filter(candidate -> visible.ballotPackage().packageId().equals(candidate.getBusinessReferenceId()))
                .orElseThrow(this::notVisible);
        Set<Long> eligibleOpids = visible.eligibleProperties().stream()
                .map(OwnerPropertyVotingView::opid)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        int countedDecisionCount = visible.eligibleProperties().stream()
                .map(property -> votingExecutionRepository.findElectorateItem(
                        executionPackage.getPackageId(), visible.owner().tenantId(), property.opid()))
                .flatMap(Optional::stream)
                .mapToInt(electorate -> (int) visible.subjects().stream()
                        .filter(subject -> votingExecutionRepository.findActiveBallot(
                                subject.getSubjectId(), electorate.snapshotItemId(), visible.owner().tenantId())
                                .isPresent())
                        .count())
                .sum();
        String deliveryStatus = aggregateDeliveryStatus(
                paperVotingRepository.listDeliveries(executionPackage.getPackageId(), visible.owner().tenantId())
                        .stream()
                        .filter(delivery -> eligibleOpids.contains(delivery.opid()))
                        .toList());
        String ballotStatus = aggregateBallotStatus(
                paperVotingRepository.listBallots(executionPackage.getPackageId(), visible.owner().tenantId())
                        .stream()
                        .filter(ballot -> eligibleOpids.contains(ballot.opid()))
                        .toList());
        OnlineVotingService.OwnerProgress ownerProgress = onlineVotingService.ownerProgress(
                executionPackage.getPackageId(), visible.owner().tenantId(), eligibleOpids.stream().toList());
        return new OwnerAssemblyDisclosure.Participation(
                true,
                countedDecisionCount > 0,
                participatedAt,
                eligiblePropertyCount,
                expectedDecisionCount,
                countedDecisionCount,
                ownerProgress.packageHash(),
                new OwnerAssemblyDisclosure.PaperProgress(deliveryStatus, ballotStatus),
                ownerProgress.properties().stream()
                        .map(progress -> toPropertyProgress(executionPackage, progress)).toList());
    }

    private String aggregateDeliveryStatus(List<PaperVotingDelivery> deliveries) {
        if (deliveries.stream().anyMatch(delivery -> delivery.status() == PaperVotingDelivery.Status.CONFIRMED)) {
            return "CONFIRMED";
        }
        if (deliveries.stream().anyMatch(delivery -> delivery.status() == PaperVotingDelivery.Status.PENDING_REVIEW)) {
            return "PENDING_REVIEW";
        }
        if (!deliveries.isEmpty()) {
            return "REJECTED";
        }
        return "NOT_REGISTERED";
    }

    private String aggregateBallotStatus(List<PaperBallot> ballots) {
        if (ballots.stream().anyMatch(ballot -> ballot.status() == PaperBallot.Status.COMPLETED)) {
            return "COMPLETED";
        }
        if (ballots.stream().anyMatch(ballot -> ballot.status() == PaperBallot.Status.IN_ENTRY)) {
            return "IN_ENTRY";
        }
        if (ballots.stream().anyMatch(ballot -> ballot.status() == PaperBallot.Status.RECEIVED)) {
            return "RECEIVED";
        }
        if (!ballots.isEmpty()) {
            return "VOIDED";
        }
        return "NOT_RECEIVED";
    }

    private void validateLockedPublicMaterials(OwnersAssemblyPackage ballotPackage,
                                               List<OwnersAssemblyMaterial> materials) {
        OwnersAssemblyMaterial publicNotice = requiredMaterial(
                materials, MaterialType.PUBLIC_NOTICE, ballotPackage.announcementHash());
        OwnersAssemblyMaterial paperBallotTemplate = requiredMaterial(
                materials, MaterialType.PAPER_BALLOT_TEMPLATE, ballotPackage.ballotTemplateHash());
        if (publicNotice == null || paperBallotTemplate == null) {
            throw notVisible();
        }
        List<OwnersAssemblyMaterial> attachments = materials.stream()
                .filter(material -> material.materialType() == MaterialType.PLAN_ATTACHMENT)
                .toList();
        if (attachments.isEmpty() || !attachmentManifestHash(attachments).equals(ballotPackage.attachmentManifestHash())) {
            throw notVisible();
        }
    }

    private OwnersAssemblyMaterial requiredMaterial(List<OwnersAssemblyMaterial> materials,
                                                     MaterialType materialType,
                                                     String expectedHash) {
        return materials.stream()
                .filter(material -> material.materialType() == materialType)
                .filter(material -> expectedHash != null && expectedHash.equals(material.contentSha256()))
                .findFirst()
                .orElseThrow(this::notVisible);
    }

    private String attachmentManifestHash(List<OwnersAssemblyMaterial> attachments) {
        return PayloadHasher.sha256Hex(attachments.stream()
                .map(OwnersAssemblyMaterial::contentSha256)
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow(this::notVisible));
    }

    private OwnerAssemblyDisclosure.Material toMaterial(OwnersAssemblyMaterial material) {
        return new OwnerAssemblyDisclosure.Material(
                material.materialId(), material.originalFileName(), material.contentType(), material.fileSize(),
                material.contentSha256());
    }

    private OwnerAssemblyDisclosure.Stage stageOf(OwnersAssemblyPackage ballotPackage) {
        return switch (ballotPackage.status()) {
            case "PUBLIC_NOTICE" -> OwnerAssemblyDisclosure.Stage.PUBLIC_NOTICE;
            case "VOTING" -> switch (ballotPackage.votingChannelPolicy()) {
                case "PAPER_ONLY" -> OwnerAssemblyDisclosure.Stage.PAPER_VOTING;
                case "ONLINE_ONLY" -> OwnerAssemblyDisclosure.Stage.ONLINE_VOTING;
                case "PAPER_AND_ONLINE" -> OwnerAssemblyDisclosure.Stage.PAPER_AND_ONLINE_VOTING;
                default -> throw notVisible();
            };
            case "SETTLED" -> OwnerAssemblyDisclosure.Stage.RESULT_FORMED;
            default -> throw notVisible();
        };
    }

    private String meetingFormOf(OwnersAssemblySession session) {
        return switch (session.preparationMode()) {
            case "WRITTEN_DECISION" -> OwnersAssemblyRuleConfiguration.MeetingForm.WRITTEN_CONSULTATION.name();
            case "INTERNET_DECISION" -> OwnersAssemblyRuleConfiguration.MeetingForm.INTERNET.name();
            case "ONLINE_AND_OFFLINE" -> OwnersAssemblyRuleConfiguration.MeetingForm.ONLINE_AND_OFFLINE.name();
            default -> throw notVisible();
        };
    }

    private String votingInstruction(
            OwnersAssemblyPackage ballotPackage, OwnersAssemblyRuleConfiguration configuration) {
        String route = switch (ballotPackage.votingChannelPolicy()) {
            case "PAPER_ONLY" -> "本次采用纸质书面征询，请使用本次公告附带的正式表决票。";
            case "ONLINE_ONLY" -> "本次采用互联网表决；确有困难的业主可为本人专有部分申请纸质表决票。";
            case "PAPER_AND_ONLINE" -> "本次按小区有效规则同时开放线上实名和纸质办理，每个专有部分只能形成一份有效票。";
            default -> throw notVisible();
        };
        return route + "未表态按冻结规则“" + nonResponseLabel(configuration.nonResponsePolicy()) + "”处理。";
    }

    private OwnerAssemblyDisclosure.PropertyProgress toPropertyProgress(
            VotingExecutionPackage executionPackage,
            OnlineVotingService.PropertyProgress progress) {
        var electorate = votingExecutionRepository.findElectorateItem(
                        executionPackage.getPackageId(), executionPackage.getTenantId(), progress.opid())
                .orElseThrow(this::notVisible);
        PropertyBindingRepository.Roster roster = propertyBindingRepository.findRosterById(electorate.rosterId());
        if (roster == null
                || !executionPackage.getTenantId().equals(roster.tenantId())
                || !electorate.buildingId().equals(roster.buildingId())
                || !electorate.roomId().equals(roster.roomId())) {
            throw notVisible();
        }
        return new OwnerAssemblyDisclosure.PropertyProgress(
                progress.opid(), electorate.buildingId(), electorate.roomId(),
                roster.buildingName(), roster.unitName(), roster.roomName(),
                progress.acknowledged(), progress.receipt() != null,
                progress.receipt() == null ? null : progress.receipt().submissionId(),
                progress.receipt() == null ? null : progress.receipt().confirmationHash(),
                progress.receipt() == null ? null : progress.receipt().submittedAt(),
                progress.paperAssistance() == null ? null : progress.paperAssistance().requestId(),
                progress.paperAssistance() == null ? "NOT_REQUESTED" : progress.paperAssistance().status().name(),
                progress.participated(),
                progress.participationChannel() == null ? null : progress.participationChannel().name(),
                progress.paperDeliveryStatus(),
                progress.paperBallotStatus());
    }

    private OwnerAssemblyDisclosure.Result toResult(VotingResultRepository.Snapshot snapshot) {
        VotingDecisionResultProjector.View view = votingDecisionResultProjector.project(snapshot);
        return new OwnerAssemblyDisclosure.Result(
                view.quorumSatisfied(), view.passed(), view.totalArea(), view.totalOwnerCount(),
                view.participatingArea(), view.participatingOwnerCount(),
                view.supportArea(), view.supportOwnerCount(),
                view.againstArea(), view.againstOwnerCount(),
                view.abstainArea(), view.abstainOwnerCount());
    }

    private String nonResponseLabel(OwnersAssemblyRuleConfiguration.NonResponsePolicy policy) {
        return switch (policy) {
            case NOT_PARTICIPATED -> "未表态不计入参与";
            case FOLLOW_MAJORITY -> "未表态跟随多数";
            case ABSTAIN -> "未表态按弃权";
        };
    }

    private UserContext requireOwner() {
        UserContext owner = userContextHolder.current();
        if (owner == null || !owner.isCUser() || owner.accountId() == null
                || owner.uid() == null || owner.tenantId() == null) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到当前小区业主身份");
        }
        return owner;
    }

    private OwnersAssemblyApplicationException notVisible() {
        return new OwnersAssemblyApplicationException(NOT_FOUND, "业主大会不存在、尚未公示或当前业主无权查看");
    }

    private record VisibleAssembly(
            UserContext owner,
            OwnersAssemblyPackage ballotPackage,
            OwnersAssemblySession session,
            OwnersAssemblyRuleSnapshot ruleSnapshot,
            List<OwnersAssemblyMaterial> materials,
            List<VotingSubject> subjects,
            List<OwnerPropertyVotingView> eligibleProperties
    ) {
    }
}
