package com.kanodays88.skytakeoutai.constant;

import com.kanodays88.skytakeoutai.content.BaseContent;

import java.nio.file.Paths;

public interface FileConstant {

    //                     获取用户工作目录
    String FILE_SAVE_DIR = Paths.get(System.getProperty("user.dir"),"tmp").toString();
}