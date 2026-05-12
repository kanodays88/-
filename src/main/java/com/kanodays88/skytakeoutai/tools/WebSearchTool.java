package com.kanodays88.skytakeoutai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WebSearchTool {

    //创建了一个静态的OkHttpClient实例，避免重复创建连接
    //设置了300秒（5分钟）的读取超时，适合处理可能耗时较长的搜索请求
    //使用单例模式，提高性能和资源利用率
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient().newBuilder().readTimeout(300, TimeUnit.SECONDS).build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(); // 用于JSON解析

    // ========== 注入Spring AI 标准组件（千问Embedding自动适配） ==========
    private final EmbeddingModel qianfanEmbeddingModel;
    // Spring AI 内置token分块器（和你本地知识库用的完全一样）
    private TokenTextSplitter textSplitter = new TokenTextSplitter();

    public WebSearchTool(EmbeddingModel qianfanEmbeddingModel) {
        this.qianfanEmbeddingModel = qianfanEmbeddingModel;

        // 自定义分片参数（搜索场景推荐配置）
        this.textSplitter = TokenTextSplitter.builder()
                .withChunkSize(600)                    // 每个块最大300 token（搜索场景推荐小一点）
                .withMinChunkSizeChars(50)             // 最小截断字符数（从标点符号截断的阈值）
                .withMinChunkLengthToEmbed(20)         // 丢弃小于20字符的块
                .withMaxNumChunks(50)                 // 单个文本最多生成50个块
                .withKeepSeparator(true)                // 保留分隔符
                .build();
    }

    //注意，我在工具统一注册的时候使用了new,new会绕过spring的容器管理，导致无法注入这个apikey
    @Value("${app.baidu.api.key}")
    private String ApiKey;

//    public String webSearch(String key) throws IOException{
//        //得到整理后的原始搜索结果
//        String webSearchResult = baiDuiWebSearch(key);
//        log.info("整理后的原始结果：{}",webSearchResult);
//        //rag检索增强，提炼搜索精华
//        String ragResult = ragOptimizeJson(webSearchResult, key);
//        log.info("rag增强结果：{}",ragResult);
//        return ragResult;
//    }

    /**
     * 批量搜索工具，可同时搜索多个关键词。参数keywords用"||"分隔，例如："上海热门景点||上海特色美食||上海交通路线"。内部会自动并行搜索并去重合并结果
     */
    @Tool(description = "批量搜索工具，可同时搜索多个关键词。参数keywords用\"||\"分隔，例如：\"上海热门景点||上海特色美食||上海交通路线\"。内部会自动并行搜索并去重合并结果")
    public String batchWebSearch(@ToolParam(description = "搜索关键词，多个关键词用||分隔，例如：上海景点||上海美食||上海交通") String keywords) throws Exception {
        // 参数校验：空/null 返回错误 JSON
        if (keywords == null || keywords.trim().isEmpty()) {
            return "{\"error\": \"No keywords provided\", \"message\": \"请提供至少一个搜索关键词\"}";
        }

        // 按 || 分割，去空，限制最大6个
        String[] keywordArray = keywords.split("\\|\\|");
        List<String> validKeywords = Arrays.stream(keywordArray)
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .limit(6)
                .collect(Collectors.toList());

        if (validKeywords.isEmpty()) {
            return "{\"error\": \"No valid keywords\", \"message\": \"请提供至少一个有效的搜索关键词\"}";
        }

        // 并行搜索：每个关键词独立 CompletableFuture
        ExecutorService executor = Executors.newFixedThreadPool(validKeywords.size());
        try {
            List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();
            for (String kw : validKeywords) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        String rawResult = baiDuiWebSearch(kw);
                        // 给每个 reference 添加 searchedKeyword 标记
                        rawResult = annotateWithKeyword(rawResult, kw);
                        return Map.entry(kw, rawResult);
                    } catch (Exception e) {
                        log.warn("关键词 [{}] 搜索失败: {}", kw, e.getMessage());
                        return Map.entry(kw, "{\"error\":\"搜索失败: " + e.getMessage() + "\"}");
                    }
                }, executor));
            }

            // 等待所有完成
            List<Map.Entry<String, String>> allResults = futures.stream()
                    .map(f -> {
                        try { return f.get(60, TimeUnit.SECONDS); }
                        catch (Exception e) { return Map.entry("unknown", "{\"error\":\"搜索超时\"}"); }
                    })
                    .collect(Collectors.toList());

            // 合并所有结果为一个大的 JSON references 数组
            String mergedJson = mergeAllResults(allResults);

            // 对合并后的结果做一次全局 RAG 增强（使用压缩后的参数）
            String query = String.join(" ", validKeywords);
            String ragResult = ragOptimizeJson(mergedJson, query);

            return ragResult;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 给 JSON references 中每个 reference 添加 searchedKeyword 字段
     */
    private String annotateWithKeyword(String jsonResult, String keyword) throws IOException {
        //这是一个数组型json结构，他的子JSONNode是数组内的元素
        JsonNode rootArray = OBJECT_MAPPER.readTree(jsonResult);
        if (!rootArray.isArray()) return jsonResult;

        ArrayNode annotatedArray = OBJECT_MAPPER.createArrayNode();
        for (JsonNode node : rootArray) {
            ObjectNode annotated = node.deepCopy();
            annotated.put("searchedKeyword", keyword);
            annotatedArray.add(annotated);
        }
        return OBJECT_MAPPER.writeValueAsString(annotatedArray);
    }

    /**
     * 合并多个关键词的搜索结果，去重（按content文本去重）
     */
    private String mergeAllResults(List<Map.Entry<String, String>> allResults) throws IOException {
        ArrayNode mergedArray = OBJECT_MAPPER.createArrayNode();
        HashSet<String> seenContents = new HashSet<>();

        for (Map.Entry<String, String> entry : allResults) {
            //遍历每个关键字的查询结果
            String keyword = entry.getKey();
            String json = entry.getValue();

            try {
                JsonNode rootArray = OBJECT_MAPPER.readTree(json);
                if (!rootArray.isArray()) continue;

                for (JsonNode node : rootArray) {
                    String content = node.has("content") ? node.get("content").asText("") : "";
                    // 去重：相同 content 只保留第一次出现的，用于做hash匹配的前缀
                    String contentHash = content.length() > 50 ? content.substring(0, 50) : content;
                    if (!seenContents.contains(contentHash)) {
                        seenContents.add(contentHash);
                        // 添加 searchedKeyword 标记
                        ObjectNode withKeyword = node.deepCopy();
                        if (!withKeyword.has("searchedKeyword")) {
                            withKeyword.put("searchedKeyword", keyword);
                        }
                        mergedArray.add(withKeyword);
                    }
                }
            } catch (Exception e) {
                log.warn("合并结果时解析失败: {}", e.getMessage());
            }
        }

        // 如果全部失败，包一个错误信息
        if (mergedArray.isEmpty()) {
            return "{\"error\":\"All searches failed\",\"partialResults\":[]}";
        }

        return OBJECT_MAPPER.writeValueAsString(mergedArray);
    }

    /**
     * 百度搜索api
     * @param key
     * @return
     * @throws IOException
     */
    public String baiDuiWebSearch(String key) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");
        String json = "{\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"edition\":\"standard\",\"search_source\":\"baidu_search_v2\",\"search_recency_filter\":\"week\"}";
        String jsonContent = String.format(json,key);
        RequestBody body = RequestBody.create(mediaType, jsonContent);
        Request request = new Request.Builder()
                .url("https://qianfan.baidubce.com/v2/ai_search/web_search")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer "+ApiKey)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API请求失败，状态码: " + response.code());
            }

            String result = response.body().string();
            log.info("搜索结果：{}",result);
            return extractReferencesArray(result);
        }
    }

    /**
     * 从API响应中提取并过滤references数组，按需求生成新JSON
     */
    private String extractReferencesArray(String jsonResponse) throws IOException {
        JsonNode rootNode = OBJECT_MAPPER.readTree(jsonResponse);

        // 校验根节点是否存在references数组
        if (rootNode.has("references") && rootNode.get("references").isArray()) {
            JsonNode referencesArray = rootNode.get("references");
            ArrayNode filteredArray = OBJECT_MAPPER.createArrayNode();

            // 遍历每个引用元素，按需过滤
            for (JsonNode ref : referencesArray) {
                if (!ref.isObject()) {
                    continue;
                }
                ObjectNode filteredRef = OBJECT_MAPPER.createObjectNode();

                // 1. 保留需求指定的3个核心字段
                filteredRef.set("content", ref.get("content"));
                filteredRef.set("video", ref.get("video"));
                filteredRef.set("date", ref.get("date"));

                // 2. 构建合并后的images数组
                ArrayNode mergedImages = OBJECT_MAPPER.createArrayNode();

                // 2.1 先添加顶层image字段的内容（非空才添加）
                JsonNode topImageNode = ref.get("image");
                if (topImageNode != null && !topImageNode.isNull()) {
                    mergedImages.add(topImageNode);
                }

                // 2.2 再添加web_extensions.images数组的所有元素（校验字段存在性）
                if (ref.has("web_extensions") && ref.get("web_extensions").isObject()) {
                    JsonNode webExtNode = ref.get("web_extensions");
                    if (webExtNode.has("images") && webExtNode.get("images").isArray()) {
                        JsonNode extImagesNode = webExtNode.get("images");
                        for (JsonNode imgItem : extImagesNode) {
                            mergedImages.add(imgItem);
                        }
                    }
                }

                // 3. 将合并后的images数组放入结果对象
                filteredRef.set("images", mergedImages);
                filteredArray.add(filteredRef);
            }

            return OBJECT_MAPPER.writeValueAsString(filteredArray);
        }

        throw new IOException("响应中缺少合法的references数组");
    }


    /**
     * rag检索增强，对网页搜索结果进行检索增强
     * @param extractedJson
     * @param query
     * @return
     * @throws IOException
     */
    private String ragOptimizeJson(String extractedJson, String query) throws IOException {
        JsonNode rootArray = OBJECT_MAPPER.readTree(extractedJson);
        if (!rootArray.isArray()) return extractedJson;

        // 按搜索引擎原始顺序，将所有搜索结果整合成document集合，并按照id标记每个document所属的搜索结果
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < rootArray.size(); i++) {
            JsonNode node = rootArray.get(i);
            String content = node.has("content") ? node.get("content").asText("") : "";
            // 把原始节点索引存入元数据，后面回填用
            int finalI = i;
            documents.add(new Document(content, new java.util.HashMap<>() {{
                put("originalIndex", finalI);
            }}));
        }
        //将整个document向量化，并且只提取最高相关度的5个片段，只有和这5个片段有关的搜索结果才会被返回，其余全部作废
        List<Document> relevantDocs = extractCoreContentFromSingleResult(documents, query);
        // ================== 新增：合并 originalIndex 相同的 Document ==================
        // 将Document按 originalIndex 分组,相同会被分到一组
        Map<Integer, List<Document>> groupedByIndex = new HashMap<>();
        for (Document doc : relevantDocs) {
            int idx = (int) doc.getMetadata().get("originalIndex");
            //computeIfAbsent方法，如果hashMap中存在idx为键的元素则直接返回元素，无则执行右侧的lambda表达式
            groupedByIndex.computeIfAbsent(idx, k -> new ArrayList<>()).add(doc);
        }

        //合并每组的 Document（content 拼接，元数据复用首项）
        List<Document> mergedDocs = new ArrayList<>();
        for (List<Document> docs : groupedByIndex.values()) {
            if (docs.size() == 1) {
                mergedDocs.add(docs.get(0));
            } else {
                // 拼接 content（可根据需求调整分隔符，如 "\n"、" " 等）
                String combinedContent = docs.stream()
                        .map(Document::getText)
                        .collect(Collectors.joining("\n\n"));
                // 构造成新的Document并存入
                mergedDocs.add(new Document(combinedContent,docs.get(0).getMetadata()));
            }
        }
        //构建最终json
        ArrayNode resultArray = OBJECT_MAPPER.createArrayNode();
        for (Document doc : mergedDocs) {
            int originalIndex = (int) doc.getMetadata().get("originalIndex");
            ObjectNode originalNode = (ObjectNode) rootArray.get(originalIndex).deepCopy();
            // 仅替换content为RAG提纯后的内容，video/date/images完全保留
            originalNode.put("content", doc.getText());
            resultArray.add(originalNode);
        }

        // 兜底
        if (resultArray.isEmpty()) {
            log.warn("RAG未检索到有效内容，返回原始搜索结果");
            return extractedJson;
        }

        return OBJECT_MAPPER.writeValueAsString(resultArray);
    }

    /**
     * 从单个搜索结果中提取最核心的1个chunk
     */
    private List<Document> extractCoreContentFromSingleResult(List<Document> documents, String query) {
        //分片时每个分片都会保留原document的Metadata元信息
        List<Document> chunks = textSplitter.split(documents);
        if (chunks.isEmpty()) return null;

        SimpleVectorStore vectorStore = SimpleVectorStore.builder(qianfanEmbeddingModel).build();
        vectorStore.add(chunks);

        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(3)
                        .similarityThreshold(0.5)
                        .build()
        );

        return relevantDocs;
    }

    /**
     * 纯数据载体：record 自动生成构造器、getter、equals、hashCode、toString
     */
    private record RefinedSearchResult(
            int originalIndex,
            String coreContent,
            JsonNode originalNode//原来节点结构
    ) {}
}



