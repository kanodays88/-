package com.kanodays88.skytakeoutai.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class OrderVO {

    //订单号
    private String number;

    //电话号码
    private String phone;

    //下单时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")//用于自定义日期、时间、数字等类型在 JSON 与 Java 对象之间的转换格式
    private LocalDateTime orderTime;

    //配送地址
    private String address;

    //实收金额
    private BigDecimal amount;

    //订单备注
    private String remark;

    //订单菜品，以及份数
    private Map<String,Integer> dishes;

    //订单套餐，以及份数
    private Map<String,Integer> setmeals;

}
