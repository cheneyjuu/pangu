package com.pangu.application.voting;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.voting.ReminderChannel;
import com.pangu.domain.model.voting.ReminderPendingOwner;
import com.pangu.domain.model.voting.ReminderTask;
import com.pangu.domain.repository.VotingReminderTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VotingReminderTaskService {

    private final VotingReminderTaskRepository repository;
    private final UserContextHolder userContextHolder;

    public List<ReminderTask> listTasks() {
        UserContext ctx = requireSysUser("查询催票任务");
        return repository.listTasks(ctx.tenantId(), ctx.userId());
    }

    public List<ReminderPendingOwner> listPendingOwners(Long subjectId) {
        UserContext ctx = requireSysUser("查询待催票业主");
        return repository.listPendingOwners(ctx.tenantId(), ctx.userId(), subjectId);
    }

    @Transactional
    public void markNotified(Long subjectId, Long uid, ReminderChannel channel, String note) {
        UserContext ctx = requireSysUser("标记催票通知");
        if (subjectId == null || uid == null || channel == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "标记催票通知必须指定议题、业主与渠道");
        }
        int updated = repository.markNotified(
                ctx.tenantId(),
                ctx.userId(),
                subjectId,
                uid,
                channel,
                normalize(note));
        if (updated == 0) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "当前用户没有该业主所在楼栋的催票权限，或业主已不在待催票范围 uid=" + uid);
        }
    }

    private UserContext requireSysUser(String action) {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isSysUser() || ctx.userId() == null || ctx.tenantId() == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "未识别到管理端 sys_user 上下文，禁止" + action);
        }
        return ctx;
    }

    private String normalize(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.strip();
    }
}
