package com.kanodays88.skytakeoutai.content;

public class BaseContent {

    private static ThreadLocal<String> chatId = new ThreadLocal<>();
    public static void setChatId(String id) {
        chatId.set(id);
    }
    public static String getChatId() {return chatId.get();}
    public static void removeChatId() {chatId.remove();}
}
