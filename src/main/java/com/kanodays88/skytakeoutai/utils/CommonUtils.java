package com.kanodays88.skytakeoutai.utils;

import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class CommonUtils {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String CHAT_RAG_STATIC = "chat:ragStatic:";

    @Data
    private class RAG{
        boolean questionStatic; //RAG问题解决状态
        String initialQuestion;  //RAG初始问题
    }


    public boolean writeChatCache(String prefix,String chatId,String msg){
        //先判断是否redis缓存
        String json = stringRedisTemplate.opsForValue().get(CHAT_RAG_STATIC + chatId);
        if(json == null || json.equals("")){
            setChatCache(prefix+chatId,msg,false);
            return false;
        }

        else{
            stringRedisTemplate.expire(prefix+chatId,30,TimeUnit.MINUTES);
            RAG rag = JSONUtil.toBean(json, RAG.class);
            return rag.questionStatic == true?true:false;
        }
    }

    public String getChatCache(String key){
        String s = stringRedisTemplate.opsForValue().get(key);
        RAG rag = JSONUtil.toBean(s, RAG.class);
        return rag.getInitialQuestion();
    }

    public void setChatCache(String key,String initialQuestion,boolean questionStatic){
        RAG rag = new RAG();
        rag.setInitialQuestion(initialQuestion);
        rag.setQuestionStatic(questionStatic);
        String jsonStr = JSONUtil.toJsonStr(rag);
        stringRedisTemplate.opsForValue().set(key,jsonStr,30, TimeUnit.MINUTES);
    }


    @PostConstruct
    public void init(){
        writePdfInVectorStore("vectorStorePdf/菜品口味信息.pdf");
    }

    public void writePdfInVectorStore(String filePath){
        //将文件路径写入到数据源，数据源resource内部会通过getInputStream获取文件内容
        FileSystemResource resource = new FileSystemResource(filePath);

        //创建PDF读取器
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource,//pdf文件的数据源
                PdfDocumentReaderConfig.builder().
                        withPageExtractedTextFormatter(ExtractedTextFormatter.defaults()).//默认转换器
                        withPagesPerDocument(1).//指定每一页pdf为一个docment
                        build()
        );

//        数据源resource内部会通过getInputStream获取输入流，reader通过输入流获取文件内容
        List<Document> documents = reader.read();

        //将读取到的documents存放到向量数据库,数据库内部会通过向量模型具体把每个数据向量化
        //且向量化后的数据会有一个向量索引和一个标量索引
        //标量索引是存储原来document中的元数据信息，比如所属文件名之类，方便索引查找
        //向量索引是用于快速计算不同向量之间的近似度
        vectorStore.add(documents);
    }



}
