package com.whu.yun.emuns;

import lombok.Getter;

public enum UavCameraParameters {
    // 焦距(f), 垂直视场角(fov_w, 弧度), 水平视场角(fov_l, 弧度)
    MATRICE_300_RTK(0.006, Math.toRadians(44.59), Math.toRadians(63.11)),
    MATRICE_350_RTK(0.006, Math.toRadians(44.59), Math.toRadians(63.11)),
    MATRICE_400(0.006, Math.toRadians(44.59), Math.toRadians(63.11));

    private final double focalLength;
    @Getter
    private final double fovW; // 垂直视场角 (radians)
    private final double fovL; // 水平视场角 (radians)

    UavCameraParameters(double focalLength, double fovW, double fovL) {
        this.focalLength = focalLength;
        this.fovW = fovW;
        this.fovL = fovL;
    }

    // 根据无人机型号名称获取相机参数
    public static UavCameraParameters getByName(String name) {
        try {
            // 将名称转换为大写并替换空格为下划线，以匹配枚举名称
            return UavCameraParameters.valueOf(name.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            System.err.println("Warning: 未找到无人机型号 [" + name + "] 的相机参数，将使用 Matrice_300_RTK 的默认参数。");
            return MATRICE_300_RTK; // 如果未找到，返回一个默认值
        }
    }
}
