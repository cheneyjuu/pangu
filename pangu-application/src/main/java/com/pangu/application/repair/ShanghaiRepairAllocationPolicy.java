// 关联业务：执行上海商品住宅专项维修资金的法定费用承担范围与面积分摊规则。
package com.pangu.application.repair;

import com.pangu.domain.model.repair.RepairProject.AllocationRuleType;
import com.pangu.domain.model.repair.RepairProject.FundSource;
import com.pangu.domain.model.repair.RepairProject.ScopeType;
import com.pangu.domain.policy.RepairAllocationPolicy;
import org.springframework.stereotype.Component;

@Component
public class ShanghaiRepairAllocationPolicy implements RepairAllocationPolicy {

    public static final String LEGAL_BASIS = "《上海市商品住宅专项维修资金管理办法》第十六条";

    @Override
    public AllocationDecision resolve(AllocationInput input) {
        if (input == null || input.scopeType() == null || input.fundSource() == null
                || input.scopeLabel() == null || input.scopeLabel().isBlank()) {
            return AllocationDecision.reject("维修范围、资金来源和费用承担范围均为必填项");
        }
        if (input.scopeType() == ScopeType.COMMUNITY
                && input.fundSource() != FundSource.COMMUNITY_MAINTENANCE_FUND) {
            return AllocationDecision.reject("全小区公共设施维修只能由小区公共维修资金对应的全体业主承担");
        }
        if (input.scopeType() != ScopeType.COMMUNITY
                && input.fundSource() != FundSource.BUILDING_MAINTENANCE_FUND) {
            return AllocationDecision.reject("楼栋或单元共用部位维修只能由对应范围的楼栋维修资金承担");
        }
        return AllocationDecision.support(
                AllocationRuleType.BY_BUILDING_AREA,
                "依据" + LEGAL_BASIS + "，维修费用由" + input.scopeLabel() + "范围内全体业主"
                        + "按各套住宅建筑面积比例共同承担",
                LEGAL_BASIS);
    }
}
