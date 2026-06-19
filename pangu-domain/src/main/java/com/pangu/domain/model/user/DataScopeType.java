package com.pangu.domain.model.user;

/**
 * 行级数据权限范围（M1 RBAC 重构后简化为三枚举）。
 *
 * <p>详见 {@code M1权限体系重构设计.md} §5.1 三枚举语义：
 * <ul>
 *   <li>{@link #ALL_COMMUNITY}  社区全量视野（业委会主任、街道办、党组织书记）</li>
 *   <li>{@link #OWNER_GROUP}    业主集合（业主代表 / 网格员 / 志愿者，
 *                                以 {@code sys_user_building} 为权威反查楼栋）</li>
 *   <li>{@link #ORG_ONLY}       仅本部门（物业 / 服务商：只看自家数据）</li>
 * </ul>
 *
 * <p>取消 V1 时期的 6 枚举（ALL/CUSTOM_DEPT/OWN_DEPT/OWN_DEPT_AND_CHILD/SELF/CUSTOM_BUILDING）—
 * 这些粒度过细且与三端 RBAC 不正交，全部归并为这三档。
 */
public enum DataScopeType {

    /** 社区全量视野 */
    ALL_COMMUNITY("ALL_COMMUNITY", "社区全量"),

    /** 楼栋反查集合（OWNER_REPRESENTATIVE / GRID_OPERATOR / VOLUNTEER） */
    OWNER_GROUP("OWNER_GROUP", "业主集合"),

    /** 仅本部门（物业 / 服务商） */
    ORG_ONLY("ORG_ONLY", "仅本部门");

    private final String value;
    private final String description;

    DataScopeType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static DataScopeType of(String value) {
        if (value == null) {
            return null;
        }
        for (DataScopeType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的 DataScopeType: " + value);
    }
}
