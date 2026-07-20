// 关联业务：统一编排正式表决包创建、名册冻结、送达和跨渠道有效票写入。
package com.pangu.application.voting;

import com.pangu.application.support.PayloadHasher;
import com.pangu.application.voting.command.SettleSubjectCommand;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.CountedVote;
import com.pangu.domain.model.voting.NonResponseVoteResolver;
import com.pangu.domain.model.voting.VotingBallotRecord;
import com.pangu.domain.model.voting.VotingDeliveryRecord;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingExecutionTrace;
import com.pangu.domain.model.voting.VotingNonResponseDerivation;
import com.pangu.domain.model.voting.VotingNonResponseSettlement;
import com.pangu.domain.model.voting.VotingSettlementPolicy;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.voting.VotingSubjectActions;
import com.pangu.domain.policy.voting.DuplicateBallotResolutionPolicy;
import com.pangu.domain.repository.VoteItemRepository;
import com.pangu.domain.repository.VotingExecutionRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 正式表决执行内核。
 *
 * <p>上层业务只提供已经确认的方案和规则快照；本服务负责把范围、时间、名册、送达和
 * 有效票固化为同一条审计链。任何正式事项都不得绕过本服务直接依据实时产权数据收票。
 */
@Service
@RequiredArgsConstructor
public class VotingExecutionService {

    private static final NonResponseVoteResolver NON_RESPONSE_RESOLVER = new NonResponseVoteResolver();
    private static final DuplicateBallotResolutionPolicy DUPLICATE_BALLOT_POLICY =
            new DuplicateBallotResolutionPolicy();

    private final VotingExecutionRepository executionRepository;
    private final VotingSubjectRepository subjectRepository;
    private final VoteItemRepository voteItemRepository;
    private final VotingApplicationService votingApplicationService;

    @Transactional
    public VotingExecutionPackage create(CreatePackageCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        VotingExecutionPackage ballotPackage = VotingExecutionPackage.draft(
                command.tenantId(), command.businessType(), command.businessReferenceId(),
                command.proposalSnapshotType(), command.proposalSnapshotId(), command.proposalSnapshotHash(),
                command.ruleSnapshotType(), command.ruleSnapshotId(), command.ruleSnapshotHash(),
                command.scope(), command.scopeReferenceId(), command.collectionMode(),
                command.duplicateBallotPolicy(),
                command.voteStartAt(), command.voteEndAt(), command.createdByUserId());
        VotingExecutionPackage inserted = executionRepository.insertPackage(ballotPackage);
        executionRepository.insertAudit(
                inserted.getPackageId(), inserted.getTenantId(), "PACKAGE_CREATED", null,
                inserted.getStatus().name(), command.createdByUserId(), null, Instant.now());
        return inserted;
    }

    @Transactional
    public void attachSubject(Long packageId, Long tenantId, Long subjectId, Long actorUserId) {
        VotingExecutionPackage ballotPackage = requirePackageForUpdate(packageId, tenantId);
        requireStatus(ballotPackage, VotingExecutionPackage.Status.DRAFT);
        VotingSubject subject = requireSubject(subjectId, tenantId);
        if (subject.getStatus() != SubjectStatus.DRAFT) {
            throw new VotingExecutionException("只有草稿事项可以加入正式表决包");
        }
        if (subject.getSubjectType() == SubjectType.ELECTION) {
            throw new VotingExecutionException("选举事项尚未接入统一表决人名册");
        }
        if (subject.getScope() != ballotPackage.getScope()
                || !Objects.equals(subject.getScopeReferenceId(), ballotPackage.getScopeReferenceId())) {
            throw new VotingExecutionException("表决事项的决定范围与表决包不一致");
        }
        if (!Objects.equals(subject.getVoteStartAt(), ballotPackage.getVoteStartAt())
                || !Objects.equals(subject.getVoteEndAt(), ballotPackage.getVoteEndAt())) {
            throw new VotingExecutionException("表决事项的投票时间与表决包不一致");
        }
        try {
            executionRepository.attachSubject(packageId, tenantId, subjectId);
        } catch (DataIntegrityViolationException ex) {
            throw new VotingExecutionException("该表决事项已属于其他正式表决包", ex);
        }
        executionRepository.insertAudit(
                packageId, tenantId, "SUBJECT_ATTACHED", ballotPackage.getStatus().name(),
                ballotPackage.getStatus().name(), actorUserId,
                "{\"subjectId\":" + subjectId + "}", Instant.now());
    }

    @Transactional
    public VotingExecutionPackage freeze(Long packageId, Long tenantId, Long actorUserId, Instant now) {
        VotingExecutionPackage ballotPackage = requirePackageForUpdate(packageId, tenantId);
        requireStatus(ballotPackage, VotingExecutionPackage.Status.DRAFT);
        requirePositive(actorUserId, "actorUserId");
        Objects.requireNonNull(now, "now 不能为空");
        List<Long> subjectIds = executionRepository.listSubjectIds(packageId, tenantId);
        if (subjectIds.isEmpty()) {
            throw new VotingExecutionException("正式表决包至少需要一个表决事项");
        }
        List<VotingElectorateSnapshot.Candidate> candidates = executionRepository.listElectorateCandidates(
                tenantId, ballotPackage.getScope(), ballotPackage.getScopeReferenceId());
        return freezeWithCandidates(ballotPackage, subjectIds, candidates, actorUserId, now);
    }

    /**
     * 以业务适配器已经核对的精确房屋候选名册冻结表决包。
     *
     * <p>该入口只接受 {@link VotingScope#REPAIR_ALLOCATION}，避免普通业务绕过社区/楼栋范围解析；
     * 候选行仍由本服务统一校验唯一代表、生成摘要、落分母并发布事项。
     */
    @Transactional
    public VotingExecutionPackage freezeExactElectorate(
            Long packageId,
            Long tenantId,
            List<VotingElectorateSnapshot.Candidate> candidates,
            Long actorUserId,
            Instant now) {
        VotingExecutionPackage ballotPackage = requirePackageForUpdate(packageId, tenantId);
        requireStatus(ballotPackage, VotingExecutionPackage.Status.DRAFT);
        if (ballotPackage.getBusinessType() != VotingExecutionPackage.BusinessType.REPAIR_PROJECT
                || ballotPackage.getScope() != VotingScope.REPAIR_ALLOCATION) {
            throw new VotingExecutionException("精确房屋名册只适用于维修授权提案");
        }
        requirePositive(actorUserId, "actorUserId");
        Objects.requireNonNull(now, "now 不能为空");
        List<Long> subjectIds = executionRepository.listSubjectIds(packageId, tenantId);
        if (subjectIds.isEmpty()) {
            throw new VotingExecutionException("正式表决包至少需要一个表决事项");
        }
        return freezeWithCandidates(ballotPackage, subjectIds, candidates, actorUserId, now);
    }

    /** 读取精确房屋的有效产权候选行，供维修适配器与冻结分摊快照逐房屋核对。 */
    public List<VotingElectorateSnapshot.Candidate> listElectorateCandidatesByRoomIds(
            Long tenantId, List<Long> roomIds) {
        return executionRepository.listElectorateCandidatesByRoomIds(tenantId, roomIds);
    }

    private VotingExecutionPackage freezeWithCandidates(
            VotingExecutionPackage ballotPackage,
            List<Long> subjectIds,
            List<VotingElectorateSnapshot.Candidate> candidates,
            Long actorUserId,
            Instant now) {
        Long packageId = ballotPackage.getPackageId();
        Long tenantId = ballotPackage.getTenantId();
        VotingElectorateSnapshot snapshot = buildElectorateSnapshot(ballotPackage, candidates, now);
        VotingElectorateSnapshot insertedSnapshot = executionRepository.insertElectorateSnapshot(snapshot);
        for (Long subjectId : subjectIds) {
            VotingSubject subject = requireSubject(subjectId, tenantId);
            if (subject.getStatus() != SubjectStatus.DRAFT) {
                throw new VotingExecutionException("锁定时表决事项必须仍为草稿 subjectId=" + subjectId);
            }
            executionRepository.insertSubjectDenominatorSnapshot(
                    subjectId, ballotPackage.getScope(), ballotPackage.getScopeReferenceId(), insertedSnapshot);
        }
        String packageHash = buildPackageHash(ballotPackage, insertedSnapshot, subjectIds);
        ballotPackage.freeze(insertedSnapshot.snapshotId(), packageHash, actorUserId, now);
        updatePackage(ballotPackage);
        for (Long subjectId : subjectIds) {
            publishSubject(requireSubject(subjectId, tenantId));
        }
        executionRepository.insertAudit(
                packageId, tenantId, "PACKAGE_FROZEN", VotingExecutionPackage.Status.DRAFT.name(),
                VotingExecutionPackage.Status.FROZEN.name(), actorUserId,
                "{\"electorateSnapshotId\":" + insertedSnapshot.snapshotId()
                        + ",\"electorateHash\":\"" + insertedSnapshot.aggregateHash() + "\"}", now);
        return requirePackage(packageId, tenantId);
    }

    @Transactional
    public VotingExecutionPackage open(Long packageId, Long tenantId, Long actorUserId, Instant now) {
        VotingExecutionPackage ballotPackage = requirePackageForUpdate(packageId, tenantId);
        VotingExecutionPackage.Status fromStatus = ballotPackage.getStatus();
        try {
            ballotPackage.open(now, actorUserId);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new VotingExecutionException(ex.getMessage(), ex);
        }
        updatePackage(ballotPackage);
        for (Long subjectId : executionRepository.listSubjectIds(packageId, tenantId)) {
            openSubject(requireSubject(subjectId, tenantId), now);
        }
        executionRepository.insertAudit(
                packageId, tenantId, "VOTING_OPENED", fromStatus.name(), ballotPackage.getStatus().name(),
                actorUserId, null, now);
        return requirePackage(packageId, tenantId);
    }

    @Transactional
    public VotingDeliveryRecord recordDelivery(RecordDeliveryCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        VotingExecutionPackage ballotPackage = requirePackage(command.packageId(), command.tenantId());
        if (ballotPackage.getStatus() != VotingExecutionPackage.Status.FROZEN
                && ballotPackage.getStatus() != VotingExecutionPackage.Status.VOTING) {
            throw new VotingExecutionException("表决材料只能在表决包锁定后送达");
        }
        VoteChannel channel = normalizeDeliveryChannel(command.deliveryChannel());
        requireChannelAllowed(ballotPackage, channel);
        Instant deliveredAt = requireInstant(command.deliveredAt(), "deliveredAt");
        if (!deliveredAt.isBefore(ballotPackage.getVoteEndAt())) {
            throw new VotingExecutionException(
                    Reason.INVALID_STATUS, "表决截止后不能补记送达并据此认定未反馈票");
        }
        VotingElectorateSnapshot.Item item = requireElectorateItem(
                command.packageId(), command.tenantId(), command.opid());
        executionRepository.lockElectorateItem(
                command.packageId(), command.tenantId(), item.snapshotItemId());
        VotingDeliveryRecord delivery = new VotingDeliveryRecord(
                null, ballotPackage.getPackageId(), item.snapshotItemId(), ballotPackage.getTenantId(),
                item.representativeOpid(), item.representativeUid(), channel,
                requireText(command.deliveryMethod(), "deliveryMethod"),
                requireText(command.evidenceHash(), "evidenceHash"),
                command.deliveredByUserId(), deliveredAt);
        try {
            VotingDeliveryRecord inserted = executionRepository.insertDelivery(delivery);
            executionRepository.insertAudit(
                    ballotPackage.getPackageId(), ballotPackage.getTenantId(), "DELIVERY_RECORDED",
                    ballotPackage.getStatus().name(), ballotPackage.getStatus().name(),
                    command.deliveredByUserId(),
                    "{\"opid\":" + item.representativeOpid()
                            + ",\"channel\":\"" + channel.name() + "\"}", command.deliveredAt());
            return inserted;
        } catch (DataIntegrityViolationException ex) {
            throw new VotingExecutionException("该专有部分在此渠道已有送达记录", ex);
        }
    }

    @Transactional
    public long cast(CastBallotCommand command) {
        return castRecordInternal(command).voteId();
    }

    /**
     * 接收一张正式选票并返回写入后的通用票据记录，供需要回显审计编号的业务适配层使用。
     */
    @Transactional
    public VotingBallotRecord castRecord(CastBallotCommand command) {
        return castRecordInternal(command);
    }

    private VotingBallotRecord castRecordInternal(CastBallotCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        VotingExecutionPackage ballotPackage = requirePackage(command.packageId(), command.tenantId());
        Instant castAt = requireInstant(command.castAt(), "castAt");
        if (ballotPackage.getStatus() != VotingExecutionPackage.Status.VOTING
                || castAt.isBefore(ballotPackage.getVoteStartAt())
                || !castAt.isBefore(ballotPackage.getVoteEndAt())) {
            throw new VotingExecutionException(Reason.INVALID_STATUS, "当前不在正式表决收票时间内");
        }
        requireChannelAllowed(ballotPackage, command.voteChannel());
        if (!executionRepository.listSubjectIds(command.packageId(), command.tenantId())
                .contains(command.subjectId())) {
            throw new VotingExecutionException("表决事项不属于当前正式表决包");
        }
        VotingSubject subject = requireSubject(command.subjectId(), command.tenantId());
        if (subject.getStatus() != SubjectStatus.VOTING) {
            throw new VotingExecutionException("表决事项尚未开始收票");
        }
        VotingElectorateSnapshot.Item item = requireElectorateItem(
                command.packageId(), command.tenantId(), command.opid());
        if (command.uid() != null && !command.uid().equals(item.representativeUid())) {
            throw new VotingExecutionException("提交身份与冻结表决人名册不一致");
        }
        VoteChannel deliveryChannel = normalizeDeliveryChannel(command.voteChannel());
        if (!executionRepository.deliveryExists(
                command.packageId(), command.tenantId(), item.snapshotItemId(), deliveryChannel)) {
            throw new VotingExecutionException(Reason.DELIVERY_REQUIRED, "尚无与本次收票渠道对应的有效送达记录");
        }
        if (command.voteChannel().paperLike()) {
            requireText(command.ballotFileHash(), "ballotFileHash");
        }
        VotingBallotRecord existing = executionRepository.findActiveBallot(
                command.subjectId(), item.snapshotItemId(), command.tenantId()).orElse(null);
        DuplicateBallotResolutionPolicy.Resolution resolution = null;
        if (existing != null) {
            resolution = DUPLICATE_BALLOT_POLICY.resolve(
                    ballotPackage.getDuplicateBallotPolicy(),
                    existing.voteChannel(), command.voteChannel());
            if (resolution.decision()
                    == DuplicateBallotResolutionPolicy.Decision.KEEP_EXISTING) {
                throw new VotingExecutionException(
                        Reason.DUPLICATE_BALLOT,
                        "本专有部分已有有效票；" + resolution.reason() + "，本次材料已留存但不重复计票");
            }
            if (executionRepository.invalidateBallot(
                    existing.ballotId(), resolution.reason(), castAt) != 1
                    || voteItemRepository.invalidateVote(existing.voteId(), resolution.reason()) != 1) {
                throw new VotingExecutionException(
                        Reason.CONCURRENT_MODIFICATION, "有效票在重复票裁决期间发生变化，请重试");
            }
        }
        VoteItem vote = VoteItem.builder()
                .opid(item.representativeOpid())
                .uid(item.representativeUid())
                .propertyArea(item.certifiedArea())
                .choice(Objects.requireNonNull(command.choice(), "choice 不能为空"))
                .voteChannel(command.voteChannel())
                .build();
        long voteId;
        VotingBallotRecord insertedBallot;
        try {
            voteId = voteItemRepository.insert(command.subjectId(), vote, command.signatureHash());
            insertedBallot = executionRepository.insertBallot(new VotingBallotRecord(
                    null, ballotPackage.getPackageId(), command.subjectId(), voteId,
                    item.snapshotItemId(), ballotPackage.getTenantId(), item.representativeOpid(),
                    item.representativeUid(), command.voteChannel(), ballotPackage.getPackageHash(),
                    trim(command.ballotFileHash()), trim(command.signatureHash()),
                    command.recordedByUserId(), castAt,
                    existing == null ? null : existing.ballotId(),
                    existing == null ? null : ballotPackage.getDuplicateBallotPolicy(),
                    resolution == null ? null : resolution.reason()));
        } catch (VoteItemRepository.DuplicateVoteException | DataIntegrityViolationException ex) {
            throw new VotingExecutionException(
                    Reason.DUPLICATE_BALLOT, "该专有部分对本事项已有有效票，不能跨渠道重复提交", ex);
        }
        executionRepository.insertAudit(
                ballotPackage.getPackageId(), ballotPackage.getTenantId(), "BALLOT_ACCEPTED",
                ballotPackage.getStatus().name(), ballotPackage.getStatus().name(),
                command.recordedByUserId(),
                "{\"subjectId\":" + command.subjectId() + ",\"opid\":" + item.representativeOpid()
                        + ",\"channel\":\"" + command.voteChannel().name() + "\",\"voteId\":" + voteId + "}",
                castAt);
        if (existing != null) {
            executionRepository.insertAudit(
                    ballotPackage.getPackageId(), ballotPackage.getTenantId(), "BALLOT_REPLACED",
                    ballotPackage.getStatus().name(), ballotPackage.getStatus().name(),
                    command.recordedByUserId(),
                    "{\"subjectId\":" + command.subjectId()
                            + ",\"opid\":" + item.representativeOpid()
                            + ",\"previousBallotId\":" + existing.ballotId()
                            + ",\"currentBallotId\":" + insertedBallot.ballotId()
                            + ",\"policy\":\"" + ballotPackage.getDuplicateBallotPolicy().name() + "\"}",
                    castAt);
        }
        return insertedBallot;
    }

    @Transactional
    public VotingExecutionPackage closeAndSettle(Long packageId,
                                                 Long tenantId,
                                                 Long actorUserId,
                                                 Instant now) {
        return closeAndSettle(packageId, tenantId, actorUserId, now, subject -> null);
    }

    /**
     * 截止收票并以冻结名册和上层提供的实际生效规则结算全部事项。
     */
    @Transactional
    public VotingExecutionPackage closeAndSettle(Long packageId,
                                                 Long tenantId,
                                                 Long actorUserId,
                                                 Instant now,
                                                 SettlementPolicyProvider settlementPolicyProvider) {
        VotingExecutionPackage ballotPackage = requirePackageForUpdate(packageId, tenantId);
        VotingExecutionPackage.Status fromStatus = ballotPackage.getStatus();
        try {
            ballotPackage.close(now);
        } catch (IllegalStateException ex) {
            throw new VotingExecutionException(ex.getMessage(), ex);
        }
        updatePackage(ballotPackage);
        executionRepository.insertAudit(
                packageId, tenantId, "VOTING_CLOSED", fromStatus.name(),
                VotingExecutionPackage.Status.CLOSED.name(), actorUserId, null, now);

        VotingElectorateSnapshot snapshot = executionRepository.findElectorateSnapshot(
                        ballotPackage.getElectorateSnapshotId(), tenantId)
                .orElseThrow(() -> new VotingExecutionException("正式表决包关联的冻结名册不存在"));
        String recomputedElectorateHash = PayloadHasher.sha256Hex(
                snapshot.items().stream().map(VotingElectorateSnapshot.Item::rowHash)
                        .collect(java.util.stream.Collectors.joining("|")));
        if (!snapshot.aggregateHash().equals(recomputedElectorateHash)) {
            throw new VotingExecutionException("冻结表决人名册摘要无效");
        }
        VotingExecutionTrace trace = new VotingExecutionTrace(
                ballotPackage.getPackageId(), snapshot.snapshotId(),
                ballotPackage.getProposalSnapshotHash(), ballotPackage.getRuleSnapshotHash(),
                ballotPackage.getPackageHash());
        for (Long subjectId : executionRepository.listSubjectIds(packageId, tenantId)) {
            VotingSubject subject = requireSubject(subjectId, tenantId);
            VotingSettlementPolicy policy = settlementPolicyProvider == null
                    ? null : settlementPolicyProvider.forSubject(subject);
            VotingNonResponseSettlement nonResponseSettlement = policy == null
                    ? null : prepareNonResponseSettlement(
                            ballotPackage, subject, snapshot, policy, actorUserId, now);
            votingApplicationService.settle(
                    new SettleSubjectCommand(subjectId, ballotPackage.getBusinessType().name()),
                    policy,
                    trace,
                    nonResponseSettlement);
        }

        VotingExecutionPackage closed = requirePackageForUpdate(packageId, tenantId);
        closed.settle();
        updatePackage(closed);
        executionRepository.insertAudit(
                packageId, tenantId, "PACKAGE_SETTLED", VotingExecutionPackage.Status.CLOSED.name(),
                VotingExecutionPackage.Status.SETTLED.name(), actorUserId, null, now);
        return requirePackage(packageId, tenantId);
    }

    /**
     * 以冻结名册、有效送达和截止时有效票生成逐事项未反馈认定记录。
     *
     * <p>该方法与关闭、结算共享事务；多数意见不确定或证据链不一致时，表决包状态、认定记录和
     * 结果全部回滚，不能留下“已经截止但没有结果”的半成品。
     */
    private VotingNonResponseSettlement prepareNonResponseSettlement(
            VotingExecutionPackage ballotPackage,
            VotingSubject subject,
            VotingElectorateSnapshot snapshot,
            VotingSettlementPolicy policy,
            Long actorUserId,
            Instant settledAt) {
        policy.requireExecutable();
        List<VoteItem> actualVoteItems = voteItemRepository.findValidVotes(subject.getSubjectId());
        List<VotingBallotRecord> activeBallots = executionRepository.listActiveBallots(
                subject.getSubjectId(), ballotPackage.getTenantId());
        if (actualVoteItems.size() != activeBallots.size()) {
            throw new VotingExecutionException(
                    "有效票与正式票据台账数量不一致，不能形成可审计计票结果");
        }

        Set<Long> votedElectorateItems = activeBallots.stream()
                .map(VotingBallotRecord::electorateItemId)
                .collect(Collectors.toSet());
        Map<Long, List<VotingDeliveryRecord>> deliveriesByItem = executionRepository
                .listDeliveries(ballotPackage.getPackageId(), ballotPackage.getTenantId()).stream()
                // 只有冻结规则承认且在截止时间前完成的送达，才能触发未反馈认定。
                .filter(delivery -> policy.validDeliveryMethods().contains(delivery.deliveryMethod()))
                .filter(delivery -> delivery.deliveredAt().isBefore(ballotPackage.getVoteEndAt()))
                .collect(Collectors.groupingBy(
                        VotingDeliveryRecord::electorateItemId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<NonResponseVoteResolver.EligibleNonResponse> eligible = snapshot.items().stream()
                .filter(item -> !votedElectorateItems.contains(item.snapshotItemId()))
                .filter(item -> deliveriesByItem.containsKey(item.snapshotItemId()))
                .map(item -> new NonResponseVoteResolver.EligibleNonResponse(
                        item.snapshotItemId(), item.representativeOpid(), item.representativeUid(),
                        item.certifiedArea(), deliveryEvidenceHash(deliveriesByItem.get(item.snapshotItemId()))))
                .toList();

        List<CountedVote> actualVotes = actualVoteItems.stream().map(CountedVote::actual).toList();
        NonResponseVoteResolver.Resolution resolution;
        try {
            resolution = NON_RESPONSE_RESOLVER.resolve(policy.nonResponsePolicy(), actualVotes, eligible);
        } catch (NonResponseVoteResolver.IndeterminateMajorityException ex) {
            throw new VotingExecutionException(
                    Reason.INVALID_COMMAND,
                    ex.getMessage() + "；请核对实际票和本小区议事规则后再办理",
                    ex);
        }

        VoteChoice derivedChoice = resolution.deemedVotes().isEmpty()
                ? null : resolution.deemedVotes().getFirst().choice();
        List<VotingNonResponseDerivation> derivations = new ArrayList<>();
        if (derivedChoice != null) {
            for (NonResponseVoteResolver.EligibleNonResponse item : eligible) {
                String reasonCode = policy.nonResponsePolicy().name()
                        + "_EFFECTIVE_DELIVERY_NO_VALID_BALLOT_AT_DEADLINE";
                String rowHash = PayloadHasher.sha256Hex(String.join("|",
                        ballotPackage.getPackageId().toString(), subject.getSubjectId().toString(),
                        item.electorateItemId().toString(), ballotPackage.getTenantId().toString(),
                        item.opid().toString(), item.uid().toString(), canonical(item.propertyArea()),
                        policy.nonResponsePolicy().name(), derivedChoice.name(), item.sourceReference(),
                        ballotPackage.getRuleSnapshotHash(), reasonCode, settledAt.toString()));
                derivations.add(new VotingNonResponseDerivation(
                        null, ballotPackage.getPackageId(), subject.getSubjectId(), item.electorateItemId(),
                        ballotPackage.getTenantId(), item.opid(), item.uid(), item.propertyArea(),
                        policy.nonResponsePolicy(), derivedChoice, item.sourceReference(),
                        ballotPackage.getRuleSnapshotHash(), reasonCode, rowHash, settledAt));
            }
            executionRepository.insertNonResponseDerivations(derivations);
        }

        String aggregateHash = PayloadHasher.sha256Hex(derivations.stream()
                .map(VotingNonResponseDerivation::rowHash)
                .sorted()
                .collect(Collectors.joining("|")));
        long eligibleOwnerCount = eligible.stream()
                .map(NonResponseVoteResolver.EligibleNonResponse::uid)
                .distinct()
                .count();
        BigDecimal eligibleArea = eligible.stream()
                .map(NonResponseVoteResolver.EligibleNonResponse::propertyArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        executionRepository.insertAudit(
                ballotPackage.getPackageId(), ballotPackage.getTenantId(), "NON_RESPONSE_DERIVED",
                VotingExecutionPackage.Status.CLOSED.name(), VotingExecutionPackage.Status.CLOSED.name(),
                actorUserId,
                "{\"subjectId\":" + subject.getSubjectId()
                        + ",\"policy\":\"" + policy.nonResponsePolicy().name()
                        + "\",\"eligibleOwnerCount\":" + eligibleOwnerCount
                        + ",\"derivationCount\":" + derivations.size()
                        + ",\"aggregateHash\":\"" + aggregateHash + "\"}",
                settledAt);
        return new VotingNonResponseSettlement(
                policy.nonResponsePolicy(), eligibleOwnerCount, eligibleArea,
                resolution.majorityChoice(), aggregateHash, derivations);
    }

    private String deliveryEvidenceHash(List<VotingDeliveryRecord> deliveries) {
        String canonicalEvidence = deliveries.stream()
                .sorted(Comparator.comparing(VotingDeliveryRecord::deliveryId))
                .map(delivery -> String.join("|",
                        delivery.deliveryId().toString(),
                        delivery.deliveryChannel().name(),
                        delivery.deliveryMethod(),
                        delivery.evidenceHash(),
                        delivery.deliveredAt().toString()))
                .collect(Collectors.joining("||"));
        return PayloadHasher.sha256Hex(canonicalEvidence);
    }

    public java.util.Optional<VotingExecutionPackage> findPackageBySubjectId(Long subjectId) {
        return executionRepository.findPackageBySubjectId(subjectId);
    }

    /** 供业务适配器把本人申请或纸票记录还原到冻结时的专有部分，不读取当前可变名册。 */
    public java.util.Optional<VotingElectorateSnapshot.Item> findElectorateItem(
            Long packageId, Long tenantId, Long opid) {
        return executionRepository.findElectorateItem(packageId, tenantId, opid);
    }

    /** 管理办理台读取本次冻结名册，禁止回读当前可变房屋关系替代历史范围。 */
    public java.util.Optional<VotingElectorateSnapshot> findElectorateSnapshot(
            Long packageId, Long tenantId) {
        return executionRepository.findPackage(packageId, tenantId)
                .flatMap(ballotPackage -> executionRepository.findElectorateSnapshot(
                        ballotPackage.getElectorateSnapshotId(), tenantId));
    }

    private VotingElectorateSnapshot buildElectorateSnapshot(
            VotingExecutionPackage ballotPackage,
            List<VotingElectorateSnapshot.Candidate> candidates,
            Instant frozenAt) {
        if (candidates == null || candidates.isEmpty()) {
            throw new VotingExecutionException("决定范围内没有可冻结的房屋名册");
        }
        Map<Long, List<VotingElectorateSnapshot.Candidate>> byRoom = new LinkedHashMap<>();
        for (VotingElectorateSnapshot.Candidate candidate : candidates) {
            byRoom.computeIfAbsent(candidate.roomId(), ignored -> new ArrayList<>()).add(candidate);
        }
        List<VotingElectorateSnapshot.Item> items = new ArrayList<>();
        LinkedHashSet<Long> representativeUids = new LinkedHashSet<>();
        BigDecimal totalArea = BigDecimal.ZERO;
        for (Map.Entry<Long, List<VotingElectorateSnapshot.Candidate>> entry : byRoom.entrySet()) {
            List<VotingElectorateSnapshot.Candidate> roomRows = entry.getValue();
            VotingElectorateSnapshot.Candidate base = roomRows.getFirst();
            if (base.certifiedArea() == null || base.certifiedArea().signum() <= 0) {
                throw new VotingExecutionException("房屋名册缺少有效法定面积 roomId=" + base.roomId());
            }
            List<VotingElectorateSnapshot.Candidate> owners = roomRows.stream()
                    .filter(row -> row.opid() != null && row.uid() != null)
                    .toList();
            if (owners.isEmpty()) {
                throw new VotingExecutionException("房屋尚无已核验产权人，不能冻结表决名册 roomId=" + base.roomId());
            }
            VotingElectorateSnapshot.Candidate representative;
            if (owners.size() == 1) {
                representative = owners.getFirst();
            } else {
                List<VotingElectorateSnapshot.Candidate> delegates = owners.stream()
                        .filter(VotingElectorateSnapshot.Candidate::votingDelegate)
                        .toList();
                if (delegates.size() != 1) {
                    throw new VotingExecutionException(
                            "共有产权房屋尚未唯一确认表决代表 roomId=" + base.roomId());
                }
                representative = delegates.getFirst();
            }
            List<Long> coOwnerUids = owners.stream()
                    .map(VotingElectorateSnapshot.Candidate::uid)
                    .distinct()
                    .sorted()
                    .toList();
            String rowHash = PayloadHasher.sha256Hex(String.join("|",
                    base.rosterId().toString(), base.roomId().toString(), base.buildingId().toString(),
                    canonical(base.certifiedArea()), representative.opid().toString(),
                    representative.uid().toString(), coOwnerUids.toString()));
            items.add(new VotingElectorateSnapshot.Item(
                    null, null, base.rosterId(), base.roomId(), base.buildingId(), base.certifiedArea(),
                    representative.opid(), representative.uid(), coOwnerUids, rowHash));
            totalArea = totalArea.add(base.certifiedArea());
            representativeUids.add(representative.uid());
        }
        items.sort(Comparator.comparing(VotingElectorateSnapshot.Item::buildingId)
                .thenComparing(VotingElectorateSnapshot.Item::roomId));
        String aggregateHash = PayloadHasher.sha256Hex(
                items.stream().map(VotingElectorateSnapshot.Item::rowHash)
                        .collect(java.util.stream.Collectors.joining("|")));
        return new VotingElectorateSnapshot(
                null, ballotPackage.getPackageId(), ballotPackage.getTenantId(),
                ballotPackage.getScope(), ballotPackage.getScopeReferenceId(), totalArea,
                representativeUids.size(), items.size(), aggregateHash, frozenAt, items);
    }

    private String buildPackageHash(VotingExecutionPackage ballotPackage,
                                    VotingElectorateSnapshot snapshot,
                                    List<Long> subjectIds) {
        return PayloadHasher.sha256Hex(String.join("|",
                ballotPackage.getPackageId().toString(),
                ballotPackage.getBusinessType().name(),
                ballotPackage.getBusinessReferenceId().toString(),
                ballotPackage.getProposalSnapshotType(),
                ballotPackage.getProposalSnapshotId().toString(),
                ballotPackage.getProposalSnapshotHash(),
                ballotPackage.getRuleSnapshotType(),
                ballotPackage.getRuleSnapshotId().toString(),
                ballotPackage.getRuleSnapshotHash(),
                ballotPackage.getScope().name(),
                ballotPackage.getScopeReferenceId() == null ? "" : ballotPackage.getScopeReferenceId().toString(),
                ballotPackage.getCollectionMode().name(),
                ballotPackage.getDuplicateBallotPolicy().name(),
                ballotPackage.getVoteStartAt().toString(),
                ballotPackage.getVoteEndAt().toString(),
                subjectIds.stream().sorted().toList().toString(),
                snapshot.aggregateHash()));
    }

    private void publishSubject(VotingSubject subject) {
        VotingSubjectActions.publish(subject);
        updateSubjectStatus(subject, SubjectStatus.PUBLISHED);
    }

    private void openSubject(VotingSubject subject, Instant now) {
        try {
            VotingSubjectActions.openVoting(subject, now);
        } catch (VotingSubjectActions.IllegalSubjectTransitionException ex) {
            throw new VotingExecutionException(ex.getMessage(), ex);
        }
        updateSubjectStatus(subject, SubjectStatus.VOTING);
    }

    private void updateSubjectStatus(VotingSubject subject, SubjectStatus target) {
        int updated = subjectRepository.updateStatus(subject.getSubjectId(), target.getDbValue(), subject.getVersion());
        if (updated != 1) {
            throw new VotingExecutionException("表决事项已被并发修改 subjectId=" + subject.getSubjectId());
        }
    }

    private VotingExecutionPackage requirePackage(Long packageId, Long tenantId) {
        return executionRepository.findPackage(packageId, tenantId)
                .orElseThrow(() -> new VotingExecutionException(Reason.NOT_FOUND, "正式表决包不存在"));
    }

    private VotingExecutionPackage requirePackageForUpdate(Long packageId, Long tenantId) {
        return executionRepository.findPackageForUpdate(packageId, tenantId)
                .orElseThrow(() -> new VotingExecutionException(Reason.NOT_FOUND, "正式表决包不存在"));
    }

    private VotingSubject requireSubject(Long subjectId, Long tenantId) {
        VotingSubject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new VotingExecutionException("表决事项不存在 subjectId=" + subjectId));
        if (!tenantId.equals(subject.getTenantId())) {
            throw new VotingExecutionException("表决事项不属于当前小区");
        }
        return subject;
    }

    private VotingElectorateSnapshot.Item requireElectorateItem(Long packageId, Long tenantId, Long opid) {
        return executionRepository.findElectorateItem(packageId, tenantId, opid)
                .orElseThrow(() -> new VotingExecutionException(
                        Reason.ELECTORATE_NOT_FOUND, "该专有部分不在本次冻结表决人名册中"));
    }

    private void updatePackage(VotingExecutionPackage ballotPackage) {
        if (executionRepository.updatePackage(ballotPackage) != 1) {
            throw new VotingExecutionException(Reason.CONCURRENT_MODIFICATION, "正式表决包已被并发修改");
        }
    }

    private void requireStatus(VotingExecutionPackage ballotPackage, VotingExecutionPackage.Status status) {
        if (ballotPackage.getStatus() != status) {
            throw new VotingExecutionException("正式表决包状态不允许该操作 status=" + ballotPackage.getStatus());
        }
    }

    private void requireChannelAllowed(VotingExecutionPackage ballotPackage, VoteChannel channel) {
        if (channel == null || !ballotPackage.accepts(channel)) {
            throw new VotingExecutionException(Reason.CHANNEL_NOT_ALLOWED, "本次表决未采用该收票方式");
        }
    }

    private VoteChannel normalizeDeliveryChannel(VoteChannel channel) {
        if (channel == null) {
            throw new VotingExecutionException("deliveryChannel 不能为空");
        }
        return channel.paperLike() ? VoteChannel.PAPER : VoteChannel.ONLINE;
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new VotingExecutionException(field + " 必须为正整数");
        }
    }

    private String requireText(String value, String field) {
        String normalized = trim(value);
        if (normalized == null) {
            throw new VotingExecutionException(field + " 不能为空");
        }
        return normalized;
    }

    private Instant requireInstant(Instant value, String field) {
        if (value == null) {
            throw new VotingExecutionException(field + " 不能为空");
        }
        return value;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String canonical(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public record CreatePackageCommand(
            Long tenantId,
            VotingExecutionPackage.BusinessType businessType,
            Long businessReferenceId,
            String proposalSnapshotType,
            Long proposalSnapshotId,
            String proposalSnapshotHash,
            String ruleSnapshotType,
            Long ruleSnapshotId,
            String ruleSnapshotHash,
            VotingScope scope,
            Long scopeReferenceId,
            VotingExecutionPackage.CollectionMode collectionMode,
            VotingExecutionPackage.DuplicateBallotPolicy duplicateBallotPolicy,
            Instant voteStartAt,
            Instant voteEndAt,
            Long createdByUserId
    ) {
    }

    public record RecordDeliveryCommand(
            Long packageId,
            Long tenantId,
            Long opid,
            VoteChannel deliveryChannel,
            String deliveryMethod,
            String evidenceHash,
            Long deliveredByUserId,
            Instant deliveredAt
    ) {
    }

    public record CastBallotCommand(
            Long packageId,
            Long subjectId,
            Long tenantId,
            Long opid,
            Long uid,
            VoteChoice choice,
            VoteChannel voteChannel,
            String ballotFileHash,
            String signatureHash,
            Long recordedByUserId,
            Instant castAt
    ) {
    }

    @FunctionalInterface
    public interface SettlementPolicyProvider {
        VotingSettlementPolicy forSubject(VotingSubject subject);
    }

    public enum Reason {
        NOT_FOUND,
        INVALID_STATUS,
        ELECTORATE_NOT_FOUND,
        DUPLICATE_BALLOT,
        CHANNEL_NOT_ALLOWED,
        DELIVERY_REQUIRED,
        CONCURRENT_MODIFICATION,
        INVALID_COMMAND
    }

    public static class VotingExecutionException extends RuntimeException {
        private final Reason reason;

        public VotingExecutionException(String message) {
            this(Reason.INVALID_COMMAND, message);
        }

        public VotingExecutionException(String message, Throwable cause) {
            this(Reason.INVALID_COMMAND, message, cause);
        }

        public VotingExecutionException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public VotingExecutionException(Reason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
