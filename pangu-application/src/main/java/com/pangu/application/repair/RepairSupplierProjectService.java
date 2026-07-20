// 关联业务：为施工单位工作台提供仅限本企业合同项目的维修点位、施工记录、材料和结算视图。
package com.pangu.application.repair;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.WorkPoint;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProjectExecution.Contract;
import com.pangu.domain.model.repair.RepairProjectExecution.Details;
import com.pangu.domain.repository.RepairProjectExecutionRepository;
import com.pangu.domain.repository.RepairProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RepairSupplierProjectService {

    private static final Set<String> SUPPLIER_ROLES = Set.of(
            "SERVICE_PROVIDER_MANAGER", "SERVICE_PROVIDER_STAFF");

    private final RepairProjectApplicationSupport support;
    private final RepairProjectRepository projectRepository;
    private final RepairProjectExecutionRepository executionRepository;
    private final RepairProjectExecutionService executionService;
    private final RepairNarrativeImageService narrativeImageService;

    @Transactional(readOnly = true)
    public List<SupplierProjectSummary> listAssignedProjects() {
        SupplierActor actor = supplierActor();
        return executionRepository.listSupplierContracts(actor.supplierDeptId()).stream()
                .map(contract -> summary(contract, actor))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierProjectDetails details(Long projectId) {
        SupplierActor actor = supplierActor();
        Contract contract = contract(projectId, actor);
        Long tenantId = contract.tenantId();
        RepairProject project = projectRepository.findProject(projectId, tenantId)
                .orElseThrow(() -> support.notFound("维修工程项目不存在"));
        PlanVersion storedPlan = projectRepository.listPlans(projectId, tenantId).stream()
                .filter(candidate -> candidate.planId().equals(project.activePlanId())
                        && candidate.status() == PlanStatus.LOCKED)
                .findFirst()
                .orElseThrow(() -> support.conflict("项目缺少已锁定实施方案"));
        PlanVersion plan = storedPlan.withPlanDescription(narrativeImageService.resolveForPlan(
                storedPlan.planId(), tenantId, storedPlan.planDescription()));
        return new SupplierProjectDetails(
                project,
                plan,
                projectRepository.listWorkPoints(plan.planId(), tenantId),
                projectRepository.listAttachments(projectId, tenantId),
                contract,
                executionService.details(projectId));
    }

    private SupplierProjectSummary summary(Contract contract, SupplierActor actor) {
        if (!actor.supplierDeptId().equals(contract.supplierDeptId())) {
            throw support.forbidden("当前施工单位没有该维修工程合同");
        }
        RepairProject project = projectRepository.findProject(contract.projectId(), contract.tenantId())
                .orElseThrow(() -> support.notFound("维修工程项目不存在"));
        return new SupplierProjectSummary(project, contract);
    }

    private Contract contract(Long projectId, SupplierActor actor) {
        return executionRepository.findSupplierContract(projectId, actor.supplierDeptId())
                .orElseThrow(() -> support.forbidden("当前施工单位没有该维修工程合同"));
    }

    private SupplierActor supplierActor() {
        UserContext actor = support.requireGlobalSysActor(
                SUPPLIER_ROLES, "仅施工单位账号可访问供应商工程工作台");
        if (actor.deptId() == null) {
            throw support.forbidden("当前施工单位账号未绑定企业组织");
        }
        return new SupplierActor(actor, actor.deptId());
    }

    public record SupplierProjectSummary(RepairProject project, Contract contract) {
    }

    public record SupplierProjectDetails(
            RepairProject project,
            PlanVersion activePlan,
            List<WorkPoint> workPoints,
            List<Attachment> attachments,
            Contract contract,
            Details execution
    ) {
        public SupplierProjectDetails {
            workPoints = workPoints == null ? List.of() : List.copyOf(workPoints);
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    private record SupplierActor(UserContext context, Long supplierDeptId) {
    }
}
