package com.kanodays88.skytakeoutai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {
        OpenAiAudioSpeechAutoConfiguration.class,      // 语音合成
        OpenAiAudioTranscriptionAutoConfiguration.class, // 语音转文字
        OpenAiImageAutoConfiguration.class,            // 图像生成
        OpenAiModerationAutoConfiguration.class        // 内容审核
})
@MapperScan("com.kanodays88.skytakeoutai.mapper")
public class SkyTakeOutAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyTakeOutAiApplication.class, args);
    }

    //配置向量数据库，基于内存的向量数据库
    @Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel model){

        SimpleVectorStore vectorStore = SimpleVectorStore.builder(model).build();

        return vectorStore;
    }
}
