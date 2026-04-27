package com.kanodays88.skytakeoutai.agent;

import cn.hutool.core.collection.CollUtil;
import com.kanodays88.skytakeoutai.agent.model.AgentState;
import com.kanodays88.skytakeoutai.agent.sse.SSESend;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ToolCallAgent extends ReActAgent{

    //可用工具集
    private final ToolCallback[] availableTools;

    //大模型返回结果
    private ChatResponse toolCallChatResponse;
    //工具执行的具体类
    private final ToolCallingManager toolCallingManager;
    //控制大模型内部的选项，比如具体用什么模型，最大token消耗为多少，自不自主调用工具这些选项
    private final ChatOptions chatOptions;

    //构造函数初始化
    public ToolCallAgent(ToolCallback[] toolCallbacks){
        super();
        this.availableTools = toolCallbacks;
        this.toolCallingManager = ToolCallingManager.builder().build();

        //TODO替换成springAi的
        this.chatOptions = DashScopeChatOptions.builder()
                .withProxyToolCalls(true)//springAi不会自动调用工具了
                .build();
    }

    @Override
    public boolean think(String userPrompt, String taskName, SseEmitter sseEmitter, SSESend sseSend) {
        //如果“下一步提示词”不为空且不为空串
        if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
            //将“下一步提示词”构建成用户提示词
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            //添加到记忆
            getMessageList().add(userMessage);
            setNextStepPrompt(null);
        }
        //获取对话记忆
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, chatOptions);
        try {
            //更新设置系统提示词
            String SYSTEM_PROMPT = """
                    角色：Kanodays88Manus全能AI助手
                    ##核心规则：
                    1.根据核心任务内容实现核心任务，不需要关注其他任务的实现情况
                    2.当你认为任务完成或无法继续时，调用AssignmentFinishTool工具结束交互
                    ##输入变量：
                    {question}：核心任务
                    {taskContent}：核心任务内容
                    {now}：当前剩余思考次数
                    {max}：最大可用思考次数
                """;
            PromptTemplate promptTemplate = new PromptTemplate(SYSTEM_PROMPT);
            Prompt systemPrompt = promptTemplate.create(Map.of("now", this.getMaxSteps()-this.getCurrentStep(), "max", this.getMaxSteps(),"question",taskName,"taskContent",userPrompt));

            log.info(systemPrompt.getContents());
            //调用大模型，并获取返回结果
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(systemPrompt.getContents())
                    .tools(availableTools)
                    .call()
                    .chatResponse();

            this.toolCallChatResponse = chatResponse;
            //获取大模型返回的消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            //通过SseEmitter将大模型思考过程发送给用户
            sseSend.sendEvent(sseEmitter,(String) assistantMessage.getMetadata().get("reasoningContent")+"\n");
            //通过SseEmitter将大模型思考结果发送给用户
            sseSend.sendEvent(sseEmitter,assistantMessage.getText()+"\n");

            //大模型思考结果的输出文本
            String result = assistantMessage.getText();
            //大模型思考结果的调用工具消息
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            log.info(getName() + "的思考: " + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            //获取工具参数信息
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s",
                            toolCall.name(),
                            toolCall.arguments())
                    )
                    .collect(Collectors.joining("\n"));//集合成字符串，用换行符分割
            log.info(toolCallInfo);
            if (toolCallList.isEmpty()) {
                //最后将大模型的思考结果加入记忆消息队列，结束思考，选择不调用工具
                getMessageList().add(assistantMessage);
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题: " + e.getMessage());
            getMessageList().add(
                    new AssistantMessage("处理时遇到错误: " + e.getMessage()));
            return false;
        }
    }

    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具调用";
        }

        Prompt prompt = new Prompt(getMessageList(), chatOptions);
        //根据上下文记忆信息和工具调用信息调用工具，然后将所有上下文信息连同工具调用信息一起集成到toolExecutionResult.conversationHistory()
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        //将集成的记忆信息覆盖原有的信息
        setMessageList(toolExecutionResult.conversationHistory());

        //获取最新的一条信息
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 完成了它的任务！结果: " + response.responseData())
                .collect(Collectors.joining("\n"));

        boolean anyMatch = toolResponseMessage.getResponses().stream().
                anyMatch(response -> "assignmentFinish".equals(response.name()));//这里response.name是工具名字
        if(anyMatch){
            setState(AgentState.FINISHED);
        }
        log.info(results);
        return results;
    }
}
