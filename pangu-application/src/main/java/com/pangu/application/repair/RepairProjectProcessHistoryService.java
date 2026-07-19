// 关联业务：将维修工程项目不可变审计事件投影为管理端可读的办理历史，同时隔离个人业主表决明细。
package com.pangu.application.repair;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairProjectProcessEvent;
import com.pangu.domain.repository.RepairProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;

/**
 * 管理端维修工程办理历史查询。
 *
 * <p>项目事件表同时承载管理操作与业主个人操作。这里仅允许已定义的管理流程事件进入页面，
 * 因此不会因新增事件而意外暴露业主房屋、个人选择或原始审计载荷。</p>
 */
@Service
@RequiredArgsConstructor
public class RepairProjectProcessHistoryService {

    private static final Map<String, HistoryDescriptor> BUSINESS_HISTORY = Map.ofEntries(
            Map.entry("PROJECT_CREATED", new HistoryDescriptor("创建维修工程项目", "已生成项目和首版实施方案。")),
            Map.entry("PLAN_VERSION_CREATED", new HistoryDescriptor("新建实施方案版本", "已归档新的方案草稿版本。")),
            Map.entry("AFFECTED_OWNER_SCOPE_LOCKED", new HistoryDescriptor("确认受影响业主验收范围", "已确认楼栋维修验收范围和规则。")),
            Map.entry("PLAN_ATTACHMENT_LINKED", new HistoryDescriptor("归档实施方案材料", "已将材料关联到当前实施方案。")),
            Map.entry("RESPONSIBILITY_DETERMINATION_PROPOSED", new HistoryDescriptor("提交责任与费用初步意见", "物业已提交维修责任和拟使用费用来源，等待业委会确认。")),
            Map.entry("RESPONSIBILITY_DETERMINATION_CONFIRMED", new HistoryDescriptor("业委会确认责任与费用意见", "业委会已确认本次维修责任和拟使用费用来源；共有维修仍须取得相关业主同意。")),
            Map.entry("AUTHORIZATION_PROPOSAL_FROZEN", new HistoryDescriptor("提交实施方案", "已确定供相关业主审议的维修范围、预算和费用分摊范围。")),
            Map.entry("PROJECT_SUPPLIERS_INVITED", new HistoryDescriptor("发出前期询价", "已向已核验的施工单位发出本轮询价。")),
            Map.entry("PROJECT_QUOTE_REVISIONS_REQUESTED", new HistoryDescriptor("要求施工单位修订报价", "已发出报价修订要求。")),
            Map.entry("PROJECT_SUPPLIER_QUOTE_SUBMITTED", new HistoryDescriptor("收到施工单位报价", "施工单位报价原件已进入本轮报价对比。")),
            Map.entry("PROJECT_SUPPLIER_SELECTED", new HistoryDescriptor("记录拟选施工单位", "拟选施工单位已关联当前实施方案和报价。")),
            Map.entry("PROJECT_SUPPLIER_SELECTION_CONFIRMED", new HistoryDescriptor("确认施工单位", "业委会已依据相关业主表决结果和评审记录确认施工单位。")),
            Map.entry("PLAN_LOCKED", new HistoryDescriptor("确认实施方案", "相关业主决定、费用分摊和实施内容已确认，可以继续办理施工单位选择和合同。")),
            Map.entry("BUILDING_DECISION_STARTED", new HistoryDescriptor("发起相关业主表决", "已确定表决规则、送达方式和费用分摊范围。")),
            Map.entry("BUILDING_DECISION_COMPLETED", new HistoryDescriptor("完成业主征询核验", "物业已归档原始证据并核验征询结果。")),
            Map.entry("BUILDING_OFFICIAL_DOCUMENT_SUBMITTED", new HistoryDescriptor("提交物业正式报审文件", "正式报审文件已归档，进入业委会审价环节。")),
            Map.entry("BUILDING_PRICE_REVIEWED", new HistoryDescriptor("完成业委会审价", "审价结论和依据已归档。")),
            Map.entry("BUILDING_COMMITTEE_APPROVED", new HistoryDescriptor("主任或副主任在线确认", "业委会主任或副主任已完成授权确认。")),
            Map.entry("BUILDING_GOVERNANCE_AUTHORIZED", new HistoryDescriptor("完成业委会用印", "用印依据已归档，项目获授权签约。")),
            Map.entry("COMMUNITY_ASSEMBLY_SUBJECT_LINKED", new HistoryDescriptor("关联业主大会维修事项", "已关联正式业主大会表决包和维修事项。")),
            Map.entry("COMMUNITY_ASSEMBLY_SUBJECT_SETTLED", new HistoryDescriptor("写入业主大会表决结果", "正式计票结果已写入维修项目。")),
            Map.entry("PROJECT_COST_REVIEW_RECORDED", new HistoryDescriptor("完成工程审价", "审价金额和报告已归档。")),
            Map.entry("PROJECT_CONTRACT_EFFECTIVE", new HistoryDescriptor("登记生效施工合同", "合同签署材料已归档，施工合同已生效。")),
            Map.entry("PROJECT_WORK_STARTED", new HistoryDescriptor("登记开工", "工程已进入施工阶段。")),
            Map.entry("PROJECT_EXECUTION_RECORDED", new HistoryDescriptor("提交施工过程记录", "施工过程原始证据已归档，等待物业核验。")),
            Map.entry("PROJECT_EXECUTION_VERIFIED", new HistoryDescriptor("核验施工过程记录", "物业已完成施工过程记录核验。")),
            Map.entry("PROJECT_MATERIAL_SUBMITTED", new HistoryDescriptor("提交材料进场记录", "材料证明和现场资料已归档，等待物业核验。")),
            Map.entry("PROJECT_MATERIAL_VERIFIED", new HistoryDescriptor("核验材料进场记录", "物业已完成材料进场记录核验。")),
            Map.entry("PROJECT_SETTLEMENT_SUBMITTED", new HistoryDescriptor("提交竣工结算", "竣工结算原件已归档，等待物业核验。")),
            Map.entry("PROJECT_SETTLEMENT_VERIFIED", new HistoryDescriptor("核验竣工结算", "竣工结算核验结果已归档。")),
            Map.entry("PROJECT_BUILDING_LEADER_ACCEPTANCE", new HistoryDescriptor("楼组长提交验收结论", "楼组长验收结论已归档。")),
            Map.entry("PROJECT_COMMITTEE_EXECUTIVE_ACCEPTANCE", new HistoryDescriptor("业委会提交验收结论", "业委会验收结论已归档。")),
            Map.entry("PROJECT_PROPERTY_TECHNICAL_ACCEPTANCE", new HistoryDescriptor("物业提交专业验收结论", "物业专业验收结论已归档。")),
            Map.entry("PROJECT_THIRD_PARTY_TECHNICAL_ACCEPTANCE", new HistoryDescriptor("登记第三方专业验收", "第三方专业验收材料已归档。")),
            Map.entry("PROJECT_ACCEPTANCE_SEALED", new HistoryDescriptor("办理验收用印", "验收用印材料已归档。")),
            Map.entry("PROJECT_ACCEPTANCE_FINALIZED", new HistoryDescriptor("完成项目验收定案", "验收结果已形成项目定案。")),
            Map.entry("PROJECT_PAYMENT_REQUESTED", new HistoryDescriptor("提交付款申请", "付款申请和节点材料已归档。")),
            Map.entry("PROJECT_COMPLETION_DISCLOSED", new HistoryDescriptor("完成完工披露", "完工告示和质保责任期已归档。")),
            Map.entry("PROJECT_ARCHIVED", new HistoryDescriptor("归档维修工程项目", "质保责任期届满，项目已归档。"))
    );

    private final RepairProjectRepository projectRepository;
    private final UserContextHolder userContextHolder;

    @Transactional(readOnly = true)
    public List<RepairProjectProcessHistoryEntry> list(Long projectId) {
        UserContext actor = requireManagementActor();
        if (projectId == null) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "维修工程项目不存在");
        }
        projectRepository.findProject(projectId, actor.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "维修工程项目不存在"));
        return projectRepository.listProcessEvents(projectId, actor.tenantId()).stream()
                .map(this::toHistoryEntry)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private java.util.Optional<RepairProjectProcessHistoryEntry> toHistoryEntry(RepairProjectProcessEvent event) {
        HistoryDescriptor descriptor = BUSINESS_HISTORY.get(event.action());
        if (descriptor == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new RepairProjectProcessHistoryEntry(
                event.eventId(), descriptor.title(), descriptor.summary(), event.occurredAt()));
    }

    private UserContext requireManagementActor() {
        UserContext actor = userContextHolder.current();
        if (actor == null || !actor.isSysUser() || actor.accountId() == null
                || actor.userId() == null || actor.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到当前小区管理端工作身份");
        }
        return actor;
    }

    private record HistoryDescriptor(String title, String summary) {
    }
}
