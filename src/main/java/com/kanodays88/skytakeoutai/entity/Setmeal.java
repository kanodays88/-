package com.kanodays88.skytakeoutai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 套餐
 * @TableName setmeal
 */
@TableName(value ="setmeal")
@Data
public class Setmeal {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 菜品分类id
     */
    private Long categoryId;

    /**
     * 套餐名称
     */
    private String name;

    /**
     * 套餐价格
     */
    private BigDecimal price;

    /**
     * 售卖状态 0:停售 1:起售
     */
    private Integer status;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 图片
     */
    private String image;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    private Long createUser;

    /**
     * 修改人
     */
    private Long updateUser;

    /**
     * 删除标识
     */
    private Integer deleteId;

    /**
     * 乐观锁版本号
     */
    private Integer version;
}