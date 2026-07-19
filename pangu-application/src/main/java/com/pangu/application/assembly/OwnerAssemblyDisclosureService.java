// 关联业务：向当前业主披露已发布业主大会的公告、方案、纸质选票规则和本人参与状态。
package com.pangu.application.assembly;

import com.pangu.application.support.PayloadHasher;
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
import com.pangu.domain.repository.VotingExecutionRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
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
 * <p>正式表决仍由纸质选票闭环办理；本服务不提供线上投票写入口，也不以“未回复”等状态推定
 * 业主选择。所有材料均以正式表决包锁定的清单为准，不能回退到会前草稿材料全集。
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
    private final OwnerPropertyVotingRepository ownerPropertyVotingRepository;
    private final VotingExecutionRepository votingExecutionRepository;
    private final PaperVotingRepository paperVotingRepository;
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
                stageOf(ballotPackage.status()),
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
                                subject.getTitle(), subject.getContent(), subject.getStatus().name()))
                        .toList(),
                new OwnerAssemblyDisclosure.Rule(
                        OwnersAssemblyRuleConfiguration.MeetingForm.WRITTEN_CONSULTATION.name(),
                        configuration.votingChannelPolicy().name(),
                        configuration.planPublicityDays(),
                        configuration.validDeliveryMethods(),
                        configuration.nonResponsePolicy(),
                        configuration.paperBallotSealRequired(),
                        configuration.countingRules()),
                ballotPackage.publicNoticeStartAt(),
                ballotPackage.publicNoticeEndAt(),
                ballotPackage.voteStartAt(),
                ballotPackage.voteEndAt(),
                paperVotingInstruction(configuration),
                participation);
    }

    private OwnerAssemblyDisclosure.Participation participationOf(VisibleAssembly visible, Instant participatedAt) {
        int eligiblePropertyCount = visible.eligibleProperties().size();
        int expectedDecisionCount = eligiblePropertyCount * visible.subjects().size();
        if (eligiblePropertyCount == 0) {
            return new OwnerAssemblyDisclosure.Participation(
                    false, false, null, 0, 0, 0,
                    new OwnerAssemblyDisclosure.PaperProgress("NOT_APPLICABLE", "NOT_APPLICABLE"));
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
        return new OwnerAssemblyDisclosure.Participation(
                true,
                countedDecisionCount > 0,
                participatedAt,
                eligiblePropertyCount,
                expectedDecisionCount,
                countedDecisionCount,
                new OwnerAssemblyDisclosure.PaperProgress(deliveryStatus, ballotStatus));
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

    private OwnerAssemblyDisclosure.Stage stageOf(String packageStatus) {
        return switch (packageStatus) {
            case "PUBLIC_NOTICE" -> OwnerAssemblyDisclosure.Stage.PUBLIC_NOTICE;
            case "VOTING" -> OwnerAssemblyDisclosure.Stage.PAPER_VOTING;
            case "SETTLED" -> OwnerAssemblyDisclosure.Stage.RESULT_FORMED;
            default -> throw notVisible();
        };
    }

    private String paperVotingInstruction(OwnersAssemblyRuleConfiguration configuration) {
        return "本次仅接受纸质选票，请使用本次公告附带的盖章选票模板；未表态按冻结规则“"
                + nonResponseLabel(configuration.nonResponsePolicy()) + "”处理。";
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
