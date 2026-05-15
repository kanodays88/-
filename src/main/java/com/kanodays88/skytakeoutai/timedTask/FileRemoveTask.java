package com.kanodays88.skytakeoutai.timedTask;

import cn.hutool.json.JSONUtil;
import com.kanodays88.skytakeoutai.constant.FileConstant;
import com.kanodays88.skytakeoutai.content.BaseContent;
import com.kanodays88.skytakeoutai.memory.FileBasedChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Set;

@Component
public class FileRemoveTask {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private VectorStore vectorStore;

    @Scheduled(cron = "0 0 */12 * * ?")
    @Async
    public void fileRemove(){

        Set<String> chatMemoryKeys = stringRedisTemplate.opsForSet().members("remove:chatMemory");
        if(chatMemoryKeys == null || chatMemoryKeys.isEmpty()) return;

        for(String key:chatMemoryKeys){
            //获取单一会话的过期时间
            String jsonTime = stringRedisTemplate.opsForValue().get(key);
            LocalDateTime TimeoutTime = JSONUtil.toBean(jsonTime, LocalDateTime.class);

            if(LocalDateTime.now().compareTo(TimeoutTime) >= 0){
                //样式    chatMemory:用户:chatId
                String[] strArr = key.split(":");
                String userName = strArr[1];
                String chatId = strArr[2];
                //超时，删除该会话
                File userChatFile = new File(FileConstant.FILE_SAVE_DIR + "\\" + userName + "\\" + chatId);
                File userChatMemory = new File(FileConstant.FILE_SAVE_DIR + "\\" + userName + "\\chatMemory\\" + chatId + ".kryo");
                if(userChatFile.exists()){
                    userChatFile.delete();
                }
                if(userChatMemory.exists()){
                    userChatMemory.delete();
                }
                //同时清除向量数据库的向量
                // 构建过滤表达式：只保留documentId在允许列表中的文档，傻呗写法四老冯了
                FilterExpressionBuilder filter = new FilterExpressionBuilder();
                FilterExpressionBuilder.Op eqUser = filter.eq("user", userName);
                FilterExpressionBuilder.Op eqChatId = filter.eq("chat_id", chatId);
                Filter.Expression expression = filter.and(eqUser, eqChatId).build();
                //上面是我见过最四老冯的写法
                vectorStore.delete(expression);
            }
        }
    }
}
