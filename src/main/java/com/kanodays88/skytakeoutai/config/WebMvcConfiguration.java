package com.kanodays88.skytakeoutai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

//继承WebMvcConfigurationSupport并且注册为Bean会将Spring原来的WebMvcAutoConfiguration 里给你自动配置好的所有东西都会失效
//public class WebMvcConfiguration extends WebMvcConfigurationSupport {
//
//    /**
//     * 设置静态资源映射
//     * @param registry
//     */
//    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
//        registry.addResourceHandler("/**").addResourceLocations("file:./tmp/");
//    }
//}
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer { // 这里改成 implements

    /**
     * 设置静态资源映射
     * @param registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations("file:./tmp/");
    }
}
