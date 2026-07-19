// 关联业务：停止新增旧维修在线票、微信接龙和外部业主大会关联，只保留历史查询及既有流程收尾。
package com.pangu.application.repair;

import org.springframework.stereotype.Component;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;

/**
 * 维修表决切换闸门。
 *
 * <p>旧流程没有统一冻结名册、跨渠道唯一票和规则快照，因而不能继续创建新记录。
 * 已经存在的旧流程仍可通过原查询和后续收尾接口办理，避免把历史事实伪造迁移成新表决包。
 */
@Component
public class LegacyRepairVotingMigrationPolicy {

    public void rejectNewLegacyFlow() {
        throw new RepairWorkOrderApplicationException(
                INVALID_STATUS,
                "该维修表决入口已停止新增，请在维修工程项目中使用“相关业主表决”办理");
    }

    public void rejectLegacyRuleRegistration() {
        throw new RepairWorkOrderApplicationException(
                INVALID_STATUS,
                "旧维修表决依据已停止登记，请在社区设置中使用“业主大会议事规则”归档并逐项核对");
    }
}
