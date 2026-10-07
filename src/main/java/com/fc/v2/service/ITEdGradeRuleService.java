package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TEdGradeRule;
import com.fc.v2.model.custom.grade.GradeEvalResult;

/**
 * TEdGradeRule 业务层处理接口。
 *
 * <p>判定契约（与 t_ed_grade_rule 列注释一致）：</p>
 * <ul>
 *   <li>档位：1优秀 2良好 3及格 4不及格；0 一律表示「未命中」，不是档位。</li>
 *   <li>界值（含）：压线归上一档，如 90 分判优秀不判良好。</li>
 *   <li>生效窗口 [eff_start, eff_end)：启用当日即归新版，交棒当日（不含）起归下一版。</li>
 *   <li>同一时刻多条现行：priority 数值大的先说话；并列按启用晚的、主键大的，次序钉死，
 *       同样的输入任何时刻查都是同一个结果。</li>
 *   <li>那一刻没有生效版本就如实判 0（available=false），绝不拿别的版本凑数。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITEdGradeRuleService {

    /** selectTEdGradeRuleById */
    TEdGradeRule selectTEdGradeRuleById(Long id);

    /** listAvailable：那一刻全部现行判定线，按「顺位大→启用晚→主键大」排定 */
    List<TEdGradeRule> listAvailable(Date at);

    /** evaluate：按该代号在 at 时刻生效的那一版判档；未命中返回 0 */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** evaluateTop：取 at 时刻顺位最前的现行判定线判档；未命中返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /** evaluateDetail：同 evaluate，但回显命中版（审计对账用） */
    GradeEvalResult evaluateDetail(String ruleCode, BigDecimal input, Date at);

    /** evaluateTopDetail：同 evaluateTop，但回显命中版（审计对账用） */
    GradeEvalResult evaluateTopDetail(BigDecimal input, Date at);

    /** usable：该版在 at 时刻是否现行（未删、在役、落在生效窗口内） */
    boolean usable(Long id, Date at);

    /** countAvailable */
    int countAvailable(Date at);

}
