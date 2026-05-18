package com.kanodays88.skytakeoutai.agent.simpleChat;

import com.kanodays88.skytakeoutai.advisor.MyLoggerAdvisor;
import com.kanodays88.skytakeoutai.common.ChatSystem;
import com.kanodays88.skytakeoutai.constant.FileConstant;
import com.kanodays88.skytakeoutai.content.BaseContent;
import com.kanodays88.skytakeoutai.memory.FileBasedChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class SimpleChatAgent {

    private final ChatClient chatClient;
    private final FileBasedChatMemory chatMemory;
    private final ToolCallback[] allTools;

    public SimpleChatAgent(OpenAiChatModel chatModel,ToolCallback[] allTools) throws IOException {
        this.chatClient = ChatClient.builder(chatModel).defaultAdvisors(new MyLoggerAdvisor()).build();
        this.chatMemory = new FileBasedChatMemory(Paths.get(FileConstant.FILE_SAVE_DIR,BaseContent.getUser().getUserName(),"chatMemory").toString());
        this.allTools = allTools;
    }

    public String simpleChat(String userPrompt,String chatId){
        //获取历史会话
        List<Message> messages = chatMemory.get(chatId);
        Prompt historyChat = new Prompt(messages);
        return chatClient.prompt(historyChat).system(ChatSystem.CHAT_SYSTEM).user(userPrompt).toolCallbacks(allTools).call().content();
    }

}
