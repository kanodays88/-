package com.kanodays88.skytakeoutai.utils;

import cn.hutool.json.JSONUtil;
import com.kanodays88.skytakeoutai.content.BaseContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RedisUtils {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void fileTimeoutSet(){
        //同时用redis记录本文件逻辑过期时间，后面统一用定时任务清除过期的文件
        String redisKeyPath = "tmp:"+ BaseContent.getUser().getUserName()+":"+BaseContent.getChatId()+":file";
        stringRedisTemplate.opsForValue().set(redisKeyPath,JSONUtil.toJsonStr(LocalDateTime.now().plusHours(24)));
    }

}
