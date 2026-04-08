package com.kanodays88.skytakeoutai.tools;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
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

    @Autowired
    private RedissonClient redissonClient;

    private static final String REDIS_SETMEAL_QUERY = "lock:setmealQuery";


    @Tool(description = "查询套餐工具")
    public List<SetmealVO> querySetmeal(@ToolParam(description = "查询套餐的条件") SetmealQuery setmealQuery){
        //生成key
        String key = generateCacheKey(setmealQuery);
        //获取缓存
        List<SetmealVO> cache = getCache(key, setmealQuery);

        return cache;
    }

    private @NonNull List<SetmealVO> getSetmealVOS(SetmealQuery setmealQuery) {
        QueryChainWrapper<Setmeal> query = setmealServiceImpl.query();
        //套餐名称
        if(setmealQuery.getSetmealNames()!=null && !setmealQuery.getSetmealNames().isEmpty()){
            query.in("name",setmealQuery.getSetmealNames());
        }
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

    private List<SetmealVO> getCache(String key,SetmealQuery setmealQuery){
        String json = stringRedisTemplate.opsForValue().get(key);

        if(json == null){
            //获取锁对象
            RLock lock = redissonClient.getLock(REDIS_SETMEAL_QUERY);

            try{
                //尝试上锁                     等待时间  锁时间    单位
                boolean tryLock = lock.tryLock(3, 30, TimeUnit.SECONDS);
                if(tryLock == true){
                    //上锁成功
                    //执行查询
                    List<SetmealVO> setmealVOS = getSetmealVOS(setmealQuery);
                    //将结果缓存
                    //生成json
                    String strJson = JSONUtil.toJsonStr(setmealVOS);
                    //缓存
                    stringRedisTemplate.opsForValue().set(key,strJson);
                    return setmealVOS;
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
        List<SetmealVO> setmealVOS = JSONUtil.toList(json, SetmealVO.class);
        return setmealVOS;
    }

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

        //4.处理套餐名称
        if(query.getSetmealNames()!=null && !query.getSetmealNames().isEmpty()){
            List<String> sortedSetmeals = new ArrayList<>(query.getSetmealNames());
            Collections.sort(sortedSetmeals); // 关键：强制排序
            sb.append("d:").append(String.join("|", sortedSetmeals));
        }

        return sb.toString();
    }
}
