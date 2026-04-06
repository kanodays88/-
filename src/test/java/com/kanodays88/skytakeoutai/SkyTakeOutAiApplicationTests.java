package com.kanodays88.skytakeoutai;

import com.kanodays88.skytakeoutai.entity.Dish;
import com.kanodays88.skytakeoutai.entity.querys.DishQuery;
import com.kanodays88.skytakeoutai.entity.querys.OrderQuery;
import com.kanodays88.skytakeoutai.entity.vo.OrderVO;
import com.kanodays88.skytakeoutai.tools.DishTool;
import com.kanodays88.skytakeoutai.tools.OrderTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
class SkyTakeOutAiApplicationTests {

    @Autowired
    private DishTool dishTool;

    @Autowired
    private OrderTool orderTool;

    @Test
    void contextLoads() {
//
//        OrderQuery orderQuery = new OrderQuery();
//        orderQuery.setAddress("aaa");
//        orderQuery.setPhone("1222222");
//        List<String> dishes = new ArrayList<>();
//        dishes.add("馒头");
//        orderQuery.setDishesName(dishes);
//        Map<String,Integer> dishNumber = new HashMap<>();
//        dishNumber.put("馒头",1);
//        orderQuery.setDishesNumber(dishNumber);
//        Map<String, BigDecimal> dishPrice = new HashMap<>();
//        dishPrice.put("馒头",BigDecimal.valueOf(1));
//        orderQuery.setDishesPrice(dishPrice);
//
//        OrderVO orderVO = orderTool.makeOrder(orderQuery);
//        System.out.println(orderVO);

        List<OrderVO> orderVOS = orderTool.queryOrder("114514");
        System.out.println(orderVOS);

    }

}
