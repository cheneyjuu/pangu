// 关联业务：依据锁定付款节点、工程事实和结构化材料生成维修项目付款申请，并在质保期满后归档。
package com.pangu.application.repair;

import com.pangu.application.repair.RepairProjectApplicationSupport.Context;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.ArchiveProject;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.CreatePaymentRequest;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestone;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestoneType;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRound;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceStatus;
import com.pangu.domain.model.repair.RepairProjectExecution.CompletionDisclosure;
import com.pangu.domain.model.repair.RepairProjectExecution.Contract;
import com.pangu.domain.model.repair.RepairProjectExecution.ContractSignature;
import com.pangu.domain.model.repair.RepairProjectExecution.CostReview;
import com.pangu.domain.model.repair.RepairProjectExecution.ExecutionRecord;
import com.pangu.domain.model.repair.RepairProjectExecution.PaymentEvidence;
import com.pangu.domain.model.repair.RepairProjectExecution.PaymentRequest;
import com.pangu.domain.model.repair.RepairProjectExecution.PaymentStatus;
import com.pangu.domain.model.repair.RepairProjectExecution.Settlement;
import com.pangu.domain.model.repair.RepairProjectExecution.SettlementStatus;
import com.pangu.domain.model.repair.RepairProjectExecution.VerificationStatus;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingProcess;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.policy.repair.RepairPaymentEligibilityPolicy;
import com.pangu.domain.repository.RepairProjectExecutionRepository;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RepairProjectPaymentService {

    private static final Set<String> PROPERTY_MANAGER_ROLE = Set.of("PROPERTY_MANAGER");
    private static final Pattern EVIDENCE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    private final RepairProjectApplicationSupport support;
    private final RepairProjectExecutionRepository executionRepository;
    private final RepairProjectGovernanceRepository governanceRepository;
    private final RepairPaymentEligibilityPolicy eligibilityPolicy = new RepairPaymentEligibilityPolicy.Default();

    @Transactional
    public PaymentRequest createPaymentRequest(Long projectId, CreatePaymentRequest command) {
        UserContext actor = support.requireSysActor(PROPERTY_MANAGER_ROLE, "仅物业经理可发起维修项目付款申请");
        require(command != null && command.milestoneType() != null, "milestoneType 必填");
        requirePositive(command.requestedAmount(), "requestedAmount");
        Context context = support.loadForUpdate(
                projectId, actor.tenantId(), Status.CONTRACT_EFFECTIVE, Status.IN_PROGRESS,
                Status.PENDING_ACCEPTANCE, Status.COMPLETED, Status.WARRANTY);
        Contract contract = executionRepository.findContract(projectId, actor.tenantId())
                .orElseThrow(() -> support.conflict("项目没有已生效施工合同"));
        PaymentMilestone milestone = context.plan().paymentMilestones().stream()
                .filter(candidate -> candidate.type() == command.milestoneType())
                .findFirst()
                .orElseThrow(() -> support.invalid("锁定实施方案没有该付款节点"));
        List<PaymentEvidence> evidence = evidence(context, command.evidence());
        ReviewFacts review = reviewFacts(context);
        assertTrustedEvidence(context, contract, milestone.type(), evidence, review);
        Settlement settlement = executionRepository.findActiveSettlement(projectId, actor.tenantId())
                .filter(candidate -> candidate.status() == SettlementStatus.VERIFIED)
                .orElse(null);
        CompletionDisclosure disclosure = executionRepository.findCompletionDisclosure(
                projectId, actor.tenantId()).orElse(null);
        BigDecimal alreadyRequested = executionRepository.sumActivePaymentRequests(
                projectId, contract.contractId(), actor.tenantId());
        RepairPaymentEligibilityPolicy.Decision decision = eligibilityPolicy.evaluate(
                new RepairPaymentEligibilityPolicy.Facts(
                        milestone, context.project().status(), contract.contractAmount(),
                        context.plan().priceReviewRequired(), review.reviewedAmount(),
                        settlement == null ? null : settlement.totalAmount(), alreadyRequested,
                        command.requestedAmount(), evidence.stream()
                        .map(PaymentEvidence::evidenceCode).collect(java.util.stream.Collectors.toSet()),
                        disclosure == null ? null : disclosure.warrantyEndDate(), LocalDate.now()));
        if (!decision.eligible()) {
            String missing = decision.missingCodes().isEmpty()
                    ? ""
                    : "，缺少材料=" + String.join(",", decision.missingCodes());
            throw support.invalid(decision.reason() + missing);
        }

        PaymentRequest created = executionRepository.insertPaymentRequest(new PaymentRequest(
                null, projectId, contract.contractId(), actor.tenantId(), milestone.type(),
                command.requestedAmount().setScale(2, java.math.RoundingMode.HALF_UP),
                decision.cumulativeAmount(), decision.upperLimit(), PaymentStatus.PENDING_FINANCE,
                evidence, actor.userId(), null), support.json(decision));
        executionRepository.insertPaymentEvidence(created.paymentRequestId(), actor.tenantId(), evidence);
        support.event(context, actor, "PROJECT_PAYMENT_REQUESTED", Map.of(
                "paymentRequestId", created.paymentRequestId(),
                "milestoneType", milestone.type().name(),
                "requestedAmount", created.requestedAmount(),
                "eligibleUpperLimit", decision.upperLimit()));
        return executionRepository.listPaymentRequests(projectId, actor.tenantId()).stream()
                .filter(request -> request.paymentRequestId().equals(created.paymentRequestId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public RepairProject archive(Long projectId, ArchiveProject command) {
        UserContext actor = support.requireSysActor(PROPERTY_MANAGER_ROLE, "仅物业经理可办理维修项目最终归档");
        require(command != null && command.expectedProjectVersion() != null,
                "expectedProjectVersion 必填");
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.WARRANTY);
        if (!context.project().version().equals(command.expectedProjectVersion())) {
            throw support.conflict("项目版本已变化，请刷新后重试");
        }
        CompletionDisclosure disclosure = executionRepository.findCompletionDisclosure(
                        projectId, actor.tenantId())
                .orElseThrow(() -> support.invalid("完工告示和物业书面维修报告未归档"));
        LocalDate today = LocalDate.now();
        if (today.isBefore(disclosure.noticeEndDate())) {
            throw support.invalid("完工告示期尚未结束，不能归档");
        }
        if (today.isBefore(disclosure.warrantyEndDate())) {
            throw support.invalid("质保责任期尚未届满，不能归档");
        }
        support.advance(context, Status.ARCHIVED);
        support.event(context, actor, "PROJECT_ARCHIVED", Map.of(
                "disclosureId", disclosure.disclosureId(),
                "warrantyEndDate", disclosure.warrantyEndDate().toString()));
        return support.load(projectId, actor.tenantId()).project();
    }

    private List<PaymentEvidence> evidence(
            Context context, List<CreatePaymentRequest.Evidence> submitted) {
        require(submitted != null && !submitted.isEmpty(), "付款申请必须提交结构化材料");
        Map<String, PaymentEvidence> byCode = new LinkedHashMap<>();
        for (CreatePaymentRequest.Evidence source : submitted) {
            require(source != null && source.evidenceCode() != null, "evidenceCode 必填");
            String code = source.evidenceCode().trim();
            if (!EVIDENCE_CODE.matcher(code).matches()) {
                throw support.invalid("evidenceCode 必须是大写英文标识");
            }
            support.attachment(context, source.attachmentId(), "付款材料 " + code);
            if (byCode.put(code, new PaymentEvidence(code, source.attachmentId())) != null) {
                throw support.invalid("付款材料代码不能重复 evidenceCode=" + code);
            }
        }
        return List.copyOf(byCode.values());
    }

    private void assertTrustedEvidence(
            Context context, Contract contract, PaymentMilestoneType milestoneType,
            List<PaymentEvidence> evidence, ReviewFacts review) {
        Map<String, Long> byCode = new LinkedHashMap<>();
        evidence.forEach(item -> byCode.put(item.evidenceCode(), item.attachmentId()));
        Set<Long> contractFiles = new LinkedHashSet<>();
        contractFiles.add(contract.contractAttachmentId());
        executionRepository.listContractSignatures(contract.contractId()).stream()
                .map(ContractSignature::signatureAttachmentId).forEach(contractFiles::add);
        assertKnownAttachment(byCode, "SIGNED_CONTRACT", contractFiles, "签署合同材料与生效合同不一致");
        Set<Long> reviewFiles = review.reportAttachmentId() == null
                ? Set.of()
                : Set.of(review.reportAttachmentId());
        assertKnownAttachment(byCode, "PRICE_REVIEW_REPORT", reviewFiles, "审价材料与项目审价事实不一致");

        List<ExecutionRecord> verifiedRecords = executionRepository.listExecutionRecords(
                        context.project().projectId(), context.project().tenantId()).stream()
                .filter(record -> record.verificationStatus() == VerificationStatus.VERIFIED)
                .toList();
        if (milestoneType == PaymentMilestoneType.PROGRESS && verifiedRecords.isEmpty()) {
            throw support.invalid("项目尚无物业核验通过的施工过程记录");
        }
        Set<Long> progressFiles = verifiedRecords.stream().flatMap(record -> record.attachmentIds().stream())
                .collect(java.util.stream.Collectors.toSet());
        assertKnownAttachment(byCode, "PROGRESS_RECORD", progressFiles, "进度款材料未引用已核验施工记录");

        if (milestoneType == PaymentMilestoneType.COMPLETION
                || milestoneType == PaymentMilestoneType.WARRANTY_RELEASE) {
            Settlement settlement = executionRepository.findActiveSettlement(
                            context.project().projectId(), context.project().tenantId())
                    .filter(candidate -> candidate.status() == SettlementStatus.VERIFIED)
                    .orElseThrow(() -> support.invalid("项目没有已核验的结构化竣工结算"));
            assertKnownAttachment(byCode, "SETTLEMENT", Set.of(settlement.settlementAttachmentId()),
                    "结算材料与已核验竣工结算不一致");
            AcceptanceRound acceptance = executionRepository.findLatestAcceptance(
                            context.project().projectId(), context.project().tenantId())
                    .filter(candidate -> candidate.status() == AcceptanceStatus.PASSED)
                    .orElseThrow(() -> support.invalid("项目尚未形成验收通过结论"));
            assertKnownAttachment(byCode, "ACCEPTANCE", Set.of(acceptance.resultAttachmentId()),
                    "验收材料未引用验收定案文件");
        }
    }

    private void assertKnownAttachment(
            Map<String, Long> byCode, String code, Set<Long> allowedAttachmentIds, String message) {
        Long attachmentId = byCode.get(code);
        if (attachmentId != null && !allowedAttachmentIds.contains(attachmentId)) {
            throw support.invalid(message);
        }
    }

    private ReviewFacts reviewFacts(Context context) {
        if (!context.plan().priceReviewRequired()) {
            return new ReviewFacts(null, null);
        }
        if (context.project().workflowType() == RepairWorkflowType.BUILDING_REPAIR) {
            BuildingProcess process = governanceRepository.findBuildingProcess(
                            context.project().projectId(), context.plan().planId(), context.project().tenantId())
                    .orElseThrow(() -> support.conflict("楼栋维修缺少有效审价事实"));
            return new ReviewFacts(process.reviewedAmount(), process.priceReviewReportAttachmentId());
        }
        CostReview review = executionRepository.findCostReview(
                        context.project().projectId(), context.plan().planId(), context.project().tenantId())
                .orElseThrow(() -> support.conflict("全小区维修缺少有效审价事实"));
        return new ReviewFacts(review.reviewedAmount(), review.reportAttachmentId());
    }

    private void requirePositive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw support.invalid(field + " 必须大于 0");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw support.invalid(message);
        }
    }

    private record ReviewFacts(BigDecimal reviewedAmount, Long reportAttachmentId) {
    }
}
