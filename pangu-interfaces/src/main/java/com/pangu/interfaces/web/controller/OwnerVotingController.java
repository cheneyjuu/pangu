package com.pangu.interfaces.web.controller;

import com.pangu.application.voting.ElectionCandidateService;
import com.pangu.application.voting.ProposalLifecycleService;
import com.pangu.application.voting.VoteSubmissionService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.VotingProgressQueryService;
import com.pangu.application.voting.command.CastVoteCommand;
import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingProgress;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.domain.repository.VoteItemRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.voting.CandidateResponse;
import com.pangu.interfaces.web.controller.dto.voting.CastVoteRequest;
import com.pangu.interfaces.web.controller.dto.voting.OwnerSubjectResponse;
import com.pangu.interfaces.web.controller.dto.voting.SubjectProgressResponse;
import com.pangu.interfaces.web.controller.dto.voting.VoteAcknowledgement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * C 端业主投票端点（M3-2 引入）。
 *
 * <p>{@code c_user} 没有 sys_role 链路，因此 endpoint 一律使用
 * {@code @PreAuthorize("isAuthenticated()")}（沿用 M2-3 异议救济的降级方案）；
 * 真正的 ABAC 校验（L3 face-auth / opid 归属 / scope 范围 / 重复投票）由
 * {@link VoteSubmissionService} 与 {@link ProposalLifecycleService#findVisibleForOwner} 在 service 层执行。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class OwnerVotingController extends BaseController {

    private final ProposalLifecycleService proposalLifecycleService;
    private final VoteSubmissionService voteSubmissionService;
    private final VotingSubjectRepository votingSubjectRepository;
    private final VoteItemRepository voteItemRepository;
    private final OwnerPropertyVotingRepository ownerPropertyVotingRepository;
    private final ElectionCandidateService electionCandidateService;
    private final VotingProgressQueryService votingProgressQueryService;

    /** "我的议题"列表：按 building 范围 + 状态过滤。 */
    @GetMapping("/voting-subjects")
    @PreAuthorize("isAuthenticated()")
    public Result<List<OwnerSubjectResponse>> listMine(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Long uid = requireUid();
        Long tenantId = requireTenantId();
        List<VotingSubject> subjects = proposalLifecycleService.findVisibleForOwner(uid, tenantId, page, size);
        return success(subjects.stream().map(OwnerSubjectResponse::from).toList());
    }

    /**
     * "我的议题"详情。返回议题快照 + 当前 uid 是否已投（不返回票数）。
     *
     * <p>可见性校验：tenant 匹配 + 状态 ∈ {PUBLISHED, VOTING, CLOSED, SETTLED}
     * + scope=COMMUNITY 或业主名下楼栋。
     */
    @GetMapping("/voting-subjects/{subjectId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> findMyDetail(
            @PathVariable("subjectId") Long subjectId) {
        Long uid = requireUid();
        Long tenantId = requireTenantId();
        VotingSubject subject = assertSubjectVisibleForOwner(subjectId, uid, tenantId);
        boolean voted = voteItemRepository.findValidVotes(subjectId).stream()
                .map(VoteItem::getUid)
                .anyMatch(uid::equals);
        return success(Map.of(
                "subject", OwnerSubjectResponse.from(subject),
                "voted", voted));
    }

    /**
     * "我的议题"可投候选人列表（仅 ELECTION，且仅返回 APPROVED 候选人）。
     *
     * <p>业主投票前查看可投候选人。可见性校验复用 {@link #findMyDetail} 的口径
     * （tenant 匹配 + 状态可见 + scope 范围），不暴露未通过资格审查的候选人。
     */
    @GetMapping("/voting-subjects/{subjectId}/candidates")
    @PreAuthorize("isAuthenticated()")
    public Result<List<CandidateResponse>> listCandidatesForOwner(
            @PathVariable("subjectId") Long subjectId) {
        Long uid = requireUid();
        Long tenantId = requireTenantId();
        assertSubjectVisibleForOwner(subjectId, uid, tenantId);
        List<Candidate> candidates = electionCandidateService.listApprovedCandidates(subjectId);
        return success(candidates.stream().map(CandidateResponse::from).toList());
    }

    /**
     * 业主侧议题进度查询（双过半实时进度 / 法定结算快照）。
     *
     * <p>与管理端 {@code SubjectAdminController.progress} 同口径，但鉴权为
     * {@code isAuthenticated()}——业主无 {@code voting:subject:audit} 权限，
     * 通过 {@link #assertSubjectVisibleForOwner} 校验议题对该业主可见即可放行。
     */
    @GetMapping("/voting-subjects/{subjectId}/progress")
    @PreAuthorize("isAuthenticated()")
    public Result<SubjectProgressResponse> progressForOwner(@PathVariable("subjectId") Long subjectId) {
        Long uid = requireUid();
        Long tenantId = requireTenantId();
        assertSubjectVisibleForOwner(subjectId, uid, tenantId);
        VotingProgress progress = votingProgressQueryService.queryProgress(subjectId, tenantId);
        return success(SubjectProgressResponse.from(progress));
    }

    /** 业主投票提交。返回 voteId + voted=true，刻意不返回当前票数。 */
    @PostMapping("/voting-subjects/{subjectId}/votes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<VoteAcknowledgement>> cast(
            @PathVariable("subjectId") Long subjectId,
            @Valid @RequestBody CastVoteRequest request) {
        CastVoteCommand cmd = new CastVoteCommand(
                subjectId,
                requireUid(),
                requireTenantId(),
                request.opid(),
                request.targetId(),
                request.choice(),
                request.signatureHash(),
                request.voteChannel());
        long voteId = voteSubmissionService.cast(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("投票成功", VoteAcknowledgement.of(voteId)));
    }

    /**
     * 业主视角议题可见性校验：tenant 匹配 + 状态 ∈ {PUBLISHED, VOTING, CLOSED, SETTLED}
     * + scope=COMMUNITY 或业主名下楼栋。所有失败统一归为 SUBJECT_NOT_FOUND，避免暴露跨租户存在性。
     */
    private VotingSubject assertSubjectVisibleForOwner(Long subjectId, Long uid, Long tenantId) {
        VotingSubject subject = votingSubjectRepository.findById(subjectId)
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + subjectId));
        if (!subject.getTenantId().equals(tenantId)) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                    "议题不在当前租户范围内 subjectId=" + subjectId);
        }
        SubjectStatus status = subject.getStatus();
        if (status != SubjectStatus.PUBLISHED && status != SubjectStatus.VOTING
                && status != SubjectStatus.CLOSED && status != SubjectStatus.SETTLED) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                    "议题尚未公示或已撤回 subjectId=" + subjectId);
        }
        if (subject.getScope() == VotingScope.BUILDING) {
            List<Long> buildingIds = ownerPropertyVotingRepository.findBuildingIdsByUid(uid, tenantId);
            if (!buildingIds.contains(subject.getScopeReferenceId())) {
                throw new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不在业主所属楼栋范围内 subjectId=" + subjectId);
            }
        }
        return subject;
    }

    private Long requireUid() {
        Long uid = SecurityUtils.getUid();
        if (uid == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.OPID_NOT_OWNED,
                    "未识别到业主身份，禁止访问该操作");
        }
        return uid;
    }

    private Long requireTenantId() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.OPID_OUT_OF_SCOPE,
                    "未识别到租户上下文，禁止访问该操作");
        }
        return tenantId;
    }
}
