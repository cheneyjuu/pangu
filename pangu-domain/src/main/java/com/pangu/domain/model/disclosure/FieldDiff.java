package com.pangu.domain.model.disclosure;

/**
 * 单个 JSON path 的差分条目。
 *
 * @param jsonPath path 表达式，如 {@code $.accounts[0].totalBalance}
 * @param before   上一期值（可能为 {@code null}，表示该 path 仅在 curr 出现）
 * @param after    当期值（可能为 {@code null}，表示该 path 仅在 prev 出现）
 * @param kind     差分类型，见 {@link DiffKind}
 */
public record FieldDiff(String jsonPath, Object before, Object after, DiffKind kind) {
    public FieldDiff {
        if (jsonPath == null || jsonPath.isBlank()) {
            throw new IllegalArgumentException("jsonPath must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
    }
}
