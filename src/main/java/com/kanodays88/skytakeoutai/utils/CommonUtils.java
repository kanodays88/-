package com.kanodays88.skytakeoutai.utils;

import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class CommonUtils {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String CHAT_RAG_STATIC = "chat:ragStatic:";

    //RAG锁/////////////////////////////////////////////////////////////////////
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

    //////////////////////////////////////////////////////////////////////


//    @PostConstruct
    public void init(){
        writeQaPdfInVectorStore("vectorStorePdf/常见问题FAQ.pdf");
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

    /**
     * 【精准切片版】FAQ问答PDF解析，按单个问答对切片写入向量库
     * @param filePath PDF文件本地路径
     */
    public void writeQaPdfInVectorStore(String filePath){
        // 1. 加载PDF文件资源，与原有逻辑完全对齐
        FileSystemResource resource = new FileSystemResource(filePath);

        // 2. 配置PDF读取器：整份PDF合并为1个文本块，避免分页拆分问答对
        PdfDocumentReaderConfig fullTextConfig = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                .withPagesPerDocument(Integer.MAX_VALUE) // 核心配置：关闭分页拆分，读取完整文本
                .build();
        PagePdfDocumentReader fullTextReader = new PagePdfDocumentReader(resource, fullTextConfig);

        // 3. 读取PDF完整文本
        List<Document> fullTextDocuments = fullTextReader.read();
        if (fullTextDocuments.isEmpty()) {
            log.warn("PDF文件无有效内容，文件路径：{}", filePath);
            return;
        }
        String fullPdfText = fullTextDocuments.get(0).getText();
        // 【修复点1】文本预处理：统一清理多余空白、换行，将缩进文本“拉直”
        // 1. 将所有非断句换行替换为空格
        // 2. 将多个连续空白（包括缩进）压缩为单个空格
        String cleanedText = fullPdfText
                .replaceAll("\\r?\\n", " ") // 先把所有换行换成空格
                .replaceAll("\\s+", " ");    // 再把所有连续空白压缩成单个空格

        log.info("文本预处理完成，清理后长度：{}", cleanedText.length());
        log.debug("清理后文本片段：{}", cleanedText.length() > 500 ? cleanedText.substring(0, 500) : cleanedText);

        // 【修复点2】修改正则表达式：不再锚定行首，匹配“可选空白 + 数字. + 内容”
        // 正则说明：
        // (?<!\\d) ：负向预查，确保前面不是数字（避免把 "12. " 里的 "2. " 也匹配了）
        // (\\d+)\\. ：捕获序号
        // (.*?) ：非贪婪捕获内容，直到遇到下一个序号
        Pattern qaPattern = Pattern.compile("(?<!\\d)(\\d+)\\.\\s+(.*?)(?=\\s*(?<!\\d)\\d+\\.\\s+|\\Z)");
        Matcher matcher = qaPattern.matcher(cleanedText);

        // 【修复点3】提取逻辑简化：直接按序号顺序成对提取
        List<String> allContentBlocks = new ArrayList<>();
        while (matcher.find()) {
            String content = matcher.group(2).trim();
            if (!content.isBlank()) {
                allContentBlocks.add(content);
            }
        }

        log.info("正则匹配完成，共提取到 {} 个内容块", allContentBlocks.size());

        // 5. 格式容错校验：FAQ必须是1问1答成对出现，内容块数量为偶数
        if (allContentBlocks.size() % 2 != 0) {
            log.error("FAQ格式异常，内容块数量为奇数，无法完成问答成对匹配，文件路径：{}", filePath);
            return;
        }

        // 6. 成对处理问答对，生成切片后的Document列表
        List<Document> qaDocuments = new ArrayList<>();
        for (int i = 0; i < allContentBlocks.size(); i += 2) {
            // 规则：i=问题、i+1=对应答案，对应原PDF 1问2答、3问4答的结构
            String question = allContentBlocks.get(i);
            String answer = allContentBlocks.get(i + 1);
            // 生成与原PDF一致的问题序号（1、2、3...）
            int questionId = (i / 2) + 1;

            // 拼接问答内容，保证单个Document是完整的问答单元，提升语义匹配精度
            String qaContent = String.format("问题：%s%n答案：%s", question, answer);

            // 构建Document，添加元数据增强检索能力
            Document qaDocument = Document.builder()
                    .text(qaContent)
                    .metadata("fileName", resource.getFilename())
                    .metadata("questionId", questionId)
                    .metadata("question", question)
                    .metadata("answer", answer)
                    .build();

            qaDocuments.add(qaDocument);
        }

        // 7. 批量写入向量库，与原有逻辑完全对齐
        vectorStore.add(qaDocuments);
        log.info("FAQ问答切片完成，共写入{}个问答单元到向量库，文件路径：{}", qaDocuments.size(), filePath);
    }



}
