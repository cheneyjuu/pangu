// 关联业务：统一维修工程应用服务的租户身份、项目锁、锁定方案、可选维修点位、附件和审计事件校验。
package com.pangu.application.repair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.WorkPoint;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.repository.RepairProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;

@Component
@RequiredArgsConstructor
class RepairProjectApplicationSupport {

    private final RepairProjectRepository projectRepository;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;

    UserContext requireSysActor(Set<String> roles, String message) {
        UserContext actor = requireActor();
        if (!actor.isSysUser() || actor.userId() == null || !roles.contains(actor.roleKey())) {
            throw forbidden(message);
        }
        return actor;
    }

    /**
     * 供应商组织可同时服务多个小区，其登录上下文不携带单一 tenant_id。
     * 具体租户必须由邀价或合同等业务凭证反查，不能由前端选择或全局默认值补齐。
     */
    UserContext requireGlobalSysActor(Set<String> roles, String message) {
        UserContext actor = userContextHolder.current();
        if (actor == null || actor.accountId() == null || !actor.isSysUser()
                || actor.userId() == null || !roles.contains(actor.roleKey())) {
            throw forbidden(message);
        }
        return actor;
    }

    UserContext requireOwnerActor() {
        UserContext actor = requireActor();
        if (!actor.isCUser() || actor.uid() == null) {
            throw forbidden("仅业主本人可提交受影响业主验收");
        }
        return actor;
    }

    UserContext requireActor() {
        UserContext actor = userContextHolder.current();
        if (actor == null || actor.accountId() == null || actor.tenantId() == null) {
            throw forbidden("未识别到当前小区身份");
        }
        return actor;
    }

    Context loadForUpdate(Long projectId, Long tenantId, Status... statuses) {
        RepairProject project = projectRepository.findProjectForUpdate(projectId, tenantId)
                .orElseThrow(() -> notFound("维修工程项目不存在"));
        requireStatus(project, statuses);
        return context(project);
    }

    Context load(Long projectId, Long tenantId) {
        RepairProject project = projectRepository.findProject(projectId, tenantId)
                .orElseThrow(() -> notFound("维修工程项目不存在"));
        return context(project);
    }

    Attachment attachment(Context context, Long attachmentId, String label) {
        if (attachmentId == null) {
            throw invalid(label + "必填");
        }
        return projectRepository.findAttachment(
                        attachmentId, context.project().projectId(), context.project().tenantId())
                .orElseThrow(() -> notFound(label + "不存在或不属于当前项目"));
    }

    void attachments(Context context, List<Long> attachmentIds, String label) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            throw invalid(label + "至少需要一个附件");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long attachmentId : attachmentIds) {
            if (attachmentId == null) {
                throw invalid(label + "不能包含空附件");
            }
            if (!uniqueIds.add(attachmentId)) {
                throw invalid(label + "不能包含重复附件");
            }
        }
        attachmentIds.forEach(id -> attachment(context, id, label));
    }

    /** 项目通用专业明细可不绑定点位；一旦填写点位，必须属于当前锁定方案。 */
    WorkPoint workPoint(Context context, Long workPointId) {
        if (workPointId == null) {
            return null;
        }
        return context.workPoints().stream()
                .filter(workPoint -> workPoint.workPointId().equals(workPointId))
                .findFirst()
                .orElseThrow(() -> invalid("维修点位不属于当前锁定方案 workPointId=" + workPointId));
    }

    void advance(Context context, Status nextStatus) {
        if (projectRepository.advanceStatus(
                context.project().projectId(), context.project().tenantId(), context.project().status(),
                nextStatus, context.project().version()) != 1) {
            throw conflict("项目状态或版本已变化，请刷新后重试");
        }
    }

    void event(Context context, UserContext actor, String action, Map<String, Object> payload) {
        try {
            projectRepository.insertEvent(
                    context.project().projectId(), context.project().tenantId(), action,
                    actor.accountId(), actor.userId(), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("维修工程事件序列化失败", ex);
        }
    }

    void ownerEvent(Context context, UserContext actor, String action, Map<String, Object> payload) {
        try {
            projectRepository.insertOwnerEvent(
                    context.project().projectId(), context.project().tenantId(), action,
                    actor.accountId(), actor.uid(), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("维修工程业主事件序列化失败", ex);
        }
    }

    String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("维修工程规则结果序列化失败", ex);
        }
    }

    RepairWorkOrderApplicationException invalid(String message) {
        return new RepairWorkOrderApplicationException(PARAM_INVALID, message);
    }

    RepairWorkOrderApplicationException conflict(String message) {
        return new RepairWorkOrderApplicationException(INVALID_STATUS, message);
    }

    RepairWorkOrderApplicationException notFound(String message) {
        return new RepairWorkOrderApplicationException(NOT_FOUND, message);
    }

    RepairWorkOrderApplicationException forbidden(String message) {
        return new RepairWorkOrderApplicationException(FORBIDDEN, message);
    }

    private Context context(RepairProject project) {
        PlanVersion plan = projectRepository.listPlans(project.projectId(), project.tenantId()).stream()
                .filter(candidate -> candidate.planId().equals(project.activePlanId())
                        && candidate.status() == PlanStatus.LOCKED)
                .findFirst()
                .orElseThrow(() -> conflict("项目没有有效的锁定实施方案"));
        List<WorkPoint> workPoints = projectRepository.listWorkPoints(plan.planId(), project.tenantId());
        return new Context(project, plan, workPoints);
    }

    private void requireStatus(RepairProject project, Status... statuses) {
        for (Status status : statuses) {
            if (project.status() == status) {
                return;
            }
        }
        throw conflict("当前项目状态不允许该动作 status=" + project.status());
    }

    record Context(RepairProject project, PlanVersion plan, List<WorkPoint> workPoints) {
        Context {
            workPoints = List.copyOf(workPoints);
        }
    }
}
