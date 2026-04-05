package com.kanodays88.skytakeoutai.entity.querys;

import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

public class OrderQuery {

    @ToolParam(required = false,description = "订单所含菜品的名称,和其对应的数量")
    private Map<String,Integer> dishes;

    @ToolParam(required = false,description = "订单的配送地址")
    private String address;

    @ToolParam(required = false,description = "订单所有者的电话号码")
    private String phone;

    @ToolParam(required = false,description = "订单备注")
    private String remark;
}
