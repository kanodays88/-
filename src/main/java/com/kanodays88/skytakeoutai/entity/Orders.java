package com.kanodays88.skytakeoutai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 订单表
 * @TableName orders
 */
@TableName(value ="orders")
@Data
public class Orders {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号
     */
    private String number;

    /**
     * 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款
     */
    private Integer status;

    /**
     * 下单用户
     */
    private Long userId;

    /**
     * 地址id
     */
    private Long addressBookId;

    /**
     * 下单时间
     */
    private LocalDateTime orderTime;

    /**
     * 结账时间
     */
    private LocalDateTime checkoutTime;

    /**
     * 支付方式 1微信,2支付宝
     */
    private Integer payMethod;

    /**
     * 支付状态 0未支付 1已支付 2退款
     */
    private Integer payStatus;

    /**
     * 实收金额
     */
    private BigDecimal amount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 地址
     */
    private String address;

    /**
     * 用户名称
     */
    private String userName;

    /**
     * 收货人
     */
    private String consignee;

    /**
     * 订单取消原因
     */
    private String cancelReason;

    /**
     * 订单拒绝原因
     */
    private String rejectionReason;

    /**
     * 订单取消时间
     */
    private LocalDateTime cancelTime;

    /**
     * 预计送达时间
     */
    private LocalDateTime estimatedDeliveryTime;

    /**
     * 配送状态  1立即送出  0选择具体时间
     */
    private Integer deliveryStatus;

    /**
     * 送达时间
     */
    private LocalDateTime deliveryTime;

    /**
     * 打包费
     */
    private Integer packAmount;

    /**
     * 餐具数量
     */
    private Integer tablewareNumber;

    /**
     * 餐具数量状态  1按餐量提供  0选择具体数量
     */
    private Integer tablewareStatus;

    /**
     * 删除标识
     */
    private Integer deleteId;

    /**
     * 乐观锁版本号
     */
    private Integer version;
}