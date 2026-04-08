package com.kanodays88.skytakeoutai.tools;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.kanodays88.skytakeoutai.entity.Category;
import com.kanodays88.skytakeoutai.entity.Dish;
import com.kanodays88.skytakeoutai.entity.querys.DishQuery;
import com.kanodays88.skytakeoutai.entity.querys.SetmealQuery;
import com.kanodays88.skytakeoutai.entity.vo.DishVO;
import com.kanodays88.skytakeoutai.entity.vo.SetmealVO;
import com.kanodays88.skytakeoutai.service.CategoryService;
import com.kanodays88.skytakeoutai.service.DishService;
import lombok.NonNull;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class DishTool {

    @Autowired
    private DishService dishServiceImpl;

    @Autowired
    private CategoryService categoryServiceImpl;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_DISH_QUERY = "lock:dishQuery";

    @Tool(description = "查询菜品工具，传入的参数是查询菜品的查询条件")
    public List<DishVO> queryDish(@ToolParam(description = "查询菜品的条件") DishQuery dishQuery){
        //生成唯一key
        String key = generateCacheKey(dishQuery);
        //查缓存
        List<DishVO> cache = getCache(key, dishQuery);
        return cache;
    }

    private @NonNull List<DishVO> getDishVOS(DishQuery dishQuery) {
        QueryChainWrapper<Dish> query = dishServiceImpl.query();
        if(dishQuery.getDishNames() != null && !dishQuery.getDishNames().isEmpty()){
            query.in("name", dishQuery.getDishNames());
        }
        if(dishQuery.getCategory()!=null && !dishQuery.getCategory().isEmpty()){
            List<Category> categories = categoryServiceImpl.query().select("id").in("name", dishQuery.getCategory()).list();
            if(categories!=null && !categories.isEmpty()){
                List<Long> categories_id = categories.stream().map(c -> c.getId()).toList();
                query.in("category_id",categories_id);
            }
        }
        if(dishQuery.getMinPrice() != null){
            query.ge("price", dishQuery.getMinPrice());
        }
        if(dishQuery.getMaxPrice() != null){
            query.le("price", dishQuery.getMaxPrice());
        }

        //查询
        List<Dish> list = query.list();

        List<DishVO> dishVOS = new ArrayList<>();

        for(Dish d:list){
            DishVO dishVO = new DishVO();
            BeanUtil.copyProperties(d,dishVO);
            List<Category> categories = categoryServiceImpl.query().select("name").eq("id", d.getCategoryId()).list();
            List<String> categories_name = categories.stream().map(c -> c.getName()).toList();
            dishVO.setCategoryName(categories_name.get(0));
            dishVOS.add(dishVO);
        }

        return dishVOS;
    }

    private List<DishVO> getCache(String key, DishQuery dishQuery){
        String json = stringRedisTemplate.opsForValue().get(key);

        if(json == null){
            //获取锁对象
            RLock lock = redissonClient.getLock(REDIS_DISH_QUERY);

            try{
                //尝试上锁                     等待时间  锁时间    单位
                boolean tryLock = lock.tryLock(3, 30, TimeUnit.SECONDS);
                if(tryLock == true){
                    //上锁成功
                    //执行查询
                    List<DishVO> dishVOS = getDishVOS(dishQuery);
                    //将结果缓存
                    //生成json
                    String strJson = JSONUtil.toJsonStr(dishVOS);
                    //缓存
                    stringRedisTemplate.opsForValue().set(key,strJson);
                    return dishVOS;
                }
                else{
                    //上锁失败
                    System.out.println("上锁失败，请重试");
                    return null;
                }

            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
                throw new RuntimeException("操作被中断");
            } finally {
                // 6. 释放锁（必须在 finally 中执行，防止死锁）
                // 注意：只能释放当前线程持有的锁，否则会报错
                if (lock.isHeldByCurrentThread()) {//判断当前线程有没有持有锁
                    lock.unlock();
                    System.out.println("释放锁成功");
                }
            }

        }

        //将json转bean返回
        List<DishVO> dishVOS = JSONUtil.toList(json, DishVO.class);
        return dishVOS;
    }

    // 2. 定义一个缓存 Key 前缀常量
    private static final String DISH_CACHE_KEY_PREFIX = "dish:query:";

    // --- 核心方法：生成唯一 Key ---
    private String generateCacheKey(DishQuery query) {
        StringBuilder sb = new StringBuilder(DISH_CACHE_KEY_PREFIX);

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
