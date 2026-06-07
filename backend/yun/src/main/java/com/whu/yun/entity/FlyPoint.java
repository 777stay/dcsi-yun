package com.whu.yun.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlyPoint {
    private String seq;
    private String imageName;
    private String longitude;
    private String latitude;
    private String elevation;
    private String flyHeading;
    private String vehicleHeading;
    private int hoveringTime;
    private int imageCount;
}
