package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TEdGradeRule;
import com.fc.v2.model.custom.GradeDecision;

/**
 * TEdGradeRule 业务层处理接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITEdGradeRuleService {

    /** selectTEdGradeRuleById */
    TEdGradeRule selectTEdGradeRuleById(Long id);

    /**
     * 列出某时点正在生效的判定线。
     *
     * <p>生效时段为<b>左闭右开</b> {@code [effStart, effEnd)}：启用之日当天 00:00:00
     * 起归新版，交棒之日 {@code effEnd} 当天的记录归下一版。返回顺序是确定的：
     * priority 降序（null 视为 0）、effStart 降序、id 升序。</p>
     */
    List<TEdGradeRule> listAvailable(Date at);

    /**
     * 按规则代号在指定时点判档。
     *
     * @throws com.fc.v2.common.exception.ed.GradeRuleIndeterminateException
     *         该时点无生效版本（空档），或同一代号有多版同时生效且无法唯一确定
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /**
     * 不指定代号、在全部生效判定线中按顺位（priority 大者先说话）判档。
     *
     * @throws com.fc.v2.common.exception.ed.GradeRuleIndeterminateException
     *         该时点无生效版本，或最高顺位有多条并列
     */
    int evaluateTop(BigDecimal input, Date at);

    /**
     * 按规则代号判档并返回完整依据（生效版本、时段、时点），供审计与成绩单引用。
     *
     * @throws com.fc.v2.common.exception.ed.GradeRuleIndeterminateException
     *         该时点无生效版本或版本不唯一
     */
    GradeDecision decide(String ruleCode, BigDecimal input, Date at);

    /**
     * 按顺位判档并返回完整依据（生效版本、时段、时点），供审计与成绩单引用。
     *
     * @throws com.fc.v2.common.exception.ed.GradeRuleIndeterminateException
     *         该时点无生效版本或最高顺位并列
     */
    GradeDecision decideTop(BigDecimal input, Date at);

    /**
     * 某条判定线在指定时点是否可用：未删除、现行、且时点落在其生效时段内。
     */
    boolean usable(Long id, Date at);

    /** 某时点生效中的判定线条数（含顺位并列，仅用于巡检；并列并不代表可判档） */
    int countAvailable(Date at);

}
