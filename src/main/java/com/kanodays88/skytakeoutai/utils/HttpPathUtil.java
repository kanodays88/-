package com.kanodays88.skytakeoutai.utils;

import com.kanodays88.skytakeoutai.content.BaseContent;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public class HttpPathUtil {

    /**
     * 动态生成静态资源文件的访问目录
     * @param path
     * @return
     */
    public static String writeHttpUrl(String path){
//        String relativePath = "/tmp/test.pdf";
//      ServletUriComponentsBuilder 必须依赖 Web 请求上下文，是因为它的核心设计目标就是：“复刻当前请求的网络环境”。
//      它需要从 HttpServletRequest（当前Web请求夹带的信息，回顾javaWeb知识）中读取以下动态信息，而这些信息如果不依赖上下文得不出来
//      返回给用户的链接应该写 http:// 还是 https://；用户刚才在浏览器里输的是哪个域名；当前端口是多少等等
//      因此需要从访问Controller接口的线程中获取到该线程的上下文
        String userPath = "/"+ BaseContent.getUser().getUserName()+path;
        String fullUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(userPath)
                .toUriString();
        return fullUrl;
    }
}
