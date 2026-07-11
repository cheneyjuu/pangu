package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairLocationOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RepairLocationOptionsResponse(List<CommunityOption> communities) {

    public static RepairLocationOptionsResponse from(List<RepairLocationOption> rows) {
        Map<Long, CommunityOption> communities = new LinkedHashMap<>();
        for (RepairLocationOption row : rows) {
            CommunityOption community = communities.computeIfAbsent(row.tenantId(), id ->
                    new CommunityOption(id, row.communityName(), new ArrayList<>()));
            BuildingOption building = community.buildings().stream()
                    .filter(item -> Objects.equals(item.buildingId(), row.buildingId()))
                    .findFirst()
                    .orElseGet(() -> {
                        BuildingOption created = new BuildingOption(
                                row.buildingId(), row.buildingName(), new ArrayList<>());
                        community.buildings().add(created);
                        return created;
                    });
            UnitOption unit = building.units().stream()
                    .filter(item -> Objects.equals(item.unitName(), row.unitName()))
                    .findFirst()
                    .orElseGet(() -> {
                        UnitOption created = new UnitOption(row.unitName(), new ArrayList<>());
                        building.units().add(created);
                        return created;
                    });
            unit.rooms().add(new RoomOption(row.roomId(), row.roomName()));
        }
        return new RepairLocationOptionsResponse(new ArrayList<>(communities.values()));
    }

    public record CommunityOption(Long tenantId, String communityName, List<BuildingOption> buildings) {
    }

    public record BuildingOption(Long buildingId, String buildingName, List<UnitOption> units) {
    }

    public record UnitOption(String unitName, List<RoomOption> rooms) {
    }

    public record RoomOption(Long roomId, String roomName) {
    }
}
