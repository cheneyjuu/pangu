package com.pangu.bootstrap.disclosure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.disclosure.DisclosureDiffCalculator;
import com.pangu.domain.model.disclosure.DiffKind;
import com.pangu.domain.model.disclosure.DisclosureDiff;
import com.pangu.domain.model.disclosure.FieldDiff;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DisclosureDiffCalculator} W/R/N 三路差分纯单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>同 leaf 等值 → N（不展开）；</li>
 *   <li>同 leaf 异值 → W；</li>
 *   <li>仅 curr 存在 → W；</li>
 *   <li>仅 prev 存在 → R；</li>
 *   <li>嵌套对象递归；</li>
 *   <li>数组按 index 比对（含长度变化）；</li>
 *   <li>类型不一致：prev 子树全 R，curr 子树全 W；</li>
 *   <li>BigDecimal/数字精度保留。</li>
 * </ul>
 */
public class DisclosureDiffCalculatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DisclosureDiffCalculator calc = new DisclosureDiffCalculator();

    private DisclosureDiff diff(String prevJson, String currJson) throws Exception {
        return calc.diff(mapper.readTree(prevJson), mapper.readTree(currJson));
    }

    // ===== 等值 N =====

    @Test
    public void equalLeaves_countAsNoChange() throws Exception {
        DisclosureDiff d = diff("{\"a\":1,\"b\":\"x\"}", "{\"a\":1,\"b\":\"x\"}");
        assertEquals(0, d.writeCount());
        assertEquals(0, d.readCount());
        assertEquals(2, d.noChangeCount());
    }

    // ===== 异值 W =====

    @Test
    public void differingLeafValue_countsAsWrite() throws Exception {
        DisclosureDiff d = diff("{\"a\":1}", "{\"a\":2}");
        assertEquals(1, d.writeCount());
        FieldDiff w = d.writes().get(0);
        assertEquals("$.a", w.jsonPath());
        assertEquals(DiffKind.WRITE, w.kind());
        assertEquals(1, w.before());
        assertEquals(2, w.after());
    }

    // ===== 仅 curr 存在 → W =====

    @Test
    public void keyOnlyInCurrent_isWrite() throws Exception {
        DisclosureDiff d = diff("{}", "{\"newKey\":42}");
        assertEquals(1, d.writeCount());
        assertEquals("$.newKey", d.writes().get(0).jsonPath());
        assertNull(d.writes().get(0).before());
        assertEquals(42, d.writes().get(0).after());
    }

    // ===== 仅 prev 存在 → R =====

    @Test
    public void keyOnlyInPrev_isRead() throws Exception {
        DisclosureDiff d = diff("{\"oldKey\":\"v\"}", "{}");
        assertEquals(1, d.readCount());
        FieldDiff r = d.reads().get(0);
        assertEquals("$.oldKey", r.jsonPath());
        assertEquals(DiffKind.READ, r.kind());
        assertEquals("v", r.before());
        assertNull(r.after());
    }

    // ===== 嵌套对象 =====

    @Test
    public void nestedObjects_recurseProperly() throws Exception {
        DisclosureDiff d = diff(
                "{\"a\":{\"x\":1,\"y\":2},\"b\":3}",
                "{\"a\":{\"x\":1,\"y\":99},\"b\":3}");
        assertEquals(1, d.writeCount());
        assertEquals(2, d.noChangeCount());
        assertEquals("$.a.y", d.writes().get(0).jsonPath());
    }

    // ===== 数组 =====

    @Test
    public void arrayLengthGrowth_writesNewIndices() throws Exception {
        DisclosureDiff d = diff(
                "{\"arr\":[\"a\",\"b\"]}",
                "{\"arr\":[\"a\",\"b\",\"c\"]}");
        assertEquals(2, d.noChangeCount());
        assertEquals(1, d.writeCount());
        assertEquals("$.arr[2]", d.writes().get(0).jsonPath());
    }

    @Test
    public void arrayLengthShrink_readsLostIndices() throws Exception {
        DisclosureDiff d = diff(
                "{\"arr\":[\"a\",\"b\",\"c\"]}",
                "{\"arr\":[\"a\",\"b\"]}");
        assertEquals(2, d.noChangeCount());
        assertEquals(1, d.readCount());
        assertEquals("$.arr[2]", d.reads().get(0).jsonPath());
    }

    // ===== 类型不一致 =====

    @Test
    public void typeMismatch_prevSubtreeAllRead_currSubtreeAllWrite() throws Exception {
        // prev 是 object 树（2 个 leaf），curr 是单个 leaf
        DisclosureDiff d = diff(
                "{\"k\":{\"a\":1,\"b\":2}}",
                "{\"k\":\"flat\"}");
        // prev 子树：$.k.a + $.k.b 两个 R
        assertEquals(2, d.readCount());
        // curr 是单个 leaf：$.k 一个 W
        assertEquals(1, d.writeCount());
        assertTrue(d.writes().get(0).jsonPath().equals("$.k"));
        assertEquals("flat", d.writes().get(0).after());
    }

    // ===== BigDecimal 精度 =====

    @Test
    public void bigDecimalLeaves_preservePrecision() throws Exception {
        DisclosureDiff d = diff(
                "{\"amt\":1234.5678}",
                "{\"amt\":1234.5679}");
        assertEquals(1, d.writeCount());
        assertEquals(0, d.noChangeCount());
        FieldDiff w = d.writes().get(0);
        assertTrue(w.before() instanceof BigDecimal);
        assertEquals(new BigDecimal("1234.5678"), w.before());
        assertEquals(new BigDecimal("1234.5679"), w.after());
    }

    // ===== 防御 =====

    @Test
    public void diff_rejectsNullInputs() {
        assertThrows(IllegalArgumentException.class, () -> calc.diff(null, mapper.createObjectNode()));
        assertThrows(IllegalArgumentException.class, () -> calc.diff(mapper.createObjectNode(), null));
    }
}
