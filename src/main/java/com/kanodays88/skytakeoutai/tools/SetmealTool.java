package com.kanodays88.skytakeoutai.tools;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.kanodays88.skytakeoutai.entity.Category;
import com.kanodays88.skytakeoutai.entity.Setmeal;
import com.kanodays88.skytakeoutai.entity.SetmealDish;
import com.kanodays88.skytakeoutai.entity.querys.SetmealQuery;
import com.kanodays88.skytakeoutai.entity.vo.SetmealVO;
import com.kanodays88.skytakeoutai.service.CategoryService;
import com.kanodays88.skytakeoutai.service.SetmealDishService;
import com.kanodays88.skytakeoutai.service.SetmealService;
import com.kanodays88.skytakeoutai.utils.RedisUtils;
import lombok.NonNull;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private

    private static final String REDIS_SETMEAL_QUERY = "lock:setmealQuery:";


    @Tool(description = "查询套餐工具")
    public List<SetmealVO> querySetmeal(@ToolParam(description = "查询套餐的条件") SetmealQuery setmealQuery){
        //生成key
        String key = generateCacheKey(setmealQuery);


        return getSetmealVOS(setmealQuery);
    }

    private @NonNull List<SetmealVO> getSetmealVOS(SetmealQuery setmealQuery) {
        QueryChainWrapper<Setmeal> query = setmealServiceImpl.query();
        //分类条件
        if(setmealQuery.getCategory()!=null && !setmealQuery.getCategory().isEmpty()){
            List<Category> categories = categoryServiceImpl.query().select("id").in("name", setmealQuery.getCategory()).list();
            if(categories!=null && !categories.isEmpty()){
                List<Long> categories_id = categories.stream().map(c -> c.getId()).toList();
                query.in("category_id",categories_id);
            }
        }
        //菜品名字条件
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
            query.ge("price", setmealQuery.getMinPrice());
        }
        if(setmealQuery.getMaxPrice() != null){
            query.le("price", setmealQuery.getMaxPrice());
        }

        List<Setmeal> list = query.list();
        //封装VO返回
        List<SetmealVO> setmealVOS = new ArrayList<>();

        for(Setmeal s:list){
            SetmealVO setmealVO = new SetmealVO();
            BeanUtil.copyProperties(s,setmealVO);
            List<Category> categories = categoryServiceImpl.query().select("name").eq("id", s.getCategoryId()).list();
            List<String> strings = categories.stream().map(c -> c.getName()).toList();
            setmealVO.setCategoryName(strings.get(0));

            List<SetmealDish> setmealDishes = setmealDishServiceImpl.query().select("name").eq("setmeal_id", s.getId()).list();
            List<String> dishName = setmealDishes.stream().map(st -> st.getName()).toList();

            setmealVO.setDishesName(dishName);

            setmealVOS.add(setmealVO);
        }

        return setmealVOS;
    }

//    private List<SetmealVO> getCache(String key){
//        String json = stringRedisTemplate.opsForValue().get(key);
//
//        if(json == null){
//            //获取分布式锁
//            Thread.currentThread().getId();
//        }
//    }

    // 2. 定义一个缓存 Key 前缀常量
    private static final String SETMEAL_CACHE_KEY_PREFIX = "setmeal:query:";

    // --- 核心方法：生成唯一 Key ---
    private String generateCacheKey(SetmealQuery query) {
        StringBuilder sb = new StringBuilder(SETMEAL_CACHE_KEY_PREFIX);

        // 1. 处理分类列表 (排序)
        if (query.getCategory() != null && !query.getCategory().isEmpty()) {
            List<String> sortedCats = new ArrayList<>(query.getCategory());
            Collections.sort(sortedCats); // 关键：强制排序
            sb.append("c:").append(String.join("|", sortedCats));
        }
//        sb.append(";"); // 分隔符

        // 2. 处理价格区间
        sb.append("p:")
                .append(query.getMinPrice() != null ? query.getMinPrice().toString() : "NULL")
                .append("-")
                .append(query.getMaxPrice() != null ? query.getMaxPrice().toString() : "NULL");
//        sb.append(";");

        // 3. 处理菜品名称列表 (排序)
        if (query.getDishNames() != null && !query.getDishNames().isEmpty()) {
            List<String> sortedDishes = new ArrayList<>(query.getDishNames());
            Collections.sort(sortedDishes); // 关键：强制排序
            sb.append("d:").append(String.join("|", sortedDishes));
        }

        return sb.toString();
    }
}
