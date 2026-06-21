package com.pangu.interfaces.web.controller;

import com.pangu.application.voting.ProposalLifecycleService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.VotingProgressQueryService;
import com.pangu.application.voting.command.CancelSubjectCommand;
import com.pangu.application.voting.command.ProposeSubjectCommand;
import com.pangu.application.voting.command.PublishSubjectCommand;
import com.pangu.domain.common.Page;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingProgress;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VoteDetailQueryRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.PageResponse;
import com.pangu.interfaces.web.controller.dto.voting.AdminSubjectResponse;
import com.pangu.interfaces.web.controller.dto.voting.CancelRequest;
import com.pangu.interfaces.web.controller.dto.voting.ProposeRequest;
import com.pangu.interfaces.web.controller.dto.voting.SubjectProgressResponse;
import com.pangu.interfaces.web.controller.dto.voting.VoteDetailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * B/G 端议题生命周期管理 endpoint（M3-2 引入）。
 *
 * <p>权限路由：
 * <ul>
 *   <li>{@code POST /api/v1/voting-subjects}        —— {@code voting:subject:create}（街道办 / 业委主任 / 物业经理）</li>
 *   <li>{@code POST /api/v1/voting-subjects/{id}/publish} —— {@code voting:subject:publish}</li>
 *   <li>{@code POST /api/v1/voting-subjects/{id}/cancel}  —— DRAFT 阶段：发起者本人即可（{@code voting:subject:create}）；
 *                                                         PUBLISHED 阶段：必须 {@code voting:subject:cancel}（街道办独占）</li>
 *   <li>{@code GET  /api/v1/voting-subjects/{id}}    —— {@code voting:subject:audit}（管理端工作台）</li>
 * </ul>
 *
 * <p>M3-3 起 SubjectType=ELECTION 放开（须携带 maxWinners）；endpoint 上的 @PreAuthorize 只做粗筛，
 * 类型与角色的最终匹配由 service 层依据 {@code voting:subject:create} 角色绑定判定。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubjectAdminController extends BaseController {

    private final ProposalLifecycleService proposalLifecycleService;
    private final VotingSubjectRepository votingSubjectRepository;
    private final VotingProgressQueryService votingProgressQueryService;

    /** 立项：DRAFT 写入。 */
    @PostMapping("/voting-subjects")
    @PreAuthorize("hasAuthority('voting:subject:create')")
    public ResponseEntity<Result<AdminSubjectResponse>> propose(
            @Valid @RequestBody ProposeRequest request) {
        ProposeSubjectCommand cmd = new ProposeSubjectCommand(
                requireTenantId(),
                request.subjectType(),
                request.scope(),
                request.scopeReferenceId(),
                request.title(),
                request.voteStartAt(),
                request.voteEndAt(),
                requireUserId(),
                request.partyRatioFloor(),
                request.maxWinners());
        VotingSubject draft = proposalLifecycleService.propose(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("议题已草稿落库，待公示", AdminSubjectResponse.from(draft)));
    }

    /** 公示：DRAFT → PUBLISHED。 */
    @PostMapping("/voting-subjects/{subjectId}/publish")
    @PreAuthorize("hasAuthority('voting:subject:publish')")
    public Result<AdminSubjectResponse> publish(@PathVariable("subjectId") Long subjectId) {
        VotingSubject subject = proposalLifecycleService.publish(
                new PublishSubjectCommand(subjectId, requireUserId()));
        return success("议题已公示", AdminSubjectResponse.from(subject));
    }

    /**
     * 撤回。
     *
     * <p>授权策略：DRAFT 阶段允许 {@code voting:subject:create} 持有者撤回，service 层会
     * 校验 currentUserId == proposedByUserId；PUBLISHED 阶段只允许 {@code voting:subject:cancel}
     * （街道办强撤）。endpoint 用 {@code isAuthenticated()} 兜底，由 service 层根据 byGovernment
     * 标志 + 角色判定准入。
     */
    @PostMapping("/voting-subjects/{subjectId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Result<AdminSubjectResponse> cancel(
            @PathVariable("subjectId") Long subjectId,
            @Valid @RequestBody CancelRequest request) {
        boolean byGovernment = hasAuthority("voting:subject:cancel");
        boolean canCreate = hasAuthority("voting:subject:create");
        if (!byGovernment && !canCreate) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANCEL_FORBIDDEN,
                    "缺少撤回权限：需 voting:subject:cancel 或 voting:subject:create");
        }
        CancelSubjectCommand cmd = new CancelSubjectCommand(
                subjectId, requireUserId(), request.reason(), byGovernment);
        VotingSubject subject = proposalLifecycleService.cancel(cmd);
        return success("议题已撤回", AdminSubjectResponse.from(subject));
    }

    /**
     * 管理端议题分页列表（M4-1）。
     *
     * <p>租户内分页查询，可选 {@code status}/{@code type} 筛选（按枚举 name 传参，如 {@code ?status=VOTING}）。
     * 返回统一分页契约 {@code { items, total, page, size }}。
     */
    @GetMapping("/voting-subjects")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PageResponse<AdminSubjectResponse>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) SubjectStatus status,
            @RequestParam(name = "type", required = false) SubjectType type) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<VotingSubject> result = votingSubjectRepository.pageForAdmin(
                requireTenantId(), status, type, safePage, safeSize);
        return success(PageResponse.from(result, AdminSubjectResponse::from));
    }

    /** 管理端议题详情（B/G 端）。 */
    @GetMapping("/voting-subjects/{subjectId}")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<AdminSubjectResponse> findAdminDetail(
            @PathVariable("subjectId") Long subjectId) {
        VotingSubject subject = votingSubjectRepository.findById(subjectId)
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + subjectId));
        return success(AdminSubjectResponse.from(subject));
    }

    /**
     * 议题双过半进度（M4-2）。
     *
     * <p>VOTING/CLOSED 等未结算态返回实时进度（与结算口径一致但非法定值）；
     * SETTLED 态返回法定结算快照。{@code support*} 在 SETTLED 态为 null。
     */
    @GetMapping("/voting-subjects/{subjectId}/progress")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<SubjectProgressResponse> progress(@PathVariable("subjectId") Long subjectId) {
        VotingProgress progress = votingProgressQueryService.queryProgress(subjectId, requireTenantId());
        return success(SubjectProgressResponse.from(progress));
    }

    /**
     * 逐户投票明细分页（M4-2）。
     *
     * <p>以分母范围内应投房产全量铺开，未投房产以 {@code voted=false} 出现，
     * 返回统一分页契约 {@code { items, total, page, size }}。
     */
    @GetMapping("/voting-subjects/{subjectId}/vote-details")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PageResponse<VoteDetailResponse>> voteDetails(
            @PathVariable("subjectId") Long subjectId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<VoteDetailQueryRepository.VoteDetailRow> result =
                votingProgressQueryService.pageVoteDetails(subjectId, requireTenantId(), page, size);
        return success(PageResponse.from(result, VoteDetailResponse::from));
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "未识别到 sys_user 上下文，禁止访问该操作");
        }
        return userId;
    }

    private Long requireTenantId() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "未识别到租户上下文，禁止访问该操作");
        }
        return tenantId;
    }

    private boolean hasAuthority(String key) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> key.equals(a.getAuthority()));
    }
}
