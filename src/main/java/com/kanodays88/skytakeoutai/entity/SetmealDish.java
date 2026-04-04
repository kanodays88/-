package com.kanodays88.skytakeoutai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 套餐菜品关系
 * @TableName setmeal_dish
 */
@TableName(value ="setmeal_dish")
@Data
public class SetmealDish {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 套餐id
     */
    private Long setmealId;

    /**
     * 菜品id
     */
    private Long dishId;

    /**
     * 菜品名称 （冗余字段）
     */
    private String name;

    /**
     * 菜品单价（冗余字段）
     */
    private BigDecimal price;

    /**
     * 菜品份数
     */
    private Integer copies;

    /**
     * 删除标识
     */
    private Integer deleteId;

    /**
     * 乐观锁版本号
     */
    private Integer version;
}