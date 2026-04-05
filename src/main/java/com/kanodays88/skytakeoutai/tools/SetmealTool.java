package com.kanodays88.skytakeoutai.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.kanodays88.skytakeoutai.entity.Category;
import com.kanodays88.skytakeoutai.entity.Setmeal;
import com.kanodays88.skytakeoutai.entity.SetmealDish;
import com.kanodays88.skytakeoutai.entity.querys.SetmealQuery;
import com.kanodays88.skytakeoutai.service.CategoryService;
import com.kanodays88.skytakeoutai.service.SetmealDishService;
import com.kanodays88.skytakeoutai.service.SetmealService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SetmealTool {

    @Autowired
    private SetmealService setmealServiceImpl;

    @Autowired
    private CategoryService categoryServiceImpl;

    @Autowired
    private SetmealDishService setmealDishServiceImpl;


    @Tool(description = "查询套餐工具")
    public List<Setmeal> querySetmeal(@ToolParam(description = "查询套餐的条件") SetmealQuery setmealQuery){
        QueryChainWrapper<Setmeal> query = setmealServiceImpl.query();
        if(setmealQuery.getCategory()!=null && !setmealQuery.getCategory().isEmpty()){
            List<Category> categories = categoryServiceImpl.query().select("id").in("name", setmealQuery.getCategory()).list();
            if(categories!=null && !categories.isEmpty()){
                List<Long> categories_id = categories.stream().map(c -> c.getId()).toList();
                query.in("category_id",categories_id);
            }
        }
        if(setmealQuery.getDishNames() != null && !setmealQuery.getDishNames().isEmpty()){
            // 1. 构建查询条件：in 查询 + 去重 DISTINCT
            QueryWrapper<SetmealDish> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("DISTINCT setmeal_id")  // 只查不重复的套餐ID
                    .in("name", setmealQuery.getDishNames());     // 菜品名称 in 集合

            // 2. 执行查询（你用 .query() 或 .list() 都可以）
            List<SetmealDish> setmealDishList = setmealDishServiceImpl.list(queryWrapper);

            // 3. 提取成 List<Long> 套餐ID集合
            List<Long> setmealIdList = setmealDishList.stream()
                    .map(s->s.getSetmealId())
                    .collect(Collectors.toList());

            //4. 根据id查询对应套餐
            query.in("id",setmealIdList);
        }

        if(setmealQuery.getMinPrice() != null){
            query.ge("price",setmealQuery.getMinPrice());
        }
        if(setmealQuery.getMaxPrice() != null){
            query.le("price",setmealQuery.getMaxPrice());
        }

        List<Setmeal> list = query.list();

        return list;
    }
}
