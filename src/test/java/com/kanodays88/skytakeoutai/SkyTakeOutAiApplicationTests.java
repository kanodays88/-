package com.kanodays88.skytakeoutai;

import com.kanodays88.skytakeoutai.entity.Dish;
import com.kanodays88.skytakeoutai.entity.querys.DishQuery;
import com.kanodays88.skytakeoutai.tools.DishTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class SkyTakeOutAiApplicationTests {

    @Autowired
    private DishTool dishTool;

    @Test
    void contextLoads() {


        DishQuery dishQuery = new DishQuery();
        ArrayList<String> strings = new ArrayList<>();
        strings.add("米饭");
        dishQuery.setDishNames(strings);
        dishQuery.setMaxPrice(100.00);
        dishQuery.setMinPrice((1.00));
        List<Dish> dishes = dishTool.queryDish(dishQuery);

        for(Dish d:dishes){
            System.out.println(d);
        }

    }

}
