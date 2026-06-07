package com.whu.yun.dto;

import lombok.Data;

@Data
public class FusionRunRequest {
    private String dataset;
    private Integer imageStride;
    private Integer maxImages;
}
