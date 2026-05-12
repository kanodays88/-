package com.kanodays88.skytakeoutai.content;

import com.kanodays88.skytakeoutai.entity.User;
import com.kanodays88.skytakeoutai.entity.dto.UserLoginDTO;
import com.kanodays88.skytakeoutai.entity.vo.UserLoginVO;

public class BaseContent {

    private static ThreadLocal<String> chatId = new ThreadLocal<>();
    public static void setChatId(String id) {
        chatId.set(id);
    }
    public static String getChatId() {return chatId.get();}
    public static void removeChatId() {chatId.remove();}


    private static ThreadLocal<UserLoginDTO> user = new ThreadLocal<>();
    public static void setUser(UserLoginDTO userLoginDTO) {
        user.set(userLoginDTO);
    }
    public static UserLoginDTO getUser() {return user.get();}
    public static void removeUser() {user.remove();}
}
