package com.kanodays88.skytakeoutai.controller;

import com.kanodays88.skytakeoutai.agent.plan.PlanExecute;
import com.kanodays88.skytakeoutai.agent.router.QuestionType;
import com.kanodays88.skytakeoutai.agent.router.RouteDecisionTotal;
import com.kanodays88.skytakeoutai.agent.router.RouterAgent;
import com.kanodays88.skytakeoutai.agent.simpleChat.SimpleChatAgent;
import com.kanodays88.skytakeoutai.agent.sse.SSESend;
import com.kanodays88.skytakeoutai.common.ChatDecide;
import com.kanodays88.skytakeoutai.constant.FileConstant;
import com.kanodays88.skytakeoutai.content.BaseContent;
import com.kanodays88.skytakeoutai.entity.dto.UserLoginDTO;
import com.kanodays88.skytakeoutai.memory.FileBasedChatMemory;
import com.kanodays88.skytakeoutai.skill.SkillRegistry;
import com.kanodays88.skytakeoutai.utils.CommonUtils;
import com.kanodays88.skytakeoutai.utils.FileScanUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springdoc.core.parsers.KotlinCoroutinesReturnTypeParser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

    @Autowired
    private OpenAiChatModel openAiChatModel;
    @Autowired
    private EmbeddingModel qianfanEmbeddingModel;
    @Autowired
    private ToolCallback[] allTools;
    @Autowired
    private SkillRegistry skillRegistry;

    private static final String CHAT_RAG_STATIC = "chat:ragStatic:";
    @Autowired
    private KotlinCoroutinesReturnTypeParser kotlinCoroutinesReturnTypeParser;

    @RequestMapping(value = "/{msg}",produces = "text/event-stream;charset=UTF-8")
    public SseEmitter serviceChat(@PathVariable("msg") String msg, @RequestHeader("chatId") String chatId){
        //获取当前主线程的上下文
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        // 1. 创建SSE发射器，设置10分钟超时（根据任务复杂度调整）
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        //获取当前线程的登录用户
        UserLoginDTO userLoginDTO = BaseContent.getUser();
        CompletableFuture.runAsync(()->{
            try{
                //将主线程上下文设置到子线程中
                if (attributes != null) {
                    //主要用于获取主线程上下文，从而获取到Web请求线程（主线程）的HttpServletRequest的请求信息
                    RequestContextHolder.setRequestAttributes(attributes);
                }
                //将会话id存储到当前线程的ThreadLocal
                BaseContent.setChatId(chatId);
                //设置当前异步线程的登录用户
                BaseContent.setUser(userLoginDTO);
                //获取记忆系统
                FileBasedChatMemory fileBasedChatMemory = new FileBasedChatMemory(FileConstant.FILE_SAVE_DIR +"\\"+BaseContent.getUser().getUserName()+ "\\chatMemory");
                //进入RouterAgent
                RouterAgent routerAgent = new RouterAgent(openAiChatModel, vectorStore, allTools, skillRegistry,emitter);
                //获取路由结果
                RouteDecisionTotal route = routerAgent.route(msg, chatId);
                String aiResult = null;
                if(route.decision().questionType() == QuestionType.SIMPLE_CHAT){
//                    简单任务
                    SimpleChatAgent simpleChatAgent = new SimpleChatAgent(openAiChatModel, allTools);
                    String s = simpleChatAgent.simpleChat(msg, chatId);
                    SSESend.sendEventResult(emitter,s);
                    aiResult = s;
                } else if(route.decision().questionType() == QuestionType.COMPLEX_TASK){
                    //复杂任务处理
                    String planResult = planExecute.planExecute(route.decision().mianTask(),chatId,emitter);
                    SSESend.sendEventResult(emitter,planResult);
                    aiResult = planResult;
                }else if(route.decision().questionType() == QuestionType.AMBIGUOUS){
                    //缺失缺失关键信息或者意图不明确，反问用户
                    String question = route.decision().returnQuestion();
                    SSESend.sendEventResult(emitter,question);
                    aiResult = question;
                }else{
                    //异常
                    System.out.println("异常");
                }
                //本次会话执行完成后，将用户提问和ai回复存入对话记忆
                fileBasedChatMemory.add(chatId,List.of(new UserMessage(msg),new AssistantMessage(aiResult)));
            }catch (Exception e){
                log.error("执行异常：{}",e.getMessage());
                SSESend.sendEventResult(emitter, "执行失败: " + e.getMessage());
                emitter.completeWithError(e);
            }finally {
                //删除ThreadLocal防止内存泄露
                BaseContent.removeChatId();
                //释放ThreadLocal
                RequestContextHolder.resetRequestAttributes();
                emitter.complete();
            }
        });
        return emitter;
    }

    @GetMapping("/history")
    public String[] historyQuery(){
        String[] filenamesWithoutExtension = FileScanUtils.getFilenamesWithoutExtension(FileConstant.FILE_SAVE_DIR +"\\"+BaseContent.getUser().getUserName()+ "\\chatMemory");
        return filenamesWithoutExtension;
    }

    @GetMapping("/history/{chatId}")
    public List<String> historyQueryByChatId(@PathVariable("chatId") String chatId) throws IOException {
        FileBasedChatMemory fileBasedChatMemory = new FileBasedChatMemory(FileConstant.FILE_SAVE_DIR +"\\"+BaseContent.getUser().getUserName()+ "\\chatMemory");
        List<Message> allMemory = fileBasedChatMemory.getAll(chatId);
        return allMemory.stream().map(m->m.getMessageType()+":"+m.getText()).collect(Collectors.toList());
    }

    @DeleteMapping("/history/remove/{chatId}")
    public String historyRemove(@PathVariable("chatId") String chatId) throws IOException {
        FileBasedChatMemory fileBasedChatMemory = new FileBasedChatMemory(FileConstant.FILE_SAVE_DIR +"\\"+BaseContent.getUser().getUserName()+ "\\chatMemory");
        try{
            fileBasedChatMemory.clear(chatId);
            File file = new File(FileConstant.FILE_SAVE_DIR +"\\"+BaseContent.getUser().getUserName()+ "\\"+chatId);
            if(file.exists() && file.isDirectory()){
                //文件存在删除
                FileUtils.deleteDirectory(file);
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
