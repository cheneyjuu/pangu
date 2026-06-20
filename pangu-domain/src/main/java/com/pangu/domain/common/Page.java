package com.pangu.domain.common;

import java.util.List;

/**
 * 仓储分页查询的统一返回载体（framework-light，纯 record）。
 *
 * <p>读侧地基：所有 {@code *Repository} 的分页方法统一返回 {@code Page<T>}，
 * 由 interfaces 层映射为对外 {@code PageResponse<T>}（{@code items/total/page/size} 契约）。
 * 全平台后续列表接口（Owners / Finance / 锁列表等）复用此约定。
 *
 * @param items 当前页数据
 * @param total 满足筛选条件的总条数（用于前端翻页器）
 * @param page  当前页码（1-based）
 * @param size  页大小
 */
public record Page<T>(List<T> items, long total, int page, int size) {
}
