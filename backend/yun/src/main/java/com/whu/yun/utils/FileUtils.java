package com.whu.yun.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@ConfigurationProperties(prefix = "upload")
@Data
public class FileUtils {

    private String path;

    public File getPath() {
        // 构建上传文件的存放 "文件夹" 路径
        String fileDirPath = path;
        File fileDir = new File(fileDirPath);
        if (!fileDir.exists()) {
            // 递归生成文件夹
            fileDir.mkdirs();
        }
        return fileDir;
    }

    public boolean del(String filename) {
        File file = new File(path + File.separator + filename);
        return file.delete();
    }
    public boolean del(String path, String filename) {
        return new File(path + File.separator + filename).delete();
    }
}

