package com.kanodays88.skytakeoutai.tools;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.db.sql.Order;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.kanodays88.skytakeoutai.entity.OrderDetail;
import com.kanodays88.skytakeoutai.entity.Orders;
import com.kanodays88.skytakeoutai.entity.querys.OrderQuery;
import com.kanodays88.skytakeoutai.entity.vo.OrderVO;
import com.kanodays88.skytakeoutai.mapper.OrderDetailMapper;
import com.kanodays88.skytakeoutai.mapper.OrdersMapper;
import com.kanodays88.skytakeoutai.service.OrderDetailService;
import com.kanodays88.skytakeoutai.service.OrdersService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrderTool {

    @Autowired
    private OrdersService ordersServiceImpl;

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private OrderDetailService orderDetailServiceImpl;


    @Tool(description = "生成订单工具")
    @Transactional
    public OrderVO makeOrder(@ToolParam(description = "生成订单所需信息")OrderQuery orderQuery){
        Orders orders = new Orders();
        orders.setNumber(String.valueOf(System.currentTimeMillis()));//订单号使用时间戳生成
        orders.setAddress(orderQuery.getAddress());
        orders.setPhone(orderQuery.getPhone());
        orders.setRemark(orderQuery.getRemark());
        orders.setOrderTime(LocalDateTime.now());
        orders.setUserId(-1L);
        orders.setAddressBookId(-1L);


        //遍历菜品和套餐，获取总额
        BigDecimal amount = BigDecimal.valueOf(0);
        if(orderQuery.getDishesName() != null && !orderQuery.getDishesName().isEmpty()){
            for(String d:orderQuery.getDishesName()){
                BigDecimal bigDecimal = orderQuery.getDishesPrice().get(d);
                Integer number = orderQuery.getDishesNumber().get(d);
                BigDecimal multiply = bigDecimal.multiply(new BigDecimal(number));
                amount = amount.add(multiply);
            }
        }
        if(orderQuery.getSetmealsName() != null && !orderQuery.getSetmealsName().isEmpty()){
            for(String s:orderQuery.getSetmealsName()){
                BigDecimal bigDecimal = orderQuery.getSetmealsPrice().get(s);
                Integer number = orderQuery.getSetmealsNumber().get(s);
                BigDecimal multiply = bigDecimal.multiply(new BigDecimal(number));
                amount = amount.add(multiply);
            }
        }

        orders.setAmount(amount);

        //生成订单
        int rows = ordersMapper.insert(orders);//自带主键回显
        if(rows <= 0){
            throw new RuntimeException("订单生成错误");
        }

        //生成订单详细项
        if(orderQuery.getDishesName() != null && !orderQuery.getDishesName().isEmpty()){
            for(String d:orderQuery.getDishesName()){
                OrderDetail orderDetail = new OrderDetail();
                orderDetail.setOrderId(orders.getId());
                orderDetail.setName(d);
                orderDetail.setDishId(0L);
                orderDetail.setSetmealId(-1L);
                orderDetail.setNumber(orderQuery.getDishesNumber().get(d));
                orderDetail.setImage(orderQuery.getDishesImage().get(d));

                BigDecimal bigDecimal = orderQuery.getDishesPrice().get(d);
                Integer number = orderQuery.getDishesNumber().get(d);
                BigDecimal multiply = bigDecimal.multiply(new BigDecimal(number));

                orderDetail.setAmount(multiply);

                rows = orderDetailMapper.insert(orderDetail);
                if(rows <= 0){
                    throw new RuntimeException("订单详细项生成错误");
                }
            }
        }
        if(orderQuery.getSetmealsName() != null && !orderQuery.getSetmealsName().isEmpty()){
            for(String s:orderQuery.getSetmealsName()){
                OrderDetail orderDetail = new OrderDetail();
                orderDetail.setOrderId(orders.getId());
                orderDetail.setName(s);
                orderDetail.setDishId(-1L);
                orderDetail.setSetmealId(0L);
                orderDetail.setNumber(orderQuery.getSetmealsNumber().get(s));
                orderDetail.setImage(orderQuery.getSetmealsImage().get(s));

                BigDecimal bigDecimal = orderQuery.getSetmealsPrice().get(s);
                Integer number = orderQuery.getSetmealsNumber().get(s);
                BigDecimal multiply = bigDecimal.multiply(new BigDecimal(number));

                orderDetail.setAmount(multiply);

                rows = orderDetailMapper.insert(orderDetail);
                if(rows <= 0){
                    throw new RuntimeException("订单详细项生成错误");
                }
            }
        }

        //包装订单信息返回
        OrderVO orderVO = new OrderVO();
        BeanUtil.copyProperties(orders,orderVO);
        orderVO.setDishes(orderQuery.getDishesNumber());
        orderVO.setSetmeals(orderQuery.getSetmealsNumber());
        orderVO.setDishesImage(orderQuery.getDishesImage());
        orderVO.setSetmealsImage(orderQuery.getSetmealsImage());

        return orderVO;

    }


    @Tool(description = "查询订单工具")
    public List<OrderVO> queryOrder(@ToolParam(description = "用于查询订单的电话号码") String phone){
        List<Orders> orders = ordersServiceImpl.query().eq("phone", phone).list();

        List<OrderVO> orderVOS = new ArrayList<>();

        for(Orders o:orders){
            List<OrderDetail> orderDetails = orderDetailServiceImpl.query().eq("order_id", o.getId()).list();
            Map<String,Integer> dishesNumber = new HashMap<>();
            Map<String,Integer> setmealsNumber = new HashMap<>();
            Map<String,String> dishesImage = new HashMap<>();
            Map<String,String> setmealsImage = new HashMap<>();

            for(OrderDetail od:orderDetails){
                if(od.getDishId() == 0L){
                    dishesNumber.put(od.getName(),od.getNumber());
                    dishesImage.put(od.getName(),od.getImage());
                }
                else if(od.getSetmealId() == 0L){
                    setmealsNumber.put(od.getName(),od.getNumber());
                    setmealsImage.put(od.getName(),od.getImage());
                }
            }
            OrderVO orderVO = new OrderVO();
            BeanUtil.copyProperties(o,orderVO);
            orderVO.setDishes(dishesNumber);
            orderVO.setSetmeals(setmealsNumber);
            orderVO.setDishesImage(dishesImage);
            orderVO.setSetmealsImage(setmealsImage);

            orderVOS.add(orderVO);
        }

        return orderVOS;
    }

    @Tool(description = "删除订单工具,会返回删除的订单的信息")
    @Transactional
    public OrderVO removeOrder(@ToolParam(description = "删除订单对应的订单号") String orderNumber){
        List<Orders> orders = ordersServiceImpl.query().eq("number", orderNumber).list();

        Long orderId = orders.get(0).getId();
        LambdaUpdateWrapper<OrderDetail> wrapper = new LambdaUpdateWrapper<>();
        List<OrderDetail> orderDetails = orderDetailServiceImpl.query().eq("order_id", orderId).list();
        int rows = orderDetailMapper.delete(wrapper.eq(OrderDetail::getOrderId, orderId));
        if(rows <= 0) throw new RuntimeException("删除订单详细项失败");

        LambdaUpdateWrapper<Orders> wrapper1 = new LambdaUpdateWrapper<>();
        rows = ordersMapper.delete(wrapper1.eq(Orders::getNumber,orderNumber));
        if(rows <= 0) throw new RuntimeException("删除订单失败");

        OrderVO orderVO = new OrderVO();
        BeanUtil.copyProperties(orders.get(0),orderVO);

        Map<String,Integer> dishesNumber = new HashMap<>();
        Map<String,Integer> setmealsNumber = new HashMap<>();
        Map<String,String> dishesImage = new HashMap<>();
        Map<String,String> setmealsImage = new HashMap<>();

        for(OrderDetail od:orderDetails){
            if(od.getDishId() == 0L){
                dishesNumber.put(od.getName(),od.getNumber());
                dishesImage.put(od.getName(),od.getImage());
            }
            else if(od.getSetmealId() == 0L){
                setmealsNumber.put(od.getName(),od.getNumber());
                setmealsImage.put(od.getName(),od.getImage());
            }
        }
        orderVO.setDishes(dishesNumber);
        orderVO.setSetmeals(setmealsNumber);
        orderVO.setDishesImage(dishesImage);
        orderVO.setSetmealsImage(setmealsImage);

        return orderVO;

    }


}
