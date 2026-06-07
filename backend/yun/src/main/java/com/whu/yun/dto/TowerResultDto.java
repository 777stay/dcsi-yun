package com.whu.yun.dto;

import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

// DTO 类 (可以放在单独文件，也可以作为静态内部类)
public class TowerResultDto {
    public String towerName;
    public String changeData; // Base64 编码的 PLY 文件内容
    public String rgbData;    // Base64 编码的 PLY 文件内容

    public TowerResultDto(String towerName, String changeData, String rgbData) {
        this.towerName = towerName;
        this.changeData = changeData;
        this.rgbData = rgbData;
    }
    // Getters and Setters...
}