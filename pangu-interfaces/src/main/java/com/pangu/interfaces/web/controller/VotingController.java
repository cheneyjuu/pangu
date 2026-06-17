package com.pangu.interfaces.web.controller;

import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.VotingApplicationService;
import com.pangu.domain.repository.VotingResultRepository;
import com.pangu.interfaces.web.controller.dto.voting.VotingResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 投票表决议题的查询入口。
 *
 * <p>设计动机：
 * <ul>
 *   <li>结算 ({@code settle}) 由后台调度器（{@code VotingDeadlineScheduler}）统一触发，
 *       <b>不</b>暴露 HTTP endpoint —— 防止外部绕过截止时间提前结算；</li>
 *   <li>本控制器仅暴露查询：议题已结算后通过 {@code GET /voting-subjects/{subjectId}/result}
 *       拉取结果快照（含司法链 stub 的 attestation_tx_hash）。</li>
 * </ul>
 *
 * <p>投票提交端点（{@code POST /api/v1/elections/{subjectId}/votes}）暂不在本期实现：
 * 计划中的 ABAC L3 + face-auth 校验链路依赖未完成的 vote-submission application service，
 * 推迟到下一个里程碑；本期 e2e 流程通过测试 fixture 直接 INSERT 投票数据驱动。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VotingController extends BaseController {

    private final VotingResultRepository votingResultRepository;
    @SuppressWarnings("unused") // 预留：后续 vote 提交场景注入
    private final VotingApplicationService votingApplicationService;

    /**
     * 获取议题的结算结果快照。议题尚未结算时返回 {@code SUBJECT_NOT_FOUND}（与不存在统一处理，
     * 避免暴露「议题存在但未结算」给未授权方做时序攻击）。
     */
    @GetMapping("/voting-subjects/{subjectId}/result")
    public Result<VotingResultResponse> findResult(@PathVariable("subjectId") Long subjectId) {
        return votingResultRepository.findBySubjectId(subjectId)
                .map(s -> success(VotingResultResponse.from(s)))
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题结果不存在或未结算 subjectId=" + subjectId));
    }
}
