package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 党员比例策略解析端口（Hexagonal Port）。
 *
 * <p>调用语义：
 * <ul>
 *   <li>返回 {@link Optional#empty()}：默认走 50%（无放宽申请，或放宽申请已被撤销）</li>
 *   <li>返回 {@link Optional#of(Object)} 包裹的具体 ratio：使用该放宽值
 *       （注意：返回值必须 < 0.50；具体实现内部已完成断路器三点对账）</li>
 * </ul>
 *
 * <p>实现位于 infrastructure 层。每次调用都会落定一条
 * {@code t_waiver_snapshot_comparison} 审计记录，无论是否撤销。
 */
public interface PartyRatioPolicyResolver {

    /**
     * 解析当前议题应当使用的党员比例下限。
     *
     * @param subjectId 议题 ID
     * @param trigger   对账触发时机
     * @return 实际应使用的党员比例（empty 表示走默认 0.50）
     */
    Optional<BigDecimal> resolveRatio(Long subjectId, RatioCheckTrigger trigger);
}
