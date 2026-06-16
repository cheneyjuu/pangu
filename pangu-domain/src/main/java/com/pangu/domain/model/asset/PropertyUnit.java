package com.pangu.domain.model.asset;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 房产专有物理单元领域模型 (PropertyUnit 聚合根)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyUnit {

    /** 房间物理单元ID */
    private Long roomId;

    /** 租户ID（关联特定小区） */
    private Long tenantId;

    /** 关联的物理空间楼栋ID */
    private Long buildingId;

    /** 该单元专有建筑面积（用于计票累加） */
    private BigDecimal buildArea;

    /** 物理单元类型 */
    private PropertyType propertyType;

    /**
     * 判断当前物理单元是否具备投票资格
     * 只有住宅(RESIDENTIAL)和商业(COMMERCIAL)类型计入投票，车位/车棚/摊位等特定空间自动排除
     */
    public boolean isEligibleForVoting() {
        return this.propertyType == PropertyType.RESIDENTIAL || this.propertyType == PropertyType.COMMERCIAL;
    }
}
