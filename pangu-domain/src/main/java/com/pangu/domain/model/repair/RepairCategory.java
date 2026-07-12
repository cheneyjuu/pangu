package com.pangu.domain.model.repair;

import java.util.Locale;

/**
 * 维修专业分类。历史客户端代码在写入前统一归一化。
 */
public enum RepairCategory {
    PLUMBING,
    ELECTRICAL,
    ELEVATOR,
    FIRE_PROTECTION,
    WATERPROOFING,
    STRUCTURAL,
    ACCESS_CONTROL,
    PUBLIC_LIGHTING,
    ROAD,
    GREENING,
    SANITATION,
    DOOR_WINDOW,
    PUBLIC_AREA_FACILITY,
    OTHER;

    public static RepairCategory fromInput(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ELECTRIC" -> ELECTRICAL;
            case "FIRE" -> FIRE_PROTECTION;
            case "PUBLIC_PIPE" -> PLUMBING;
            case "WALL_LEAK" -> WATERPROOFING;
            case "PUBLIC_FACILITY" -> PUBLIC_AREA_FACILITY;
            default -> valueOf(normalized);
        };
    }
}
