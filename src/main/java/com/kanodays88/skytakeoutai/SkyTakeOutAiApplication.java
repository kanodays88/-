package com.kanodays88.skytakeoutai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@MapperScan("com.kanodays88.skytakeoutai.mapper")
public class SkyTakeOutAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyTakeOutAiApplication.class, args);
    }

    //配置向量数据库，基于内存的向量数据库
    @Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel model){
        return SimpleVectorStore.builder(model).build();
    }
}
