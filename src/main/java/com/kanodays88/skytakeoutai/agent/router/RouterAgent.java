package com.kanodays88.skytakeoutai.agent.router;

import com.kanodays88.skytakeoutai.advisor.MyLoggerAdvisor;
import com.kanodays88.skytakeoutai.agent.sse.SSESend;
import com.kanodays88.skytakeoutai.common.ChatSystem;
import com.kanodays88.skytakeoutai.constant.FileConstant;
import com.kanodays88.skytakeoutai.content.BaseContent;
import com.kanodays88.skytakeoutai.memory.FileBasedChatMemory;
import com.kanodays88.skytakeoutai.skill.Skill;
import com.kanodays88.skytakeoutai.skill.SkillRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RouterAgent {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final FileBasedChatMemory chatMemory;
    private final ToolCallback[] allTools;
    private final SkillRegistry skillRegistry;
    private final SseEmitter sseEmitter;

    public RouterAgent(OpenAiChatModel model, VectorStore vectorStore, ToolCallback[] allTools, SkillRegistry skillRegistry, SseEmitter sseEmitter) throws IOException {
        this.chatClient = ChatClient.builder(model).defaultAdvisors(new MyLoggerAdvisor()).build();
        this.vectorStore = vectorStore;
        this.chatMemory = new FileBasedChatMemory(Paths.get(FileConstant.FILE_SAVE_DIR,BaseContent.getUser().getUserName(),"chatMemory").toString());
        this.allTools = allTools;
        this.skillRegistry = skillRegistry;
        this.sseEmitter = sseEmitter;
    }


    public RouteDecisionTotal route(String userPrompt, String conversationId) {
        SSESend.sendEventThink(sseEmitter,"正在进行意图分析...\n");
        //用LLM匹配这次用户提问所使用的skill
        List<String> selectedSkillNames = selectSkillsWithLLM(userPrompt);
        List<Skill> skills = buildSelectedSkillsContext(selectedSkillNames);
        String skillContext = skills == null||skills.isEmpty()?"未匹配到技能":skillRegistry.getSkillsPromptContext(skills);
//        //计算RAG的相似度
//        java.util.List<Document> documents = getVectorRelevanceScore(userPrompt);
//        double vectorScore = documents == null||documents.isEmpty()?0.0:documents.get(0).getScore();
        RouteDecision decision = llmClassify(userPrompt, conversationId, skillContext);
        SSESend.sendEventThink(sseEmitter,decision.reason()+"\n");
        //将LLM匹配到的全部skill名称携带到RouteDecision中，供下游Agent使用
        return new RouteDecisionTotal(
                decision,
                skills
        );
    }

    private List<Document> getVectorRelevanceScore(String prompt) {
        if (prompt == null || prompt.isBlank()) return null;
        SearchRequest request = SearchRequest.builder()
                .query(prompt)
                .topK(1)
                .build();
        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null || documents.isEmpty()) return null;
        return documents;
    }

    private RouteDecision llmClassify(String prompt, String conversationId, String skillContext) {
        String history = buildHistoryContext(conversationId);

        BeanOutputConverter<ClassifyResult> converter = new BeanOutputConverter<>(ClassifyResult.class);
        String systemPrompt = """
                 {role}
                 【当前工作】
                 你是一个问题分类专家，根据用户输入和对话历史，判断问题类型，并结合业务技能生成这次对话的总任务
                 工具信息只用做判断该问题需要用到哪些工具，**绝对禁止实际调用任何工具**
    
                 【匹配到以下业务技能】
                 {skillContext}
    
                 【分类规则（优先级从高到低）】
                 1. 如果匹配到1个或多个业务技能，根据每个技能的业务流程描述和参数表，
                     综合判断用户输入+历史对话中的信息是否足以开始执行业务流程:
                     - 若存在至少1个技能信息充分、可以执行 → COMPLEX_TASK
                     - 若所有匹配技能均缺少启动必需的重要信息 → AMBIGUOUS
                       （missingInfo 列出具体缺失的信息点）
                     - 多个技能匹配时，优先选择与用户输入语义最相关、信息最充分的技能作为主任务依据
                 2. 如果没有匹配到任何业务技能，但解决该问题需要使用2种及以上工具 → COMPLEX_TASK
                 3. 如果没有匹配到任何业务技能，且结合历史对话和用户问题（无历史会话就单独判断用户问题）判断该问题模糊不清、语义不明确 → AMBIGUOUS
                 4. 如果只需用到一个工具，或者能够从历史对话中找到答案 → SIMPLE_CHAT
    
                 【输出参数说明】
                 questionType: 问题分类的结果，只能是COMPLEX_TASK / AMBIGUOUS / SIMPLE_CHAT三者之一
                 reason: 详细的判断理由，需明确说明匹配到的业务技能（如有）、信息充分性判断依据或问题模糊的具体原因
                 returnQuestion: 反问用户的问题（只有当questionType为AMBIGUOUS才会有内容，否则为空字符串）
                 mainTask: 详细总任务：
                     - 如果有业务技能，必须严格按照该技能的业务流程描述撰写
                     - 如果未匹配到业务技能但为COMPLEX_TASK，需明确说明核心任务和需要用到的工具
                     - 如果为SIMPLE_CHAT或AMBIGUOUS，需简要说明当前对话状态
                     - 如果用户询问的问题与匹配的业务无关，或者没有匹配到业务，需要用到联网搜索工具
    
                 【输出要求】
                 严格按照以下JSON格式输出，不得包含任何额外的解释、说明或markdown内容：
                 JSON格式里的内容不得使用双引号：
                    错误案例："Content":"用户的名字叫"五条五""
                    正确案例："Content":"用户的名字叫五条五"
                 具体json格式：{format}
                """;

        String userContent = "用户问题：" + prompt + "\n历史对话：\n" + history;

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false) // 核心配置：禁用Spring AI内部自动工具执行
                // 可保留原有的其他通义千问专属配置（模型名、温度、top_p等）
                .build();

        Prompt userPrompt = new Prompt(new UserMessage(userContent), chatOptions);

        ClassifyResult result = chatClient.prompt(userPrompt)
                .system(s -> s.text(systemPrompt)
                        .param("skillContext", skillContext)
                        .param("format", converter.getJsonSchema())
                        .param("role", ChatSystem.CHAT_SYSTEM))
                .toolCallbacks(allTools)
                .call()
                .entity(converter);

        if (result == null) {
            return new RouteDecision(QuestionType.SIMPLE_CHAT, "LLM分类失败，默认简单对话",null,userContent);
        }

        QuestionType type;
        try {
            type = QuestionType.valueOf(result.questionType());
        } catch (IllegalArgumentException e) {
            type = QuestionType.SIMPLE_CHAT;
        }

        return new RouteDecision(type, result.reason(), result.returnQuestion,result.mainTask());
    }

    private record ClassifyResult(String questionType, String reason, String returnQuestion,String mainTask) {}
    private record SkillSelection(List<String> skillNames) {}

    private List<String> selectSkillsWithLLM(String userPrompt) {
        String skillsSummary = skillRegistry.getAllSkills().stream()
                .map(s -> "- " + s.getName() + ": " + s.getDescription())
                .collect(Collectors.joining("\n"));
        if (skillsSummary.isBlank()) return List.of();
        //获取历史会话
        String history = buildHistoryContext(BaseContent.getChatId());

        BeanOutputConverter<SkillSelection> converter = new BeanOutputConverter<>(SkillSelection.class);
        String prompt = """
                根据用户输入和历史对话，从以下可用技能中选择最匹配的业务技能（可多选，也可不选）：

                {skills}

                选择原则：仅基于技能名称和描述进行语义匹配。
                【历史会话】：{history}

                json输出格式：{format}
                """;

        SkillSelection result = chatClient.prompt()
                .system(s -> s.text(prompt)
                        .param("skills", skillsSummary)
                        .param("format", converter.getJsonSchema())
                        .param("history",history))
                .user(userPrompt)
                .call()
                .entity(converter);

        return result != null ? result.skillNames() : List.of();
    }

    /**
     * 通过skillName加载详细的skill
     * @param skillNames
     * @return
     */
    private List<Skill> buildSelectedSkillsContext(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) return null;
        List<Skill> selected = skillNames.stream()
                .map(name -> skillRegistry.getSkill(name))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (selected.isEmpty()) return null;
        //将指定的skill集合，格式化为LLM的上下文文本格式
        return selected;
    }

    private String buildHistoryContext(String conversationId) {
        List<Message> history = chatMemory.get(conversationId);
        if (history == null || history.isEmpty()) return "无历史对话";
        return history.stream()
                .map(m -> m.getMessageType() + ": " + m.getText())
                .collect(Collectors.joining("\n"));
    }
}
