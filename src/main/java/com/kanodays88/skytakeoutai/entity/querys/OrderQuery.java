package com.kanodays88.skytakeoutai.entity.querys;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class OrderQuery {

    @ToolParam(required = false,description = "订单所含菜品名称")
    private List<String> dishesName;

    @ToolParam(required = false,description = "订单所含套餐名称")
    private List<String> setmealsName;

    @ToolParam(required = false,description = "订单所含的菜品名称，和其所对应的单价")
    private Map<String, BigDecimal> dishesPrice;

    @ToolParam(required = false,description = "订单所含套餐名称，和其所对应的单价")
    private Map<String,BigDecimal> setmealsPrice;

    @ToolParam(required = false,description = "订单所含菜品的名称,和其对应的数量")
    private Map<String,Integer> dishesNumber;

    @ToolParam(required = false,description = "订单所含套餐的名称,和其对应的数量")
    private Map<String,Integer> setmealsNumber;

    @ToolParam(required = false,description = "订单所含菜品的名称,和其图片的链接")
    private Map<String,String> dishesImage;

    @ToolParam(required = false,description = "订单所含套餐的名称,和其图片的链接")
    private Map<String,String> setmealsImage;

    @ToolParam(required = true,description = "订单的配送地址")
    private String address;

    @ToolParam(required = true,description = "订单所有者的电话号码")
    private String phone;

    @ToolParam(required = false,description = "订单备注")
    private String remark;
}
