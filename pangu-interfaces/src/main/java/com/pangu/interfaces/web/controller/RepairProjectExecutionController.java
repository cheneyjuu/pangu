// 关联业务：暴露两类维修流程共用的合同、施工、材料、结算、验收、付款、披露和归档接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairProjectAcceptanceService;
import com.pangu.application.repair.RepairProjectExecutionService;
import com.pangu.application.repair.RepairProjectPaymentService;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceParty;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRound;
import com.pangu.domain.model.repair.RepairProjectExecution.CompletionDisclosure;
import com.pangu.domain.model.repair.RepairProjectExecution.Contract;
import com.pangu.domain.model.repair.RepairProjectExecution.CostReview;
import com.pangu.domain.model.repair.RepairProjectExecution.Details;
import com.pangu.domain.model.repair.RepairProjectExecution.ExecutionRecord;
import com.pangu.domain.model.repair.RepairProjectExecution.MaterialInspection;
import com.pangu.domain.model.repair.RepairProjectExecution.PaymentRequest;
import com.pangu.domain.model.repair.RepairProjectExecution.Settlement;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.AcceptanceFinalizationRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.AcceptancePartyRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.AcceptanceSealRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.ArchiveRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.CompletionDisclosureRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.ContractRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.CostReviewRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.ExecutionRecordRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.MaterialInspectionRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.SettlementRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.SettlementVerificationRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.StartWorkRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.VerificationRequest;
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
public class RepairProjectExecutionController extends BaseController {

    private final RepairProjectExecutionService executionService;
    private final RepairProjectAcceptanceService acceptanceService;
    private final RepairProjectPaymentService paymentService;

    @PostMapping("/cost-review")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<CostReview> recordCostReview(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody CostReviewRequest request) {
        return success("全小区维修审价已归档", executionService.recordCostReview(projectId, request.toCommand()));
    }

    @PostMapping("/contract")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<Contract> recordContract(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ContractRequest request) {
        return success("维修施工合同已生效", executionService.recordEffectiveContract(projectId, request.toCommand()));
    }

    @PostMapping("/execution/start")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field')")
    public Result<Contract> startWork(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody StartWorkRequest request) {
        return success("维修工程已开工", executionService.startWork(projectId, request.toCommand()));
    }

    @PostMapping("/execution-records")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field','repair:workorder:supplier')")
    public Result<ExecutionRecord> submitExecutionRecord(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ExecutionRecordRequest request) {
        return success("施工过程记录已提交", executionService.submitExecutionRecord(projectId, request.toCommand()));
    }

    @PostMapping("/execution-records/{recordId}/verification")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field')")
    public Result<ExecutionRecord> verifyExecutionRecord(
            @PathVariable("projectId") Long projectId,
            @PathVariable("recordId") Long recordId,
            @Valid @RequestBody VerificationRequest request) {
        return success("施工过程记录已核验",
                executionService.verifyExecutionRecord(projectId, recordId, request.toExecutionCommand()));
    }

    @PostMapping("/material-inspections")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field','repair:workorder:supplier')")
    public Result<MaterialInspection> submitMaterialInspection(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody MaterialInspectionRequest request) {
        return success("材料进场记录已提交",
                executionService.submitMaterialInspection(projectId, request.toCommand()));
    }

    @PostMapping("/material-inspections/{inspectionId}/verification")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field')")
    public Result<MaterialInspection> verifyMaterialInspection(
            @PathVariable("projectId") Long projectId,
            @PathVariable("inspectionId") Long inspectionId,
            @Valid @RequestBody VerificationRequest request) {
        return success("材料进场记录已核验",
                executionService.verifyMaterialInspection(projectId, inspectionId, request.toMaterialCommand()));
    }

    @PostMapping("/settlement")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field','repair:workorder:supplier')")
    public Result<Settlement> submitSettlement(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody SettlementRequest request) {
        return success("结构化竣工结算已提交", executionService.submitSettlement(projectId, request.toCommand()));
    }

    @PostMapping("/settlement/verification")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field')")
    public Result<Settlement> verifySettlement(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody SettlementVerificationRequest request) {
        return success("竣工结算已核验", executionService.verifySettlement(projectId, request.toCommand()));
    }

    @GetMapping("/execution")
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<Details> executionDetails(@PathVariable("projectId") Long projectId) {
        return success(executionService.details(projectId));
    }

    @PostMapping("/acceptance/building-leader")
    @PreAuthorize("hasAuthority('repair:workorder:local-decision')")
    public Result<AcceptanceParty> recordBuildingLeaderAcceptance(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody AcceptancePartyRequest request) {
        return success("楼组长验收意见已提交", acceptanceService.recordBuildingLeader(projectId, request.toCommand()));
    }

    @PostMapping("/acceptance/committee-executive")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<AcceptanceParty> recordCommitteeExecutiveAcceptance(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody AcceptancePartyRequest request) {
        return success("主任或副主任验收意见已提交",
                acceptanceService.recordCommitteeExecutive(projectId, request.toCommand()));
    }

    @PostMapping("/acceptance/property-technical")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field')")
    public Result<AcceptanceParty> recordPropertyTechnicalAcceptance(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody AcceptancePartyRequest request) {
        return success("物业专业签署已提交", acceptanceService.recordPropertyTechnical(projectId, request.toCommand()));
    }

    @PostMapping("/acceptance/third-party-technical")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:governance')")
    public Result<AcceptanceParty> recordThirdPartyTechnicalAcceptance(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody AcceptancePartyRequest request) {
        return success("第三方专业签署已归档", acceptanceService.recordThirdPartyTechnical(projectId, request.toCommand()));
    }

    @PostMapping("/acceptance/seal")
    @PreAuthorize("hasAuthority('repair:workorder:governance') and hasAuthority('committee:seal:use')")
    public Result<AcceptanceParty> sealAcceptance(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody AcceptanceSealRequest request) {
        return success("业委会验收用印已登记", acceptanceService.sealCommunityAcceptance(projectId, request.toCommand()));
    }

    @PostMapping("/acceptance/finalization")
    @PreAuthorize("hasAnyAuthority('repair:workorder:local-decision','repair:workorder:governance',"
            + "'repair:workorder:manage','repair:workorder:field')")
    public Result<AcceptanceRound> finalizeAcceptance(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody AcceptanceFinalizationRequest request) {
        return success("项目验收已定案", acceptanceService.finalizeAcceptance(projectId, request.toCommand()));
    }

    @PostMapping("/payment-requests")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<PaymentRequest> createPaymentRequest(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.PaymentRequest request) {
        return success("付款申请已进入财务处理", paymentService.createPaymentRequest(projectId, request.toCommand()));
    }

    @PostMapping("/completion-disclosure")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field')")
    public Result<CompletionDisclosure> createCompletionDisclosure(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody CompletionDisclosureRequest request) {
        return success("完工告示和物业书面报告已归档",
                acceptanceService.createCompletionDisclosure(projectId, request.toCommand()));
    }

    @PostMapping("/archive")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairProject> archive(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ArchiveRequest request) {
        return success("维修工程项目已归档", paymentService.archive(projectId, request.toCommand()));
    }
}
