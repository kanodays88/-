package com.kanodays88.skytakeoutai.controller;

import com.kanodays88.skytakeoutai.entity.dto.UserLoginDTO;
import com.kanodays88.skytakeoutai.entity.vo.UserLoginVO;
import com.kanodays88.skytakeoutai.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/user")
@CrossOrigin
@Slf4j
public class UserController {

    @Autowired
    private UserService userServiceImpl;

    @PostMapping("/login")
    public UserLoginVO login(@RequestBody UserLoginDTO userLoginDTO){
        return userServiceImpl.login(userLoginDTO);
    }
}
