// 关联业务：向费用承担业主生成楼栋维修在线表决任务，并将本人选择写入受控票仓。
package com.pangu.application.repair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.repair.command.SubmitOwnerRepairProjectDecisionVoteCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairProjectGovernance.OwnerDecisionTask;
import com.pangu.domain.model.repair.RepairVoteChoice;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import com.pangu.domain.repository.RepairProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;

@Service
@RequiredArgsConstructor
public class OwnerBuildingRepairDecisionService {

    private static final Set<RepairVoteChoice> ONLINE_CHOICES = Set.of(
            RepairVoteChoice.AGREE, RepairVoteChoice.DISAGREE, RepairVoteChoice.ABSTAIN);

    private final RepairProjectGovernanceRepository governanceRepository;
    private final RepairProjectRepository projectRepository;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<OwnerDecisionTask> listTasks() {
        UserContext owner = requireOwner();
        return governanceRepository.listOwnerDecisionTasks(owner.uid(), owner.tenantId());
    }

    @Transactional(readOnly = true)
    public OwnerDecisionTask task(Long decisionId) {
        UserContext owner = requireOwner();
        return governanceRepository.findOwnerDecisionTask(decisionId, owner.uid(), owner.tenantId())
                .orElseThrow(() -> notFound("在线表决不存在、已结束或当前业主不在费用承担范围内"));
    }

    @Transactional
    public OwnerDecisionTask submit(
            Long decisionId, SubmitOwnerRepairProjectDecisionVoteCommand command) {
        UserContext owner = requireOwner();
        if (command == null || command.roomId() == null || command.choice() == null) {
            throw invalid("roomId 和 choice 均为必填项");
        }
        RepairVoteChoice choice;
        try {
            choice = RepairVoteChoice.valueOf(command.choice().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw invalid("choice 取值不合法");
        }
        if (!ONLINE_CHOICES.contains(choice)) {
            throw invalid("在线表决仅支持同意、不同意或弃权");
        }
        OwnerDecisionTask task = governanceRepository.findOwnerDecisionTask(
                        decisionId, command.roomId(), owner.uid(), owner.tenantId())
                .orElseThrow(() -> notFound("在线表决不存在、已结束或房屋不在费用承担范围内"));
        // 一个业主名下多套房屋只形成一票；票的面积为该业主在本方案内全部房屋面积之和。
        governanceRepository.submitOwnerDecisionVote(
                task.decisionId(), owner.tenantId(), task.roomId(), owner.uid(), owner.accountId(),
                choice.name(), task.buildArea());
        insertEvent(task, owner, choice);
        return governanceRepository.findOwnerDecisionTask(
                        decisionId, owner.uid(), owner.tenantId())
                .orElseThrow(() -> notFound("在线表决已结束"));
    }

    private void insertEvent(OwnerDecisionTask task, UserContext owner, RepairVoteChoice choice) {
        try {
            projectRepository.insertOwnerEvent(
                    task.projectId(), owner.tenantId(), "OWNER_CAST_BUILDING_DECISION",
                    owner.accountId(), owner.uid(), objectMapper.writeValueAsString(Map.of(
                            "decisionId", task.decisionId(), "roomId", task.roomId(), "choice", choice.name())));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("楼栋维修在线表决事件序列化失败", ex);
        }
    }

    private UserContext requireOwner() {
        UserContext owner = userContextHolder.current();
        if (owner == null || !owner.isCUser() || owner.accountId() == null
                || owner.uid() == null || owner.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到当前小区业主身份");
        }
        return owner;
    }

    private RepairWorkOrderApplicationException invalid(String message) {
        return new RepairWorkOrderApplicationException(PARAM_INVALID, message);
    }

    private RepairWorkOrderApplicationException notFound(String message) {
        return new RepairWorkOrderApplicationException(NOT_FOUND, message);
    }
}
