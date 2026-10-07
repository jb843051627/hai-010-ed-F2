package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.common.exception.ed.GradeRuleIndeterminateException;
import com.fc.v2.common.exception.ed.GradeRuleIndeterminateException.Reason;
import com.fc.v2.mapper.auto.TEdGradeRuleMapper;
import com.fc.v2.model.auto.TEdGradeRule;
import com.fc.v2.model.custom.GradeDecision;

/**
 * TEdGradeRuleServiceImpl 判档口径测试。
 *
 * 覆盖学院反映的五类问题：版本按点选、压线向上、生效日左闭右开、空档报错、顺位确定性。
 *
 * @author fuce
 * @date 2026-10-07
 */
@ExtendWith(MockitoExtension.class)
class TEdGradeRuleServiceImplTest {

    @Mock
    private TEdGradeRuleMapper edGradeRuleMapper;

    @InjectMocks
    private TEdGradeRuleServiceImpl service;

    private static Date t(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static TEdGradeRule rule(long id, String code, String start, String end,
                                     String th1, String th2, String th3, Integer priority) {
        TEdGradeRule r = new TEdGradeRule();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName(code + "-v" + id);
        r.setEffStart(start == null ? null : t(start));
        r.setEffEnd(end == null ? null : t(end));
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        r.setPriority(priority);
        r.setStatus(0);
        r.setDelFlag(0);
        return r;
    }

    /** C1 两版首尾相接：旧版 2025 上半年（优秀线 85），新版 2025-07-01 起（优秀线 90） */
    private TEdGradeRule v1;
    private TEdGradeRule v2;

    @BeforeEach
    void setUp() {
        v1 = rule(101, "C1", "2025-01-01 00:00:00", "2025-07-01 00:00:00",
                "85", "75", "60", 0);
        v2 = rule(102, "C1", "2025-07-01 00:00:00", null,
                "90", "80", "60", 0);
    }

    // ---------- 1. 版本必须按判定时点挑，不许拿新版算旧学期 ----------

    @Test
    void oldTermUsesOldVersionEvenWhenNewerExists() {
        when(edGradeRuleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>(Arrays.asList(v2, v1)));

        // 88 分：旧版（线85）是优秀，新版（线90）是良好。旧学期必须给优秀。
        GradeDecision d = service.decide("C1", new BigDecimal("88"), t("2025-06-15 10:00:00"));
        assertEquals(1, d.getLevel());
        assertEquals(Long.valueOf(101L), d.getRuleId());
    }

    @Test
    void newTermUsesNewVersion() {
        when(edGradeRuleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>(Arrays.asList(v1, v2)));

        GradeDecision d = service.decide("C1", new BigDecimal("88"), t("2025-07-15 10:00:00"));
        assertEquals(2, d.getLevel());
        assertEquals(Long.valueOf(102L), d.getRuleId());
    }

    // ---------- 2. 压线往上一档 ----------

    @Test
    void exactlyOnThresholdGoesUpOneBand() {
        when(edGradeRuleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>(Arrays.asList(v1, v2)));
        Date at = t("2025-08-01 00:00:00");

        assertEquals(1, service.evaluate("C1", new BigDecimal("90.00"), at), "90 压优秀线 → 优秀");
        assertEquals(2, service.evaluate("C1", new BigDecimal("80.00"), at), "80 压良好线 → 良好");
        assertEquals(3, service.evaluate("C1", new BigDecimal("60.00"), at), "60 压及格线 → 及格");
        assertEquals(2, service.evaluate("C1", new BigDecimal("89.99"), at));
        assertEquals(4, service.evaluate("C1", new BigDecimal("59.50"), at));
    }

    @Test
    void nullThresholdBandIsSkipped() {
        // 没设优秀线，85 分落到良好档，而不是被当成优秀
        TEdGradeRule noTop = rule(301, "C9", "2025-01-01 00:00:00", null,
                null, "80", "60", 0);
        when(edGradeRuleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>(Collections.singletonList(noTop)));

        assertEquals(2, service.evaluate("C9", new BigDecimal("85"), t("2025-05-01 00:00:00")));
    }

    // ---------- 3. 生效日归属：左闭右开 [effStart, effEnd) ----------

    @Test
    void handoverDayBelongsToNewVersion() {
        when(edGradeRuleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>(Arrays.asList(v1, v2)));

        // 交棒日 2025-07-01 00:00:00 当天的第一条记录归新版
        GradeDecision atHandover = service.decide("C1", new BigDecimal("88"),
                t("2025-07-01 00:00:00"));
        assertEquals(Long.valueOf(102L), atHandover.getRuleId());

        // 交棒前一刻仍归旧版
        GradeDecision justBefore = service.decide("C1", new BigDecimal("88"),
                t("2025-06-30 23:59:59"));
        assertEquals(Long.valueOf(101L), justBefore.getRuleId());

        // 启用当天即生效
        GradeDecision atStart = service.decide("C1", new BigDecimal("88"),
                t("2025-01-01 00:00:00"));
        assertEquals(Long.valueOf(101L), atStart.getRuleId());
    }

    // ---------- 4. 空档：不许凭空给档 ----------

    @Test
    void noEffectiveVersionThrowsInsteadOfGuessing() {
        // C3 唯一一版 2026 年才启用，2025 年中是生效空档
        TEdGradeRule future = rule(401, "C3", "2026-01-01 00:00:00", null,
                "90", "80", "60", 0);
        when(edGradeRuleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>(Collections.singletonList(future)));

        GradeRuleIndeterminateException ex = assertThrows(GradeRuleIndeterminateException.class,
                () -> service.evaluate("C3", new BigDecimal("95"), t("2025-06-01 00:00:00")));
        assertEquals(Reason.NONE, ex.getReason());
        assertEquals("C3", ex.getRuleCode());
    }

    @Test
    void overlappingVersionsOfSameCodeRejected() {
        TEdGradeRule a = rule(501, "C4", "2025-01-01 00:00:00", null, "70", "60", "50", 0);
        TEdGradeRule b = rule(502, "C4", "2025-03-01 00:00:00", null, "90", "80", "60", 0);
        when(edGradeRuleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>(Arrays.asList(a, b)));

        GradeRuleIndeterminateException ex = assertThrows(GradeRuleIndeterminateException.class,
                () -> service.evaluate("C4", new BigDecimal("88"), t("2025-06-01 00:00:00")));
        assertEquals(Reason.AMBIGUOUS, ex.getReason());
        assertTrue(ex.getCandidateRuleIds().containsAll(Arrays.asList(501L, 502L)));
    }

    // ---------- 5. 多条规则：priority 决胜，结果稳定可复现 ----------

    @Test
    void evaluateTopHonorsPriorityRegardlessOfDbOrder() {
        TEdGradeRule low = rule(601, "P1", "2025-01-01 00:00:00", null, "85", "70", "60", 5);
        TEdGradeRule high = rule(602, "P2", "2025-01-01 00:00:00", null, "90", "80", "60", 10);
        // 88 分：低顺位规则判优秀，高顺位规则判良好——谁顺位高听谁的
        when(edGradeRuleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>(Arrays.asList(low, high)))
                .thenReturn(new ArrayList<>(Arrays.asList(high, low)));

        Date at = t("2025-06-01 00:00:00");
        GradeDecision first = service.decideTop(new BigDecimal("88"), at);
        GradeDecision second = service.decideTop(new BigDecimal("88"), at);

        assertEquals(Long.valueOf(602L), first.getRuleId());
        assertEquals(2, first.getLevel());
        // 库返回顺序反过来，结果必须一模一样
        assertEquals(first.getLevel(), second.getLevel());
        assertEquals(first.getRuleId(), second.getRuleId());
    }

    @Test
    void evaluateTopTiedPriorityRejected() {
        TEdGradeRule a = rule(701, "T1", "2025-01-01 00:00:00", null, "85", "70", "60", null);
        TEdGradeRule b = rule(702, "T2", "2025-01-01 00:00:00", null, "90", "80", "60", null);
        when(edGradeRuleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>(Arrays.asList(a, b)));

        GradeRuleIndeterminateException ex = assertThrows(GradeRuleIndeterminateException.class,
                () -> service.evaluateTop(new BigDecimal("88"), t("2025-06-01 00:00:00")));
        assertEquals(Reason.AMBIGUOUS, ex.getReason());
        assertEquals(2, ex.getCandidateRuleIds().size());
    }

    @Test
    void listAvailableIsSortedByPriorityThenStartThenId() {
        TEdGradeRule p5 = rule(801, "S1", "2025-01-01 00:00:00", null, "90", null, null, 5);
        TEdGradeRule p10a = rule(802, "S2", "2025-01-01 00:00:00", null, "90", null, null, 10);
        TEdGradeRule p10b = rule(803, "S3", "2025-03-01 00:00:00", null, "90", null, null, 10);
        when(edGradeRuleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>(Arrays.asList(p5, p10a, p10b)));

        List<TEdGradeRule> avail = service.listAvailable(t("2025-06-01 00:00:00"));
        assertEquals(Arrays.asList(803L, 802L, 801L),
                Arrays.asList(avail.get(0).getId(), avail.get(1).getId(), avail.get(2).getId()));
    }

    // ---------- 依据与可用性 ----------

    @Test
    void decisionCarriesEvidenceForAudit() {
        when(edGradeRuleMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>(Arrays.asList(v1, v2)));

        Date at = t("2025-06-15 10:00:00");
        GradeDecision d = service.decide("C1", new BigDecimal("85"), at);
        assertEquals(1, d.getLevel());
        assertEquals("C1", d.getRuleCode());
        assertEquals(Long.valueOf(101L), d.getRuleId());
        assertEquals(at, d.getEvaluatedAt());
        assertEquals(t("2025-01-01 00:00:00"), d.getEffStart());
        assertEquals(t("2025-07-01 00:00:00"), d.getEffEnd());
        assertEquals(0, new BigDecimal("85").compareTo(d.getInput()));
    }

    @Test
    void usableChecksWindowAndFlags() {
        when(edGradeRuleMapper.selectById(101L)).thenReturn(v1);
        assertTrue(service.usable(101L, t("2025-06-30 23:59:59")));
        assertFalse(service.usable(101L, t("2025-07-01 00:00:00")), "交棒日起旧版不可用");
        assertFalse(service.usable(101L, t("2024-12-31 23:59:59")), "启用前不可用");

        v1.setDelFlag(1);
        assertFalse(service.usable(101L, t("2025-03-01 00:00:00")), "已删除不可用");
        v1.setDelFlag(0);
        v1.setStatus(1);
        assertFalse(service.usable(101L, t("2025-03-01 00:00:00")), "已让位不可用");
    }

    @Test
    void invalidArgumentsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.decide(null, new BigDecimal("80"), new Date()));
        assertThrows(IllegalArgumentException.class,
                () -> service.decideTop(new BigDecimal("80"), null));
    }
}
