package com.kanodays88.skytakeoutai;

import com.kanodays88.skytakeoutai.advisor.MyLoggerAdvisor;
import com.kanodays88.skytakeoutai.entity.Dish;
import com.kanodays88.skytakeoutai.entity.querys.DishQuery;
import com.kanodays88.skytakeoutai.entity.querys.OrderQuery;
import com.kanodays88.skytakeoutai.entity.querys.SetmealQuery;
import com.kanodays88.skytakeoutai.entity.vo.OrderVO;
import com.kanodays88.skytakeoutai.tools.DishTool;
import com.kanodays88.skytakeoutai.tools.OrderTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
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

    @Autowired
    private OpenAiChatModel model;

    @Test
    void contextLoads() {

        ChatClient chatClient = ChatClient.builder(model).defaultAdvisors(new MyLoggerAdvisor()).build();

        String s = chatClient.prompt("你好").call().content();



//
//        OrderQuery orderQuery = new OrderQuery();
//        orderQuery.setAddress("aaa");
//        orderQuery.setPhone("1222222");
//        List<String> dishes = new ArrayList<>();
//        dishes.add("馒头");
//        orderQuery.setDishesName(dishes);
//        Map<String,Integer> dishNumber = new HashMap<>();
//        dishNumber.put("馒头",2);
//        orderQuery.setDishesNumber(dishNumber);
//        Map<String, BigDecimal> dishPrice = new HashMap<>();
//        dishPrice.put("馒头",BigDecimal.valueOf(1));
//        orderQuery.setDishesPrice(dishPrice);
//        Map<String,String> dishImage = new HashMap<>();
//        dishImage.put("馒头","http://localhost:8081/17756319371133837.jpg");
//        orderQuery.setDishesImage(dishImage);
//        List<String> setmeals = new ArrayList<>();
//        setmeals.add("我是元神高手喵");
//        orderQuery.setSetmealsName(setmeals);
//        Map<String,Integer> setmealNumber = new HashMap<>();
//        setmealNumber.put("我是元神高手喵",1);
//        orderQuery.setSetmealsNumber(setmealNumber);
//        Map<String, BigDecimal> setmealPrice = new HashMap<>();
//        setmealPrice.put("我是元神高手喵",BigDecimal.valueOf(678));
//        orderQuery.setSetmealsPrice(setmealPrice);
//        Map<String,String> setmealImage = new HashMap<>();
//        setmealImage.put("我是元神高手喵","http://localhost:8081/17756328097712842.jpg");
//        orderQuery.setSetmealsImage(dishImage);
//
//
//
//        OrderVO orderVO = orderTool.makeOrder(orderQuery);
//        System.out.println(orderVO);

//        List<OrderVO> orderVOS = orderTool.queryOrder("114514");
//
//        SetmealQuery setmealQuery = new SetmealQuery();
//        setmealQuery.setCategory(new ArrayList<>());
//        List<String>
//        setmealQuery.setCategory();
//        System.out.println(orderVOS);

    }

}
