package com.kanodays88.skytakeoutai.controller;

import com.kanodays88.skytakeoutai.entity.vo.FileUploadVO;
import com.kanodays88.skytakeoutai.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@RestController
@RequestMapping("/ai/upload")
@Slf4j
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadServiceImpl;


    @RequestMapping("/pdf/{chatId}")
    public FileUploadVO uploadPdf(@PathVariable String chatId, @RequestParam("file") MultipartFile file) {
        try {
            FileUploadVO fileUploadVO = new FileUploadVO();
            // 1. 校验文件是否为PDF格式
            if (!Objects.equals(file.getContentType(), "application/pdf")) {
                fileUploadVO.setStatus(2);
                fileUploadVO.setMsg("上传失败，文件格式必须是pdf格式喵");
                return fileUploadVO;
            }
            // 2.保存文件
            boolean success = fileUploadServiceImpl.save(chatId, file.getResource());
            if(! success) {
                fileUploadVO.setMsg("后台保存文件失败，请重新上传喵");
                fileUploadVO.setStatus(2);
                return fileUploadVO;
            }
            fileUploadVO.setMsg("上传成功喵");
            fileUploadVO.setStatus(1);
            return fileUploadVO;
        } catch (Exception e) {
            log.error("Failed to upload PDF.", e);
            FileUploadVO fileUploadVO = new FileUploadVO();
            fileUploadVO.setStatus(2);
            fileUploadVO.setMsg("系统出错喵");
            return fileUploadVO;
        }
    }



}
