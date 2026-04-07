package com.kanodays88.skytakeoutai.entity.vo;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;

@Data
public class DishVO {
    @ToolParam(description = "菜品名称")
    private String name;
    @ToolParam(description = "菜品价格")
    private BigDecimal price;
    @ToolParam(description = "菜品分类名")
    private String categoryName;
    @ToolParam(description = "菜品图片链接")
    private String image;
}
