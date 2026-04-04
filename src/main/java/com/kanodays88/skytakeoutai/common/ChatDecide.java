package com.kanodays88.skytakeoutai.common;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChatDecide {


    @Autowired
    private OpenAiChatModel model;

    //用于判断此次对话需不需要走向量数据库获取资料
    public boolean chatDecideRAG(String msg){
        String defaultSystem = """
                你是检索路由助手，只做二分类判断。
                规则：
                1. 当用户询问有关菜品口味相关问题时 → 返回：需要检索
                2. 闲聊、常识、创意、问候、通用问题 → 返回：无需检索
    
                只返回结果，不要任何多余内容。
                """;
        ChatClient client = ChatClient.builder(model).defaultSystem(defaultSystem).build();
        String s = client.prompt(msg).call().content();
        if(s.equals("需要检索")) return true;
        return false;
    }

    public boolean chatDecideRAGEnd(String initailQuestion,String answer){

    }
}
