package com.kanodays88.skytakeoutai.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.kanodays88.skytakeoutai.content.BaseContent;
import com.kanodays88.skytakeoutai.entity.dto.UserLoginDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 在请求前执行拦截判断，该拦截器只做刷新token操作，不做实际拦截处理
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("开始执行token刷新拦截");
        //获取请求头中的token
        String token = request.getHeader("authorization");
        //根据token获取redis中对应的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries("login:user:"+token);
        if(userMap != null && userMap.size() != 0){
            log.info("检测到用户登录，刷新用户token");
            //有用户信息，将用户信息转为userDTO并存放致ThreadLocal
            //将userMap的数据转入到userLoginDTO对象中
            UserLoginDTO userLoginDTO = BeanUtil.fillBeanWithMap(userMap, new UserLoginDTO(), false);
            //存在，则将user用户信息存放到ThreadLocal
            BaseContent.setUser(userLoginDTO);
            //重新刷新时间
            stringRedisTemplate.expire("login:user::"+token,60, TimeUnit.MINUTES);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        BaseContent.removeUser();
    }
}
