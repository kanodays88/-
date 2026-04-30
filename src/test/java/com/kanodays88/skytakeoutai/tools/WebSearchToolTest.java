package com.kanodays88.skytakeoutai.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchToolTest {



    @Test
    public void testTool(){
        WebSearchTool webSearchTool = new WebSearchTool();
        try{
            String s = webSearchTool.webSearch("上海旅游景点推荐");
            System.out.println(s);
        }catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}