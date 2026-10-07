package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.Date;

/**
 * 一次成绩判档的结果及其依据。
 *
 * <p>除了档位本身，还记录「用的是哪一版判定线、在哪个时点判的」，
 * 成绩单与审计追溯一律以此为准，避免同一分数各处口径不一致。</p>
 *
 * <ul>
 *   <li>{@code level}：1 优秀 / 2 良好 / 3 及格 / 4 不及格；</li>
 *   <li>{@code ruleId/ruleCode/ruleName}：实际生效的那一版判定线；</li>
 *   <li>{@code effStart/effEnd}：该版本的生效时段（左闭右开，{@code effEnd} 不含）；</li>
 *   <li>{@code evaluatedAt}：判定时点；</li>
 *   <li>{@code input}：原始输入分值。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-07
 */
public class GradeDecision implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 1 优秀 / 2 良好 / 3 及格 / 4 不及格 */
    private int level;

    /** 实际生效版本主键 */
    private Long ruleId;

    /** 判定线代号 */
    private String ruleCode;

    /** 判定线名 */
    private String ruleName;

    /** 启用之日（含） */
    private Date effStart;

    /** 交棒之日（不含） */
    private Date effEnd;

    /** 判定时点 */
    private Date evaluatedAt;

    /** 输入分值 */
    private java.math.BigDecimal input;

    public GradeDecision() {
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Date getEffStart() {
        return effStart == null ? null : new Date(effStart.getTime());
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart == null ? null : new Date(effStart.getTime());
    }

    public Date getEffEnd() {
        return effEnd == null ? null : new Date(effEnd.getTime());
    }

    public void setEffEnd(Date effEnd) {
        this.effEnd = effEnd == null ? null : new Date(effEnd.getTime());
    }

    public Date getEvaluatedAt() {
        return evaluatedAt == null ? null : new Date(evaluatedAt.getTime());
    }

    public void setEvaluatedAt(Date evaluatedAt) {
        this.evaluatedAt = evaluatedAt == null ? null : new Date(evaluatedAt.getTime());
    }

    public java.math.BigDecimal getInput() {
        return input;
    }

    public void setInput(java.math.BigDecimal input) {
        this.input = input;
    }
}
