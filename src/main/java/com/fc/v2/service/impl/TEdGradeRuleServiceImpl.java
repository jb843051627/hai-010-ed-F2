package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TEdGradeRuleMapper;
import com.fc.v2.model.auto.TEdGradeRule;
import com.fc.v2.model.custom.grade.GradeEvalResult;
import com.fc.v2.service.ITEdGradeRuleService;

/**
 * 成绩评定判定线 Service 业务层处理（rule-eval 形状）。
 *
 * <p>判定契约见 {@link ITEdGradeRuleService}：半开窗口、顺位定序、界值（含）压线归上、
 * 未命中判 0 不凑数。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TEdGradeRuleServiceImpl implements ITEdGradeRuleService {

    /** 现行版定序：顺位数值大的先说话；并列按启用晚的、再按主键大的，次序钉死 */
    private static final Comparator<TEdGradeRule> AVAILABLE_ORDER = Comparator
            .comparing(TEdGradeRule::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TEdGradeRule::getEffStart, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TEdGradeRule::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    @javax.annotation.Resource
    private TEdGradeRuleMapper edGradeRuleMapper;

    @Override
    public TEdGradeRule selectTEdGradeRuleById(Long id) {
        return this.edGradeRuleMapper.selectById(id);
    }

    @Override
    public List<TEdGradeRule> listAvailable(Date at) {
        List<TEdGradeRule> out = new ArrayList<TEdGradeRule>();
        if (at == null) {
            return out;
        }
        QueryWrapper<TEdGradeRule> q = new QueryWrapper<TEdGradeRule>();
        q.eq("del_flag", 0).eq("status", 0);
        List<TEdGradeRule> rows = this.edGradeRuleMapper.selectList(q);
        if (rows != null) {
            for (TEdGradeRule r : rows) {
                if (inWindow(r, at)) {
                    out.add(r);
                }
            }
        }
        // 不依赖库返回顺序，就地按契约钉死
        out.sort(AVAILABLE_ORDER);
        return out;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        return evaluateDetail(ruleCode, input, at).getLevel();
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        return evaluateTopDetail(input, at).getLevel();
    }

    @Override
    public GradeEvalResult evaluateDetail(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            GradeEvalResult rs = new GradeEvalResult();
            rs.setRuleCode(ruleCode);
            rs.setInput(input);
            rs.setAt(at);
            return miss(rs, "入参不全（判定线代号/判定数值/判定时刻缺一），无法判档");
        }
        // 只在该时点生效的版本里挑；列表已按顺位定序，同代号取到即是最优先那一版
        TEdGradeRule hit = null;
        for (TEdGradeRule r : listAvailable(at)) {
            if (ruleCode.equals(r.getRuleCode())) {
                hit = r;
                break;
            }
        }
        GradeEvalResult rs = judge(hit, input, at);
        if (!rs.isAvailable() && rs.getMessage() == null) {
            rs.setRuleCode(ruleCode);
            rs.setMessage("代号 " + ruleCode + " 在该时点没有生效版本，未判档");
        }
        return rs;
    }

    @Override
    public GradeEvalResult evaluateTopDetail(BigDecimal input, Date at) {
        if (input == null || at == null) {
            GradeEvalResult rs = new GradeEvalResult();
            rs.setInput(input);
            rs.setAt(at);
            return miss(rs, "入参不全（判定数值/判定时刻缺一），无法判档");
        }
        List<TEdGradeRule> avail = listAvailable(at);
        TEdGradeRule top = avail.isEmpty() ? null : avail.get(0);
        GradeEvalResult rs = judge(top, input, at);
        if (!rs.isAvailable() && rs.getMessage() == null) {
            rs.setMessage("该时点没有任何生效的判定线，未判档");
        }
        return rs;
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TEdGradeRule r = this.edGradeRuleMapper.selectById(id);
        if (r == null) {
            return false;
        }
        if (r.getDelFlag() == null || r.getDelFlag().intValue() != 0) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus().intValue() != 0) {
            return false;
        }
        return inWindow(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /**
     * 生效窗口 [eff_start, eff_end)：启用当日（含）即现行，交棒当日（不含）起让位。
     * 起为空视为自古生效，止为空表示至今仍现行。
     */
    private boolean inWindow(TEdGradeRule r, Date at) {
        Date s = r.getEffStart();
        if (s != null && s.after(at)) {
            return false;
        }
        Date e = r.getEffEnd();
        if (e != null && !e.after(at)) {
            return false;
        }
        return true;
    }

    /**
     * 命中即判档并回显那一版；未命中（或命中版无界值可判）level 恒 0，直说原因。
     */
    private GradeEvalResult judge(TEdGradeRule rule, BigDecimal input, Date at) {
        GradeEvalResult rs = new GradeEvalResult();
        rs.setInput(input);
        rs.setAt(at);
        if (rule == null) {
            rs.setLevel(0);
            return rs;
        }
        rs.setRuleId(rule.getId());
        rs.setRuleCode(rule.getRuleCode());
        rs.setRuleName(rule.getRuleName());
        rs.setEffStart(rule.getEffStart());
        rs.setEffEnd(rule.getEffEnd());
        rs.setPriority(rule.getPriority());
        if (rule.getTh1Max() == null && rule.getTh2Max() == null && rule.getTh3Max() == null) {
            rs.setLevel(0);
            rs.setMessage("命中版三档界值均未配置，无法判档");
            return rs;
        }
        rs.setAvailable(true);
        rs.setLevel(levelOf(rule, input));
        rs.setMessage("已按命中版判档");
        return rs;
    }

    private GradeEvalResult miss(GradeEvalResult rs, String message) {
        rs.setAvailable(false);
        rs.setLevel(0);
        rs.setMessage(message);
        return rs;
    }

    /** 分档：界值（含），压线归上一档。1优秀 2良好 3及格 4不及格 */
    private int levelOf(TEdGradeRule rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) >= 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) >= 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) >= 0) {
            return 3;
        }
        return 4;
    }
}
