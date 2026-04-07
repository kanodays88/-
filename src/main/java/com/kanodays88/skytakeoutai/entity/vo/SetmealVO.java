package com.kanodays88.skytakeoutai.entity.vo;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.List;
@Data
public class SetmealVO {
    @ToolParam(description = "套餐名称")
    private String name;
    @ToolParam(description = "套餐价格")
    private BigDecimal price;
    @ToolParam(description = "套餐分类名")
    private String categoryName;
    @ToolParam(description = "套餐图片链接")
    private String image;
    @ToolParam(description = "套餐所含菜品的名称")
    private List<String> dishesName;
}
