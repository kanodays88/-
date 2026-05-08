package com.kanodays88.skytakeoutai.agent.router;

import com.kanodays88.skytakeoutai.advisor.MyLoggerAdvisor;
import com.kanodays88.skytakeoutai.constant.FileConstant;
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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RouterAgent {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final FileBasedChatMemory chatMemory;
    private final ToolCallback[] allTools;
    private final SkillRegistry skillRegistry;

    public RouterAgent(OpenAiChatModel model, EmbeddingModel embeddingModel, ToolCallback[] allTools, SkillRegistry skillRegistry){
        this.chatClient = ChatClient.builder(model).defaultAdvisors(new MyLoggerAdvisor()).build();
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        this.chatMemory = new FileBasedChatMemory(FileConstant.FILE_SAVE_DIR + "/chatMemory");
        this.allTools = allTools;
        this.skillRegistry = skillRegistry;
    }


    public RouteDecisionAndSkills route(String userPrompt, String conversationId) {
        //用LLM匹配这次用户提问所使用的skill
        List<String> selectedSkillNames = selectSkillsWithLLM(userPrompt);
        List<Skill> skills = buildSelectedSkillsContext(selectedSkillNames);
        String skillContext = skills.isEmpty()||skills == null?"未匹配到技能":skillRegistry.getSkillsPromptContext(skills);
        //计算RAG的相似度
        double vectorScore = getVectorRelevanceScore(userPrompt);
        RouteDecision decision = llmClassify(userPrompt, conversationId, vectorScore, skillContext);
        //将LLM匹配到的全部skill名称携带到RouteDecision中，供下游Agent使用
        return new RouteDecisionAndSkills(
                decision,
                skills
        );
    }

    private double getVectorRelevanceScore(String prompt) {
        if (prompt == null || prompt.isBlank()) return 0.0;
        SearchRequest request = SearchRequest.builder()
                .query(prompt)
                .topK(1)
                .build();
        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null || documents.isEmpty()) return 0.0;
        return documents.get(0).getScore();
    }

    private RouteDecision llmClassify(String prompt, String conversationId, double vectorScore, String skillContext) {
        String history = buildHistoryContext(conversationId);

        BeanOutputConverter<ClassifyResult> converter = new BeanOutputConverter<>(ClassifyResult.class);
        String systemPrompt = """
                你是一个问题分类专家，根据用户输入和对话历史，判断问题类型，并结合业务技能生成这次对话的总任务
                工具信息只用做判断该问题需要用到那些工具，不得使用工具

                当前向量相似度分数：{vectorScore}

                【匹配到以下业务技能】
                {skillContext}

                【分类规则】
                1. 如果向量相似度 > 0.7 → RAG_KNOWLEDGE
                2. 如果匹配到业务技能，根据技能的业务流程描述和参数表，
                    综合判断用户输入+历史对话中的信息是否足以开始执行业务流程:
                    - 信息充分、可以执行 → COMPLEX_TASK
                    - 缺少关键信息，或需求模糊 → AMBIGUOUS
                      （missingInfo 列出具体缺失的信息）
                3. 如果没有匹配到技能，但需要 2 种以上工具 → COMPLEX_TASK
                4. 同时满足 1 和 2/3 → RAG_AND_TASK
                5. 以上都不符合 → SIMPLE_CHAT

                【参数检查说明】
                - 不要机械匹配参数名称，应根据业务流程描述判断哪些信息是当前步骤必需的
                - 用户可能使用同义词或口语化表达，请按语义理解
                - 历史对话中已提供的信息也算已提供
                - 匹配到多个技能时，综合判断各技能所需关键信息是否齐全

                【输出参数说明】
                questionType: 问题分类的结果
                reason: 判断理由
                missingInfo: 缺失的信息列表（只有当questionType为AMBIGUOUS才会有内容，否则为空）
                mainTask: 总任务

                JSON输出格式：{format}
                """;

        String userContent = "用户问题：" + prompt + "\n历史对话：\n" + history + "\n向量相似度分数：" + vectorScore;

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false) // 核心配置：禁用Spring AI内部自动工具执行
                // 可保留原有的其他通义千问专属配置（模型名、温度、top_p等）
                .build();

        Prompt userPrompt = new Prompt(new UserMessage(userContent), chatOptions);

        ClassifyResult result = chatClient.prompt(userPrompt)
                .system(s -> s.text(systemPrompt)
                        .param("vectorScore", String.valueOf(vectorScore))
                        .param("skillContext", skillContext)
                        .param("format", converter.getJsonSchema()))
                .toolCallbacks(allTools)
                .call()
                .entity(converter);

        if (result == null) {
            return new RouteDecision(QuestionType.SIMPLE_CHAT, "LLM分类失败，默认简单对话", null,userContent);
        }

        QuestionType type;
        try {
            type = QuestionType.valueOf(result.questionType());
        } catch (IllegalArgumentException e) {
            type = QuestionType.SIMPLE_CHAT;
        }

        return new RouteDecision(type, result.reason(), result.missingInfo(),result.mainTask());
    }

    private record ClassifyResult(String questionType, String reason, List<String> missingInfo,String mainTask) {}
    private record SkillSelection(List<String> skillNames) {}

    private List<String> selectSkillsWithLLM(String userPrompt) {
        String skillsSummary = skillRegistry.getAllSkills().stream()
                .map(s -> "- " + s.getName() + ": " + s.getDescription())
                .collect(Collectors.joining("\n"));
        if (skillsSummary.isBlank()) return List.of();

        BeanOutputConverter<SkillSelection> converter = new BeanOutputConverter<>(SkillSelection.class);
        String prompt = """
                根据用户输入，从以下可用技能中选择最匹配的业务技能（可多选，也可不选）：

                {skills}

                选择原则：仅基于技能名称和描述进行语义匹配。
                如果用户问题与任何技能都不匹配，返回空列表。

                输出格式：{format}
                """;

        SkillSelection result = chatClient.prompt()
                .system(s -> s.text(prompt)
                        .param("skills", skillsSummary)
                        .param("format", converter.getJsonSchema()))
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
