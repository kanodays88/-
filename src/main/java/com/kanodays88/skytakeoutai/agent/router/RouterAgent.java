package com.kanodays88.skytakeoutai.agent.router;

import com.kanodays88.skytakeoutai.advisor.MyLoggerAdvisor;
import com.kanodays88.skytakeoutai.constant.FileConstant;
import com.kanodays88.skytakeoutai.memory.FileBasedChatMemory;
import com.kanodays88.skytakeoutai.skill.Skill;
import com.kanodays88.skytakeoutai.skill.SkillRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
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


    public RouteDecision route(String userPrompt, String conversationId) {
        //用LLM匹配这次用户提问所使用的skill
        List<String> selectedSkillNames = selectSkillsWithLLM(userPrompt);
        String skillContext = buildSelectedSkillsContext(selectedSkillNames);
        //计算RAG的相似度
        double vectorScore = getVectorRelevanceScore(userPrompt);
        RouteDecision decision = llmClassify(userPrompt, conversationId, vectorScore, skillContext);
        //将LLM匹配到的全部skill名称携带到RouteDecision中，供下游Agent使用
        return new RouteDecision(
                decision.questionType(),
                decision.reason(),
                decision.missingInfo(),
                selectedSkillNames
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
                你是一个问题分类专家，根据用户输入和对话历史，判断问题类型。

                当前向量相似度分数：{vectorScore}

                【匹配到以下业务技能】
                {skillContext}

                【分类规则】
                1. 如果向量相似度 > 0.7 → RAG_KNOWLEDGE
                2. 如果匹配到业务技能，检查用户输入+历史对话是否提供了该技能的【必需参数】:
                   - 已完整提供所有必需参数 → COMPLEX_TASK
                   - 缺少部分必需参数，或者用户需求模糊 → AMBIGUOUS（missingInfo 列出具体缺失的参数名和说明）
                3. 如果没有匹配到技能，但需要 2 种以上工具 → COMPLEX_TASK
                4. 同时满足 1 和 2/3 → RAG_AND_TASK
                5. 以上都不符合 → SIMPLE_CHAT

                【参数检查说明】
                - 用户可能使用同义词或口语化表达，请按语义判断参数是否已提供
                - 历史对话中已提供的参数也算已提供
                - 匹配到多个技能时，任一技能的必需参数缺失都应列入 missingInfo

                【输出参数说明】
                questionType: 问题分类的结果
                reason: 判断理由
                missingInfo: 缺失的信息列表（只有当questionType为AMBIGUOUS才会有内容，否则为空）

                输出格式：{format}
                """;

        String userContent = "用户问题：" + prompt + "\n历史对话：\n" + history + "\n向量相似度分数：" + vectorScore;

        ClassifyResult result = chatClient.prompt()
                .system(s -> s.text(systemPrompt)
                        .param("vectorScore", String.valueOf(vectorScore))
                        .param("skillContext", skillContext)
                        .param("format", converter.getJsonSchema()))
                .user(userContent)
                .tools(allTools)
                .call()
                .entity(converter);

        if (result == null) {
            return new RouteDecision(QuestionType.SIMPLE_CHAT, "LLM分类失败，默认简单对话", null, null);
        }

        QuestionType type;
        try {
            type = QuestionType.valueOf(result.questionType());
        } catch (IllegalArgumentException e) {
            type = QuestionType.SIMPLE_CHAT;
        }

        return new RouteDecision(type, result.reason(), result.missingInfo(), null);
    }

    private record ClassifyResult(String questionType, String reason, List<String> missingInfo) {}
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
    private String buildSelectedSkillsContext(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) return "未匹配到具体业务技能。";
        List<Skill> selected = skillNames.stream()
                .map(name -> skillRegistry.getSkill(name))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (selected.isEmpty()) return "未匹配到具体业务技能。";
        //将指定的skill集合，格式化为LLM的上下文文本格式
        return skillRegistry.getSkillsPromptContext(selected);
    }

    private String buildHistoryContext(String conversationId) {
        List<Message> history = chatMemory.get(conversationId);
        if (history == null || history.isEmpty()) return "无历史对话";
        return history.stream()
                .map(m -> m.getMessageType() + ": " + m.getText())
                .collect(Collectors.joining("\n"));
    }
}
