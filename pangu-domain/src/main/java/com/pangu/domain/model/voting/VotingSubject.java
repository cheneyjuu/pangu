package com.pangu.domain.model.voting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 投票表决议题基类（泛型高层抽象）。
 *
 * <p>新增字段：
 * <ul>
 *   <li>{@link #scope}：分母范围（社区/楼栋/单元），决定 {@link VotingDenominatorResolver} 计算多大空间</li>
 *   <li>{@link #scopeReferenceId}：scope 关联实体 ID（楼栋 / 单元 ID）</li>
 *   <li>{@link #partyRatioFloor}：党员比例下限（默认 0.50；放宽通过后由 application 写入实际值）</li>
 * </ul>
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class VotingSubject {

    /** 议题/表决事项 ID。 */
    private Long subjectId;

    /** 租户/小区 ID。 */
    private Long tenantId;

    /** 议题名称/表决标题。 */
    private String title;

    /** 分母范围（默认社区级）。 */
    private VotingScope scope;

    /** 范围引用 ID：scope=BUILDING 时为 building_id；UNIT 时为单元 ID；COMMUNITY 可为 null。 */
    private Long scopeReferenceId;

    /**
     * 党员比例下限（默认 0.50）。
     *
     * <p>设计原则：本字段是「应用层断路器结果」的载体——
     * application 在结算前调用 {@link PartyRatioPolicyResolver} 解析出实际值，
     * 写入本字段后再交给引擎；引擎自身只读不算。
     */
    private BigDecimal partyRatioFloor;

    /**
     * @return 安全获取 partyRatioFloor，未设置时返回默认 0.50
     */
    public BigDecimal getEffectivePartyRatioFloor() {
        return partyRatioFloor != null ? partyRatioFloor : new BigDecimal("0.50");
    }
}
