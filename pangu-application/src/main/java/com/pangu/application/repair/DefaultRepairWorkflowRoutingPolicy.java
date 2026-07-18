// 关联业务：把楼栋专有维修资金与全小区公共维修资金严格路由到两个独立维修流程。
package com.pangu.application.repair;

import com.pangu.domain.model.repair.RepairProject.FundSource;
import com.pangu.domain.model.repair.RepairProject.GovernancePath;
import com.pangu.domain.model.repair.RepairProject.ScopeType;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.policy.RepairWorkflowRoutingPolicy;
import org.springframework.stereotype.Component;

@Component
public class DefaultRepairWorkflowRoutingPolicy implements RepairWorkflowRoutingPolicy {

    @Override
    public RoutingDecision route(RoutingInput input) {
        if (input == null || input.scopeType() == null
                || input.fundSource() == null || input.governancePath() == null) {
            return RoutingDecision.reject("维修范围、资金来源和治理依据均为必填项");
        }
        if (input.scopeType() == ScopeType.BUILDING || input.scopeType() == ScopeType.BUILDING_UNIT) {
            if (input.buildingId() == null) {
                return RoutingDecision.reject("楼栋或单元维修必须选择楼栋");
            }
            if (input.scopeType() == ScopeType.BUILDING_UNIT && isBlank(input.unitName())) {
                return RoutingDecision.reject("单元维修必须选择单元");
            }
            if (input.fundSource() != FundSource.BUILDING_MAINTENANCE_FUND
                    || input.governancePath() != GovernancePath.BUILDING_REPAIR_DECISION) {
                return RoutingDecision.reject("楼栋或单元维修只能使用对应楼栋维修资金并履行楼栋业主决定流程");
            }
            return RoutingDecision.allow(RepairWorkflowType.BUILDING_REPAIR);
        }
        if (input.scopeType() == ScopeType.COMMUNITY) {
            if (input.buildingId() != null || !isBlank(input.unitName())) {
                return RoutingDecision.reject("全小区公共区域维修不能绑定单一楼栋或单元");
            }
            if (input.fundSource() != FundSource.COMMUNITY_MAINTENANCE_FUND
                    || input.governancePath() != GovernancePath.COMMUNITY_ASSEMBLY_DECISION) {
                return RoutingDecision.reject("全小区公共区域维修只能使用小区公共维修资金并履行业主大会流程");
            }
            return RoutingDecision.allow(RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR);
        }
        return RoutingDecision.reject("不支持的维修范围");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
