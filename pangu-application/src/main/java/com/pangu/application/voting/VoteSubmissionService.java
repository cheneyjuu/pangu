package com.pangu.application.voting;

import com.pangu.application.voting.command.CastVoteCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.asset.OwnerPropertyVotingView;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.EvaluationResult;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.domain.repository.VoteItemRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业主投票提交服务（M3-2 引入）。
 *
 * <p>主链路：
 * <ol>
 *   <li>读取议题，校验 status=VOTING 且 subjectType ∈ {GENERAL, MAJOR}（ELECTION 本期不开放投票）；</li>
 *   <li>校验 tenant 一致；</li>
 *   <li>对 MAJOR 议题调用 {@link AbacPolicyEngine#evaluateVoting} 强制 L3 face-auth；</li>
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
        if (subject.getSubjectType() == SubjectType.ELECTION) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_TYPE_NOT_SUPPORTED,
                    "ELECTION 类型投票暂不支持，将在 M3-3 放开");
        }
        if (!subject.getTenantId().equals(cmd.tenantId())) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.OPID_OUT_OF_SCOPE,
                    "议题租户与当前业主租户不一致 subjectTenantId=" + subject.getTenantId()
                            + " currentTenantId=" + cmd.tenantId());
        }

        // 2. MAJOR 议题强制 L3 face-auth；GENERAL 议题不要求（沿用现有 evaluateVoting 设计）
        if (subject.getSubjectType() == SubjectType.MAJOR) {
            UserContext ctx = userContextHolder.current();
            AuthenticationLevel currentLevel = ctx == null ? null : ctx.authLevel();
            EvaluationResult eval = abacPolicyEngine.evaluateVoting(cmd.uid(), cmd.tenantId(), currentLevel);
            if (!eval.isAllowed()) {
                throw new VotingApplicationException(
                        VotingApplicationException.Reason.AUTH_LEVEL_INSUFFICIENT,
                        eval.getMessage() == null ? "当前认证等级不足" : eval.getMessage());
            }
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

        // 4. 写入投票（UNIQUE 冲突 → VOTE_ALREADY_CAST）
        VoteItem item = VoteItem.builder()
                .opid(cmd.opid())
                .uid(cmd.uid())
                .targetId(cmd.targetId())
                .propertyArea(view.buildArea())
                .choice(cmd.choice())
                .build();
        long voteId;
        try {
            voteId = voteItemRepository.insert(cmd.subjectId(), item, cmd.signatureHash());
        } catch (VoteItemRepository.DuplicateVoteException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.VOTE_ALREADY_CAST,
                    "您已对该议题投过票 subjectId=" + cmd.subjectId() + " opid=" + cmd.opid(), e);
        }

        log.info("Vote cast subjectId={} uid={} opid={} choice={} voteId={}",
                cmd.subjectId(), cmd.uid(), cmd.opid(), cmd.choice(), voteId);
        return voteId;
    }
}
