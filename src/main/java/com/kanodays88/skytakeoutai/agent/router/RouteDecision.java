package com.kanodays88.skytakeoutai.agent.router;

import com.kanodays88.skytakeoutai.skill.Skill;

import java.util.List;

/**
 * 路由决策结果 —— 记录 RouterAgent 对用户输入的分类判定。
 * <p>
 * 包含问题类型（SIMPLE_CHAT / COMPLEX_TASK / AMBIGUOUS 等）、
 * 判定理由、缺失信息列表，以及匹配到的全部业务技能名称。
 * <p>
 * matchedSkillNames 字段用于将路由结果传递给 PlanExecute，
 * 使其能按技能定义的执行流程进行任务分解（Agent Skill Reference）。
 */
public record RouteDecision(
        QuestionType questionType,      // 问题类型
        String reason,                  // 判定理由
        List<String> missingInfo,       // 缺失信息列表（AMIGUOUS时有值）
        String mianTask                 //总结出这次对话的总任务
) {}