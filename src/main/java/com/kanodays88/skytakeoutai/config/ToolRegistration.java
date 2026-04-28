package com.kanodays88.skytakeoutai.config;

import com.kanodays88.skytakeoutai.tools.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegistration {

    /**
     * 工厂模式统一注册工具
     * @return
     */
    @Bean
    public ToolCallback[] allTools(WebSearchTool webSearchTool){
        AssignmentFinishTool assignmentFinishTool = new AssignmentFinishTool();
        FileOperationTool fileOperationTool = new FileOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        DishTool dishTool = new DishTool();
        OrderTool orderTool = new OrderTool();
        SetmealTool setmealTool = new SetmealTool();
        return ToolCallbacks.from(
                dishTool,
                orderTool,
                setmealTool,
                assignmentFinishTool,
                fileOperationTool,
                pdfGenerationTool,
                resourceDownloadTool,
//                webScrapingTool,网页爬取工具由于返回的上下文太长，会导致被阿里拒绝访问
                webSearchTool
        );
    }
}
