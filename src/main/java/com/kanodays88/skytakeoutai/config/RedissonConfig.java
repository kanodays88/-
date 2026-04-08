package com.kanodays88.skytakeoutai.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 初始化配置
        Config config = new Config();
        // 单机模式，指定本地Redis默认地址
        config.useSingleServer().setAddress("redis://192.168.74.100:6379");
        // 创建并返回Redisson客户端实例
        return Redisson.create(config);
    }
}