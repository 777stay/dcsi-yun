package com.whu.yun.emuns;

/**
 * 无人机及配套设备枚举类
 * 包含产品名称、领域、主类型、子类型及说明信息
 */
public enum UavProductEnums {
    // ------------- 无人机系列 -------------
    MATRICE_400("Matrice_400", 0, 103, 0,"39-0-7", "-"),
    MATRICE_350_RTK("Matrice_350_RTK", 0, 89, 0,"39-0-7","-"),
    MATRICE_300_RTK("Matrice_300_RTK", 0, 60, 0, "39-0-7","-"),
    MATRICE_30("Matrice_30", 0, 67, 0, "39-0-7","-"),
    MATRICE_30T("Matrice_30T", 0, 67, 1,"39-0-7", "-"),
    MAVIC_3_INDUSTRY_M3E("Mavic_3_行业系列（M3E_相机）", 0, 77, 0, null,"-"),
    MAVIC_3_INDUSTRY_M3T("Mavic_3_行业系列（M3T_相机）", 0, 77, 1, null,"-"),
    MAVIC_3_INDUSTRY_M3A("Mavic_3_行业系列（M3A_相机）", 0, 77, 3, null,"-"),
    MATRICE_3D("Matrice_3D", 0, 91, 0, "176-0-0","-"),
    MATRICE_3TD("Matrice_3TD", 0, 91, 1, "176-0-0","-"),
    MATRICE_4D("Matrice_4D", 0, 100, 0, "176-0-0","-"),
    MATRICE_4TD("Matrice_4TD", 0, 100, 1, "176-0-0","-"),
    DJI_MATRICE_4_M4E("DJI_Matrice_4_系列（M4E_相机）", 0, 99, 0, "88-0-0","-"),
    DJI_MATRICE_4_M4T("DJI_Matrice_4_系列（M4T_相机）", 0, 99, 1,"89-0-0", "-"),

    // ------------- 遥控器系列 -------------
    DJI_RC_WITH_SCREEN_INDUSTRY("DJI_带屏遥控器行业版", 2, 56, 0, null,"搭配 Matrice 300 RTK"),
    DJI_RC_PLUS("DJI_RC_Plus", 2, 119, 0, null,"搭配 Matrice 350 RTK、Matrice 300 RTK、Matrice 30/30T"),
    DJI_RC_PLUS_2("DJI_RC_Plus_2", 2, 174, 0, null,"搭配 >DJI Matrice 4 系列"),
    DJI_RC_PRO_INDUSTRY("DJI_RC_Pro_行业版", 2, 144, 0, null,"搭配 Mavic 3 行业系列"),

    // ------------- 机场系列 -------------
    DJI_AIRPORT("大疆机场", 3, 1, 0, "165-0-7","-"),
    DJI_AIRPORT_2("大疆机场_2", 3, 2, 0, "165-0-7","-"),
    DJI_AIRPORT_3("大疆机场_3", 3, 3, 0, "165-0-7","-");

    // 枚举属性定义
    private final String name;       // 产品名称
    private final int domain;        // 领域
    private final int type;          // 主类型
    private final int subType;       // 子类型
    private final String typeSubtypeGimbalindex;
    private final String description;// 说明

    // 构造方法（枚举构造方法必须为 private）
    UavProductEnums(String name, int domain, int type, int subType, String typeSubtypeGimbalindex, String description) {
        this.name = name;
        this.domain = domain;
        this.type = type;
        this.subType = subType;
        this.typeSubtypeGimbalindex = typeSubtypeGimbalindex;
        this.description = description;
    }

    // Getter 方法（按需提供）
    public String getName() {
        return name;
    }

    public int getDomain() {
        return domain;
    }

    public int getType() {
        return type;
    }

    public int getSubType() {
        return subType;
    }

    public String getDescription() {
        return description;
    }

    public String getTypeSubtypeGimbalindex(){
        return typeSubtypeGimbalindex;
    }

    // 示例：根据产品名称查找枚举（可选扩展）
    public static UavProductEnums findByName(String name) {
        for (UavProductEnums product : values()) {
            if (product.getName().equals(name)) {
                return product;
            }
        }
        return null;
    }
}
