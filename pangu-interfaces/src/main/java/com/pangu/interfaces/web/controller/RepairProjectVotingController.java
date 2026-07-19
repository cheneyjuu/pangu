// 关联业务：向管理端暴露维修授权提案准备、开始、结算和查看相关业主正式表决的接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairProjectVotingService;
import com.pangu.application.repair.RepairProjectVotingChannelService;
import com.pangu.application.voting.PaperVotingService;
import com.pangu.interfaces.web.controller.dto.assembly.PaperBallotEntryResponse;
import com.pangu.interfaces.web.controller.dto.assembly.PaperBallotResponse;
import com.pangu.interfaces.web.controller.dto.assembly.PaperBallotReviewResponse;
import com.pangu.interfaces.web.controller.dto.assembly.PaperVotingDeliveryResponse;
import com.pangu.interfaces.web.controller.dto.assembly.ReviewPaperVotingRecordRequest;
import com.pangu.interfaces.web.controller.dto.assembly.SubmitAssemblyPaperBallotEntryRequest;
import com.pangu.interfaces.web.controller.dto.assembly.VoidAssemblyPaperBallotRequest;
import com.pangu.interfaces.web.controller.dto.repair.PrepareRepairProjectVotingRequest;
import com.pangu.interfaces.web.controller.dto.repair.RecordRepairVotingDeliveryRequest;
import com.pangu.interfaces.web.controller.dto.repair.RegisterRepairVotingPaperBallotRequest;
import com.pangu.interfaces.web.controller.dto.repair.TransitionRepairProjectVotingRequest;
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
@RequestMapping("/api/v1/admin/repair-projects/{projectId}/voting")
@RequiredArgsConstructor
public class RepairProjectVotingController extends BaseController {

    private final RepairProjectVotingService service;
    private final RepairProjectVotingChannelService channelService;

    @GetMapping("/preparation-options")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field','repair:workorder:governance')")
    public Result<RepairProjectVotingService.PreparationOptions> preparationOptions(
            @PathVariable("projectId") Long projectId) {
        return success(service.preparationOptions(projectId));
    }

    @PostMapping("/prepare")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairProjectVotingService.Details> prepare(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody PrepareRepairProjectVotingRequest request) {
        return success("相关业主表决安排已确认，方案、规则和表决人名册已冻结",
                service.prepare(projectId, request.toCommand()));
    }

    @PostMapping("/open")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairProjectVotingService.Details> open(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody TransitionRepairProjectVotingRequest request) {
        return success("相关业主表决已开始", service.open(projectId, request.toCommand()));
    }

    @PostMapping("/settle")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairProjectVotingService.Details> settle(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody TransitionRepairProjectVotingRequest request) {
        return success("相关业主表决已完成计票", service.settle(projectId, request.toCommand()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<RepairProjectVotingService.Details> details(
            @PathVariable("projectId") Long projectId) {
        return success(service.find(projectId));
    }

    @PostMapping("/paper-deliveries")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PaperVotingDeliveryResponse> recordPaperDelivery(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody RecordRepairVotingDeliveryRequest request) {
        return success("纸质材料送达情况已登记，等待另一名工作人员核对",
                PaperVotingDeliveryResponse.from(channelService.recordDelivery(projectId, request.toCommand())));
    }

    @PostMapping("/paper-deliveries/{deliveryId}/review")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PaperVotingDeliveryResponse> reviewPaperDelivery(
            @PathVariable("projectId") Long projectId,
            @PathVariable Long deliveryId,
            @Valid @RequestBody ReviewPaperVotingRecordRequest request) {
        return success("纸质材料送达情况已核对", PaperVotingDeliveryResponse.from(
                channelService.reviewDelivery(projectId, deliveryId,
                        new RepairProjectVotingChannelService.ReviewCommand(
                                request.decision(), request.reviewNote()))));
    }

    @PostMapping("/paper-ballots")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PaperBallotResponse> registerPaperBallot(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody RegisterRepairVotingPaperBallotRequest request) {
        return success("纸质表决票已登记，等待录入",
                PaperBallotResponse.from(channelService.registerBallot(projectId, request.toCommand())));
    }

    @PostMapping("/paper-ballots/{ballotId}/void")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PaperBallotResponse> voidPaperBallot(
            @PathVariable("projectId") Long projectId,
            @PathVariable Long ballotId,
            @Valid @RequestBody VoidAssemblyPaperBallotRequest request) {
        return success("纸质表决票登记已作废并保留记录",
                PaperBallotResponse.from(channelService.voidBallot(projectId, ballotId, request.reason())));
    }

    @PostMapping("/paper-ballots/{ballotId}/entries")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PaperBallotEntryResponse> submitPaperBallotEntry(
            @PathVariable("projectId") Long projectId,
            @PathVariable Long ballotId,
            @Valid @RequestBody SubmitAssemblyPaperBallotEntryRequest request) {
        return success("纸质表决票录入已提交，等待另一名工作人员核对",
                PaperBallotEntryResponse.from(channelService.submitEntry(
                        projectId, ballotId, request.toDomainItems())));
    }

    @PostMapping("/paper-ballots/{ballotId}/entries/{entryId}/review")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PaperBallotReviewResponse> reviewPaperBallotEntry(
            @PathVariable("projectId") Long projectId,
            @PathVariable Long ballotId,
            @PathVariable Long entryId,
            @Valid @RequestBody ReviewPaperVotingRecordRequest request) {
        PaperVotingService.BallotReviewResult result = channelService.reviewEntry(
                projectId, ballotId, entryId,
                new RepairProjectVotingChannelService.ReviewCommand(
                        request.decision(), request.reviewNote()));
        return success(request.decision() == PaperVotingService.ReviewDecision.CONFIRM
                        ? "纸质表决票已核对并完成处理" : "纸质表决票录入已退回",
                PaperBallotReviewResponse.from(result));
    }

    @GetMapping("/workbench")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<RepairProjectVotingChannelService.Workbench> workbench(
            @PathVariable("projectId") Long projectId) {
        return success(channelService.workbench(projectId));
    }
}
