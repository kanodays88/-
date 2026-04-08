package com.kanodays88.skytakeoutai.entity.querys;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SetmealQuery {

    //         不必须             对这个属性的信息说明，给LLM看
    @ToolParam(required = false,description = "套餐的分类类别")
    private List<String> category;
    @ToolParam(required = false,description = "用户可接受的最便宜的菜品价格")
    private BigDecimal minPrice;
    @ToolParam(required = false,description = "用户可接受的最贵的菜品价格")
    private BigDecimal maxPrice;
    @ToolParam(required = false,description = "多个菜品的名称")
    private List<String> dishNames;
    @ToolParam(required = false,description = "套餐名称,当用户指定套餐名称时才会使用")
    private List<String> setmealNames;
}
