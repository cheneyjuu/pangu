// 关联业务：提供业主表决议题的结算结果查询。
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
 * <p>投票提交由业主端控制器和 application service 承担。本控制器不承载提交逻辑，
 * 避免结果查询与身份、专有部分资格及重复票校验混在同一接口边界。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VotingController extends BaseController {

    private final VotingResultRepository votingResultRepository;
    @SuppressWarnings("unused") // 保留既有应用服务依赖，供结果查询编排扩展使用
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
