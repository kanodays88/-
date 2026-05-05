package com.kanodays88.skytakeoutai.agent.router;

import com.kanodays88.skytakeoutai.advisor.MyLoggerAdvisor;
import com.kanodays88.skytakeoutai.constant.FileConstant;
import com.kanodays88.skytakeoutai.memory.FileBasedChatMemory;
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
import java.util.stream.Collectors;

public class RouterAgent {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final FileBasedChatMemory chatMemory;
    private final ToolCallback[] allTools;

    public RouterAgent(OpenAiChatModel model, EmbeddingModel embeddingModel,ToolCallback[] allTools){
        this.chatClient = ChatClient.builder(model).defaultAdvisors(new MyLoggerAdvisor()).build();
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        this.chatMemory = new FileBasedChatMemory(FileConstant.FILE_SAVE_DIR + "/chatMemory");
        this.allTools = allTools;
    }


    public RouteDecision route(String userPrompt, String conversationId) {

        //LLM分类 + 向量相似度辅助
        double vectorScore = getVectorRelevanceScore(userPrompt);
        return llmClassify(userPrompt, conversationId, vectorScore);
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

    private RouteDecision llmClassify(String prompt, String conversationId, double vectorScore) {
        String history = buildHistoryContext(conversationId);

        BeanOutputConverter<ClassifyResult> converter = new BeanOutputConverter<>(ClassifyResult.class);
        String systemPrompt = """
                你是一个问题分类专家，根据用户输入和对话历史，判断问题类型。

                当前向量相似度分数：{vectorScore}

                【分类规则】
                1. 如果向量相似度 > 0.7 → RAG_KNOWLEDGE
                   如果问题需要同时使用2种或以上工具才能完成 → COMPLEX_TASK
                2. 如果同时满足规则1（向量相似度 > 0.7）和规则2（需2种以上工具）→ RAG_AND_TASK
                4. 如果问题信息不完整，且历史对话无法补全缺失信息 → AMBIGUOUS（必须列出缺失信息列表）
                5. 以上都不符合 → SIMPLE_CHAT

                输出格式：{format}
                """;

        String userContent = "用户问题：" + prompt + "\n历史对话：\n" + history + "\n向量相似度分数：" + vectorScore;

        ClassifyResult result = chatClient.prompt()
                .system(s -> s.text(systemPrompt)
                        .param("vectorScore", String.valueOf(vectorScore))
                        .param("format", converter.getJsonSchema()))
                .user(userContent)
                .tools(allTools)
                .call()
                .entity(converter);

        if (result == null) {
            return new RouteDecision(QuestionType.SIMPLE_CHAT, 0.5, "LLM分类失败，默认简单对话", null);
        }

        QuestionType type;
        try {
            type = QuestionType.valueOf(result.questionType());
        } catch (IllegalArgumentException e) {
            type = QuestionType.SIMPLE_CHAT;
        }

        return new RouteDecision(type, result.confidence(), result.reason(), result.missingInfo());
    }

    private record ClassifyResult(String questionType, double confidence, String reason, List<String> missingInfo) {}

    private String buildHistoryContext(String conversationId) {
        List<Message> history = chatMemory.get(conversationId);
        if (history == null || history.isEmpty()) return "无历史对话";
        return history.stream()
                .map(m -> m.getMessageType() + ": " + m.getText())
                .collect(Collectors.joining("\n"));
    }
}
