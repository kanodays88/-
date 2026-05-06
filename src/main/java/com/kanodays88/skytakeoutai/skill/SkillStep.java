package com.kanodays88.skytakeoutai.skill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skill 执行步骤 —— 描述一个业务技能中按顺序执行的每一步操作。
 * <p>
 * 对应 Markdown Skill 文件中 ## Execution Flow 下的编号列表项，
 * 经 {@link SkillLoader#parseSteps} 解析而来。
 * <p>
 * 当 PlanExecute 进行任务分解时，Skill 的执行步骤可作为参考上下文
 * 注入到 LLM 的 system prompt 中，指导任务拆分方向。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillStep {

    /** 步骤序号（从 1 开始递增） */
    private int order;

    /** 步骤名称，如 "查询菜品信息"、"收集配送信息" */
    private String name;

    /** 步骤详细描述，说明该步骤要做什么 */
    private String description;

    /** 该步骤建议使用的工具名列表，如 ["dishTool", "orderTool"] */
    private List<String> tools;
}
