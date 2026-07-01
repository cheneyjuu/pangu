package com.pangu.application.voting;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.notification.VotingReminderDeliveryStatus;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VotingReminderDeliveryQueryRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VotingReminderDeliveryQueryService {

    private final VotingSubjectRepository subjectRepository;
    private final VotingReminderDeliveryQueryRepository deliveryQueryRepository;
    private final UserContextHolder userContextHolder;

    @Transactional(readOnly = true)
    public List<VotingReminderDeliveryStatus> listBySubject(Long subjectId,
                                                            Long buildingId,
                                                            Integer deliveryStatus,
                                                            int limit) {
        UserContext ctx = requireSysUser();
        VotingSubject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> notFound(subjectId));
        if (!subject.getTenantId().equals(ctx.tenantId())) {
            throw notFound(subjectId);
        }
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return deliveryQueryRepository.listBySubject(
                ctx.tenantId(), subjectId, buildingId, deliveryStatus, safeLimit);
    }

    private UserContext requireSysUser() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isSysUser() || ctx.tenantId() == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "未识别到管理端 sys_user 上下文，禁止查询催票投递明细");
        }
        return ctx;
    }

    private VotingApplicationException notFound(Long subjectId) {
        return new VotingApplicationException(
                VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                "议题不存在 subjectId=" + subjectId);
    }
}
