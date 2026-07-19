// 关联业务：承载业主登记私有空间报修时的标题、问题描述与维修分类。
package com.pangu.application.repair.command;

public record CreatePrivateRepairCommand(
        Long opid,
        String title,
        String description,
        String category
) {
}
