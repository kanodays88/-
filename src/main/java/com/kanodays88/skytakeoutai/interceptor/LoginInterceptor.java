package com.kanodays88.skytakeoutai.interceptor;

import com.kanodays88.skytakeoutai.content.BaseContent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;



@Component
@Slf4j
//HandlerInterceptor接口，SpringMVC提供的拦截器实现接口
public class LoginInterceptor implements HandlerInterceptor {

    //因为有一些页面不需要登录就能访问，导致用户长时间访问不需要登录的页面时无法刷新登录token导致掉登录
    //所以要在登录拦截器前再套用一个全局拦截器解决访问所有页面都可以刷新token

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 在请求到达controller层前进行拦截
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //拦截，校验是否登录,判断ThreadLocal是否存有用户即可
        if(BaseContent.getUser() == null){
            response.setStatus(401);
            log.info("拦截，用户未登录");
            return false;
        }
        return true;
    }
}
