package com.whu.yun.utils;

import com.cleaner.djuav.domain.*;
import com.whu.yun.emuns.UavProductEnums;
import com.whu.yun.entity.*;

import java.util.ArrayList;
import java.util.List;

public class FlyPointConvert {

    /**
     * 将FlyPoint列表映射为UavRouteReq对象
     * @param flyPoints 飞行点列表
     * @return 映射后的UavRouteReq
     */
    public static UavRouteReq mapToUavRouteReq(List<FlyPoint> flyPoints,String uavTypeName,Integer speed) {
        if (flyPoints == null || flyPoints.isEmpty()) {
            return new UavRouteReq();
        }

        UavProductEnums uavProductEnum = UavProductEnums.findByName(uavTypeName);
        // 获取字符串
        String str = null; // "39-0-7"
        if (uavProductEnum != null) {
            str = uavProductEnum.getTypeSubtypeGimbalindex();
        }
        //split 分割
        String[] parts = null;
        if (str != null) {
            parts = str.split("-");
        }

        int payloadType = 0;   // 39
        if (parts != null) {
            payloadType = Integer.parseInt(parts[0]);
        }
        int payloadPosition = 0;    // 7
        if (parts != null) {
            payloadPosition = Integer.parseInt(parts[2]);
        }

        UavRouteReq uavRouteReq = new UavRouteReq();
        // 基础属性设置（根据业务场景补充默认值）
        uavRouteReq.setTemplateType("waypoint"); // 自定义航线类型
        //todo:起始点还是航线起点
        uavRouteReq.setTakeOffRefPoint(flyPoints.get(0).getLatitude()+","+flyPoints.get(0).getLongitude()+","+flyPoints.get(0).getElevation());
        uavRouteReq.setDroneType(uavProductEnum.getType());
        uavRouteReq.setSubDroneType(uavProductEnum.getSubType());
        uavRouteReq.setPayloadType(payloadType);
        uavRouteReq.setPayloadPosition(payloadPosition);
        uavRouteReq.setImageFormat("visable,ir");
        uavRouteReq.setFinishAction("goHome");//完成规划后返航'
        uavRouteReq.setExitOnRcLostAction("goBack");
        uavRouteReq.setGlobalHeight(100.0); // 默认全局高度
        uavRouteReq.setAutoFlightSpeed(Double.valueOf(speed)); // 默认飞行速度

        WaypointTurnReq waypointTurnReq = new WaypointTurnReq();
        waypointTurnReq.setWaypointTurnMode("toPointAndStopWithDiscontinuityCurvature");
        uavRouteReq.setWaypointTurnReq(waypointTurnReq);

        WaypointHeadingReq headingReq = new WaypointHeadingReq();
        headingReq.setWaypointHeadingMode("followWayline"); // 自定义偏航模式
        uavRouteReq.setWaypointHeadingReq(headingReq);

        uavRouteReq.setGimbalPitchMode("usePointSetting");

        List<PointActionReq> startActionList = new ArrayList<>();

        // 只设置悬停，不涉及云台
        PointActionReq action0 = new PointActionReq();
        action0.setActionIndex(0);
        action0.setHoverTime(30.0);  // 悬停 30 秒
        startActionList.add(action0);

        uavRouteReq.setStartActionList(startActionList);

        // 构建航点列表
        List<RoutePointReq> routePointList = new ArrayList<>();
        for (FlyPoint flyPoint : flyPoints) {
            RoutePointReq routePoint = mapToRoutePointReq(flyPoint);
            routePointList.add(routePoint);
        }
        uavRouteReq.setRoutePointList(routePointList);

        return uavRouteReq;
    }

    /**
     * 将单个FlyPoint映射为RoutePointReq
     * @param flyPoint 飞行点
     * @return 映射后的航点
     */
    private static RoutePointReq mapToRoutePointReq(FlyPoint flyPoint) {
        RoutePointReq routePoint = new RoutePointReq();

        // 航点编号（从seq转换）
        routePoint.setRoutePointIndex(Integer.parseInt(flyPoint.getSeq()));

        // 经纬度、高度（字符串转Double）
        routePoint.setLongitude(Double.parseDouble(flyPoint.getLongitude()));
        routePoint.setLatitude(Double.parseDouble(flyPoint.getLatitude()));
        routePoint.setHeight(Double.parseDouble(flyPoint.getElevation()));

        // 偏航角设置（使用机身朝向）
        WaypointHeadingReq headingReq = new WaypointHeadingReq();
        headingReq.setWaypointHeadingMode("followWayline"); // 自定义偏航模式
        headingReq.setWaypointHeadingAngle(Double.parseDouble(flyPoint.getVehicleHeading()));
        routePoint.setWaypointHeadingReq(headingReq);

        // 构建动作组（包含悬停、拍照等动作）
        List<ActionGroupReq> actionGroups = new ArrayList<>();
        ActionGroupReq actionGroup = new ActionGroupReq();
        actionGroup.setActionGroupId(1); // 动作组编号
        actionGroup.setActionGroupStartIndex(routePoint.getRoutePointIndex());
        actionGroup.setActionGroupEndIndex(routePoint.getRoutePointIndex());

        // 动作列表（悬停、拍照）
        List<PointActionReq> actions = new ArrayList<>();
        PointActionReq pointAction = new PointActionReq();
        pointAction.setActionIndex(1);
        pointAction.setHoverTime((double) flyPoint.getHoveringTime()); // 悬停时间
        pointAction.setTakePhotoType(flyPoint.getImageCount() > 0 ? 0 : -1); // 0：普通拍照
//        pointAction.setImageName(flyPoint.getImageName()); // 图片文件名
        actions.add(pointAction);

        actionGroup.setActions(actions);
        actionGroups.add(actionGroup);
        routePoint.setActionGroupList(actionGroups);

        return routePoint;
    }
}
