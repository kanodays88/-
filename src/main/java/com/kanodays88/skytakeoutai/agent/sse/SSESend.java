package com.kanodays88.skytakeoutai.agent.sse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SSESend {

    // ====================== 辅助方法：发送SSE事件 ======================
    public boolean sendEventThink(SseEmitter emitter, String data) {
        try {
            // 核心修改：同上，强制 UTF-8
            emitter.send(
                    SseEmitter.event()
                            .data("Agent思考:" + data, new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
            );
            return true;
        } catch (IOException e) {
            // 发送失败时关闭连接
            emitter.completeWithError(e);
            return false;
        }
    }

    public boolean sendEventResult(SseEmitter emitter,String data){
        try {
            // 核心修改：同上，强制 UTF-8
            emitter.send(
                    SseEmitter.event()
                            .data("Agent结果:" + data, new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
            );
            return true;
        } catch (IOException e) {
            // 发送失败时关闭连接
            emitter.completeWithError(e);
            return false;
        }
    }
}
