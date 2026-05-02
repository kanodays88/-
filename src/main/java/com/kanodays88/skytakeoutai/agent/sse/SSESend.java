package com.kanodays88.skytakeoutai.agent.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Component
public class SSESend {

    // ====================== 辅助方法：发送SSE事件 ======================
    public boolean sendEventThink(SseEmitter emitter, String data) {
        try {
            emitter.send("Agent思考:"+data); // Spring自动将对象转为JSON
            return true;
        } catch (IOException e) {
            // 发送失败时关闭连接
            emitter.completeWithError(e);
            return false;
        }
    }

    public boolean sendEventResult(SseEmitter emitter,String data){
        try {
            emitter.send("Agent结果:"+data); // Spring自动将对象转为JSON
            return true;
        } catch (IOException e) {
            // 发送失败时关闭连接
            emitter.completeWithError(e);
            return false;
        }
    }
}
