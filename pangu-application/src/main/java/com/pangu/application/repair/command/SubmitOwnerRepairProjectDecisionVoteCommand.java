// 关联业务：提交费用承担业主本人对楼栋维修锁定方案的 C 端在线表决。
package com.pangu.application.repair.command;

public record SubmitOwnerRepairProjectDecisionVoteCommand(
        Long roomId,
        String choice
) {
}
