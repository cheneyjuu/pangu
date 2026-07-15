// 关联业务：分别暴露楼栋维修接龙治理和全小区维修业主大会事项关联接口，禁止两类流程混用。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.BuildingRepairWorkflowService;
import com.pangu.application.repair.CommunityAssemblyRepairWorkflowService;
import com.pangu.domain.model.repair.RepairProjectGovernance.AssemblySubjectLink;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingProcessDetails;
import com.pangu.interfaces.web.controller.dto.repair.ApproveBuildingRepairRequest;
import com.pangu.interfaces.web.controller.dto.repair.CompleteBuildingRepairDecisionRequest;
import com.pangu.interfaces.web.controller.dto.repair.LinkCommunityRepairAssemblySubjectRequest;
import com.pangu.interfaces.web.controller.dto.repair.ReviewBuildingRepairPriceRequest;
import com.pangu.interfaces.web.controller.dto.repair.SealBuildingRepairRequest;
import com.pangu.interfaces.web.controller.dto.repair.SettleCommunityRepairAssemblySubjectRequest;
import com.pangu.interfaces.web.controller.dto.repair.StartBuildingRepairDecisionRequest;
import com.pangu.interfaces.web.controller.dto.repair.SubmitBuildingRepairOfficialDocumentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/repair-projects/{projectId}")
@RequiredArgsConstructor
public class RepairProjectGovernanceController extends BaseController {

    private final BuildingRepairWorkflowService buildingWorkflowService;
    private final CommunityAssemblyRepairWorkflowService communityWorkflowService;

    @PostMapping("/building-governance/start")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field','repair:workorder:local-decision')")
    public Result<BuildingProcessDetails> startBuildingDecision(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody StartBuildingRepairDecisionRequest request) {
        return success("楼栋维修征询已发起",
                buildingWorkflowService.startDecision(projectId, request.toCommand()));
    }

    @PostMapping("/building-governance/decision/complete")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field','repair:workorder:local-decision')")
    public Result<BuildingProcessDetails> completeBuildingDecision(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody CompleteBuildingRepairDecisionRequest request) {
        return success("楼栋维修征询已核验",
                buildingWorkflowService.completeDecision(projectId, request.toCommand()));
    }

    @PostMapping("/building-governance/official-document")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field')")
    public Result<BuildingProcessDetails> submitBuildingOfficialDocument(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody SubmitBuildingRepairOfficialDocumentRequest request) {
        return success("物业正式报审文件已归档",
                buildingWorkflowService.submitOfficialDocument(projectId, request.toCommand()));
    }

    @PostMapping("/building-governance/price-review")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<BuildingProcessDetails> reviewBuildingPrice(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ReviewBuildingRepairPriceRequest request) {
        return success("楼栋维修审价已完成",
                buildingWorkflowService.reviewPrice(projectId, request.toCommand()));
    }

    @PostMapping("/building-governance/committee-approval")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<BuildingProcessDetails> approveBuildingRepair(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ApproveBuildingRepairRequest request) {
        return success("楼栋维修已由主任或副主任在线确认",
                buildingWorkflowService.approve(projectId, request.toCommand()));
    }

    @PostMapping("/building-governance/seal")
    @PreAuthorize("hasAuthority('repair:workorder:governance') and hasAuthority('committee:seal:use')")
    public Result<BuildingProcessDetails> sealBuildingRepair(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody SealBuildingRepairRequest request) {
        return success("楼栋维修正式文件用印已登记",
                buildingWorkflowService.seal(projectId, request.toCommand()));
    }

    @GetMapping("/building-governance")
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<BuildingProcessDetails> buildingGovernance(
            @PathVariable("projectId") Long projectId) {
        return success(buildingWorkflowService.find(projectId));
    }

    @PostMapping("/community-assembly/link")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<AssemblySubjectLink> linkCommunityAssemblySubject(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody LinkCommunityRepairAssemblySubjectRequest request) {
        return success("全小区维修已关联业主大会表决事项",
                communityWorkflowService.linkSubject(projectId, request.toCommand()));
    }

    @PostMapping("/community-assembly/settle")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<AssemblySubjectLink> settleCommunityAssemblySubject(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody SettleCommunityRepairAssemblySubjectRequest request) {
        return success("业主大会表决事项结果已写入维修项目",
                communityWorkflowService.settleSubject(projectId, request.toCommand()));
    }

    @GetMapping("/community-assembly")
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<AssemblySubjectLink> communityAssembly(
            @PathVariable("projectId") Long projectId) {
        return success(communityWorkflowService.find(projectId));
    }
}
