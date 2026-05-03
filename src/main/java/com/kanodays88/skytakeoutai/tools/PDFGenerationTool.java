package com.kanodays88.skytakeoutai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.kanodays88.skytakeoutai.constant.FileConstant;
import com.kanodays88.skytakeoutai.content.BaseContent;
import com.kanodays88.skytakeoutai.utils.HttpPathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class PDFGenerationTool {

    private static final String FALLBACK_FONT = "STSongStd-Light";
    private static final String WINDOWS_FONT_DIR = "C:/Windows/Fonts/";
    private static final String DEFAULT_ENCODING = "UniGB-UCS2-H";
    private static final String LATIN_ENCODING = "Identity-H";

    private static final Set<String> CHINESE_FONTS = Set.of(
            "STSongStd-Light", "SimSun", "SimHei", "KaiTi", "FangSong",
            "Microsoft YaHei", "Microsoft JhengHei", "DengXian", "YouYuan"
    );

    private static final Set<String> TYPE1_FONTS = Set.of(
            "Helvetica", "Times-Roman", "Courier", "Symbol", "ZapfDingbats"
    );

    private static final Map<String, String> FONT_FILE_MAP = Map.ofEntries(
            Map.entry("SimSun", "simsun.ttc"),
            Map.entry("SimHei", "simhei.ttf"),
            Map.entry("Microsoft YaHei", "msyh.ttc"),
            Map.entry("Microsoft JhengHei", "msjh.ttc"),
            Map.entry("KaiTi", "simkai.ttf"),
            Map.entry("FangSong", "simfang.ttf"),
            Map.entry("DengXian", "dengxian.ttf"),
            Map.entry("YouYuan", "youyuan.ttf"),
            Map.entry("Arial", "arial.ttf"),
            Map.entry("Times New Roman", "times.ttf"),
            Map.entry("Courier New", "cour.ttf"),
            Map.entry("Verdana", "verdana.ttf"),
            Map.entry("Tahoma", "tahoma.ttf")
    );

    @Tool(description = "生成PDF工具，支持自定义字体和插入图片，默认字体为STSongStd-Light,返回值是pdf文件的路径")
    public String generatePDF(
            @ToolParam(description = "PDF文件名，如 report.pdf") String fileName,
            @ToolParam(description = "PDF文本内容") String content,
            @ToolParam(description = "字体名称，如 STSongStd-Light、Helvetica、SimSun、Microsoft YaHei、Arial 等，不传则使用默认字体", required = false) String fontName,
            @ToolParam(description = "要插入的图片http url路径", required = false) List<String> imagePaths) {

        if (fileName == null || fileName.isBlank()) return "Error: File name is required.";
        if (content == null) return "Error: Content cannot be null.";

        String fileDir = FileConstant.FILE_SAVE_DIR + "/"+BaseContent.getChatId()+ "/file";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {

                PdfFont font = resolveFont(fontName);
                document.setFont(font);
                document.add(new Paragraph(sanitizeContent(content)));
                addImages(document, imagePaths, fileDir);
            }
            //生成访问路径
            String httpUrl = HttpPathUtil.writeHttpUrl("/" + BaseContent.getChatId() + "/file/" + fileName);
            return "PDF生成成功，访问路径："+httpUrl;
        } catch (Exception e) {
            log.error("Error generating PDF", e);
            return "Error generating PDF: " + e.getMessage();
        }
    }

    private PdfFont resolveFont(String fontName) {
        if (fontName == null || fontName.isBlank()) {
            fontName = FALLBACK_FONT;
        }

        if (TYPE1_FONTS.contains(fontName)) {
            try {
                return PdfFontFactory.createFont(fontName);
            } catch (Exception e) {
                log.debug("Type1 font '{}' not available", fontName);
            }
        }

        String encoding = CHINESE_FONTS.contains(fontName) ? DEFAULT_ENCODING : LATIN_ENCODING;

        try {
            return PdfFontFactory.createFont(fontName, encoding, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        } catch (Exception e) {
            log.debug("Font '{}' not directly available, trying file lookup", fontName);
        }

        String fontFile = FONT_FILE_MAP.get(fontName);
        if (fontFile != null) {
            try {
                return PdfFontFactory.createFont(WINDOWS_FONT_DIR + fontFile, encoding, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            } catch (Exception e) {
                log.debug("Font file '{}' not found", fontFile);
            }
        }

        try {
            String finalFontName = fontName;
            java.io.File[] files = new java.io.File(WINDOWS_FONT_DIR).listFiles((dir, name) ->
                    name.toLowerCase().contains(finalFontName.toLowerCase()));
            if (files != null && files.length > 0) {
                return PdfFontFactory.createFont(files[0].getAbsolutePath(), encoding, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            }
        } catch (Exception e) {
            log.debug("Fuzzy font search failed for '{}'", fontName);
        }

        log.warn("Font '{}' not found, falling back to '{}'", fontName, FALLBACK_FONT);
        try {
            return PdfFontFactory.createFont(FALLBACK_FONT, DEFAULT_ENCODING, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        } catch (Exception e) {
            throw new RuntimeException("Cannot load fallback font STSongStd-Light", e);
        }
    }

    private String sanitizeContent(String content) {
        StringBuilder sb = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            if (codePoint <= 0xFFFF) {
                sb.append((char) codePoint);
            }
            i += Character.charCount(codePoint);
        }
        String result = sb.toString();
        if (result.length() != content.length()) {
            log.warn("{} non-BMP characters (emoji etc.) stripped from PDF content", content.length() - result.length());
        }
        return result;
    }

    private void addImages(Document document, List<String> imagePaths, String fileDir) {
        if (imagePaths == null || imagePaths.isEmpty()) return;
        for (String path : imagePaths) {
            try {
                ImageData imageData;
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    byte[] bytes = HttpUtil.downloadBytes(path);
                    imageData = ImageDataFactory.create(bytes);
                } else {
                    String fullPath = path.startsWith("/") || path.matches("[A-Za-z]:.*")
                            ? path : fileDir + "/" + path;
                    imageData = ImageDataFactory.create(fullPath);
                }
                Image image = new Image(imageData);
                float pageWidth = document.getPdfDocument().getDefaultPageSize().getWidth()
                        - document.getLeftMargin() - document.getRightMargin();
                if (image.getImageWidth() > pageWidth) {
                    image.scaleToFit(pageWidth, image.getImageHeight() * pageWidth / image.getImageWidth());
                }
                document.add(image);
            } catch (Exception e) {
                log.warn("Failed to insert image '{}': {}", path, e.getMessage());
            }
        }
    }
}
