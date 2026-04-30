package com.kanodays88.skytakeoutai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class WebSearchTool {

    //创建了一个静态的OkHttpClient实例，避免重复创建连接
    //设置了300秒（5分钟）的读取超时，适合处理可能耗时较长的搜索请求
    //使用单例模式，提高性能和资源利用率
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient().newBuilder().readTimeout(300, TimeUnit.SECONDS).build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(); // 用于JSON解析

    //注意，我在工具统一注册的时候使用了new,new会绕过spring的容器管理，导致无法注入这个apikey
    @Value("${app.baidu.api.key}")
    private String ApiKey;

    @Tool(description = "搜索工具，传入关键词搜索出结果")
    public String webSearch(@ToolParam(description = "搜索的关键词") String key) throws IOException {
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
}



