package com.kanodays88.skytakeoutai.entity.vo;

import lombok.Data;

@Data
public class UserLoginVO {

    //登录状态
    int status;
    //登录状态描述
    String msg;
    //登录的token令牌
    String token;
}
