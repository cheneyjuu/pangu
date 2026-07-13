// 关联业务：承载提交物业管理模式变更申请时的乐观锁版本号。
package com.pangu.application.community.command;

/**
 * 物业管理模式变更申请提交版本命令。
 */
public record PropertyManagementModeChangeVersionCommand(Integer expectedVersion) {
}
