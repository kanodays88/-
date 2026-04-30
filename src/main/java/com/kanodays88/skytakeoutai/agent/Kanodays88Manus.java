package com.kanodays88.skytakeoutai.agent;

import com.kanodays88.skytakeoutai.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.ToolCallback;

import java.util.Map;

public class Kanodays88Manus extends ToolCallAgent{

    public Kanodays88Manus(ToolCallback[] allTools, ChatModel openAiChatModel){
        //父类构造函数初始化工具
        super(allTools);
        //设置智能体名字
        this.setName("kanodays88Manus");
        //设置最大执行步数
        this.setMaxSteps(3);
//        //设置系统提示词
//        String SYSTEM_PROMPT = """
//                你是Kanodays88Manus，全能AI助手，可解决用户任何任务。具备多种工具，能高效处理复杂请求。
//                你必须在有限的思考次数中尽可能完成任务，实在完不成使用AssignmentFinish工具，并告知完不成的信息
//                【当前思考次数】：
//                {now}
//                【最大思考次数】：
//                {max}
//                """;
//        PromptTemplate promptTemplate = new PromptTemplate(SYSTEM_PROMPT);
//        Prompt systemPrompt = promptTemplate.create(Map.of("now", 0, "max", this.getMaxSteps()));

//        this.setSystemPrompt(systemPrompt.getContents());
//        String NEXT_STEP_PROMPT = """
//                根据需求主动选择合适工具。复杂任务可分解后逐步解决。使用工具后需解释结果并建议下一步。如需停止交互，使用AssignmentFinish工具。
//                """;
//        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        //设置会话客户端
        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
