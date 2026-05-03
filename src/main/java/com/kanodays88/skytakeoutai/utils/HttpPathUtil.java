package com.kanodays88.skytakeoutai.utils;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public class HttpPathUtil {

    /**
     * 动态生成静态资源文件的访问目录
     * @param path
     * @return
     */
    public static String writeHttpUrl(String path){
//        String relativePath = "/tmp/test.pdf";
        String fullUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(path)
                .toUriString();
        return fullUrl;
    }
}
