package com.kanodays88.skytakeoutai.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.kanodays88.skytakeoutai.entity.Category;
import com.kanodays88.skytakeoutai.entity.Dish;
import com.kanodays88.skytakeoutai.entity.querys.DishQuery;
import com.kanodays88.skytakeoutai.service.CategoryService;
import com.kanodays88.skytakeoutai.service.DishService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DishTool {

    @Autowired
    private DishService dishServiceImpl;

    @Autowired
    private CategoryService categoryServiceImpl;

    @Tool(description = "查询菜品工具，传入的参数是查询菜品的查询条件")
    public List<Dish> queryDish(@ToolParam(description = "查询菜品的条件") DishQuery dishQuery){
        QueryChainWrapper<Dish> query = dishServiceImpl.query();
        if(dishQuery.getDishNames() != null && !dishQuery.getDishNames().isEmpty()){
            query.in("name",dishQuery.getDishNames());
        }
        if(dishQuery.getCategory()!=null && !dishQuery.getCategory().isEmpty()){
            List<Category> categories = categoryServiceImpl.query().select("id").in("name", dishQuery.getCategory()).list();
            if(categories!=null && !categories.isEmpty()){
                List<Long> categories_id = categories.stream().map(c -> c.getId()).toList();
                query.in("category_id",categories_id);
            }
        }
        if(dishQuery.getMinPrice() != null){
            query.ge("price",dishQuery.getMinPrice());
        }
        if(dishQuery.getMaxPrice() != null){
            query.le("price",dishQuery.getMaxPrice());
        }

        //查询
        List<Dish> list = query.list();

        return list;
    }

}
