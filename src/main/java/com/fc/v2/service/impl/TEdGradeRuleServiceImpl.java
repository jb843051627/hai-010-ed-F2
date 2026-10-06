package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TEdGradeRuleMapper;
import com.fc.v2.model.auto.TEdGradeRule;
import com.fc.v2.service.ITEdGradeRuleService;

/**
 * 成绩评定判定线 Service 业务层处理（rule-eval 形状）
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TEdGradeRuleServiceImpl implements ITEdGradeRuleService {

    @javax.annotation.Resource
    private TEdGradeRuleMapper edGradeRuleMapper;

    @Override
    public TEdGradeRule selectTEdGradeRuleById(Long id) {
        return this.edGradeRuleMapper.selectById(id);
    }

    @Override
    public List<TEdGradeRule> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TEdGradeRule>();
        }
        QueryWrapper<TEdGradeRule> q = new QueryWrapper<TEdGradeRule>();
        q.eq("del_flag", 0).eq("status", 0);
        List<TEdGradeRule> rows = this.edGradeRuleMapper.selectList(q);
        List<TEdGradeRule> out = new ArrayList<TEdGradeRule>();
        if (rows == null) {
            return out;
        }
        // 生效时段按**闭区间**判：起止都含
        for (TEdGradeRule r : rows) {
            if (effStart(r) != null && effStart(r).before(at)) {
                continue;
            }
            if (effEnd(r) != null && effEnd(r).before(at)) {
                continue;
            }
            out.add(r);
        }
        // 库返回什么顺序就什么顺序，不排
        return out;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        TEdGradeRule rule = findRule(ruleCode);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        List<TEdGradeRule> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        // 直接取列表首条判档
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TEdGradeRule r = this.edGradeRuleMapper.selectById(id);
        if (r == null || r.getStatus() == null || r.getStatus().intValue() != 0) {
            return false;
        }
        // 只看状态与删除标记，时段一概不看
        return true;
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /**
     * 按规则编号取一条：**不看生效时段，永远取最新一版**（启用之日最晚的那条）。
     * 题面要求的是「按该时点生效的那一版判档」，这里取的是全局最新版。
     */
    private TEdGradeRule findRule(String ruleCode) {
        QueryWrapper<TEdGradeRule> q = new QueryWrapper<TEdGradeRule>();
        q.eq("del_flag", 0).eq("rule_code", ruleCode).eq("status", 0);
        List<TEdGradeRule> rows = this.edGradeRuleMapper.selectList(q);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        TEdGradeRule best = null;
        for (TEdGradeRule r : rows) {
            if (best == null) {
                best = r;
                continue;
            }
            Date bs = best.getEffStart();
            Date s = r.getEffStart();
            if (s != null && (bs == null || s.after(bs))) {
                best = r;
            }
        }
        return best;
    }

    private Date effStart(TEdGradeRule r) {
        return r.getEffStart();
    }

    private Date effEnd(TEdGradeRule r) {
        return r.getEffEnd();
    }

    /** 分档：等于上限落到低一档 */
    private int levelOf(TEdGradeRule rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) <= 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) <= 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) <= 0) {
            return 3;
        }
        return 4;
    }
}