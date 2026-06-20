package com.pangu.application.voting;

import com.pangu.application.voting.command.NominateCandidateCommand;
import com.pangu.application.voting.command.ReviewCandidateCommand;
import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.CandidateStatus;
import com.pangu.domain.model.voting.ElectionCandidateActions;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 选举候选人编排服务（M3-3 引入）。
 *
 * <p>三条命令：
 * <ul>
 *   <li>{@link #nominate} —— 管理端提名（status=PENDING_REVIEW）；仅 ELECTION 且议题处于
 *       DRAFT/PUBLISHED（投票开始前才能改名单）；</li>
 *   <li>{@link #review} —— G 端资格审查（PENDING_REVIEW → APPROVED/REJECTED）；</li>
 *   <li>{@link #listCandidates} —— 候选人列表（含所有状态）。</li>
 * </ul>
 *
 * <p>角色授权由 endpoint 的 {@code @PreAuthorize} 粗筛（candidate:nominate / candidate:approve /
 * voting:subject:audit），业务约束在此服务内强校验，分层与 {@link ProposalLifecycleService} 一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElectionCandidateService {

    private final VotingSubjectRepository subjectRepository;
    private final ElectionCandidateRegistry electionCandidateRegistry;

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

    /**
     * 资格审查：PENDING_REVIEW → APPROVED / REJECTED。
     */
    @Transactional
    public Candidate review(ReviewCandidateCommand cmd) {
        Candidate candidate = electionCandidateRegistry.findById(cmd.candidateId())
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.CANDIDATE_NOT_FOUND,
                        "候选人不存在 candidateId=" + cmd.candidateId()));
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
                candidate.getCandidateId(), candidate.getQualificationStatus().getDbValue());
        if (updated != 1) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANDIDATE_REVIEW_CONFLICT,
                    "候选人已被并发审查 candidateId=" + cmd.candidateId());
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

    /**
     * C 端可投候选人列表（仅 APPROVED）。业主投票前查看可投候选人。
     */
    public List<Candidate> listApprovedCandidates(Long subjectId) {
        return electionCandidateRegistry.findApprovedCandidates(subjectId);
    }
}
