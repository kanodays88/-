package com.kanodays88.skytakeoutai.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileScanUtils {

    public static String[] getFilenamesWithoutExtension(String folderPath) {
        File folder = new File(folderPath);
        List<String> nameList = new ArrayList<>();

        // 检查文件夹是否存在且为目录
        if (!folder.exists() || !folder.isDirectory()) {
            return nameList.toArray(new String[0]);
        }

        // 获取所有文件（不包含子目录）
        File[] files = folder.listFiles(File::isFile);
        if (files == null) {
            return nameList.toArray(new String[0]);
        }

        // 遍历文件并处理文件名
        for (File file : files) {
            String fullName = file.getName();
            int dotIndex = fullName.lastIndexOf('.');
            // 去掉后缀（如果没有后缀则保留原名）
            String name = (dotIndex == -1) ? fullName : fullName.substring(0, dotIndex);
            nameList.add(name);
        }

        return nameList.toArray(new String[0]);
    }
}
