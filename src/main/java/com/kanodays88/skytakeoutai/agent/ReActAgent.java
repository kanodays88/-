package com.kanodays88.skytakeoutai.agent;

import com.kanodays88.skytakeoutai.agent.sse.SSESend;
import lombok.EqualsAndHashCode;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


@EqualsAndHashCode(callSuper = true)//自动生成equals和hashcode方法
public abstract class ReActAgent extends BaseAgent{

    //让智能体思考问题是否解决
    public abstract boolean think(String userPrompt,String taskName,SseEmitter sseEmitter, SSESend sseSend);
    //让智能体动手解决
    public abstract String act();

    @Override
    public String step(String userPrompt, String taskName, SseEmitter sseEmitter, SSESend sseSend) {
        try{
            boolean think = think(userPrompt,taskName,sseEmitter,sseSend);
            if(!think){
                return "思考完成-无需调用工具";
            }
            return act();
        }catch (Exception e){
            e.printStackTrace();
            return "步骤执行失败: " + e.getMessage();
        }
    }
}
