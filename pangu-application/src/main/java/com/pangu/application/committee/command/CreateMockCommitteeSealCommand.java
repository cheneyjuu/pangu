// 关联业务：开发测试环境为当前业委会届期制发明确无效力的模拟电子印章。
package com.pangu.application.committee.command;

public record CreateMockCommitteeSealCommand(
        String sealName,
        String sealType
) {
}
