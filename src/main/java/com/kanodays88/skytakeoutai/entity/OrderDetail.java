package com.kanodays88.skytakeoutai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 订单明细表
 * @TableName order_detail
 */
@TableName(value ="order_detail")
@Data
public class OrderDetail {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 名字
     */
    private String name;

    /**
     * 图片
     */
    private String image;

    /**
     * 订单id
     */
    private Long orderId;

    /**
     * 菜品id
     */
    private Long dishId;

    /**
     * 套餐id
     */
    private Long setmealId;

    /**
     * 口味
     */
    private String dishFlavor;

    /**
     * 数量
     */
    private Integer number;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 删除标识
     */
    private Integer deleteId;

    /**
     * 乐观锁版本号
     */
    private Integer version;
}