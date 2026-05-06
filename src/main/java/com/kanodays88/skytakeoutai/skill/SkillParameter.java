package com.kanodays88.skytakeoutai.skill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 参数定义 —— 描述一个业务技能需要或可选的输入参数。
 * <p>
 * 对应 Markdown Skill 文件中 ## Required Parameters / ## Optional Parameters
 * 表格中的每一行数据，经 {@link SkillLoader#parseParameterTable} 解析而来。
 * <p>
 * 主要用于两处：
 * <ul>
 *   <li>RouterAgent：检查用户输入是否已提供必需参数，判断是否 AMBIGUOUS</li>
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

    /** 是否必需参数。true 来自 Required Parameters 表格，false 来自 Optional Parameters 表格 */
    private boolean required;
}
