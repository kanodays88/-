package com.kanodays88.skytakeoutai.service;

import org.springframework.core.io.Resource;

public interface FileUploadService {
    boolean save(String chatId, Resource resource);
}
