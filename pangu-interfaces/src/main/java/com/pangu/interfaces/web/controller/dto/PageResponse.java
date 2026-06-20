package com.pangu.interfaces.web.controller.dto;

import com.pangu.domain.common.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 对外分页响应体（M4-1 立约，全平台列表接口统一复用）。
 *
 * <p>JSON 形态固定为 {@code { items, total, page, size }}：
 * <ul>
 *   <li>{@code items}：当前页数据；</li>
 *   <li>{@code total}：满足筛选条件的总条数；</li>
 *   <li>{@code page}：当前页码（1-based）；</li>
 *   <li>{@code size}：页大小。</li>
 * </ul>
 *
 * <p>由领域层 {@link Page} 经 {@link #from(Page, Function)} 映射而来，
 * 把领域聚合逐项转换为对外视图 DTO。
 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {

    /** 把领域 {@link Page}{@code <S>} 逐项经 {@code mapper} 映射为 {@code PageResponse<T>}。 */
    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        List<T> mapped = page.items().stream().map(mapper).toList();
        return new PageResponse<>(mapped, page.total(), page.page(), page.size());
    }
}
