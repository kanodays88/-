package com.kanodays88.skytakeoutai.skill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 参数定义 —— 描述一个业务技能的输入参数。
 * <p>
 * 不再区分 Required / Optional 两张表格，统一使用单一参数列表，
 * 通过 {@code importance} 字段标注重要程度（"high" / "medium" / "low"），
 * LLM 根据 skill 的 Execution Flow 描述自行判断参数必要性。
 * <p>
 * 经 {@link SkillLoader#parseParameterTable} 解析而来。
 * <p>
 * 主要用于：
 * <ul>
 *   <li>PlanExecute：将参数清单注入 LLM 上下文，指引 LLM 收集缺失信息</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillParameter {

    /** 参数名，如 "items"、"address"、"phone" */
    private String name;

    /** 参数类型，如 "string"、"string[]"、"number" */
    private String type;

    /** 参数说明，描述该参数的用途和填写要求 */
    private String description;

    /** 参数重要程度: "high"(关键), "medium"(重要), "low"(可选) */
    private String importance;
}
