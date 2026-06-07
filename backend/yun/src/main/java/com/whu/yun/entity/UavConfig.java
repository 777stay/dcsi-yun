package com.whu.yun.entity;

import lombok.Data;

@Data
public class UavConfig {

    private UavType selectedUav;
    private Integer droneSpeed;
    private Integer startHeight;
    private Integer flightRouteHeight;
    private String initialLocation;



    //   drone_speed: 10,    // 飞行速度
    //   start_height:20,
    //   flight_route_height:20,
}
