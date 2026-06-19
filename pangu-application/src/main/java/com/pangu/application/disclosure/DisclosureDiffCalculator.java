package com.pangu.application.disclosure;

import com.fasterxml.jackson.databind.JsonNode;
import com.pangu.domain.model.disclosure.DiffKind;
import com.pangu.domain.model.disclosure.DisclosureDiff;
import com.pangu.domain.model.disclosure.FieldDiff;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 财务公示快照字段级差分工具（W/R/N 三路）。
 *
 * <p>遍历两份 canonical JSON，按 JSONPath 风格 ({@code $.x.y[0].z}) 递归到 leaf 后比对：
 * <ul>
 *   <li><b>W (WRITE)</b>：path 在 prev/curr 都存在但值不同，或 path 仅在 curr 存在；</li>
 *   <li><b>R (READ)</b> ：path 仅在 prev 存在（被移除 / 当期沿用历史）；</li>
 *   <li><b>N (NO_CHANGE)</b>：两期完全一致（仅计数，不展开列表）。</li>
 * </ul>
 *
 * <p>类型变化（如 prev 是 object 而 curr 是 leaf）视为：在新 path 上 W 写入了 curr 的 leaf
 * 集合，同时把 prev 的所有原 leaf path 标 R——保证审计能识别"重命名 / 重新组织"。
 *
 * <p>本类无状态、纯函数；建议在 application 层以 {@code @Component} 单例注入。
 */
@Component
public class DisclosureDiffCalculator {

    /** path 根标识，与 JSONPath 习惯一致。 */
    private static final String ROOT = "$";

    public DisclosureDiff diff(JsonNode prev, JsonNode curr) {
        if (prev == null || curr == null) {
            throw new IllegalArgumentException("prev/curr JsonNode must not be null");
        }
        Acc acc = new Acc();
        walk(ROOT, prev, curr, acc);
        return new DisclosureDiff(acc.writes, acc.reads, acc.noChangeCount);
    }

    private void walk(String path, JsonNode prev, JsonNode curr, Acc acc) {
        // 同结构对象 → 按 key 合并递归
        if (prev.isObject() && curr.isObject()) {
            Set<String> keys = new LinkedHashSet<>();
            prev.fieldNames().forEachRemaining(keys::add);
            curr.fieldNames().forEachRemaining(keys::add);
            for (String k : keys) {
                String childPath = path + "." + k;
                JsonNode pv = prev.get(k);
                JsonNode cv = curr.get(k);
                if (pv == null) {
                    flattenAsKind(childPath, cv, DiffKind.WRITE, null, acc);
                } else if (cv == null) {
                    flattenAsKind(childPath, pv, DiffKind.READ, null, acc);
                } else {
                    walk(childPath, pv, cv, acc);
                }
            }
            return;
        }

        // 同结构数组 → 按 index 合并递归
        if (prev.isArray() && curr.isArray()) {
            int max = Math.max(prev.size(), curr.size());
            for (int i = 0; i < max; i++) {
                String childPath = path + "[" + i + "]";
                if (i >= prev.size()) {
                    flattenAsKind(childPath, curr.get(i), DiffKind.WRITE, null, acc);
                } else if (i >= curr.size()) {
                    flattenAsKind(childPath, prev.get(i), DiffKind.READ, null, acc);
                } else {
                    walk(childPath, prev.get(i), curr.get(i), acc);
                }
            }
            return;
        }

        // 类型不一致：prev 子树全部 R，curr 子树全部 W
        if (prev.getNodeType() != curr.getNodeType()
                || prev.isContainerNode() || curr.isContainerNode()) {
            flattenAsKind(path, prev, DiffKind.READ, null, acc);
            flattenAsKind(path, curr, DiffKind.WRITE, null, acc);
            return;
        }

        // 两边都是 leaf → 直接比值
        if (prev.equals(curr)) {
            acc.noChangeCount++;
        } else {
            acc.writes.add(new FieldDiff(path, leafValue(prev), leafValue(curr), DiffKind.WRITE));
        }
    }

    /**
     * 把一个子树（可能是 object/array/leaf）展平为 leaf path 集合，统一打成同一个 kind。
     * 用于「键被新增 / 删除」与「类型不一致」两种场景：单边的整棵子树要么全 W 要么全 R。
     *
     * <p>{@code other} 始终为 null（单边）：W 时 before=null/after=leaf；R 时 before=leaf/after=null。
     */
    private void flattenAsKind(String path, JsonNode node, DiffKind kind, Object other, Acc acc) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            // null 视作 leaf；"sentinel"-类的 null 字段也要落审计
            addOneSided(path, node, kind, acc);
            return;
        }
        if (node.isObject()) {
            Iterator<String> it = node.fieldNames();
            while (it.hasNext()) {
                String k = it.next();
                flattenAsKind(path + "." + k, node.get(k), kind, other, acc);
            }
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flattenAsKind(path + "[" + i + "]", node.get(i), kind, other, acc);
            }
            return;
        }
        addOneSided(path, node, kind, acc);
    }

    private void addOneSided(String path, JsonNode leaf, DiffKind kind, Acc acc) {
        Object value = leafValue(leaf);
        if (kind == DiffKind.WRITE) {
            acc.writes.add(new FieldDiff(path, null, value, DiffKind.WRITE));
        } else if (kind == DiffKind.READ) {
            acc.reads.add(new FieldDiff(path, value, null, DiffKind.READ));
        } else {
            acc.noChangeCount++;
        }
    }

    /** 把 leaf JsonNode 转成 Java 标量；container 节点直接返回 toString()（仅在异常路径触达）。 */
    private static Object leafValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isInt()) return node.intValue();
        if (node.isLong()) return node.longValue();
        if (node.isBigInteger()) return node.bigIntegerValue();
        if (node.isFloatingPointNumber()) {
            // 保留精度：BigDecimal/Double 都按 toString
            return node.decimalValue();
        }
        return node.toString();
    }

    /** 内部累加器。 */
    private static final class Acc {
        final List<FieldDiff> writes = new ArrayList<>();
        final List<FieldDiff> reads = new ArrayList<>();
        int noChangeCount = 0;
    }
}
