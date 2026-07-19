// 关联业务：承载业主或物业登记公共区域报修时的位置、标题、问题描述与维修分类。
package com.pangu.application.repair.command;

public record CreatePublicRepairCommand(
        String publicAreaScope,
        Long buildingId,
        String locationText,
        String title,
        String description,
        String category
) {
}
