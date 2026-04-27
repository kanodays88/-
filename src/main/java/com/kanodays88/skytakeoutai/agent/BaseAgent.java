package com.kanodays88.skytakeoutai.agent;


import com.itextpdf.styledxmlparser.jsoup.internal.StringUtil;
import com.kanodays88.skytakeoutai.agent.model.AgentState;
import com.kanodays88.skytakeoutai.agent.sse.SSESend;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

@Data
@Slf4j
public abstract class BaseAgent {


    //智能体名字
    private String name;

    //系统提示词
    private String systemPrompt;
    //下一步提示词
    private String nextStepPrompt;

    //智能体当前状态
    private AgentState state = AgentState.IDLE;

    //最大执行步骤
    private int maxSteps = 5;
    //当前执行步骤
    private int currentStep = 0;
    //会话客户端
    private ChatClient chatClient;
    //ReAct循环思考时的记忆消息列表，不是总的记忆
    private List<Message> messageList = new ArrayList<>();


    public List<String> run(String userPrompt, String taskName, SseEmitter emitter, SSESend sseSend) {
        //智能体不空闲
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        //用户提示词为空
        if (StringUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        //修改状态为运行中
        state = AgentState.RUNNING;
        //添加记忆
        messageList.add(new UserMessage(userPrompt));

        List<String> results = new ArrayList<>();
        try {
            //循环执行ReAct
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                //循环次数+1
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step " + stepNumber + "/" + maxSteps);
                //执行智能体思考
                String stepResult = step(userPrompt,taskName,emitter,sseSend);
                String result = "Step " + stepNumber + ": " + stepResult;
                //卡死检测（预防无效步数浪费）
                if(isStuck()){
                    handleStuckState();
                }
                //存储思考结果
                results.add(result);
            }
            //如果循环思考步数大于最大步数，强制结束
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return results;
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            results.add("执行错误,错误原因："+e.getMessage());
            return results;
        } finally {
            this.cleanup();
        }
    }

    protected boolean isStuck() {
        List<Message> messages = messageList;
        if (messages.size() < 2) {
            return false;
        }

        Message lastMessage = messages.get(messages.size() - 1);
        if (lastMessage.getText() == null || lastMessage.getText().isEmpty()) {
            return false;
        }

        //判断是否重复，如果重复次数超过两次判断为卡死
        int duplicateCount = 0;
        for (int i = messages.size() - 2; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.getMessageType() == MessageType.ASSISTANT &&
                    lastMessage.getText().equals(msg.getText())) {
                duplicateCount++;
            }
        }
        //重复次数超过两次直接判断卡死
        return duplicateCount >= 3;
    }


    protected void handleStuckState() {
        this.nextStepPrompt = "观察到重复响应。考虑新策略，避免重复已尝试过的无效路径。";
        log.info("Agent detected stuck state. Added prompt: " + nextStepPrompt);
    }


    protected void cleanup() {
        this.state = AgentState.IDLE;
        this.messageList.clear();
    }

    public abstract String step(String userPrompt,String taskName,SseEmitter sseEmitter,SSESend sseSend);
}