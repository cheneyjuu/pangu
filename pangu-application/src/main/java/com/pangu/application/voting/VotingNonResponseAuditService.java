// 关联业务：为有权管理人员提供未反馈票逐条认定依据，供计票复核和争议处理。
package com.pangu.application.voting;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.voting.VotingNonResponseDerivation;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VotingExecutionRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 未反馈表决票认定审计查询。
 *
 * <p>每条记录来自结算时的冻结名册、有效送达和生效规则；该查询不会重算或
 * 修改结果，也不把认定票伪装成业主实际提交的选票。
 */
@Service
@RequiredArgsConstructor
public class VotingNonResponseAuditService {

    private final VotingExecutionRepository executionRepository;
    private final VotingSubjectRepository subjectRepository;
    private final UserContextHolder userContextHolder;

    @Transactional(readOnly = true)
    public List<VotingNonResponseDerivation> list(Long subjectId) {
        UserContext actor = userContextHolder.current();
        if (actor == null || !actor.isSysUser() || actor.tenantId() == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "未识别到小区管理工作身份");
        }
        VotingSubject subject = subjectRepository.findById(subjectId)
                .filter(candidate -> actor.tenantId().equals(candidate.getTenantId()))
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "表决事项不存在或不属于当前小区"));
        return executionRepository.listNonResponseDerivations(
                subject.getSubjectId(), actor.tenantId());
    }
}
