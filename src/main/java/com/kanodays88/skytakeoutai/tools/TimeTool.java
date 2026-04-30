package com.kanodays88.skytakeoutai.tools;

import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;

import java.time.LocalDateTime;

public class TimeTool {

    @Tool(description = "获取当前时间")
    public String getNowDate(){
        return JSONUtil.toJsonStr(LocalDateTime.now());
    }
}
