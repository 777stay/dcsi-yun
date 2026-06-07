package com.whu.yun.entity.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartingPointVo {
    private String name;
    private String model;
    @JsonProperty("long")
    private Double longValue;
    private Double lat;
}
