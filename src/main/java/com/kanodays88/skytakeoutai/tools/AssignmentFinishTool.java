package com.kanodays88.skytakeoutai.tools;

import org.springframework.ai.tool.annotation.Tool;

public class AssignmentFinishTool {

    @Tool(description = "任务完成或无法继续时，调用此工具终止交互。")
    public String assignmentFinish(){
        return "任务结束";
    }
}
