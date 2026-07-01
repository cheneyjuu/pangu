package com.pangu.application.voting;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.application.voting.command.NominateCandidateCommand;
import com.pangu.application.voting.command.PartyReviewCandidateCommand;
import com.pangu.application.voting.command.ReviewCandidateCommand;
import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.model.asset.OwnerSummary;
import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.CandidateStatus;
import com.pangu.domain.model.voting.ElectionCandidateActions;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import com.pangu.domain.repository.VotingSubjectRepository;
import com.pangu.domain.security.NameDecryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 选举候选人编排服务（M3-3 引入）。
 *
 * <p>候选人资格审查为两段闸（本期插入党组书记前置审查）：
 * <ul>
 *   <li>{@link #nominate} —— 管理端提名（status=PENDING_PARTY_REVIEW）；仅 ELECTION 且议题处于
 *       DRAFT/PUBLISHED（投票开始前才能改名单）；</li>
 *   <li>{@link #partyReview} —— 党组书记前置审查（PENDING_PARTY_REVIEW → PENDING_COMMITTEE_REVIEW/REJECTED）；</li>
 *   <li>{@link #review} —— 居委会资格审查（PENDING_COMMITTEE_REVIEW → APPROVED/REJECTED）；</li>
 *   <li>{@link #listCandidates} —— 候选人列表（含所有状态）。</li>
 * </ul>
 *
 * <p>角色授权由 endpoint 的 {@code @PreAuthorize} 粗筛（candidate:nominate / candidate:review:party /
 * candidate:approve / voting:subject:audit），业务约束在此服务内强校验，分层与 {@link ProposalLifecycleService} 一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElectionCandidateService {

    private final VotingSubjectRepository subjectRepository;
    private final ElectionCandidateRegistry electionCandidateRegistry;
    private final PropertyGateway propertyGateway;
    private final NameDecryptor nameDecryptor;
    private final UserContextHolder userContextHolder;

    /** 数字 keyword（手机号）检索最小位数：低于此长度不查，避免无意义的全表扫描。 */
    private static final int MIN_PHONE_FRAGMENT_LENGTH = 3;
    /** 姓名 keyword 检索最小位数：1 字符即可（中文一字也常用）。 */
    private static final int MIN_NAME_KEYWORD_LENGTH = 1;
    /** 姓名匹配预扫描池上限：本租户业主超过此值时只取前 N 条。 */
    private static final int NAME_SEARCH_POOL_LIMIT = 1000;
    /** 姓名搜索结果上限。 */
    private static final int NAME_SEARCH_RESULT_LIMIT = 20;
    /** 选举候选人录入执行人：G 端基层经办员。 */
    private static final String ELECTION_NOMINATE_ROLE = "GOV_OPERATOR";
    private static final Set<Integer> ELECTION_NOMINATE_DEPT_TYPES = Set.of(2, 5);
    private static final String PARTY_REVIEW_ROLE = "PARTY_SECRETARY";
    private static final String COMMITTEE_REVIEW_ROLE = "COMMUNITY_ADMIN";

    /**
     * 提名候选人。要求议题存在、为 ELECTION、status ∈ {DRAFT, PUBLISHED}。
     */
    @Transactional
    public Long nominate(NominateCandidateCommand cmd) {
        VotingSubject subject = subjectRepository.findById(cmd.subjectId())
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + cmd.subjectId()));
        if (subject.getSubjectType() != SubjectType.ELECTION) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_NOMINATABLE,
                    "非 ELECTION 议题不支持候选人提名 subjectId=" + cmd.subjectId()
                            + " type=" + subject.getSubjectType());
        }
        UserContext ctx = userContextHolder.current();
        if (!canNominateElection(ctx)) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "ELECTION 候选人提名仅限 G 端基层经办员（dept_type IN (2,5)），当前角色="
                            + (ctx == null ? "ANONYMOUS" : ctx.roleKey())
                            + " deptType=" + (ctx == null ? null : ctx.deptType()));
        }
        if (subject.getStatus() != SubjectStatus.DRAFT && subject.getStatus() != SubjectStatus.PUBLISHED) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_NOMINATABLE,
                    "议题已进入 " + subject.getStatus() + " 状态，不可增改候选人名单 subjectId=" + cmd.subjectId());
        }
        if (subject.getTenantId() != null && !subject.getTenantId().equals(cmd.tenantId())) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_NOMINATABLE,
                    "议题租户与当前租户不一致 subjectTenantId=" + subject.getTenantId()
                            + " currentTenantId=" + cmd.tenantId());
        }
        Long candidateId;
        try {
            candidateId = electionCandidateRegistry.nominate(
                    cmd.subjectId(), cmd.uid(), cmd.name(), cmd.partyMember());
        } catch (ElectionCandidateRegistry.DuplicateCandidateException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANDIDATE_ALREADY_NOMINATED,
                    "该业主已被提名为本议题候选人 subjectId=" + cmd.subjectId() + " uid=" + cmd.uid(), e);
        }
        log.info("Candidate nominated candidateId={} subjectId={} uid={} partyMember={} operatorUserId={}",
                candidateId, cmd.subjectId(), cmd.uid(), cmd.partyMember(), cmd.operatorUserId());
        return candidateId;
    }

    private boolean canNominateElection(UserContext ctx) {
        return ctx != null
                && ELECTION_NOMINATE_ROLE.equals(ctx.roleKey())
                && ELECTION_NOMINATE_DEPT_TYPES.contains(ctx.deptType());
    }

    /**
     * 党组书记前置审查：PENDING_PARTY_REVIEW → PENDING_COMMITTEE_REVIEW（通过）/ REJECTED（驳回）。
     *
     * <p>资格审查的第一道闸，必须先过此审查候选人才进入居委会资格审查。
     */
    @Transactional
    public Candidate partyReview(PartyReviewCandidateCommand cmd) {
        requireRole(PARTY_REVIEW_ROLE, "候选人党组前置审查仅限党组织书记");
        RejectEvidencePolicy.requireForReject(cmd.approve(), cmd.rejectReasonCode(), cmd.rejectEvidenceJson());
        Candidate candidate = electionCandidateRegistry.findById(cmd.candidateId())
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.CANDIDATE_NOT_FOUND,
                        "候选人不存在 candidateId=" + cmd.candidateId()));
        CandidateStatus from = candidate.getQualificationStatus();
        try {
            if (cmd.approve()) {
                ElectionCandidateActions.partyApprove(candidate);
            } else {
                ElectionCandidateActions.partyReject(candidate);
            }
        } catch (ElectionCandidateActions.IllegalCandidateTransitionException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANDIDATE_REVIEW_CONFLICT, e.getMessage(), e);
        }
        int updated = electionCandidateRegistry.updateQualification(
                candidate.getCandidateId(),
                from.getDbValue(),
                candidate.getQualificationStatus().getDbValue(),
                cmd.approve() ? null : cmd.rejectReasonCode(),
                cmd.approve() ? null : cmd.rejectEvidenceJson(),
                cmd.approve() ? null : cmd.operatorUserId(),
                cmd.approve() ? null : "PARTY_REVIEW");
        if (updated != 1) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANDIDATE_REVIEW_CONFLICT,
                    "候选人已被并发审查 candidateId=" + cmd.candidateId());
        }
        if (!cmd.approve()) {
            candidate.setRejectReasonCode(cmd.rejectReasonCode());
            candidate.setRejectEvidenceJson(cmd.rejectEvidenceJson());
            candidate.setRejectReviewerUserId(cmd.operatorUserId());
            candidate.setRejectReviewStage("PARTY_REVIEW");
        }
        log.info("Candidate party-reviewed candidateId={} approve={} newStatus={} operatorUserId={}",
                cmd.candidateId(), cmd.approve(), candidate.getQualificationStatus(), cmd.operatorUserId());
        return candidate;
    }

    /**
     * 居委会资格审查：PENDING_COMMITTEE_REVIEW → APPROVED / REJECTED。
     *
     * <p>候选人须已过党组书记前置审查（处于 PENDING_COMMITTEE_REVIEW）。对仍处
     * PENDING_PARTY_REVIEW 的候选人调用本审查，DB 阶段化乐观锁命中 0 行 → CANDIDATE_REVIEW_CONFLICT。
     */
    @Transactional
    public Candidate review(ReviewCandidateCommand cmd) {
        requireRole(COMMITTEE_REVIEW_ROLE, "候选人居委会资格审查仅限居委会");
        RejectEvidencePolicy.requireForReject(cmd.approve(), cmd.rejectReasonCode(), cmd.rejectEvidenceJson());
        Candidate candidate = electionCandidateRegistry.findById(cmd.candidateId())
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.CANDIDATE_NOT_FOUND,
                        "候选人不存在 candidateId=" + cmd.candidateId()));
        CandidateStatus from = candidate.getQualificationStatus();
        try {
            if (cmd.approve()) {
                ElectionCandidateActions.approve(candidate);
            } else {
                ElectionCandidateActions.reject(candidate);
            }
        } catch (ElectionCandidateActions.IllegalCandidateTransitionException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANDIDATE_REVIEW_CONFLICT, e.getMessage(), e);
        }
        int updated = electionCandidateRegistry.updateQualification(
                candidate.getCandidateId(),
                from.getDbValue(),
                candidate.getQualificationStatus().getDbValue(),
                cmd.approve() ? null : cmd.rejectReasonCode(),
                cmd.approve() ? null : cmd.rejectEvidenceJson(),
                cmd.approve() ? null : cmd.operatorUserId(),
                cmd.approve() ? null : "COMMITTEE_REVIEW");
        if (updated != 1) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANDIDATE_REVIEW_CONFLICT,
                    "候选人已被并发审查 candidateId=" + cmd.candidateId());
        }
        if (!cmd.approve()) {
            candidate.setRejectReasonCode(cmd.rejectReasonCode());
            candidate.setRejectEvidenceJson(cmd.rejectEvidenceJson());
            candidate.setRejectReviewerUserId(cmd.operatorUserId());
            candidate.setRejectReviewStage("COMMITTEE_REVIEW");
        }
        log.info("Candidate reviewed candidateId={} approve={} newStatus={} operatorUserId={}",
                cmd.candidateId(), cmd.approve(), candidate.getQualificationStatus(), cmd.operatorUserId());
        return candidate;
    }

    /**
     * 候选人列表（含所有状态）。
     */
    public List<Candidate> listCandidates(Long subjectId) {
        return electionCandidateRegistry.findBySubject(subjectId);
    }

    private void requireRole(String expectedRole, String message) {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !expectedRole.equals(ctx.roleKey())) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANDIDATE_REVIEW_FORBIDDEN,
                    message + "，当前角色=" + (ctx == null ? "ANONYMOUS" : ctx.roleKey()));
        }
    }

    /**
     * C 端可投候选人列表（仅 APPROVED）。业主投票前查看可投候选人。
     */
    public List<Candidate> listApprovedCandidates(Long subjectId) {
        return electionCandidateRegistry.findApprovedCandidates(subjectId);
    }

    /**
     * 提名候选人前按关键词检索业主，自动分流到「手机号 / 姓名」两种路径：
     * <ul>
     *   <li>关键词全数字 → 调 {@link PropertyGateway#searchOwnersByPhone}，
     *       SQL 模糊匹配前缀/中段/尾号，截 20 条；</li>
     *   <li>关键词含非数字字符 → 拉本租户业主池（≤ {@value #NAME_SEARCH_POOL_LIMIT}），
     *       逐条 {@link NameDecryptor} 解密 {@code real_name}，做 contains 过滤。</li>
     * </ul>
     *
     * <p>守卫：
     * 数字 keyword 不足 {@value #MIN_PHONE_FRAGMENT_LENGTH} 位 / 姓名 keyword 为空 → 返回空列表。
     * 命中结果统一截 {@value #NAME_SEARCH_RESULT_LIMIT} 条。
     */
    public List<OwnerSummary> searchNominatableOwners(String keyword, Long tenantId) {
        if (keyword == null) {
            return List.of();
        }
        String k = keyword.trim();
        if (k.isEmpty()) {
            return List.of();
        }
        if (isAllDigits(k)) {
            if (k.length() < MIN_PHONE_FRAGMENT_LENGTH) {
                return List.of();
            }
            return propertyGateway.searchOwnersByPhone(k, tenantId);
        }
        if (k.length() < MIN_NAME_KEYWORD_LENGTH) {
            return List.of();
        }
        return searchByName(k, tenantId);
    }

    /**
     * 姓名匹配：从本租户业主池逐条解密 real_name，做姓名 contains 过滤。
     */
    private List<OwnerSummary> searchByName(String keyword, Long tenantId) {
        List<OwnerSummary> pool = propertyGateway.listAllNominatableOwners(tenantId, NAME_SEARCH_POOL_LIMIT);
        return pool.stream()
                .map(this::decryptRealName)
                .filter(owner -> owner.getRealName() != null && owner.getRealName().contains(keyword))
                .limit(NAME_SEARCH_RESULT_LIMIT)
                .toList();
    }

    /** 用 NameDecryptor 把 OwnerSummary 的 SM4 密文 realName 替换为明文（密文非法则保留原值）。 */
    private OwnerSummary decryptRealName(OwnerSummary owner) {
        String decrypted = nameDecryptor.safeDecrypt(owner.getRealName());
        return OwnerSummary.builder()
                .uid(owner.getUid())
                .phone(owner.getPhone())
                .buildingId(owner.getBuildingId())
                .roomId(owner.getRoomId())
                .realName(decrypted)
                .build();
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
