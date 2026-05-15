package com.kanodays88.skytakeoutai.service.impl;

import cn.hutool.json.JSONUtil;
import com.kanodays88.skytakeoutai.constant.FileConstant;
import com.kanodays88.skytakeoutai.content.BaseContent;
import com.kanodays88.skytakeoutai.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public boolean save(String chatId, Resource resource) {
        // 1.保存到本地磁盘
        String filename = resource.getFilename();//获取文件名字
        File target = new File(FileConstant.FILE_SAVE_DIR+"\\"+ BaseContent.getUser().getUserName()+"\\"+chatId+"\\"+Objects.requireNonNull(filename));
        if (!target.exists()) {
            try {
                // 2. 关键修复：创建所有不存在的父目录
                Files.createDirectories(target.toPath().getParent());
                Files.copy(resource.getInputStream(), target.toPath());
            } catch (IOException e) {
                log.error("Failed to save PDF resource.", e);
                return false;
            }
        }
        // 3.写入向量库
        writeToVectorStore(resource, chatId);
        return true;
    }

    public void writeToVectorStore(Resource resource, String chatId) {
        // 1.创建PDF的读取器
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource, // 文件源
                PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                        .withPagesPerDocument(1) // 每1页PDF作为一个Document
                        .build()
        );
        // 2.读取PDF文档，拆分为Document
        List<Document> documents = reader.read();
        documents.forEach(document -> {
            document.getMetadata().put("user", BaseContent.getUser().getUserName());
            document.getMetadata().put("chat_id",chatId);
        });
        // 3.写入向量库
        vectorStore.add(documents);
    }
}
