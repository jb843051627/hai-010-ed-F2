package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.ed.GradeRuleIndeterminateException;
import com.fc.v2.common.exception.ed.GradeRuleIndeterminateException.Reason;
import com.fc.v2.mapper.auto.TEdGradeRuleMapper;
import com.fc.v2.model.auto.TEdGradeRule;
import com.fc.v2.model.custom.GradeDecision;
import com.fc.v2.service.ITEdGradeRuleService;

/**
 * 成绩评定判定线 Service 业务层处理（rule-eval 形状）。
 *
 * <p>口径定死如下：</p>
 * <ul>
 *   <li>生效时段<b>左闭右开</b> {@code [effStart, effEnd)}：启用之日当天归新版，
 *       交棒之日当天归下一版，两版首尾相接、不重不漏；</li>
 *   <li>判档必须按<b>判定时点</b>挑那一版，绝不拿全局最新版去算旧学期；</li>
 *   <li>同一时点无生效版本（空档），或有多条规则顺位并列时，
 *       抛 {@link GradeRuleIndeterminateException}，不凭空给档；</li>
 *   <li>多条规则并存时按 priority 降序（null 视为 0）说话，
 *       再以 effStart 降序、id 升序兜底，保证同一输入任意次刷新结果一致；</li>
 *   <li>界值含本点：达到某档界值即入该档，压线往上一档算。</li>
 * </ul>
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
        for (TEdGradeRule r : rows) {
            if (isEffective(r, at)) {
                out.add(r);
            }
        }
        // 顺序定死：顺位降序 → 启用之日降序 → id 升序，库返回顺序不再影响结果
        Collections.sort(out, RULE_ORDER);
        return out;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        return decide(ruleCode, input, at).getLevel();
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        return decideTop(input, at).getLevel();
    }

    @Override
    public GradeDecision decide(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            throw new IllegalArgumentException("ruleCode 不能为空");
        }
        if (input == null) {
            throw new IllegalArgumentException("input 不能为空");
        }
        if (at == null) {
            throw new IllegalArgumentException("判定时点 at 不能为空");
        }
        TEdGradeRule rule = findVersion(ruleCode, at);
        return toDecision(rule, input, at);
    }

    @Override
    public GradeDecision decideTop(BigDecimal input, Date at) {
        if (input == null) {
            throw new IllegalArgumentException("input 不能为空");
        }
        if (at == null) {
            throw new IllegalArgumentException("判定时点 at 不能为空");
        }
        List<TEdGradeRule> avail = listAvailable(at);
        if (avail.isEmpty()) {
            throw new GradeRuleIndeterminateException(null, at, Reason.NONE,
                    Collections.<Long>emptyList());
        }
        TEdGradeRule top = avail.get(0);
        // 最高顺位若有并列，拒绝猜一条——交棒次序/优先级必须先在数据里理清楚
        List<Long> tied = new ArrayList<Long>();
        tied.add(top.getId());
        for (int i = 1; i < avail.size(); i++) {
            TEdGradeRule other = avail.get(i);
            if (priorityOf(other) == priorityOf(top)) {
                tied.add(other.getId());
            } else {
                break;
            }
        }
        if (tied.size() > 1) {
            throw new GradeRuleIndeterminateException(null, at, Reason.AMBIGUOUS, tied);
        }
        return toDecision(top, input, at);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TEdGradeRule r = this.edGradeRuleMapper.selectById(id);
        if (r == null
                || r.getDelFlag() == null || r.getDelFlag().intValue() != 0
                || r.getStatus() == null || r.getStatus().intValue() != 0) {
            return false;
        }
        return isEffective(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /**
     * 按规则代号取判定时点上生效的那一版。
     * 空档报 NONE，同时刻多版重叠报 AMBIGUOUS——同一代号的版本只许首尾相接，不许并存。
     */
    private TEdGradeRule findVersion(String ruleCode, Date at) {
        QueryWrapper<TEdGradeRule> q = new QueryWrapper<TEdGradeRule>();
        q.eq("del_flag", 0).eq("rule_code", ruleCode).eq("status", 0);
        List<TEdGradeRule> rows = this.edGradeRuleMapper.selectList(q);
        List<TEdGradeRule> hits = new ArrayList<TEdGradeRule>();
        if (rows != null) {
            for (TEdGradeRule r : rows) {
                if (isEffective(r, at)) {
                    hits.add(r);
                }
            }
        }
        if (hits.isEmpty()) {
            throw new GradeRuleIndeterminateException(ruleCode, at, Reason.NONE,
                    Collections.<Long>emptyList());
        }
        Collections.sort(hits, RULE_ORDER);
        if (hits.size() > 1) {
            List<Long> ids = new ArrayList<Long>();
            for (TEdGradeRule r : hits) {
                ids.add(r.getId());
            }
            throw new GradeRuleIndeterminateException(ruleCode, at, Reason.AMBIGUOUS, ids);
        }
        return hits.get(0);
    }

    /**
     * 生效时段判定：左闭右开 [effStart, effEnd)。
     * 起点含（启用之日当天即归新版），终点不含（交棒之日当天归下一版）。
     * 任一端为 null 表示该方向不设界（沿用至今或自古有之）。
     */
    private boolean isEffective(TEdGradeRule r, Date at) {
        if (r.getEffStart() != null && at.before(r.getEffStart())) {
            return false;
        }
        if (r.getEffEnd() != null && !at.before(r.getEffEnd())) {
            return false;
        }
        return true;
    }

    private int priorityOf(TEdGradeRule r) {
        return r.getPriority() == null ? 0 : r.getPriority().intValue();
    }

    /** 说话次序：priority 降序（null 视同 0）→ effStart 晚的在前（null 垫底）→ id 升序（null 垫底） */
    private static final Comparator<TEdGradeRule> RULE_ORDER = new Comparator<TEdGradeRule>() {
        @Override
        public int compare(TEdGradeRule a, TEdGradeRule b) {
            int byPriority = Integer.compare(
                    b.getPriority() == null ? 0 : b.getPriority().intValue(),
                    a.getPriority() == null ? 0 : a.getPriority().intValue());
            if (byPriority != 0) {
                return byPriority;
            }
            int byStart = compareDateDescNullsLast(a.getEffStart(), b.getEffStart());
            if (byStart != 0) {
                return byStart;
            }
            return compareIdAscNullsLast(a.getId(), b.getId());
        }
    };

    private static int compareDateDescNullsLast(Date x, Date y) {
        if (x == null && y == null) {
            return 0;
        }
        if (x == null) {
            return 1;
        }
        if (y == null) {
            return -1;
        }
        return y.compareTo(x);
    }

    private static int compareIdAscNullsLast(Long x, Long y) {
        if (x == null && y == null) {
            return 0;
        }
        if (x == null) {
            return 1;
        }
        if (y == null) {
            return -1;
        }
        return x.compareTo(y);
    }

    /**
     * 分档：界值含本点，达线即入该档——压线往上一档算。
     * 依次比 优秀 → 良好 → 及格；某档界值未设则跳过该档；都够不着为 4（不及格）。
     * BigDecimal 用 compareTo，90 与 90.00 同值，压线不会因小数位被压下去。
     */
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

    private GradeDecision toDecision(TEdGradeRule rule, BigDecimal input, Date at) {
        GradeDecision d = new GradeDecision();
        d.setLevel(levelOf(rule, input));
        d.setRuleId(rule.getId());
        d.setRuleCode(rule.getRuleCode());
        d.setRuleName(rule.getRuleName());
        d.setEffStart(rule.getEffStart());
        d.setEffEnd(rule.getEffEnd());
        d.setEvaluatedAt(at);
        d.setInput(input);
        return d;
    }
}
