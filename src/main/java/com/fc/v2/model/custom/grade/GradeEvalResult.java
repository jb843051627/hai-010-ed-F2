package com.fc.v2.model.custom.grade;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 成绩判档回执。
 * available=false 表示那一刻套不上任何生效判定线（或命中版未配置界值），
 * level 恒为 0，调用方不得把它当成某个档位；命中时 level 为 1..4
 * （1优秀 2良好 3及格 4不及格），并回显命中的那一版
 * （主键/代号/名称/窗口/顺位），定责两边照此对账。
 *
 * @author fuce
 * @date 2026-10-06
 */
@ApiModel(value = "GradeEvalResult", description = "成绩判档回执")
public class GradeEvalResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否命中生效判定线（false=那一刻没有可用判定线） */
    @ApiModelProperty(value = "是否命中生效判定线")
    private boolean available;

    /** 判定档位 1..4（1优秀 2良好 3及格 4不及格）；未命中恒为 0（不是档位） */
    @ApiModelProperty(value = "判定档位 1..4，未命中为 0")
    private int level;

    /** 判定线代号（命中版代号；未命中时为入参回显） */
    @ApiModelProperty(value = "判定线代号")
    private String ruleCode;

    /** 判定数值（入参回显） */
    @ApiModelProperty(value = "判定数值")
    private BigDecimal input;

    /** 判定时刻（入参回显） */
    @ApiModelProperty(value = "判定时刻")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date at;

    /** 命中版主键 */
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "命中版主键")
    private Long ruleId;

    /** 命中版名称 */
    @ApiModelProperty(value = "命中版名称")
    private String ruleName;

    /** 命中版启用之日（含） */
    @ApiModelProperty(value = "命中版启用之日（含）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effStart;

    /** 命中版交棒之日(不含)，空表示至今仍现行 */
    @ApiModelProperty(value = "命中版交棒之日(不含)")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effEnd;

    /** 命中版顺位 */
    @ApiModelProperty(value = "命中版顺位")
    private Integer priority;

    /** 提示语（未命中/无法判档都直说） */
    @ApiModelProperty(value = "提示语")
    private String message;

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public BigDecimal getInput() {
        return input;
    }

    public void setInput(BigDecimal input) {
        this.input = input;
    }

    public Date getAt() {
        return at;
    }

    public void setAt(Date at) {
        this.at = at;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public Date getEffEnd() {
        return effEnd;
    }

    public void setEffEnd(Date effEnd) {
        this.effEnd = effEnd;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
