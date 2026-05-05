package com.kanodays88.skytakeoutai.agent.router;

import java.util.List;

public record RouteDecision(
        QuestionType questionType,      // 问题类型
        double confidence,              // 置信度 0.0-1.0
        String reason,                  // 判定理由
        List<String> missingInfo        // 缺失信息列表（AMIGUOUS时有值）
) {}