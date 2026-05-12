package com.kanodays88.skytakeoutai.agent.plan;

import java.util.Set;

/**
 * 2. 子任务契约：核心解决「蒸馏不丢下游信息」的关键
 */
public record SubTask(
        // 子任务序号（全局唯一）
        int taskId,
        // 子任务名称
        String taskName,
        // 子任务执行内容
        String taskContent,
        // 【核心】所有依赖该子任务的下游任务ID（不止紧邻的下一个）
        Set<Integer> downstreamTaskIds,
        // 【核心】所有下游任务要求必须输出核心内容（蒸馏时绝对不能删）
        Set<String> coreContent,
        // 该任务需要调用的工具
        Set<String> toolNames
) {}
