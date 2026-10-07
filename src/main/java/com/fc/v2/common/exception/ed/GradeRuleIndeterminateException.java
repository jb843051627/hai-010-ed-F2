package com.fc.v2.common.exception.ed;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 成绩判定线在某时点无法唯一确定时抛出。
 *
 * <p>两种情形：</p>
 * <ul>
 *   <li>{@link Reason#NONE}：该时点没有任何生效版本（生效空档），不得凭空给档；</li>
 *   <li>{@link Reason#AMBIGUOUS}：同一时点有多条规则并列、顺位无法分出先后，
 *       系统拒绝在候选中随便挑一条。</li>
 * </ul>
 *
 * <p>异常携带判定时点与全部候选版本编号，供审计留存依据。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
public class GradeRuleIndeterminateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** 该时点无生效版本（生效空档） */
        NONE,
        /** 多条规则同时生效且顺位并列，无法唯一确定 */
        AMBIGUOUS
    }

    private final String ruleCode;
    private final Date at;
    private final Reason reason;
    private final List<Long> candidateRuleIds;

    public GradeRuleIndeterminateException(String ruleCode, Date at, Reason reason,
                                           List<Long> candidateRuleIds) {
        super(buildMessage(ruleCode, at, reason, candidateRuleIds));
        this.ruleCode = ruleCode;
        this.at = at == null ? null : new Date(at.getTime());
        this.reason = reason;
        this.candidateRuleIds = candidateRuleIds == null
                ? Collections.<Long>emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<Long>(candidateRuleIds));
    }

    private static String buildMessage(String ruleCode, Date at, Reason reason,
                                       List<Long> candidateRuleIds) {
        StringBuilder sb = new StringBuilder("成绩判定线无法唯一确定：");
        sb.append("规则代号=").append(ruleCode);
        sb.append("，判定时点=").append(at);
        sb.append("，原因=").append(reason == Reason.NONE
                ? "该时点无生效版本（生效空档），不得判档"
                : "多条规则顺位并列，无法唯一确定");
        if (candidateRuleIds != null && !candidateRuleIds.isEmpty()) {
            sb.append("，候选规则ID=").append(candidateRuleIds);
        }
        return sb.toString();
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Date getAt() {
        return at == null ? null : new Date(at.getTime());
    }

    public Reason getReason() {
        return reason;
    }

    public List<Long> getCandidateRuleIds() {
        return candidateRuleIds;
    }
}
