// 关联业务：接收业主线上表决；正式表决事项必须转入冻结名册和跨渠道唯一票内核。
package com.pangu.application.voting;

import com.pangu.application.voting.command.CastVoteCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.gateway.VoteCastMonitorGateway;
import com.pangu.domain.model.asset.OwnerPropertyVotingView;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.CandidateStatus;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.EvaluationResult;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.domain.repository.VoteItemRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * 业主投票提交服务（M3-2 引入）。
 *
 * <p>主链路：
 * <ol>
 *   <li>读取议题，校验 status=VOTING 且 subjectType ∈ {GENERAL, MAJOR}（ELECTION 本期不开放投票）；</li>
 *   <li>校验 tenant 一致；</li>
 *   <li>线上表决统一调用 {@link AbacPolicyEngine#evaluateVoting}：共同决定要求 L2，业委会选举要求 L3；</li>
 *   <li>校验 opid 归属 + scope=BUILDING 时 building_id 范围；</li>
 *   <li>INSERT t_vote_item，UNIQUE 冲突 → {@link VotingApplicationException.Reason#VOTE_ALREADY_CAST}。</li>
 * </ol>
 *
 * <p>不返回当前票数（本期前端禁止"实时票数曝光"，防止从众心理）；仅返回 vote_id 作为回执。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoteSubmissionService {

    private final VotingSubjectRepository subjectRepository;
    private final VoteItemRepository voteItemRepository;
    private final OwnerPropertyVotingRepository ownerPropertyVotingRepository;
    private final AbacPolicyEngine abacPolicyEngine;
    private final UserContextHolder userContextHolder;
    private final ElectionCandidateRegistry electionCandidateRegistry;
    private final VoteCastMonitorGateway voteCastMonitorGateway;
    private final VotingExecutionService votingExecutionService;

    @Transactional
    public long cast(CastVoteCommand cmd) {
        // 0. 基础参数兜底
        if (cmd.subjectId() == null || cmd.uid() == null || cmd.tenantId() == null
                || cmd.opid() == null || cmd.choice() == null) {
            throw new IllegalArgumentException("CastVoteCommand 关键字段不能为空");
        }

        // 1. 议题校验
        VotingSubject subject = subjectRepository.findById(cmd.subjectId())
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + cmd.subjectId()));
        if (subject.getStatus() != SubjectStatus.VOTING) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_VOTING_CASTABLE,
                    "议题不在投票中 subjectId=" + cmd.subjectId() + " status=" + subject.getStatus());
        }
        if (!subject.getTenantId().equals(cmd.tenantId())) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.OPID_OUT_OF_SCOPE,
                    "议题租户与当前业主租户不一致 subjectTenantId=" + subject.getTenantId()
                            + " currentTenantId=" + cmd.tenantId());
        }

        // 1.1 ELECTION 专属候选人闸门：targetId 必填 + 候选人属本议题且 APPROVED + 选择必须 SUPPORT
        if (subject.getSubjectType() == SubjectType.ELECTION) {
            if (cmd.targetId() == null) {
                throw new VotingApplicationException(
                        VotingApplicationException.Reason.ELECTION_TARGET_REQUIRED,
                        "选举投票必须指定候选人 candidateId subjectId=" + cmd.subjectId());
            }
            Candidate candidate = electionCandidateRegistry.findById(cmd.targetId())
                    .orElseThrow(() -> new VotingApplicationException(
                            VotingApplicationException.Reason.CANDIDATE_NOT_VOTABLE,
                            "候选人不存在 candidateId=" + cmd.targetId()));
            if (!cmd.subjectId().equals(candidate.getSubjectId())
                    || candidate.getQualificationStatus() != CandidateStatus.APPROVED) {
                throw new VotingApplicationException(
                        VotingApplicationException.Reason.CANDIDATE_NOT_VOTABLE,
                        "候选人不可被投票 candidateId=" + cmd.targetId()
                                + " subjectId=" + candidate.getSubjectId()
                                + " status=" + candidate.getQualificationStatus());
            }
            if (cmd.choice() != VoteChoice.SUPPORT) {
                throw new VotingApplicationException(
                        VotingApplicationException.Reason.CANDIDATE_NOT_VOTABLE,
                        "选举投票仅支持 SUPPORT（投候选人=支持） choice=" + cmd.choice());
            }
        }

        VoteChannel voteChannel = VoteChannel.defaultIfNull(cmd.voteChannel());

        // 2. C 端线上票按业务目的校验实名等级；纸票/线下代录由原件与复核证明身份。
        if (voteChannel == VoteChannel.ONLINE) {
            UserContext ctx = userContextHolder.current();
            AuthenticationLevel currentLevel = ctx == null ? null : ctx.authLevel();
            AbacPolicyEngine.VotingPurpose purpose = subject.getSubjectType() == SubjectType.ELECTION
                    ? AbacPolicyEngine.VotingPurpose.COMMITTEE_ELECTION
                    : AbacPolicyEngine.VotingPurpose.COMMON_DECISION;
            EvaluationResult eval = abacPolicyEngine.evaluateVoting(
                    cmd.uid(), cmd.tenantId(), purpose, currentLevel);
            if (!eval.isAllowed()) {
                VotingApplicationException.Reason reason = purpose
                        == AbacPolicyEngine.VotingPurpose.COMMITTEE_ELECTION
                        ? VotingApplicationException.Reason.ELECTION_AUTH_LEVEL_INSUFFICIENT
                        : VotingApplicationException.Reason.AUTH_LEVEL_INSUFFICIENT;
                throw new VotingApplicationException(
                        reason,
                        eval.getMessage() == null ? "当前认证等级不足" : eval.getMessage());
            }
        }

        // 正式共同决定必须通过整包确认入口提交，禁止单事项接口产生部分票或接受客户端签名摘要。
        var formalPackage = votingExecutionService.findPackageBySubjectId(cmd.subjectId());
        if (formalPackage.isPresent()) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_VOTING_CASTABLE,
                    "该事项属于正式共同决定，请从本次业主大会表决页面核对全部事项后统一提交");
        }

        // 3. opid 归属 + scope 范围校验
        OwnerPropertyVotingView view = ownerPropertyVotingRepository.findByOpid(cmd.opid())
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.OPID_NOT_OWNED,
                        "opid 不存在 opid=" + cmd.opid()));
        if (!view.uid().equals(cmd.uid()) || !view.tenantId().equals(cmd.tenantId())) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.OPID_NOT_OWNED,
                    "opid 与当前业主或租户不匹配 opid=" + cmd.opid());
        }
        if (!view.isValidForVoting()) {
            // 投票代表 / 账户状态不正常 → 视同不在范围内（防欠费/冻结业主投票）
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.OPID_OUT_OF_SCOPE,
                    "该房产当前不具备投票资格 opid=" + cmd.opid()
                            + " votingDelegate=" + view.votingDelegate()
                            + " accountStatus=" + view.accountStatus());
        }
        if (subject.getScope() == VotingScope.BUILDING
                && !subject.getScopeReferenceId().equals(view.buildingId())) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.OPID_OUT_OF_SCOPE,
                    "opid 所在楼栋与议题 scope 不匹配 opid=" + cmd.opid()
                            + " ownerBuildingId=" + view.buildingId()
                            + " subjectBuildingId=" + subject.getScopeReferenceId());
        }

        // 4. ELECTION maxWinners 计数门：该 opid 已投票数不得超过应选名额（读后写软约束）
        if (subject.getSubjectType() == SubjectType.ELECTION) {
            long castSoFar = electionCandidateRegistry.countSupportByOpid(cmd.subjectId(), cmd.opid());
            Integer maxWinners = subject.getMaxWinners();
            if (maxWinners != null && castSoFar >= maxWinners) {
                throw new VotingApplicationException(
                        VotingApplicationException.Reason.VOTE_LIMIT_EXCEEDED,
                        "该房产已投满应选名额 opid=" + cmd.opid() + " castSoFar=" + castSoFar
                                + " maxWinners=" + maxWinners);
            }
        }

        // 5. 写入投票（UNIQUE 冲突 → VOTE_ALREADY_CAST）
        VoteItem item = VoteItem.builder()
                .opid(cmd.opid())
                .uid(cmd.uid())
                .targetId(cmd.targetId())
                .propertyArea(view.buildArea())
                .choice(cmd.choice())
                .voteChannel(voteChannel)
                .build();
        long voteId;
        try {
            voteId = voteItemRepository.insert(cmd.subjectId(), item, cmd.signatureHash());
        } catch (VoteItemRepository.DuplicateVoteException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.VOTE_ALREADY_CAST,
                    "您已对该议题投过票 subjectId=" + cmd.subjectId() + " opid=" + cmd.opid(), e);
        }

        log.info("Vote cast subjectId={} uid={} opid={} choice={} channel={} voteId={}",
                cmd.subjectId(), cmd.uid(), cmd.opid(), cmd.choice(), voteChannel, voteId);
        recordMonitorAfterCommit(new VoteCastMonitorGateway.VoteCastEvent(
                cmd.subjectId(),
                cmd.tenantId(),
                cmd.uid(),
                cmd.opid(),
                cmd.targetId(),
                subject.getSubjectType(),
                cmd.choice(),
                cmd.signatureHash(),
                voteChannel,
                Instant.now()));
        return voteId;
    }

    private void recordMonitorAfterCommit(VoteCastMonitorGateway.VoteCastEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    voteCastMonitorGateway.recordCast(event);
                }
            });
            return;
        }
        voteCastMonitorGateway.recordCast(event);
    }
}
