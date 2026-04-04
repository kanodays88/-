package com.kanodays88.skytakeoutai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kanodays88.skytakeoutai.entity.Orders;
import com.kanodays88.skytakeoutai.service.OrdersService;
import com.kanodays88.skytakeoutai.mapper.OrdersMapper;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【orders(订单表)】的数据库操作Service实现
* @createDate 2026-04-03 21:42:00
*/
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders>
    implements OrdersService{

}




