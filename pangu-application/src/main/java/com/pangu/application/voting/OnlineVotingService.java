// 关联业务：编排业主本人阅读确认、整包线上投票、回执和纸质协助申请。
package com.pangu.application.voting;

import com.pangu.application.support.PayloadHasher;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.voting.OnlineBallotSubmission;
import com.pangu.domain.model.voting.OnlinePaperAssistanceRequest;
import com.pangu.domain.model.voting.OnlineVotingAcknowledgement;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VotingBallotRecord;
import com.pangu.domain.model.voting.VotingDeliveryRecord;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.repository.OnlineVotingRepository;
import com.pangu.domain.repository.PaperVotingRepository;
import com.pangu.domain.repository.VotingExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.pangu.application.voting.OnlineVotingException.Reason.ALREADY_SUBMITTED;
import static com.pangu.application.voting.OnlineVotingException.Reason.AUTHENTICATION_REQUIRED;
import static com.pangu.application.voting.OnlineVotingException.Reason.CONCURRENT_MODIFICATION;
import static com.pangu.application.voting.OnlineVotingException.Reason.FORBIDDEN;
import static com.pangu.application.voting.OnlineVotingException.Reason.INVALID_ARGUMENT;
import static com.pangu.application.voting.OnlineVotingException.Reason.INVALID_STATUS;
import static com.pangu.application.voting.OnlineVotingException.Reason.NOT_FOUND;

/**
 * 线上渠道只接收当前 C 端实名上下文。一次确认覆盖全部事项，并在一个事务中写入统一票据，
 * 避免通用单事项接口留下部分提交或接受客户端伪造的签名摘要。
 */
@Service
@RequiredArgsConstructor
public class OnlineVotingService {

    private final VotingExecutionRepository votingExecutionRepository;
    private final OnlineVotingRepository onlineVotingRepository;
    private final PaperVotingRepository paperVotingRepository;
    private final VotingExecutionService votingExecutionService;
    private final VotingConflictAuditService conflictAuditService;
    private final UserContextHolder userContextHolder;
    private final Clock clock;

    @Transactional
    public OnlineVotingAcknowledgement acknowledge(AcknowledgeCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        UserContext owner = requireL3Owner(command.tenantId());
        VotingExecutionPackage ballotPackage = requireOnlineVotingPackage(
                command.packageId(), command.tenantId(), command.packageHash());
        if (!Boolean.TRUE.equals(command.confirmed())) {
            throw new OnlineVotingException(INVALID_ARGUMENT, "请确认已阅读本次公示材料和全部表决事项");
        }
        VotingElectorateSnapshot.Item electorate = requireOwnerElectorate(ballotPackage, command.opid(), owner);
        requireOnlineRouteAvailable(ballotPackage, electorate);
        OnlineVotingAcknowledgement existing = onlineVotingRepository.findAcknowledgement(
                ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId()).orElse(null);
        if (existing != null) {
            if (sameOwnerAndPackage(existing, owner, ballotPackage)) {
                return existing;
            }
            throw new OnlineVotingException(CONCURRENT_MODIFICATION, "本专有部分的阅读确认已由其他身份完成");
        }
        Instant acknowledgedAt = requireInstant(command.acknowledgedAt(), "确认时间");
        String acknowledgementHash = PayloadHasher.sha256Hex(String.join("|",
                ballotPackage.getPackageId().toString(), electorate.snapshotItemId().toString(),
                owner.accountId().toString(), owner.uid().toString(), electorate.representativeOpid().toString(),
                ballotPackage.getPackageHash(), acknowledgedAt.toString()));
        try {
            VotingDeliveryRecord delivery = votingExecutionService.recordDelivery(
                    new VotingExecutionService.RecordDeliveryCommand(
                            ballotPackage.getPackageId(), ballotPackage.getTenantId(),
                            electorate.representativeOpid(), VoteChannel.ONLINE,
                            "ELECTRONIC", acknowledgementHash, null, acknowledgedAt));
            return onlineVotingRepository.insertAcknowledgement(new OnlineVotingAcknowledgement(
                    null, ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId(),
                    owner.accountId(), owner.uid(), electorate.representativeOpid(), ballotPackage.getPackageHash(),
                    acknowledgementHash, delivery.deliveryId(), acknowledgedAt));
        } catch (DataIntegrityViolationException ex) {
            throw new OnlineVotingException(CONCURRENT_MODIFICATION, "本专有部分的阅读确认已被其他请求完成", ex);
        } catch (VotingExecutionService.VotingExecutionException ex) {
            throw translate(ex);
        }
    }

    @Transactional
    public OnlineBallotSubmission submit(SubmitCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        UserContext owner = requireL3Owner(command.tenantId());
        VotingExecutionPackage ballotPackage = requireOnlineVotingPackage(
                command.packageId(), command.tenantId(), command.packageHash());
        if (!Boolean.TRUE.equals(command.confirmed())) {
            throw new OnlineVotingException(INVALID_ARGUMENT, "请核对全部表决选择并确认提交");
        }
        VotingElectorateSnapshot.Item electorate = requireOwnerElectorate(ballotPackage, command.opid(), owner);
        String idempotencyKey = requireIdempotencyKey(command.idempotencyKey());
        List<Decision> decisions = requireCompleteDecisions(
                command.decisions(), votingExecutionRepository.listSubjectIds(
                        ballotPackage.getPackageId(), ballotPackage.getTenantId()));
        String choiceManifestHash = choiceManifestHash(decisions);
        OnlineBallotSubmission retried = onlineVotingRepository.findSubmissionByIdempotencyKey(
                ballotPackage.getPackageId(), ballotPackage.getTenantId(), idempotencyKey).orElse(null);
        if (retried != null) {
            if (sameSubmission(retried, electorate, owner, ballotPackage, choiceManifestHash)) {
                return retried;
            }
            throw new OnlineVotingException(INVALID_ARGUMENT, "同一提交凭据不能用于不同的表决内容");
        }
        try {
            requireOnlineRouteAvailable(ballotPackage, electorate);
        } catch (OnlineVotingException failure) {
            if (failure.getReason() == ALREADY_SUBMITTED && hasPaperBallot(ballotPackage, electorate)) {
                conflictAuditService.recordRejectedOnlineBallot(
                        ballotPackage, electorate, owner, choiceManifestHash, clock.instant());
            }
            throw failure;
        }
        if (onlineVotingRepository.findSubmission(
                ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId()).isPresent()) {
            throw new OnlineVotingException(ALREADY_SUBMITTED, "本专有部分已经完成表决");
        }
        OnlineVotingAcknowledgement acknowledgement = onlineVotingRepository.findAcknowledgement(
                        ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId())
                .filter(item -> sameOwnerAndPackage(item, owner, ballotPackage))
                .orElseThrow(() -> new OnlineVotingException(
                        INVALID_STATUS, "请先阅读并确认本次锁定公示材料"));
        Instant submittedAt = requireInstant(command.submittedAt(), "提交时间");
        String confirmationHash = PayloadHasher.sha256Hex(String.join("|",
                ballotPackage.getPackageId().toString(), electorate.snapshotItemId().toString(),
                owner.accountId().toString(), owner.uid().toString(), ballotPackage.getPackageHash(),
                acknowledgement.acknowledgementHash(), choiceManifestHash, submittedAt.toString()));
        try {
            OnlineBallotSubmission inserted = onlineVotingRepository.insertSubmission(new OnlineBallotSubmission(
                    null, ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId(),
                    owner.accountId(), owner.uid(), electorate.representativeOpid(), idempotencyKey,
                    ballotPackage.getPackageHash(), choiceManifestHash, confirmationHash,
                    OnlineBallotSubmission.Status.ACCEPTED, submittedAt, List.of()));
            for (Decision decision : decisions) {
                String itemHash = PayloadHasher.sha256Hex(String.join("|",
                        confirmationHash, decision.subjectId().toString(), decision.choice().name()));
                VotingBallotRecord ballot = votingExecutionService.castRecord(
                        new VotingExecutionService.CastBallotCommand(
                                ballotPackage.getPackageId(), decision.subjectId(), ballotPackage.getTenantId(),
                                electorate.representativeOpid(), owner.uid(), decision.choice(), VoteChannel.ONLINE,
                                null, itemHash, null, submittedAt));
                onlineVotingRepository.insertSubmissionItem(inserted.submissionId(),
                        new OnlineBallotSubmission.Item(
                                null, inserted.submissionId(), decision.subjectId(), decision.choice(),
                                ballot.ballotId(), itemHash));
            }
            return onlineVotingRepository.findSubmission(
                    ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId())
                    .orElseThrow();
        } catch (DataIntegrityViolationException ex) {
            throw new OnlineVotingException(ALREADY_SUBMITTED, "本专有部分已经通过纸质或线上方式完成表决", ex);
        } catch (VotingExecutionService.VotingExecutionException ex) {
            throw translate(ex);
        }
    }

    @Transactional
    public OnlinePaperAssistanceRequest requestPaperAssistance(PaperAssistanceCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        UserContext owner = requireOwner(command.tenantId());
        VotingExecutionPackage ballotPackage = requireOnlineVotingPackage(
                command.packageId(), command.tenantId(), command.packageHash());
        if (ballotPackage.getCollectionMode()
                != VotingExecutionPackage.CollectionMode.ONLINE_WITH_PAPER_ASSISTANCE) {
            throw new OnlineVotingException(INVALID_STATUS, "本次表决已经直接开放纸质办理，无需另行申请");
        }
        VotingElectorateSnapshot.Item electorate = requireOwnerElectorate(ballotPackage, command.opid(), owner);
        if (onlineVotingRepository.findSubmission(
                ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId()).isPresent()
                || hasActiveBallot(ballotPackage, electorate)) {
            throw new OnlineVotingException(ALREADY_SUBMITTED, "本专有部分已经完成表决，不能再申请纸质办理");
        }
        Instant requestedAt = requireInstant(command.occurredAt(), "申请时间");
        OnlinePaperAssistanceRequest existing = onlineVotingRepository.findPaperAssistanceRequest(
                ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId()).orElse(null);
        if (existing == null) {
            try {
                return onlineVotingRepository.insertPaperAssistanceRequest(new OnlinePaperAssistanceRequest(
                        null, ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId(),
                        owner.accountId(), owner.uid(), electorate.representativeOpid(),
                        OnlinePaperAssistanceRequest.Status.REQUESTED, requestedAt, null, null, null));
            } catch (DataIntegrityViolationException ex) {
                throw new OnlineVotingException(CONCURRENT_MODIFICATION, "纸质办理申请已被其他请求处理", ex);
            }
        }
        if (!sameOwner(existing, owner)) {
            throw new OnlineVotingException(FORBIDDEN, "纸质办理申请不属于当前业主");
        }
        if (existing.status() == OnlinePaperAssistanceRequest.Status.REQUESTED
                || existing.status() == OnlinePaperAssistanceRequest.Status.FULFILLED) {
            return existing;
        }
        if (onlineVotingRepository.reactivatePaperAssistanceRequest(
                existing.requestId(), existing.tenantId(), requestedAt) != 1) {
            throw new OnlineVotingException(CONCURRENT_MODIFICATION, "纸质办理申请状态已变化，请刷新后重试");
        }
        return onlineVotingRepository.findPaperAssistanceRequest(
                ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId()).orElseThrow();
    }

    @Transactional
    public OnlinePaperAssistanceRequest withdrawPaperAssistance(
            Long packageId, Long requestId, Long tenantId, Long opid, String packageHash, Instant occurredAt) {
        UserContext owner = requireOwner(tenantId);
        VotingExecutionPackage ballotPackage = requireOnlineVotingPackage(packageId, tenantId, packageHash);
        VotingElectorateSnapshot.Item electorate = requireOwnerElectorate(ballotPackage, opid, owner);
        OnlinePaperAssistanceRequest request = onlineVotingRepository.findPaperAssistanceRequest(
                        ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId())
                .orElseThrow(() -> new OnlineVotingException(NOT_FOUND, "纸质办理申请不存在"));
        if (!Objects.equals(requestId, request.requestId()) || !sameOwner(request, owner)) {
            throw new OnlineVotingException(FORBIDDEN, "纸质办理申请不属于当前业主");
        }
        if (request.status() == OnlinePaperAssistanceRequest.Status.WITHDRAWN) {
            return request;
        }
        if (request.status() == OnlinePaperAssistanceRequest.Status.FULFILLED
                || paperVotingRepository.listDeliveries(packageId, tenantId).stream()
                .anyMatch(delivery -> delivery.electorateItemId().equals(electorate.snapshotItemId()))
                || paperVotingRepository.listBallots(packageId, tenantId).stream()
                .anyMatch(ballot -> ballot.electorateItemId().equals(electorate.snapshotItemId()))) {
            throw new OnlineVotingException(INVALID_STATUS, "纸质材料已开始办理，不能撤回申请");
        }
        if (onlineVotingRepository.withdrawPaperAssistanceRequest(
                request.requestId(), tenantId, requireInstant(occurredAt, "撤回时间")) != 1) {
            throw new OnlineVotingException(CONCURRENT_MODIFICATION, "纸质办理申请状态已变化，请刷新后重试");
        }
        return onlineVotingRepository.findPaperAssistanceRequest(
                ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public OwnerProgress ownerProgress(Long packageId, Long tenantId, List<Long> opids) {
        UserContext owner = requireOwner(tenantId);
        VotingExecutionPackage ballotPackage = votingExecutionRepository.findPackage(packageId, tenantId)
                .orElseThrow(() -> new OnlineVotingException(NOT_FOUND, "正式表决包不存在"));
        List<PropertyProgress> properties = (opids == null ? List.<Long>of() : opids).stream()
                .distinct()
                .map(opid -> votingExecutionRepository.findElectorateItem(packageId, tenantId, opid).orElse(null))
                .filter(Objects::nonNull)
                .filter(item -> owner.uid().equals(item.representativeUid()))
                .map(item -> propertyProgress(ballotPackage, item))
                .toList();
        return new OwnerProgress(ballotPackage.getCollectionMode(), ballotPackage.getPackageHash(), properties);
    }

    @Transactional(readOnly = true)
    public List<OnlinePaperAssistanceRequest> listPaperAssistanceRequests(Long packageId, Long tenantId) {
        return onlineVotingRepository.listPaperAssistanceRequests(packageId, tenantId);
    }

    @Transactional(readOnly = true)
    public ManagementProgress managementProgress(Long packageId, Long tenantId) {
        return new ManagementProgress(
                onlineVotingRepository.countAcceptedSubmissions(packageId, tenantId),
                votingExecutionRepository.countAudits(packageId, tenantId, "ONLINE_BALLOT_CONFLICT"));
    }

    private PropertyProgress propertyProgress(
            VotingExecutionPackage ballotPackage, VotingElectorateSnapshot.Item item) {
        OnlineVotingAcknowledgement acknowledgement = onlineVotingRepository.findAcknowledgement(
                ballotPackage.getPackageId(), item.snapshotItemId(), ballotPackage.getTenantId()).orElse(null);
        OnlineBallotSubmission submission = onlineVotingRepository.findSubmission(
                ballotPackage.getPackageId(), item.snapshotItemId(), ballotPackage.getTenantId()).orElse(null);
        OnlinePaperAssistanceRequest assistance = onlineVotingRepository.findPaperAssistanceRequest(
                ballotPackage.getPackageId(), item.snapshotItemId(), ballotPackage.getTenantId()).orElse(null);
        List<VotingBallotRecord> activeBallots = votingExecutionRepository
                .listSubjectIds(ballotPackage.getPackageId(), ballotPackage.getTenantId()).stream()
                .map(subjectId -> votingExecutionRepository.findActiveBallot(
                        subjectId, item.snapshotItemId(), ballotPackage.getTenantId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        VoteChannel firstChannel = activeBallots.isEmpty() ? null : activeBallots.getFirst().voteChannel();
        VoteChannel participationChannel = firstChannel != null
                && activeBallots.stream().allMatch(ballot -> ballot.voteChannel() == firstChannel)
                ? firstChannel : null;
        return new PropertyProgress(
                item.representativeOpid(), acknowledgement != null,
                submission == null ? null : new Receipt(
                        submission.submissionId(), submission.confirmationHash(), submission.submittedAt()),
                assistance,
                !activeBallots.isEmpty(),
                participationChannel,
                paperDeliveryStatus(ballotPackage, item),
                paperBallotStatus(ballotPackage, item));
    }

    private String paperDeliveryStatus(
            VotingExecutionPackage ballotPackage, VotingElectorateSnapshot.Item item) {
        var deliveries = paperVotingRepository.listDeliveries(
                        ballotPackage.getPackageId(), ballotPackage.getTenantId()).stream()
                .filter(delivery -> delivery.electorateItemId().equals(item.snapshotItemId()))
                .toList();
        if (deliveries.stream().anyMatch(delivery -> delivery.status()
                == com.pangu.domain.model.voting.PaperVotingDelivery.Status.CONFIRMED)) {
            return "CONFIRMED";
        }
        if (deliveries.stream().anyMatch(delivery -> delivery.status()
                == com.pangu.domain.model.voting.PaperVotingDelivery.Status.PENDING_REVIEW)) {
            return "PENDING_REVIEW";
        }
        return deliveries.isEmpty() ? "NOT_REGISTERED" : "REJECTED";
    }

    private String paperBallotStatus(
            VotingExecutionPackage ballotPackage, VotingElectorateSnapshot.Item item) {
        var ballots = paperVotingRepository.listBallots(
                        ballotPackage.getPackageId(), ballotPackage.getTenantId()).stream()
                .filter(ballot -> ballot.electorateItemId().equals(item.snapshotItemId()))
                .toList();
        if (ballots.stream().anyMatch(ballot -> ballot.status()
                == com.pangu.domain.model.voting.PaperBallot.Status.COMPLETED)) {
            return "COMPLETED";
        }
        if (ballots.stream().anyMatch(ballot -> ballot.status()
                == com.pangu.domain.model.voting.PaperBallot.Status.IN_ENTRY)) {
            return "IN_ENTRY";
        }
        if (ballots.stream().anyMatch(ballot -> ballot.status()
                == com.pangu.domain.model.voting.PaperBallot.Status.RECEIVED)) {
            return "RECEIVED";
        }
        return ballots.isEmpty() ? "NOT_RECEIVED" : "VOIDED";
    }

    private UserContext requireL3Owner(Long tenantId) {
        UserContext owner = requireOwner(tenantId);
        if (owner.authLevel() == null || owner.authLevel().getValue() < AuthenticationLevel.L3.getValue()) {
            throw new OnlineVotingException(AUTHENTICATION_REQUIRED, "在线表决前请先完成人脸实名核验");
        }
        return owner;
    }

    private UserContext requireOwner(Long tenantId) {
        UserContext owner = userContextHolder.current();
        if (owner == null || !owner.isCUser() || owner.accountId() == null || owner.uid() == null) {
            throw new OnlineVotingException(FORBIDDEN, "未识别到当前业主身份");
        }
        if (tenantId == null || !tenantId.equals(owner.tenantId())) {
            throw new OnlineVotingException(FORBIDDEN, "当前业主不属于该小区");
        }
        return owner;
    }

    private VotingExecutionPackage requireOnlineVotingPackage(Long packageId, Long tenantId, String packageHash) {
        VotingExecutionPackage ballotPackage = votingExecutionRepository.findPackage(packageId, tenantId)
                .orElseThrow(() -> new OnlineVotingException(NOT_FOUND, "正式表决包不存在"));
        Instant now = clock.instant();
        if (ballotPackage.getStatus() != VotingExecutionPackage.Status.VOTING
                || now.isBefore(ballotPackage.getVoteStartAt()) || !now.isBefore(ballotPackage.getVoteEndAt())) {
            throw new OnlineVotingException(INVALID_STATUS, "当前不在本次表决时间内");
        }
        if (!ballotPackage.accepts(VoteChannel.ONLINE)) {
            throw new OnlineVotingException(INVALID_STATUS, "本次表决未采用线上办理方式");
        }
        if (packageHash == null || !packageHash.equals(ballotPackage.getPackageHash())) {
            throw new OnlineVotingException(INVALID_STATUS, "本次表决材料版本已变化，请刷新后重新核对");
        }
        return ballotPackage;
    }

    private VotingElectorateSnapshot.Item requireOwnerElectorate(
            VotingExecutionPackage ballotPackage, Long opid, UserContext owner) {
        VotingElectorateSnapshot.Item electorate = votingExecutionRepository.findElectorateItem(
                        ballotPackage.getPackageId(), ballotPackage.getTenantId(), opid)
                .orElseThrow(() -> new OnlineVotingException(NOT_FOUND, "该专有部分不在本次表决范围内"));
        if (!owner.uid().equals(electorate.representativeUid())) {
            throw new OnlineVotingException(FORBIDDEN, "当前业主不是该专有部分本次冻结的表决代表");
        }
        return electorate;
    }

    private void requireOnlineRouteAvailable(
            VotingExecutionPackage ballotPackage, VotingElectorateSnapshot.Item electorate) {
        OnlinePaperAssistanceRequest assistance = onlineVotingRepository.findPaperAssistanceRequest(
                ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId()).orElse(null);
        if (assistance != null && assistance.status() != OnlinePaperAssistanceRequest.Status.WITHDRAWN) {
            throw new OnlineVotingException(INVALID_STATUS, "本专有部分已申请纸质办理，请按纸质表决票完成");
        }
        if (ballotPackage.getCollectionMode()
                != VotingExecutionPackage.CollectionMode.PAPER_AND_ONLINE
                && hasActiveBallot(ballotPackage, electorate)) {
            throw new OnlineVotingException(ALREADY_SUBMITTED, "本专有部分已经通过纸质或线上方式完成表决");
        }
    }

    private boolean hasActiveBallot(
            VotingExecutionPackage ballotPackage, VotingElectorateSnapshot.Item electorate) {
        return votingExecutionRepository.listSubjectIds(ballotPackage.getPackageId(), ballotPackage.getTenantId())
                .stream()
                .anyMatch(subjectId -> votingExecutionRepository.findActiveBallot(
                        subjectId, electorate.snapshotItemId(), ballotPackage.getTenantId()).isPresent());
    }

    private boolean hasPaperBallot(
            VotingExecutionPackage ballotPackage, VotingElectorateSnapshot.Item electorate) {
        return votingExecutionRepository.listSubjectIds(ballotPackage.getPackageId(), ballotPackage.getTenantId())
                .stream()
                .map(subjectId -> votingExecutionRepository.findActiveBallot(
                        subjectId, electorate.snapshotItemId(), ballotPackage.getTenantId()).orElse(null))
                .filter(Objects::nonNull)
                .anyMatch(ballot -> ballot.voteChannel() == VoteChannel.PAPER);
    }

    private List<Decision> requireCompleteDecisions(List<Decision> supplied, List<Long> subjectIds) {
        if (supplied == null || supplied.isEmpty()) {
            throw new OnlineVotingException(INVALID_ARGUMENT, "请对本次全部表决事项作出选择");
        }
        Set<Long> seen = new HashSet<>();
        for (Decision decision : supplied) {
            if (decision == null || decision.subjectId() == null || decision.choice() == null) {
                throw new OnlineVotingException(INVALID_ARGUMENT, "表决事项和选择不能为空");
            }
            if (!seen.add(decision.subjectId())) {
                throw new OnlineVotingException(INVALID_ARGUMENT, "同一表决事项不能重复提交");
            }
        }
        if (!seen.equals(Set.copyOf(subjectIds))) {
            throw new OnlineVotingException(INVALID_ARGUMENT, "一次提交必须覆盖本次全部表决事项");
        }
        return supplied.stream().sorted(Comparator.comparing(Decision::subjectId)).toList();
    }

    private String choiceManifestHash(List<Decision> decisions) {
        return PayloadHasher.sha256Hex(decisions.stream()
                .map(decision -> decision.subjectId() + ":" + decision.choice().name())
                .reduce((left, right) -> left + "|" + right).orElseThrow());
    }

    private String requireIdempotencyKey(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 128
                || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new OnlineVotingException(INVALID_ARGUMENT, "提交凭据格式无效，请刷新页面后重试");
        }
        return normalized;
    }

    private boolean sameOwnerAndPackage(
            OnlineVotingAcknowledgement acknowledgement, UserContext owner, VotingExecutionPackage ballotPackage) {
        return owner.accountId().equals(acknowledgement.accountId())
                && owner.uid().equals(acknowledgement.uid())
                && ballotPackage.getPackageHash().equals(acknowledgement.packageHash());
    }

    private boolean sameSubmission(
            OnlineBallotSubmission submission,
            VotingElectorateSnapshot.Item electorate,
            UserContext owner,
            VotingExecutionPackage ballotPackage,
            String choiceManifestHash) {
        return electorate.snapshotItemId().equals(submission.electorateItemId())
                && owner.accountId().equals(submission.accountId())
                && owner.uid().equals(submission.uid())
                && ballotPackage.getPackageHash().equals(submission.packageHash())
                && choiceManifestHash.equals(submission.choiceManifestHash());
    }

    private boolean sameOwner(OnlinePaperAssistanceRequest request, UserContext owner) {
        return owner.accountId().equals(request.accountId()) && owner.uid().equals(request.uid());
    }

    private Instant requireInstant(Instant value, String field) {
        if (value == null) {
            throw new OnlineVotingException(INVALID_ARGUMENT, field + "不能为空");
        }
        return value;
    }

    private OnlineVotingException translate(VotingExecutionService.VotingExecutionException failure) {
        OnlineVotingException.Reason reason = switch (failure.getReason()) {
            case NOT_FOUND, ELECTORATE_NOT_FOUND -> NOT_FOUND;
            case INVALID_STATUS, CHANNEL_NOT_ALLOWED, DELIVERY_REQUIRED -> INVALID_STATUS;
            case DUPLICATE_BALLOT -> ALREADY_SUBMITTED;
            case CONCURRENT_MODIFICATION -> CONCURRENT_MODIFICATION;
            case INVALID_COMMAND -> INVALID_ARGUMENT;
        };
        return new OnlineVotingException(reason, failure.getMessage(), failure);
    }

    public record AcknowledgeCommand(
            Long packageId,
            Long tenantId,
            Long opid,
            String packageHash,
            Boolean confirmed,
            Instant acknowledgedAt
    ) {
    }

    public record SubmitCommand(
            Long packageId,
            Long tenantId,
            Long opid,
            String packageHash,
            Boolean confirmed,
            String idempotencyKey,
            List<Decision> decisions,
            Instant submittedAt
    ) {
    }

    public record Decision(Long subjectId, VoteChoice choice) {
    }

    public record PaperAssistanceCommand(
            Long packageId,
            Long tenantId,
            Long opid,
            String packageHash,
            Instant occurredAt
    ) {
    }

    public record Receipt(Long submissionId, String confirmationHash, Instant submittedAt) {
    }

    public record PropertyProgress(
            Long opid,
            boolean acknowledged,
            Receipt receipt,
            OnlinePaperAssistanceRequest paperAssistance,
            boolean participated,
            VoteChannel participationChannel,
            String paperDeliveryStatus,
            String paperBallotStatus
    ) {
    }

    public record OwnerProgress(
            VotingExecutionPackage.CollectionMode collectionMode,
            String packageHash,
            List<PropertyProgress> properties
    ) {
        public OwnerProgress {
            properties = properties == null ? List.of() : List.copyOf(properties);
        }
    }

    /** 管理端仅需要完成量和冲突量，不能读取线上票面选择。 */
    public record ManagementProgress(long completedPropertyCount, long conflictCount) {
    }
}
