package com.kanodays88.skytakeoutai.config;

import com.kanodays88.skytakeoutai.tools.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegistration {

    @Autowired
    private AssignmentFinishTool assignmentFinishTool;
    @Autowired
    private DishTool dishTool;
    @Autowired
    private FileOperationTool fileOperationTool;
    @Autowired
    private LoadReferenceTool loadReferenceTool;
    @Autowired
    private OrderTool orderTool;
    @Autowired
    private PDFGenerationTool pdfGenerationTool;
    @Autowired
    private ResourceDownloadTool resourceDownloadTool;
    @Autowired
    private SetmealTool setmealTool;
    @Autowired
    private TimeTool timeTool;
    @Autowired
    private WebSearchTool webSearchTool;

    /**
     * 工厂模式统一注册工具
     * @return
     */
    @Bean
    public ToolCallback[] allTools(){
        return ToolCallbacks.from(
                dishTool,
                orderTool,
                setmealTool,
                timeTool,
                assignmentFinishTool,
                fileOperationTool,
                pdfGenerationTool,
                resourceDownloadTool,
//                webScrapingTool,网页爬取工具由于返回的上下文太长，会导致被阿里拒绝访问
                webSearchTool
        );
    }
}
