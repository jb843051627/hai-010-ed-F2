package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fc.v2.mapper.auto.TEdGradeRuleMapper;
import com.fc.v2.model.auto.TEdGradeRule;
import com.fc.v2.model.custom.grade.GradeEvalResult;

/**
 * 成绩判定线契约测试：版本按时刻选、压线归上一档、窗口半开、顺位定序、未命中判 0。
 */
public class TEdGradeRuleServiceImplTest {

    private TEdGradeRuleMapper mapper;
    private TEdGradeRuleServiceImpl service;

    /** 旧版：2024-09-01 启用，2025-09-01 00:00:00 交棒 */
    private TEdGradeRule oldV;
    /** 新版：2025-09-01 00:00:00 接棒，至今现行 */
    private TEdGradeRule newV;
    /** 另一门线，顺位更高 */
    private TEdGradeRule otherLine;

    @BeforeEach
    public void setUp() {
        mapper = mock(TEdGradeRuleMapper.class);
        service = new TEdGradeRuleServiceImpl();
        ReflectionTestUtils.setField(service, "edGradeRuleMapper", mapper);

        oldV = rule(1L, "GR01", "90", "80", "60", "2024-09-01 00:00:00", "2025-09-01 00:00:00", 5);
        newV = rule(2L, "GR01", "85", "75", "60", "2025-09-01 00:00:00", null, 5);
        otherLine = rule(3L, "GR02", "95", "85", "65", "2025-01-01 00:00:00", null, 9);
    }

    private void givenRules(List<TEdGradeRule> rows) {
        when(mapper.selectList(any())).thenReturn(rows);
    }

    @Test
    public void 压线归上一档() {
        givenRules(Arrays.asList(oldV));
        Date at = d("2025-06-01 00:00:00");
        // 界值（含）：正好压线算上一档
        assertEquals(1, service.evaluate("GR01", new BigDecimal("90"), at));
        assertEquals(2, service.evaluate("GR01", new BigDecimal("80"), at));
        assertEquals(3, service.evaluate("GR01", new BigDecimal("60"), at));
        // 差一分不到线就归下一档
        assertEquals(2, service.evaluate("GR01", new BigDecimal("89.99"), at));
        assertEquals(3, service.evaluate("GR01", new BigDecimal("79.5"), at));
        assertEquals(4, service.evaluate("GR01", new BigDecimal("59.99"), at));
        assertEquals(1, service.evaluate("GR01", new BigDecimal("100"), at));
    }

    @Test
    public void 版本按判定时刻选取_旧学期不吃新版线() {
        givenRules(Arrays.asList(oldV, newV));
        BigDecimal input = new BigDecimal("86");
        // 旧学期：按旧版 90/80/60 判，86 -> 良好(2)；若错用新版 85/75/60 会得优秀(1)
        GradeEvalResult oldSemester = service.evaluateDetail("GR01", input, d("2025-06-01 00:00:00"));
        assertTrue(oldSemester.isAvailable());
        assertEquals(2, oldSemester.getLevel());
        assertEquals(Long.valueOf(1L), oldSemester.getRuleId());
        // 新学期：按新版判，86 -> 优秀(1)
        GradeEvalResult newSemester = service.evaluateDetail("GR01", input, d("2025-10-01 00:00:00"));
        assertEquals(1, newSemester.getLevel());
        assertEquals(Long.valueOf(2L), newSemester.getRuleId());
    }

    @Test
    public void 启用当日归新版_交棒当日旧版已让位() {
        givenRules(Arrays.asList(oldV, newV));
        // 交棒瞬间 2025-09-01 00:00:00：旧版窗口 [起,止) 已闭合，新版启用当日即现行
        GradeEvalResult handover = service.evaluateDetail("GR01", new BigDecimal("86"), d("2025-09-01 00:00:00"));
        assertEquals(Long.valueOf(2L), handover.getRuleId());
        // 交棒前一秒仍归旧版
        GradeEvalResult before = service.evaluateDetail("GR01", new BigDecimal("86"), d("2025-08-31 23:59:59"));
        assertEquals(Long.valueOf(1L), before.getRuleId());
    }

    @Test
    public void 没有生效版本时判零且直说_不拿别的版本凑数() {
        givenRules(Arrays.asList(oldV, newV));
        Date tooEarly = d("2020-01-01 00:00:00");
        // 那一刻任何版本都没启用：判 0（不是档位），且回执直说
        assertEquals(0, service.evaluate("GR01", new BigDecimal("86"), tooEarly));
        GradeEvalResult rs = service.evaluateDetail("GR01", new BigDecimal("86"), tooEarly);
        assertFalse(rs.isAvailable());
        assertEquals(0, rs.getLevel());
        assertTrue(rs.getMessage() != null && rs.getMessage().contains("没有生效版本"));
        // evaluateTop 同样不许静默给档
        GradeEvalResult top = service.evaluateTopDetail(new BigDecimal("86"), tooEarly);
        assertFalse(top.isAvailable());
        assertEquals(0, top.getLevel());
        // 未知代号一样判 0
        assertEquals(0, service.evaluate("NO_SUCH", new BigDecimal("86"), d("2025-10-01 00:00:00")));
    }

    @Test
    public void 多条现行顺位大的先说话_与库返回顺序无关() {
        // 2025-10-01 时 newV(顺位5) 与 otherLine(顺位9) 同现行，90 分：
        // newV 判 1(>=85)，otherLine 判 2(>=85)；顺位大的 otherLine 先说话
        Date at = d("2025-10-01 00:00:00");
        givenRules(Arrays.asList(newV, otherLine));
        GradeEvalResult first = service.evaluateTopDetail(new BigDecimal("90"), at);
        assertEquals(2, first.getLevel());
        assertEquals("GR02", first.getRuleCode());
        // 库返回顺序反过来，结果必须一模一样（刷新不变）
        givenRules(Arrays.asList(otherLine, newV));
        GradeEvalResult second = service.evaluateTopDetail(new BigDecimal("90"), at);
        assertEquals(first.getLevel(), second.getLevel());
        assertEquals(first.getRuleId(), second.getRuleId());
    }

    @Test
    public void 顺位并列时按启用晚的再按主键大的() {
        // 与 otherLine 同顺位 9，但启用更晚、主键更大
        TEdGradeRule laterSamePri = rule(4L, "GR03", "50", "40", "30", "2026-01-01 00:00:00", null, 9);
        givenRules(Arrays.asList(otherLine, laterSamePri));
        Date at = d("2026-06-01 00:00:00");
        // 90 分：otherLine 判 2，laterSamePri 判 1；并列顺位取启用晚的 laterSamePri
        GradeEvalResult rs = service.evaluateTopDetail(new BigDecimal("90"), at);
        assertEquals(Long.valueOf(4L), rs.getRuleId());
        assertEquals(1, rs.getLevel());
    }

    @Test
    public void usable_看删除看状态看窗口() {
        when(mapper.selectById(1L)).thenReturn(oldV);
        assertTrue(service.usable(1L, d("2025-06-01 00:00:00")));
        assertFalse(service.usable(1L, d("2024-08-31 23:59:59"))); // 未启用
        assertFalse(service.usable(1L, d("2025-09-01 00:00:00"))); // 交棒当日(不含)
        // 已让位
        TEdGradeRule retired = rule(5L, "GR09", "90", "80", "60", null, null, 1);
        retired.setStatus(1);
        when(mapper.selectById(5L)).thenReturn(retired);
        assertFalse(service.usable(5L, d("2025-06-01 00:00:00")));
        // 已删除
        TEdGradeRule deleted = rule(6L, "GR10", "90", "80", "60", null, null, 1);
        deleted.setDelFlag(1);
        when(mapper.selectById(6L)).thenReturn(deleted);
        assertFalse(service.usable(6L, d("2025-06-01 00:00:00")));
    }

    @Test
    public void 命中版未配置界值时判零直说() {
        TEdGradeRule noTh = rule(7L, "GR07", null, null, null, "2025-01-01 00:00:00", null, 1);
        givenRules(new ArrayList<TEdGradeRule>(Arrays.asList(noTh)));
        GradeEvalResult rs = service.evaluateDetail("GR07", new BigDecimal("90"), d("2025-06-01 00:00:00"));
        assertFalse(rs.isAvailable());
        assertEquals(0, rs.getLevel());
        assertTrue(rs.getMessage() != null && rs.getMessage().contains("界值"));
    }

    private static TEdGradeRule rule(Long id, String code, String th1, String th2, String th3,
            String effStart, String effEnd, Integer priority) {
        TEdGradeRule r = new TEdGradeRule();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName("判定线" + code);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        r.setEffStart(effStart == null ? null : d(effStart));
        r.setEffEnd(effEnd == null ? null : d(effEnd));
        r.setPriority(priority);
        r.setStatus(0);
        r.setDelFlag(0);
        return r;
    }

    private static Date d(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
