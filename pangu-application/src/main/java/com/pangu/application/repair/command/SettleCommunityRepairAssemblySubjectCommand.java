// 关联业务：读取正式业主大会单个表决事项结算结果并推进全小区维修项目。
package com.pangu.application.repair.command;

public record SettleCommunityRepairAssemblySubjectCommand(
        Integer expectedProjectVersion
) {
}
