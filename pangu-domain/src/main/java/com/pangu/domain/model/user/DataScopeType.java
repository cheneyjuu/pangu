package com.pangu.domain.model.user;

/**
 * 角色数据权限范围枚举 (ABAC 数据粒度限制)
 */
public enum DataScopeType {
    
    /** 全部数据权限 */
    ALL("1", "全部数据"),
    
    /** 自定义部门数据权限 */
    CUSTOM_DEPT("2", "自定部门"),
    
    /** 本部门数据权限 */
    OWN_DEPT("3", "本部门"),
    
    /** 本部门及以下数据权限 */
    OWN_DEPT_AND_CHILD("4", "本部门及以下"),
    
    /** 仅本人数据权限 */
    SELF("5", "仅本人"),
    
    /** 自定义指定楼栋数据权限 (主要面向网格员和楼栋志愿者) */
    CUSTOM_BUILDING("6", "自定义指定楼栋");

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
        for (DataScopeType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的数据权限范围值: " + value);
    }
}
