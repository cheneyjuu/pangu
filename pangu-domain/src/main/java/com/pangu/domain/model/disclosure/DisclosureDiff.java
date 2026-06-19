package com.pangu.domain.model.disclosure;

import java.util.List;

/**
 * 两份公示快照的字段级差分结果（W/R/N 三路）。
 *
 * <p>作为 {@code DisclosureDiffCalculator.diff(prev, curr)} 的返回值；
 * 由 application 层序列化为 JSONB 后写入 {@code t_disclosure_audit_compare.diff_json}。
 *
 * @param writes        W：当期写入或修改的字段（含两边值不同 + 仅 curr 存在两类）
 * @param reads         R：当期未写入但引用的历史字段（path 仅在 prev 存在）
 * @param noChangeCount N：两期完全一致的 leaf path 数量（不展开列表，仅计数）
 */
public record DisclosureDiff(List<FieldDiff> writes, List<FieldDiff> reads, int noChangeCount) {
    public DisclosureDiff {
        if (writes == null) {
            throw new IllegalArgumentException("writes must not be null");
        }
        if (reads == null) {
            throw new IllegalArgumentException("reads must not be null");
        }
        if (noChangeCount < 0) {
            throw new IllegalArgumentException("noChangeCount must be non-negative");
        }
        // 取防御性副本，保证不可变
        writes = List.copyOf(writes);
        reads = List.copyOf(reads);
    }

    public int writeCount() {
        return writes.size();
    }

    public int readCount() {
        return reads.size();
    }
}
