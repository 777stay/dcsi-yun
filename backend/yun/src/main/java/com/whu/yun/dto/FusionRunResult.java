package com.whu.yun.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class FusionRunResult {
    private String dataset;
    private String name;
    private String url;
    private String fusedPly;
    private List<Map<String, Object>> robots;
    private String log;
}
