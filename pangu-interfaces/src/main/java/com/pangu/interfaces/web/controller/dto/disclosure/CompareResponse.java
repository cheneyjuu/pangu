package com.pangu.interfaces.web.controller.dto.disclosure;

import com.pangu.domain.model.disclosure.DisclosureDiff;
import com.pangu.domain.model.disclosure.FieldDiff;

import java.util.List;

/**
 * W/R/N 差分审计响应体。
 *
 * <p>{@code writes} / {@code reads} 是字段级 leaf path 列表；{@code noChangeCount} 仅返回数量。
 * 详细字段语义见 {@link com.pangu.domain.model.disclosure.DiffKind}。
 */
public record CompareResponse(
        List<FieldDiff> writes,
        List<FieldDiff> reads,
        int writeCount,
        int readCount,
        int noChangeCount
) {
    public static CompareResponse from(DisclosureDiff diff) {
        return new CompareResponse(
                diff.writes(),
                diff.reads(),
                diff.writeCount(),
                diff.readCount(),
                diff.noChangeCount()
        );
    }
}
