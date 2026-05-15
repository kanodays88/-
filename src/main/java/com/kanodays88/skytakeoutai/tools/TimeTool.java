package com.kanodays88.skytakeoutai.tools;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TimeTool {

    @Tool(description = "获取当前时间")
    public String getNowDate(){
        return  DateUtil.format(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss");
    }
}
