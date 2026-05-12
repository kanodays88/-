package com.kanodays88.skytakeoutai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kanodays88.skytakeoutai.entity.User;
import com.kanodays88.skytakeoutai.entity.dto.UserLoginDTO;
import com.kanodays88.skytakeoutai.entity.vo.UserLoginVO;
import com.kanodays88.skytakeoutai.service.UserService;
import com.kanodays88.skytakeoutai.mapper.UserMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
* @author Administrator
* @description 针对表【user(用户信息)】的数据库操作Service实现
* @createDate 2026-05-12 15:00:23
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public UserLoginVO login(UserLoginDTO userLoginDTO) {
        List<User> name = query().eq("name", userLoginDTO.getUserName()).list();
        UserLoginVO userLoginVO = new UserLoginVO();
        if(name == null || name.isEmpty()){
            //无用户名冲突，创建用户
            User user = new User();
            user.setName(userLoginDTO.getUserName());
            user.setPhone(userLoginDTO.getPassword());//设置密码
            int rows = userMapper.insert(user);
            if(rows <= 0){
                userLoginVO.setStatus(2);
                userLoginVO.setMsg("登录异常，请重试喵");
                return userLoginVO;
            }
        }else{
            //用户存在，验证密码
            User user = name.get(0);
            if(!user.getPhone().equals(userLoginDTO.getPassword())){
                userLoginVO.setStatus(2);
                userLoginVO.setMsg("登录失败，用户名已被占用或密码错误喵");
                return userLoginVO;
            }
        }

        //找到登录,直接将用户信息保存到redis
        //将用户保存到redis,键就用UUID生成
        String token = UUID.randomUUID().toString();
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userLoginDTO, new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //这里用的是stringRedisTemplate,序列化为string，所以传入的Map里的所有数据都得是string类型
        stringRedisTemplate.opsForHash().putAll("login:user:"+token,stringObjectMap);
        //设置有效时间30分钟
        stringRedisTemplate.expire("login:user:"+token,60, TimeUnit.MINUTES);

        userLoginVO.setMsg("登录成功喵");
        userLoginVO.setStatus(1);
        userLoginVO.setToken(token);
        return userLoginVO;
    }
}




