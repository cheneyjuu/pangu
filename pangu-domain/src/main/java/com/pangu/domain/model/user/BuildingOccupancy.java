package com.pangu.domain.model.user;

import java.util.List;

/**
 * 楼栋占用快照（楼栋分配页右栏占用展示用）。
 *
 * <p>列出某楼栋所有 {@code status=1} 的分配——含不同角色，因为业务允许「不同角色
 * 可共享同一楼栋」（如网格员 + 业主代表同管 30001）。前端按 {@code roleKey}
 * 分组展示，识别同角色冲突时弹「转移」确认。
 *
 * @param buildingId 楼栋 id
 * @param occupants  当前占用者列表
 */
public record BuildingOccupancy(
        Long buildingId,
        List<BuildingOccupant> occupants) {

    public BuildingOccupancy {
        occupants = occupants == null ? List.of() : List.copyOf(occupants);
    }
}
