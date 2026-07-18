// 关联业务：把全小区维修项目的锁定方案关联到业主大会表决包中的具体事项。
package com.pangu.application.repair.command;

public record LinkCommunityRepairAssemblySubjectCommand(
        Integer expectedProjectVersion,
        Long packageId,
        Long subjectId
) {
}
