package com.kanodays88.skytakeoutai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kanodays88.skytakeoutai.entity.OrderDetail;
import com.kanodays88.skytakeoutai.service.OrderDetailService;
import com.kanodays88.skytakeoutai.mapper.OrderDetailMapper;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【order_detail(订单明细表)】的数据库操作Service实现
* @createDate 2026-04-03 21:42:23
*/
@Service
public class OrderDetailServiceImpl extends ServiceImpl<OrderDetailMapper, OrderDetail>
    implements OrderDetailService{

}




