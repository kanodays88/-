package com.kanodays88.skytakeoutai.config;


import com.kanodays88.skytakeoutai.common.ChatSystem;
import com.kanodays88.skytakeoutai.tools.DishTool;
import com.kanodays88.skytakeoutai.tools.OrderTool;
import com.kanodays88.skytakeoutai.tools.SetmealTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;

@Configuration
@CrossOrigin
public class ChatClientConfiguration {

    /**
     *
     * @param model  是springAi读取yaml配置文件自动配置的model,读取的是openai配置方面的model
     * @param chatMemory  会话缓存，默认实现InMemoryChatMemory,缓存到内存
     * @return
     */
    @Bean
    public ChatClient chatClient(OpenAiChatModel model, ChatMemory chatMemory, DishTool dishTool, SetmealTool setmealTool, OrderTool orderTool){

        return ChatClient.builder(model)
                .defaultSystem(ChatSystem.CHAT_SYSTEM)//设置系统角色
                .defaultTools(dishTool,setmealTool,orderTool)//添加工具
                .defaultAdvisors(SimpleLoggerAdvisor.builder().build(),//设置切面环绕增强,输出日志
                        MessageChatMemoryAdvisor.builder(chatMemory).build())//记忆化环绕增强，本质就是把之前的会话记录通过aop添加进去
                .build();
    }


}
