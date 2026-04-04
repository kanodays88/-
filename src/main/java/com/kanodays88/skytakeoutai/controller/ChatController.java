package com.kanodays88.skytakeoutai.controller;

import com.kanodays88.skytakeoutai.common.ChatDecide;
import com.kanodays88.skytakeoutai.common.ChatSystem;
import com.kanodays88.skytakeoutai.utils.CommonUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai/chat")
public class ChatController {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatClient chatClientRAG;

    @Autowired
    private ChatDecide chatDecide;

    @Autowired
    private CommonUtils commonUtils;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String CHAT_RAG_STATIC = "chat:ragStatic:";

    @RequestMapping(value = "/{msg}", produces = "text/html;charset=utf-8")
    public Flux<String> serviceChat(@PathVariable("msg") String msg, @RequestHeader("chatId") String chatId){
        //判断之前的对话有没有走rag
        boolean ragStatic = commonUtils.writeChatCache(CHAT_RAG_STATIC, chatId, msg);
        //判断这次提问该不该走向量数据库
        boolean rag = chatDecide.chatDecideRAG(msg);

        if(ragStatic == false && rag == false){
            //之前对话没有走rag，并且此次提问也没有走rag
            return chatClient.prompt(msg).stream().content();
        }
        else{
            if(ragStatic == false){
                commonUtils.setChatCache(CHAT_RAG_STATIC+chatId,msg,true);
            }
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(msg) //搜索文本
                    .topK(2) //只返回最相关的前2个向量数据
                    //            .filterExpression("file_name == '菜品口味表.pdf'") //元数据过滤，只搜索指定文件的数据
                    .build();
            //将文本发送到向量数据库，向量数据库会根据向量模型的计算结果匹配近似文本
            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            //将从向量数据库读取的内容转为字符串
            String context = documents.stream().map(d->d.getText()).collect(Collectors.joining("\n"));

            String defaultSystem = """
                    %s
    
                    【额外资料】
                    %s
    
                    注意：额外资料仅用于回答相关问题，不影响你处理其他类型的问题。
                    """.formatted(ChatSystem.CHAT_SYSTEM,context);

            return chatClient.prompt(msg).system(defaultSystem).stream().content();
        }
    }
}
