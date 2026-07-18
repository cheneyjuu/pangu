// 关联业务：持久化维修工程项目、单一决定范围、维修点位、可信资金切片、实施方案版本及项目附件。
package com.pangu.domain.repository;

import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProjectProcessEvent;
import com.pangu.domain.model.repair.RepairProject.AllocationRoom;
import com.pangu.domain.model.repair.RepairProject.AllocationBasis;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.DecisionScope;
import com.pangu.domain.model.repair.RepairProject.FundingSlice;
import com.pangu.domain.model.repair.RepairProject.EligibleAffectedOwner;
import com.pangu.domain.model.repair.RepairProject.PlanAttachment;
import com.pangu.domain.model.repair.RepairProject.PlanAffectedOwner;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProject.WorkPoint;

import java.util.List;
import java.util.Optional;

public interface RepairProjectRepository {

    RepairProject insertProject(RepairProject project);

    PlanVersion insertPlan(PlanVersion plan);

    DecisionScope insertDecisionScope(DecisionScope decisionScope);

    Optional<DecisionScope> findDecisionScope(Long projectId, Long tenantId);

    int updateDecisionScopeVerification(
            Long projectId,
            Long tenantId,
            RepairProject.DecisionScopeVerificationStatus verificationStatus,
            String verificationBasis);

    /**
     * 资金切片只能由可信账簿/责任认定/决定适配器写入；建项草稿只读查询其冻结前置条件。
     */
    List<FundingSlice> listFundingSlices(Long decisionScopeId, Long tenantId);

    WorkPoint insertWorkPoint(WorkPoint workPoint);

    void linkWorkPointToWorkOrder(Long workPointId, Long workOrderId, Long tenantId);

    List<AllocationRoom> snapshotAllocationRooms(
            Long planId, Long tenantId, RepairProject.ScopeType scopeType, Long buildingId, String unitName);

    Optional<AllocationBasis> findAllocationBasis(
            Long tenantId, RepairProject.ScopeType scopeType, Long buildingId, String unitName);

    /** 从已固化的方案费用承担房屋生成只读范围名称和统计，不读取项目端手工文本。 */
    Optional<AllocationBasis> findAllocationSnapshotBasis(Long planId, Long tenantId);

    void linkPlanAttachment(Long planId, PlanAttachment attachment);

    Optional<RepairProject> findProject(Long projectId, Long tenantId);

    /** 查找当前锁定方案中仍关联指定报修事项的工程项目。 */
    Optional<RepairProject> findProjectByActivePlanWorkOrder(Long workOrderId, Long tenantId);

    Optional<RepairProject> findProjectForUpdate(Long projectId, Long tenantId);

    List<RepairProject> listProjects(Long tenantId, Status status, String keyword, int offset, int limit);

    long countProjects(Long tenantId, Status status, String keyword);

    List<PlanVersion> listPlans(Long projectId, Long tenantId);

    Optional<PlanVersion> findPlanForUpdate(Long planId, Long projectId, Long tenantId);

    List<WorkPoint> listWorkPoints(Long planId, Long tenantId);

    List<AllocationRoom> listAllocationRooms(Long planId, Long tenantId);

    List<EligibleAffectedOwner> listEligibleAffectedOwners(
            Long tenantId, RepairProject.ScopeType scopeType, Long buildingId, String unitName);

    PlanAffectedOwner insertPlanAffectedOwner(PlanAffectedOwner affectedOwner);

    List<PlanAffectedOwner> listPlanAffectedOwners(Long planId, Long tenantId);

    List<Attachment> listAttachments(Long projectId, Long tenantId);

    Optional<Attachment> findAttachment(Long attachmentId, Long projectId, Long tenantId);

    List<PlanAttachment> listPlanAttachments(Long planId, Long tenantId);

    Attachment insertAttachment(Attachment attachment);

    int lockPlan(Long planId, Long projectId, Long tenantId, String snapshotHash, Long lockedByUserId);

    int supersedeLockedPlans(Long projectId, Long tenantId, Long exceptPlanId);

    int activatePlan(Long projectId, Long tenantId, Long planId, Integer expectedVersion);

    int advanceStatus(Long projectId, Long tenantId, Status expectedStatus,
                      Status nextStatus, Integer expectedVersion);

    void insertEvent(Long projectId, Long tenantId, String action,
                     Long actorAccountId, Long actorUserId, String payloadJson);

    void insertOwnerEvent(Long projectId, Long tenantId, String action,
                          Long actorAccountId, Long actorOwnerUid, String payloadJson);

    /** 查询管理端办理记录所需的最小化事件字段，不读取个人身份和原始审计载荷。 */
    List<RepairProjectProcessEvent> listProcessEvents(Long projectId, Long tenantId);
}
