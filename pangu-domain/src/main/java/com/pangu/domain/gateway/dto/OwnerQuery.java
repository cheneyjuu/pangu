package com.pangu.domain.gateway.dto;

/**
 * 业主名册列表查询参数（M4 读侧）。
 *
 * <p>所有过滤条件为「可空 → 不过滤」语义：
 * <ul>
 *   <li>{@code phonePrefix}：非空走 {@code phone LIKE 'prefix%'}（命中 {@code idx_account_phone}）；</li>
 *   <li>{@code buildingId}：非空走 {@code op.building_id = ?}；</li>
 *   <li>{@code roomId}：非空走 {@code op.room_id = ?}。</li>
 * </ul>
 *
 * <p>{@code page} 1-based，{@code size} 在 interfaces 层完成 [1,100] 收口。
 * 行级数据范围由 mapper {@code @DataScope(buildingAlias="op")} 注入。
 */
public record OwnerQuery(
        Long tenantId,
        String phonePrefix,
        Long buildingId,
        Long roomId,
        int page,
        int size
) {
}
