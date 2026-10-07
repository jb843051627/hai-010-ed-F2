package com.fc.v2.ed;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TEdGradeRuleMapper;
import com.fc.v2.model.auto.TEdGradeRule;
import com.fc.v2.service.ITEdGradeRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ed F2 课程成绩等级判定与在用评定标准版本取用 · 功能点验收测试（rule-eval 形状：直调求值，用例自播种）
 * create 侧产物，仅质检使用，不交付执行模型。
 */
@SpringBootTest
public class EdF2AcceptanceTest {

    /** 本测试类自己插入的规则编号前缀（@BeforeEach 只清这一批，不动其它数据） */
    private static final String PFX = "AR-";

    @javax.annotation.Resource
    private ITEdGradeRuleService edGradeRuleService;

    @javax.annotation.Resource
    private TEdGradeRuleMapper edGradeRuleMapper;

    @BeforeEach
    public void setUp() {
        edGradeRuleMapper.delete(new QueryWrapper<TEdGradeRule>().likeRight("rule_code", PFX));
    }

    private static java.util.Date at(String s) {
        return java.sql.Timestamp.valueOf(s);
    }

    private TEdGradeRule mk(String code, String t1, String t2, String t3, int prio,
                    String start, String end) {
        TEdGradeRule r = new TEdGradeRule();
        r.setRuleCode(code);
        r.setTh1Max(new java.math.BigDecimal(t1));
        r.setTh2Max(new java.math.BigDecimal(t2));
        r.setTh3Max(new java.math.BigDecimal(t3));
        r.setPriority(prio);
        r.setEffStart(at(start));
        r.setEffEnd(at(end));
        r.setStatus(0);
        return r;
    }

    /** 坑2 时段定位 */
    @Test
    public void trap2_should_use_version_effective_at_given_time() {
        edGradeRuleMapper.insert(mk(PFX + "B2", "20", "40", "60", 10,
                "2024-01-01 00:00:00", "2025-01-01 00:00:00"));
        edGradeRuleMapper.insert(mk(PFX + "B2", "30", "45", "60", 10,
                "2025-01-01 00:00:00", "2099-01-01 00:00:00"));
        assertEquals(2, edGradeRuleService.evaluate(PFX + "B2", new java.math.BigDecimal("25"), at("2024-06-01 00:00:00")),
                "档位不符合预期");
    }

    /** 坑1 分档边界 */
    @Test
    public void trap1_band_boundary_should_go_higher() {
        edGradeRuleMapper.insert(mk(PFX + "B1", "30", "45", "60", 10,
                "2025-01-01 00:00:00", "2099-01-01 00:00:00"));
        assertEquals(2, edGradeRuleService.evaluate(PFX + "B1", new java.math.BigDecimal("30"), at("2025-06-01 00:00:00")),
                "档位不符合预期");
    }

    /** 坑4 生效窗口口径：定位 / 可用集 / 旁路判定 / 条数 四个入口必须同口径 */
    @Test
    public void trap4_effective_window_is_half_open() {
        edGradeRuleMapper.insert(mk(PFX + "B4", "20", "40", "60", 10,
                "2024-01-01 00:00:00", "2025-01-01 00:00:00"));
        edGradeRuleMapper.insert(mk(PFX + "B4", "30", "45", "60", 10,
                "2025-01-01 00:00:00", "2026-01-01 00:00:00"));
        edGradeRuleMapper.insert(mk(PFX + "B4", "50", "70", "90", 10,
                "2026-01-01 00:00:00", "2099-01-01 00:00:00"));
        java.util.Date t1 = at("2025-01-01 00:00:00");
        java.util.Date t2 = at("2026-01-01 00:00:00");
        // 入口1：单规则定位（含起不含止 → 换版当天归新版）
        // 输入取 44 而非 45：45 恰等于 th2Max，期望档位会随分档算子（`<` / `<=`）翻转，
        // 本坑只考时段半开口径，必须与 band-eval 是否入选无关。
        assertEquals(2, edGradeRuleService.evaluate(PFX + "B4", new java.math.BigDecimal("44"), t1),
                "定位档位不符合预期");
        assertEquals(1, edGradeRuleService.evaluate(PFX + "B4", new java.math.BigDecimal("44"), t2),
                "定位档位不符合预期");
        // 入口2：可用集（同一时刻只能有一版可用）
        assertEquals(1, edGradeRuleService.listAvailable(t1).size(), "可用集口径不符合预期");
        // 入口3：旁路单条判定（列表里有、导出里也必须算有）
        java.util.List<TEdGradeRule> rows = edGradeRuleMapper.selectList(
                new QueryWrapper<TEdGradeRule>().eq("rule_code", PFX + "B4").orderByAsc("eff_start"));
        assertEquals(3, rows.size(), "播种条数不符合预期");
        assertTrue(edGradeRuleService.usable(rows.get(1).getId(), t1), "旁路判定应判可用");
        assertFalse(edGradeRuleService.usable(rows.get(0).getId(), t1), "旁路判定不应判可用");
        // 入口4：条数角标（必须与可用集逐条一致）
        assertEquals(1, edGradeRuleService.countAvailable(t1), "条数口径不符合预期");
    }

    /** 坑6 并列次序 */
    @Test
    public void trap6_tie_should_break_by_code_desc() {
        edGradeRuleMapper.insert(mk(PFX + "B6A", "0.01", "0.02", "0.03", 90,
                "2024-01-01 00:00:00", "2099-01-01 00:00:00"));
        edGradeRuleMapper.insert(mk(PFX + "B6B", "45", "60", "75", 90,
                "2024-01-01 00:00:00", "2099-01-01 00:00:00"));
        assertEquals(1, edGradeRuleService.evaluateTop(new java.math.BigDecimal("30"), at("2026-07-01 00:00:00")),
                "档位不符合预期");
    }

    /** 坑5 取优顺序 */
    @Test
    public void trap5_top_should_follow_priority() {
        edGradeRuleMapper.insert(mk(PFX + "B5A", "30", "45", "60", 10,
                "2024-01-01 00:00:00", "2099-01-01 00:00:00"));
        edGradeRuleMapper.insert(mk(PFX + "B5B", "10", "20", "30", 50,
                "2024-01-01 00:00:00", "2099-01-01 00:00:00"));
        edGradeRuleMapper.insert(mk(PFX + "B5C", "0.01", "0.02", "0.03", 90,
                "2024-01-01 00:00:00", "2099-01-01 00:00:00"));
        assertEquals(4, edGradeRuleService.evaluateTop(new java.math.BigDecimal("30"), at("2025-06-01 00:00:00")),
                "档位不符合预期");
    }

    /** 坑3 空档兜底 */
    @Test
    public void trap3_no_effective_version_should_fallback() {
        edGradeRuleMapper.insert(mk(PFX + "B3", "10", "20", "30", 20,
                "2026-01-01 00:00:00", "2026-06-01 00:00:00"));
        assertEquals(0, edGradeRuleService.evaluate(PFX + "B3", new java.math.BigDecimal("30"), at("2026-07-01 00:00:00")),
                "期望值为零");
    }
}
