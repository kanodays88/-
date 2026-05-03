package com.kanodays88.skytakeoutai.controller;

import com.kanodays88.skytakeoutai.agent.plan.PlanExecute;
import com.kanodays88.skytakeoutai.common.ChatDecide;
import com.kanodays88.skytakeoutai.common.ChatSystem;
import com.kanodays88.skytakeoutai.constant.FileConstant;
import com.kanodays88.skytakeoutai.memory.FileBasedChatMemory;
import com.kanodays88.skytakeoutai.utils.CommonUtils;
import com.kanodays88.skytakeoutai.utils.FileScanUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai/chat")
@CrossOrigin
@Slf4j
public class ChatController {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatDecide chatDecide;

    @Autowired
    private CommonUtils commonUtils;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PlanExecute planExecute;

    private static final String CHAT_RAG_STATIC = "chat:ragStatic:";
//
//    @RequestMapping(value = "/{msg}", produces = "text/html;charset=utf-8")
//    public Flux<String> serviceChat(@PathVariable("msg") String msg, @RequestHeader("chatId") String chatId){
//        //判断之前的对话有没有走rag
//        boolean ragStatic = commonUtils.writeChatCache(CHAT_RAG_STATIC, chatId, msg);
//        //判断这次提问该不该走向量数据库
//        boolean rag = chatDecide.chatDecideRAG(msg);
//
//        if(ragStatic == false && rag == false){
//            //之前对话没有走rag，并且此次提问也没有走rag
//            return chatClient.prompt(msg).stream().content();
//        }
//        else{
//            if(ragStatic == false){
//                commonUtils.setChatCache(CHAT_RAG_STATIC+chatId,msg,true);
//            }
//            SearchRequest searchRequest = SearchRequest.builder()
//                    .query("初始问题："+commonUtils.getChatCache(CHAT_RAG_STATIC+chatId)+";当前问题："+msg) //搜索文本
//                    .topK(2) //只返回最相关的前2个向量数据
//                    //            .filterExpression("file_name == '菜品口味表.pdf'") //元数据过滤，只搜索指定文件的数据
//                    .build();
//            //将文本发送到向量数据库，向量数据库会根据向量模型的计算结果匹配近似文本
//            List<Document> documents = vectorStore.similaritySearch(searchRequest);
//            //将从向量数据库读取的内容转为字符串
//            String context = documents.stream().map(d->d.getText()).collect(Collectors.joining("\n"));
//
//            String defaultSystem = """
//                    %s
//
//                    【额外资料】
//                    %s
//
//                    注意：额外资料仅用于回答相关问题，不影响你处理其他类型的问题。
//                    """.formatted(ChatSystem.CHAT_SYSTEM,context);
//
//            return chatClient.prompt(msg).system(defaultSystem).stream().content();
//        }
//    }

    @RequestMapping(value = "/{msg}",produces = "text/event-stream;charset=UTF-8")
    public SseEmitter serviceChat(@PathVariable("msg") String msg, @RequestHeader("chatId") String chatId){
        SseEmitter sseEmitter = planExecute.planExecute(msg, chatId);
        return sseEmitter;
    }

    @GetMapping("/history")
    public String[] historyQuery(){
        String[] filenamesWithoutExtension = FileScanUtils.getFilenamesWithoutExtension(FileConstant.FILE_SAVE_DIR + "/chatMemory");
        return filenamesWithoutExtension;
    }

    @GetMapping("/history/{chatId}")
    public List<String> historyQueryByChatId(@PathVariable("chatId") String chatId){
        FileBasedChatMemory fileBasedChatMemory = new FileBasedChatMemory(FileConstant.FILE_SAVE_DIR + "/chatMemory");
        List<Message> allMemory = fileBasedChatMemory.getAll(chatId);
        return allMemory.stream().map(m->m.getMessageType()+":"+m.getText()).collect(Collectors.toList());
    }

    @DeleteMapping("/history/remove/{chatId}")
    public String historyRemove(@PathVariable("chatId") String chatId){
        FileBasedChatMemory fileBasedChatMemory = new FileBasedChatMemory(FileConstant.FILE_SAVE_DIR + "/chatMemory");
        try{
            fileBasedChatMemory.clear(chatId);
            File file = new File(FileConstant.FILE_SAVE_DIR + "/" + chatId);
            if(file.exists() && file.isDirectory()){
                //文件存在删除
                FileUtils.deleteDirectory(new File(FileConstant.FILE_SAVE_DIR + "/"+chatId));
            }else{
                log.info("指定文件目录：{}不存在",file.getPath());
            }
            return "删除成功";
        }catch (Exception e){
            log.info("删除历史会话失败：{}；错误原因：{}",chatId,e.getMessage());
            return "删除失败";
        }
    }


//    @RequestMapping("/finish")
//    public void finishRag(@RequestBody Map<String,String> map ,@RequestHeader("chatId") String chatId){
//        String msg = map.get("msg");
//        boolean b = commonUtils.writeChatCache(CHAT_RAG_STATIC, chatId, msg);
//        if(b == true){
//            //获取原始问题
//            String s = commonUtils.getChatCache(CHAT_RAG_STATIC + chatId);
//            boolean end = chatDecide.chatDecideRAGEnd(s, msg);
//
//            if(end == true){
//                //结束RAG锁
//                commonUtils.setChatCache(CHAT_RAG_STATIC+chatId,"",false);
//            }
//        }
//    }
}
