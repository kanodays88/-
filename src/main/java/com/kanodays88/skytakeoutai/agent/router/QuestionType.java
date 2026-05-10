package com.kanodays88.skytakeoutai.agent.router;

public enum QuestionType {
    SIMPLE_CHAT,      // 简单闲聊/打招呼
    COMPLEX_TASK,     // 需要Plan+Execute+ReAct的复杂任务
    AMBIGUOUS         // 问题模糊，需要反问澄清
}