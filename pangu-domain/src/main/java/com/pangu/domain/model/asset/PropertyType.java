package com.pangu.domain.model.asset;

/**
 * 房产/物理单元类型枚举
 */
public enum PropertyType {
    
    /** 住宅 */
    RESIDENTIAL,
    
    /** 商业/写字楼 */
    COMMERCIAL,
    
    /** 特定空间：车位 (不计入业主大会投票权) */
    PARKING_SPACE,
    
    /** 特定空间：车棚 (不计入业主大会投票权) */
    CARPORT,
    
    /** 特定空间：摊位 (不计入业主大会投票权) */
    STALL
}
