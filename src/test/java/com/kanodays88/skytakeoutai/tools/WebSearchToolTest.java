package com.kanodays88.skytakeoutai.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WebSearchToolTest {

    @Autowired
    private WebSearchTool webSearch;

    @Test
    public void testTool(){
        try{
            String s = webSearch.webSearch("上海旅游景点推荐");
            System.out.println(s);
        }catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}