package com.whu.yun.service.impl;

import com.cleaner.djuav.domain.UavRouteReq;
import com.cleaner.djuav.util.RouteFileUtils;
import com.google.gson.GsonBuilder;
import com.whu.yun.emuns.UavCameraParameters;
import com.whu.yun.emuns.UavProductEnums;
import com.whu.yun.entity.*;
import com.whu.yun.entity.vo.MissionAreaVo;
import com.whu.yun.mapper.MissionPlanMapper;
import com.whu.yun.service.FlyPlanService;
import com.whu.yun.service.MissionPlannerService;
import com.whu.yun.utils.FlyPointConvert;
import com.whu.yun.utils.JsonParser;
import com.whu.yun.utils.TxtGpsDistanceCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.gson.Gson;

@Service
public class MissionPlannerServiceImpl implements MissionPlannerService {

    @Value("${py2json.path}")
    private String py2jsonPath;

//    @Value("${kml.path}")
//    private String kmlPath;

    @Value("${lib.path}")
    private String libPath;

    @Value("${python.path}")
    private String pythonPath;

    @Autowired
    private FlyPlanService flyPlanService;

    @Autowired
    private MissionPlanMapper missionPlanMapper;

    @Autowired
    private TxtGpsDistanceCalculator calculator;

    private String getDateString(){
        // 获取当前日期和时间，格式为 "yyyyMMddHHmmss"
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return now.format(formatter);
    }

    private String processKmlFromString(String py2jsonCommand,String kmlContent,int scanDensity,String uavConfigs,Float overlapDegree) {
        File tempFile = null;
        try {

            tempFile = File.createTempFile("kml_temp_", ".kml");
            System.out.println("临时 KML 文件路径：" + tempFile.getAbsolutePath());

            // 2. 将字符串内容写入临时文件
            Files.write(tempFile.toPath(), kmlContent.getBytes(StandardCharsets.UTF_8));

            // 3. 构造调用 Python 的命令
            System.out.println("python命令："+py2jsonCommand);
            String py2jsonOutput = executeCommand(py2jsonCommand+" "+tempFile.getAbsolutePath()+" "+scanDensity+" "+uavConfigs+" "+overlapDegree,py2jsonPath+"/cpp_input");
            System.out.println("py2json返回的输出:"+py2jsonOutput);

            return py2jsonOutput;

        } catch (IOException e) {
            e.printStackTrace();
            return "处理 KML 出错: " + e.getMessage();
        } finally {
            // 5. 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
                System.out.println("临时文件已删除: " + tempFile.getAbsolutePath());
            }
        }
    }

    /**
     * 根据无人机配置和重叠度计算扫描密度。
     *
     * @param config 无人机配置，包含无人机型号和航线高度。
     * @param overlapRate 重叠度，一个介于0到1之间的浮点数。
     * @return 计算得到的扫描密度（整数）。
     * @throws IllegalArgumentException 如果输入参数无效。
     */
    public static int calculateScanningDensity(UavConfig config, double overlapRate) {
        if (config == null || config.getSelectedUav() == null || config.getFlightRouteHeight() == null) {
            throw new IllegalArgumentException("无人机配置、所选无人机型号或飞行航线高度不能为空。");
        }
        if (overlapRate < 0 || overlapRate > 1) {
            throw new IllegalArgumentException("重叠度必须在0到1之间。");
        }

        String uavTypeName = config.getSelectedUav().getName();
        UavCameraParameters cameraParams = UavCameraParameters.getByName(uavTypeName);

        double flightHeight = config.getFlightRouteHeight();
        double fovW = cameraParams.getFovW();

        // 计算地面覆盖宽度 (W)
        // W = 2 * H * tan(fov_w / 2)
        double groundWidth = 2 * flightHeight * Math.tan(fovW / 2);

        // 计算扫描密度
        // 扫描密度 = 向上取整(地面覆盖宽度 * 重叠度)
        return (int) Math.ceil(groundWidth * overlapRate);
    }

    @Override
    public List<List<double[]>> startTowerMissionPlanner(MissionPlannerRequest request) {
        // 获取 KML 杆塔坐标和无人机初始位置
        List<Map<String, Double>> kmlTowerPoints = request.getKmlTowerPoints();
        System.out.println("沿塔模式 KML 杆塔坐标: " + kmlTowerPoints);

        // 获取无人机数量
        int numberOfDrones = request.getNumberDevice();
        if (numberOfDrones <= 0) {
            System.out.println("沿塔模式：无人机数量为0，无法规划航线。");
            return new ArrayList<>();
        }

        // 获取每个无人机的起始点
        List<Map<String, Double>> droneInitialLocations = new ArrayList<>();
        List<String> topLevelInitialLocations = request.getInitialLocations();

        if (topLevelInitialLocations != null && !topLevelInitialLocations.isEmpty()) {
            for (String locationStr : topLevelInitialLocations) {
                try {
                    String[] parts = locationStr.split(",");
                    if (parts.length == 2) {
                        Map<String, Double> locMap = new HashMap<>();
                        locMap.put("long", Double.parseDouble(parts[0].trim()));
                        locMap.put("lat", Double.parseDouble(parts[1].trim()));
                        droneInitialLocations.add(locMap);
                    } else {
                        System.out.println("沿塔模式：顶层 initialLocations 中存在无效格式的起始点: " + locationStr);
                        droneInitialLocations.add(null);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("沿塔模式：顶层 initialLocations 中存在无法解析的起始点: " + locationStr + ", 错误: " + e.getMessage());
                    droneInitialLocations.add(null);
                }
            }
            while (droneInitialLocations.size() < numberOfDrones) {
                droneInitialLocations.add(null);
            }
        } else {
            for (int i = 0; i < numberOfDrones; i++) {
                droneInitialLocations.add(null);
                System.out.println("沿塔模式：无人机 " + (i + 1) + " 没有指定起始点。");
            }
        }
        System.out.println("沿塔模式 无人机起始点 (最终确定): " + droneInitialLocations);

        if (kmlTowerPoints == null || kmlTowerPoints.isEmpty()) {
            System.out.println("沿塔模式：没有 KML 杆塔坐标，无法生成航线。");
            return new ArrayList<>();
        }

        // --- 第一步：将杆塔点分组 ---
        List<List<Map<String, Double>>> taskGroups = new ArrayList<>();
        int totalPoints = kmlTowerPoints.size();
        int basePointsPerGroup = totalPoints / numberOfDrones;
        int remainder = totalPoints % numberOfDrones;
        int currentPointIndex = 0;

        for (int i = 0; i < numberOfDrones; i++) {
            List<Map<String, Double>> group = new ArrayList<>();
            int pointsToAssign = basePointsPerGroup + (i < remainder ? 1 : 0);
            for (int j = 0; j < pointsToAssign; j++) {
                if (currentPointIndex < totalPoints) {
                    group.add(kmlTowerPoints.get(currentPointIndex++));
                }
            }
            if (!group.isEmpty()) {
                taskGroups.add(group);
            }
        }
        System.out.println("沿塔模式：初始分组的任务组: " + taskGroups);

        // --- 第二步：为每个任务组找到最合适的无人机 ---
        // 记录每个无人机的任务分配信息
        class DroneAssignment {
            int groupIndex = -1;        // 分配的任务组索引
            boolean shouldReverse = false; // 是否需要反转飞行顺序
            double distance = Double.MAX_VALUE; // 距离
        }

        DroneAssignment[] droneAssignments = new DroneAssignment[numberOfDrones];
        for (int i = 0; i < numberOfDrones; i++) {
            droneAssignments[i] = new DroneAssignment();
        }

        boolean[] groupAssigned = new boolean[taskGroups.size()];

        // 使用贪心算法：每次为距离最近的"任务组-无人机"对进行分配
        for (int iteration = 0; iteration < taskGroups.size(); iteration++) {
            double minDistance = Double.MAX_VALUE;
            int bestGroup = -1;
            int bestDrone = -1;
            boolean bestReverse = false;

            // 找到当前最优的"任务组-无人机"配对
            for (int groupIndex = 0; groupIndex < taskGroups.size(); groupIndex++) {
                if (groupAssigned[groupIndex]) continue; // 跳过已分配的任务组

                List<Map<String, Double>> group = taskGroups.get(groupIndex);
                if (group.isEmpty()) continue;

                Map<String, Double> firstPoint = group.get(0);
                Map<String, Double> lastPoint = group.get(group.size() - 1);

                for (int droneIndex = 0; droneIndex < numberOfDrones; droneIndex++) {
                    if (droneAssignments[droneIndex].groupIndex != -1) continue; // 跳过已分配的无人机

                    Map<String, Double> droneStart = droneInitialLocations.get(droneIndex);
                    if (droneStart == null) {
                        // 如果无人机没有起始点，使用任务组的第一个点作为起始点
                        droneStart = firstPoint;
                    }

                    double droneLat = droneStart.get("lat");
                    double droneLon = droneStart.get("long");

                    // 计算到首点和尾点的距离
                    double distToFirst = calculateDistance(droneLat, droneLon,
                            firstPoint.get("lat"), firstPoint.get("long"));
                    double distToLast = calculateDistance(droneLat, droneLon,
                            lastPoint.get("lat"), lastPoint.get("long"));

                    // 选择较小的距离
                    double minDist = Math.min(distToFirst, distToLast);
                    boolean shouldReverse = (distToLast < distToFirst);

                    if (minDist < minDistance) {
                        minDistance = minDist;
                        bestGroup = groupIndex;
                        bestDrone = droneIndex;
                        bestReverse = shouldReverse;
                    }
                }
            }

            // 执行分配
            if (bestDrone != -1 && bestGroup != -1) {
                droneAssignments[bestDrone].groupIndex = bestGroup;
                droneAssignments[bestDrone].shouldReverse = bestReverse;
                droneAssignments[bestDrone].distance = minDistance;
                groupAssigned[bestGroup] = true;

                System.out.println(String.format("沿塔模式：无人机 %d 分配到任务组 %d，距离 %.2f 米，%s",
                        bestDrone + 1, bestGroup + 1, minDistance, bestReverse ? "反向飞行" : "正向飞行"));
            }
        }

        // --- 第三步：构建每个无人机的航线 ---
        List<List<double[]>> result = new ArrayList<>();

        for (int droneIndex = 0; droneIndex < numberOfDrones; droneIndex++) {
            DroneAssignment assignment = droneAssignments[droneIndex];

            if (assignment.groupIndex == -1) {
                // 该无人机未分配任务
                System.out.println("沿塔模式：无人机 " + (droneIndex + 1) + " 未分配任务。");
                continue;
            }

            List<Map<String, Double>> taskGroup = taskGroups.get(assignment.groupIndex);
            List<double[]> droneRoute = new ArrayList<>();

            // 添加起始点
            Map<String, Double> droneStart = droneInitialLocations.get(droneIndex);
            if (droneStart != null) {
                droneRoute.add(new double[]{droneStart.get("lat"), droneStart.get("long")});
            } else {
                // 如果没有起始点，使用任务组的第一个点
                Map<String, Double> firstTaskPoint = taskGroup.get(0);
                droneRoute.add(new double[]{firstTaskPoint.get("lat"), firstTaskPoint.get("long")});
            }

            // 根据是否需要反转来添加任务点
            List<Map<String, Double>> orderedTaskGroup = new ArrayList<>(taskGroup);
            if (assignment.shouldReverse) {
                Collections.reverse(orderedTaskGroup);
                System.out.println("沿塔模式：无人机 " + (droneIndex + 1) + " 的任务组已反转顺序");
            }

            // 添加所有任务点
            for (Map<String, Double> point : orderedTaskGroup) {
                droneRoute.add(new double[]{point.get("lat"), point.get("long")});
            }

            // 添加结束点
            if (droneStart != null) {
                droneRoute.add(new double[]{droneStart.get("lat"), droneStart.get("long")});
            } else {
                // 如果没有起始点，使用任务组的第一个点
                Map<String, Double> firstTaskPoint = taskGroup.get(0);
                droneRoute.add(new double[]{firstTaskPoint.get("lat"), firstTaskPoint.get("long")});
            }

            result.add(droneRoute);
        }

        // 获取当前时间用于后面的文件夹设置
        String outFileDate = getDateString();

        File directory = new File(py2jsonPath + "/cppFiles/"+outFileDate);
        directory.mkdirs();

        // 模拟 missionRouteResult 的生成
        StringBuilder missionRouteResultBuilder = new StringBuilder();
        if (!result.isEmpty()) {
            for (int i = 0; i < result.size(); i++) { // 遍历每个无人机航线
                List<double[]> droneRoute = result.get(i);
                // 输出当前无人机航线的航点数量
                missionRouteResultBuilder.append(String.format(" ~ Number of Waypoints: %d ~ \n", droneRoute.size()));
                for (double[] point : droneRoute) {
                    // 注意：这里的经纬度顺序通常是 long, lat。您的示例是 lat, long。我将按照您的示例输出 lat, long
                    missionRouteResultBuilder.append(String.format("%f, %f;\n", point[0], point[1]));
                }
                missionRouteResultBuilder.append("\n");
            }
        }
        String missionRouteResult = missionRouteResultBuilder.toString();
        System.out.println("模拟的missionRouteResult:\n" + missionRouteResult);

        // 保存规划结果到txt文件 输入String mission_route_result，保存输出cpp_waypoint.txt
        System.out.println(" 保存规划结果到txt文件 输入String mission_route_result，保存输出cpp_waypoint.txt");
        saveTextToFile(py2jsonPath+"/cppFiles/"+outFileDate+"/cpp_waypoint.txt", missionRouteResult);

        //6.执行wp2kml Python脚本 cpp_waypoint.txt航点文件->wp0.txt
        System.out.println(" 执行wp2kml Python脚本 cpp_waypoint.txt航点文件->kml  生成/cppFiles/last/wps/wp{}.txt");
        String wp2kmlCommmand = pythonPath+" "+py2jsonPath+"/cpp_input/wp2kml.py";
        if(request.getFlyMode() == 2){
            System.out.println(" 手动航高：");
            //todo:把三个航高都传过去修改
            wp2kmlCommmand = wp2kmlCommmand+" "+generateJson(request.getUavConfigs());
        }else if(request.getFlyMode() == 1){
            System.out.println("仿地飞行：");
        }
        String wp2kmlOutput = executeCommand(wp2kmlCommmand,py2jsonPath+"/cpp_input");
        System.out.println("wp2kmlOutput:"+wp2kmlOutput);
        int jsonStartIndex = wp2kmlOutput.indexOf("towers not found in DEM, please use mannual H");
        if(jsonStartIndex != -1){
            return null;
        }
        //6.2 移动wp.txt文件至最新文件夹
        String lastWpsDir = py2jsonPath+"/cppFiles/last/wps";
        String targetWpsDir = py2jsonPath+"/cppFiles/"+outFileDate+"/wps";
        String[] wpsTxtFilenames = new String[request.getNumberDevice()];
        for (int i = 0; i < request.getNumberDevice(); i++) {
            wpsTxtFilenames[i] = "wp" + i + ".txt";
        }
        ArrayList<String> wpsAllTxtFilenames = new ArrayList<>();
        //6.3根据电量对txt文件进行分割
        for(int i = 0;i< wpsTxtFilenames.length;i++){
            int txtNum = calculator.calculatorAndSaveTxtFile(lastWpsDir,wpsTxtFilenames[i],request.getUavConfigs().get(i).getSelectedUav().getBatteryLength());
            wpsAllTxtFilenames.add(wpsTxtFilenames[i]);
            for(int j = 0; j < txtNum; j++){
                wpsAllTxtFilenames.add("wp" + i +"_"+j+".txt");
            }
        }
        moveFiles(lastWpsDir, targetWpsDir, wpsAllTxtFilenames);

        //7. 使用FlyPlan_GBK 执行wp0.txt -> wp0.kml
        String lastKmlDir = py2jsonPath+"/cppFiles/last/kml";
        String targetKmlDir = py2jsonPath+"/cppFiles/"+outFileDate+"/kml";

        String lastKmzDir = py2jsonPath+"/cppFiles/last/kmz";
        String targetKmzDir = py2jsonPath+"/cppFiles/"+outFileDate+"/kmz";

        ArrayList<String>  wpsAllKmlFilenames = new ArrayList<>();
        ArrayList<String>  wpsAllKmzFilenames = new ArrayList<>();
        for(String txtFilename: wpsAllTxtFilenames){
            //获得当前为第几个机型，用于后续的kmz设置
            Integer droneIndex = getDroneIndex(txtFilename);
            //设置kml或者kmz的名字
            int dotIndex = txtFilename.lastIndexOf('.');
            String prefix = (dotIndex > 0) ? txtFilename.substring(0, dotIndex) : txtFilename;

            String kmlFilename = prefix+".kml";
            String kmzFilename = prefix+".kmz";
            System.out.println("生成kml文件："+ kmlFilename);

            generateKml(lastWpsDir+"/"+txtFilename,lastKmlDir,prefix,request,droneIndex);
            generateKmz(lastWpsDir+"/"+txtFilename,lastKmzDir,prefix,request,droneIndex);

            wpsAllKmlFilenames.add(kmlFilename);
            wpsAllKmzFilenames.add(kmzFilename);
        }
        moveFiles(lastKmlDir, targetKmlDir, wpsAllKmlFilenames);
        moveFiles(lastKmzDir, targetKmzDir, wpsAllKmzFilenames);

        insertMissionPlan(request,targetKmlDir);
        insertMissionPlan(request,targetKmzDir);


        System.out.println("任务规划记录已成功存入数据库，存储的KML路径为: " + targetKmlDir);
        System.out.println("result:"+result);
        return result;
    }

    /**
     * 计算两个经纬度点之间的距离（哈弗赛因公式）。
     * @param lat1 第一个点的纬度
     * @param lon1 第一个点的经度
     * @param lat2 第二个点的纬度
     * @param lon2 第二个点的经度
     * @return 两个点之间的距离，单位为米
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 地球半径，单位：公里

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // 返回距离，单位：米
    }

    @Override
    public List<List<double[]>> startMissionPlanner(MissionPlannerRequest request) {
        // 1.从请求实体中提取部分可能用到的数据
        int planMode = request.getPlanMode();

        //2.获取当前时间用于后面的文件夹设置
        String outFileDate = getDateString();
        //3.拼接json文件路径和文件夹路径 若没有递归地创建目录
        String missionPlannerFilePath = py2jsonPath+"/cppFiles/"+outFileDate+"/cpp.json";
        File directory = new File(py2jsonPath + "/cppFiles/"+outFileDate);
        directory.mkdirs();

        //4 生成cpp.json文件 作为任务规划算法的输入
        //1是区域模式自己画框    ————使用java生成json
        //2是kml文件的杆塔模式   ————使用python生成json
        String py2jsonCommand = "";
        if (planMode == 1) {
            try {
                // 生成任务规划JSON文件
                System.out.println(" 生成任务规划JSON文件  保存至cpp.json");
                generateMissionPlannerFile(request , missionPlannerFilePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (planMode == 2) {
            //python文件主要是针对kml杆塔的方式   无人机数量，起始位置，结束位置，文件路径
            //传进来的是kml文件的内容，我爸kml保存成了临时路径下的文件用于后面生成json文件
            int numberDevice = request.getNumberDevice();
            py2jsonCommand = pythonPath+" "+py2jsonPath+"/cpp_input/py2json.py " + numberDevice+" "+request.getDroneStart()+" "+request.getDroneEnd()+" "+outFileDate;
            processKmlFromString(py2jsonCommand,request.getKmldir(),request.getScanDensity(),generateJson(request.getUavConfigs()),request.getOverlapDegree());
        }

        // 5.执行任务规划程序（mCPP-optimized-DARP.jar任务） 生成任务规划结果 mission_route_result
        System.out.println(" 执行任务规划程序（mCPP任务）根据cpp.json生成 String mission_route_result 命令是：java -jar "+libPath+"/mCPP-optimized-DARP.jar");
        String missionRouteResult = executeCommand("java -jar "+libPath+"/mCPP-optimized-DARP.jar "+missionPlannerFilePath,null);
        System.out.println(" 保存规划结果到txt文件 输入String mission_route_result，保存输出cpp_waypoint.txt");
        saveTextToFile(py2jsonPath+"/cppFiles/"+outFileDate+"/cpp_waypoint.txt", missionRouteResult);
        //提取结果中的经纬度结果 其实可以直接返回了 后面的都是生成文件了
        List<List<double[]>> result = text2arr(missionRouteResult,request);
        //6.执行wp2kml Python脚本 cpp_waypoint.txt航点文件->wp0.txt
        System.out.println(" 执行wp2kml Python脚本 cpp_waypoint.txt航点文件->kml  生成/cppFiles/last/wps/wp{}.txt");
        String wp2kmlCommmand = pythonPath+" "+py2jsonPath+"/cpp_input/wp2kml.py";
        if(request.getFlyMode() == 2){
            System.out.println(" 手动航高：");
            //todo:把三个航高都传过去修改
            wp2kmlCommmand = wp2kmlCommmand+" "+generateJson(request.getUavConfigs());
        }else if(request.getFlyMode() == 1){
            System.out.println("仿地飞行：");
        }
        String wp2kmlOutput = executeCommand(wp2kmlCommmand,py2jsonPath+"/cpp_input");
        System.out.println("wp2kmlOutput:"+wp2kmlOutput);
        int jsonStartIndex = wp2kmlOutput.indexOf("towers not found in DEM, please use mannual H");
        if(jsonStartIndex != -1){
            return null;
        }
        //6.2 移动wp.txt文件至最新文件夹
        String lastWpsDir = py2jsonPath+"/cppFiles/last/wps";
        String targetWpsDir = py2jsonPath+"/cppFiles/"+outFileDate+"/wps";
        String[] wpsTxtFilenames = new String[request.getNumberDevice()];
        for (int i = 0; i < request.getNumberDevice(); i++) {
            wpsTxtFilenames[i] = "wp" + i + ".txt";
        }
        ArrayList<String> wpsAllTxtFilenames = new ArrayList<>();
        //6.3根据电量对txt文件进行分割
        for(int i = 0;i< wpsTxtFilenames.length;i++){
            int txtNum = calculator.calculatorAndSaveTxtFile(lastWpsDir,wpsTxtFilenames[i],request.getUavConfigs().get(i).getSelectedUav().getBatteryLength());
            wpsAllTxtFilenames.add(wpsTxtFilenames[i]);
            for(int j = 0; j < txtNum; j++){
                wpsAllTxtFilenames.add("wp" + i +"_"+j+".txt");
            }
        }
        moveFiles(lastWpsDir, targetWpsDir, wpsAllTxtFilenames);

        //7. 使用FlyPlan_GBK 执行wp0.txt -> wp0.kml
        String lastKmlDir = py2jsonPath+"/cppFiles/last/kml";
        String targetKmlDir = py2jsonPath+"/cppFiles/"+outFileDate+"/kml";

        String lastKmzDir = py2jsonPath+"/cppFiles/last/kmz";
        String targetKmzDir = py2jsonPath+"/cppFiles/"+outFileDate+"/kmz";

        ArrayList<String>  wpsAllKmlFilenames = new ArrayList<>();
        ArrayList<String>  wpsAllKmzFilenames = new ArrayList<>();
        for(String txtFilename: wpsAllTxtFilenames){
            //获得当前为第几个机型，用于后续的kmz设置
            Integer droneIndex = getDroneIndex(txtFilename);
            //设置kml或者kmz的名字
            int dotIndex = txtFilename.lastIndexOf('.');
            String prefix = (dotIndex > 0) ? txtFilename.substring(0, dotIndex) : txtFilename;

            String kmlFilename = prefix+".kml";
            String kmzFilename = prefix+".kmz";
            System.out.println("生成kml文件："+ kmlFilename);

            generateKml(lastWpsDir+"/"+txtFilename,lastKmlDir,prefix,request,droneIndex);
            generateKmz(lastWpsDir+"/"+txtFilename,lastKmzDir,prefix,request,droneIndex);

            wpsAllKmlFilenames.add(kmlFilename);
            wpsAllKmzFilenames.add(kmzFilename);
        }
        moveFiles(lastKmlDir, targetKmlDir, wpsAllKmlFilenames);
        moveFiles(lastKmzDir, targetKmzDir, wpsAllKmzFilenames);

        insertMissionPlan(request,targetKmlDir);
        insertMissionPlan(request,targetKmzDir);


        System.out.println("任务规划记录已成功存入数据库，存储的KML路径为: " + targetKmlDir);
        System.out.println("result:"+result);
        return result;
    }
    private String generateJson(List<UavConfig> uavConfigs) {
        // 1. Gson 生成合法 JSON（带双引号）
        Gson gson = new GsonBuilder().create();
        String uavConfigsJson = gson.toJson(uavConfigs);
        System.out.println("Java生成的原始JSON：" + uavConfigsJson);

        // 2. 关键：转义双引号（替换 " 为 \"），适配 Windows 命令行
//        String escapedJson = uavConfigsJson.replace("\"", "\\\"");
//        System.out.println("转义后用于传递的JSON：" + escapedJson);

        return uavConfigsJson;
    }

    private void insertMissionPlan( MissionPlannerRequest request,String targetDir){
        MissionPlanEntity missionPlan = new MissionPlanEntity();
        missionPlan.setNumberDevice(request.getNumberDevice());
        missionPlan.setDroneStart(request.getDroneStart()+"");
        missionPlan.setDroneEnd(request.getDroneEnd()+"");
        //todo：需要协商这个速度的数据库存储问题
        missionPlan.setDroneSpeed("20"+"");
        missionPlan.setKmlFilePath(targetDir);
        missionPlan.setScanDensity(request.getScanDensity()+"");
        missionPlan.setTime(LocalDateTime.now());
        missionPlanMapper.insertMissionPlan(missionPlan);
    }

    @Override
    public Result<MissionAreaVo> uploadKML(MissionPlannerRequest request) throws Exception {
        //python文件主要是针对kml杆塔的方式   无人机数量，起始位置，结束位置，文件路径
        String py2jsonCommand = pythonPath+" "+py2jsonPath+"/cpp_input/py2json_view.py " +
                                                request.getNumberDevice()+" "+
                                                request.getDroneStart()+" "+
                                                request.getDroneEnd()+" "+
                                                getDateString();
        String out = processKmlFromString(py2jsonCommand,request.getKmldir(),
                                                        request.getScanDensity(),
                                                        generateJson(request.getUavConfigs()),
                                                        request.getOverlapDegree());
        System.out.println("python输出"+out);
        JsonParser.parseJson(out);
        return Result.ok(JsonParser.parseJson(out));
//        return null;
    }

    private void isLastDirExist(String lastDirPath) {
        Path dirPath = Paths.get(lastDirPath);
        try {
            // 如果目录不存在，则创建
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                System.out.println("目录已创建: " + lastDirPath);
            } else {
                // 目录存在，清空所有文件和子目录
                System.out.println("目录已存在，正在清空: " + lastDirPath);
//                clearDirectory(dirPath);
            }
        } catch (IOException e) {
            System.err.println("处理目录时发生错误: " + e.getMessage());
        }
    }

    /**
     * 清空目录下的所有内容（保留目录本身）
     */
    private void clearDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(directory))  // 保留根目录
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            System.out.println("已删除: " + path.getFileName());
                        } catch (IOException e) {
                            System.err.println("删除失败: " + path + ", 错误: " + e.getMessage());
                        }
                    });
        }
        System.out.println("目录已清空: " + directory);
    }

    public String generateKml(String txtFilePath,
                                    String lastKmlDirPath,
                                    String fileNamePrefix,
                                    MissionPlannerRequest request,
                                    Integer droneIndex) {
        isLastDirExist(lastKmlDirPath);

        try {
            // 加载飞行点数据
            List<FlyPoint> flyPoints = flyPlanService.loadFlyPoints(txtFilePath);
            // 生成 KML 文件
            flyPlanService.generateKmlFile(lastKmlDirPath,
                    fileNamePrefix,
                    flyPoints,
                    request.getUavConfigs().get(droneIndex).getDroneSpeed());
            return fileNamePrefix +" KML file generated successfully!";
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "Error occurred while generating KML file.";
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public String generateKmz(String txtFilePath,
                                    String lastKmzDirPath,
                                    String fileNamePrefix,
                                    MissionPlannerRequest request,
                                    Integer droneIndex) {
        isLastDirExist(lastKmzDirPath);

        String uavName = request.getUavConfigs().get(droneIndex).getSelectedUav().getName();
        Integer speed  = request.getUavConfigs().get(droneIndex).getDroneSpeed();

        // 加载飞行点数据
        List<FlyPoint> flyPoints = flyPlanService.loadFlyPoints(txtFilePath);
        //生成kmz的入参
        UavRouteReq uavRouteReq = FlyPointConvert.mapToUavRouteReq(flyPoints, uavName, speed);
        //生成kmz文件
        RouteFileUtils.buildKmzFromWpx(lastKmzDirPath, fileNamePrefix, uavRouteReq);
        return fileNamePrefix + " KMZ file generated successfully!";

    }

    /**
     * 将指定的文件从源目录移动到目标目录。
     *
     * @param sourceDirPath 源目录路径
     * @param targetDirPath 目标目录路径
     * @param filenames     要移动的文件名数组
     */
    public void moveFiles(String sourceDirPath, String targetDirPath, ArrayList<String>  filenames) {
        // 将字符串路径转换为 Path 对象
        Path sourceDir = Paths.get(sourceDirPath);
        Path targetDir = Paths.get(targetDirPath);

        // 确保目标目录存在
        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // 遍历文件名数组并移动文件
            for (String filename : filenames) {
                Path sourceFile = sourceDir.resolve(filename);  // 源文件路径
                Path targetFile = targetDir.resolve(filename);  // 目标文件路径

                // 检查源文件是否存在
                if (Files.exists(sourceFile)) {
                    // 移动文件
                    Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("文件 " + filename + " 已复制到 " + targetDir);
                } else {
                    System.err.println("文件不存在: " + sourceFile);
                }
            }
        } catch (IOException e) {
            System.err.println("移动文件时发生错误: " + e.getMessage());
        }
    }


    public static List<List<double[]>> text2arr(String text, MissionPlannerRequest request) {
        // 1. 提取各个无人机的 Waypoints 数量
        Pattern waypointsPattern = Pattern.compile("Number of Waypoints for drone (\\d+): (\\d+)");
        Matcher waypointsMatcher = waypointsPattern.matcher(text);

        List<Integer> waypointsCounts = new ArrayList<>();
        while (waypointsMatcher.find()) {
            int droneIndex = Integer.parseInt(waypointsMatcher.group(1));
            int waypointsCount = Integer.parseInt(waypointsMatcher.group(2));
            System.out.println("无人机 " + droneIndex + " 的航点数量: " + waypointsCount);

            while (waypointsCounts.size() < droneIndex) {
                waypointsCounts.add(0);
            }
            waypointsCounts.set(droneIndex - 1, waypointsCount);
        }

        // 2. 提取所有坐标
        Pattern coordinatePattern = Pattern.compile("\\s*([-\\d.]+),\\s*([-\\d.]+)\\s*[;,]?");
        Matcher coordinateMatcher = coordinatePattern.matcher(text);

        List<double[]> coordinates = new ArrayList<>();
        while (coordinateMatcher.find()) {
            double lat = Double.parseDouble(coordinateMatcher.group(1));
            double lon = Double.parseDouble(coordinateMatcher.group(2));
            coordinates.add(new double[]{lat, lon});
        }

        System.out.println("提取到的坐标总数: " + coordinates.size());

        // 3. 按航点数量分割成多组（临时分组）
        List<List<double[]>> tempGroups = new ArrayList<>();
        int coordinateIndex = 0;
        for (int waypointsCount : waypointsCounts) {
            List<double[]> group = new ArrayList<>();
            for (int j = 0; j < waypointsCount && coordinateIndex < coordinates.size(); j++) {
                group.add(coordinates.get(coordinateIndex++));
            }
            tempGroups.add(group);
        }

        // 4. 解析所有起飞点
        List<double[]> startPositions = new ArrayList<>();
        for (String locationStr : request.getInitialLocations()) {
            String[] parts = locationStr.split(",");
            // 反转: lat,lon -> lon,lat
            double[] position = IntStream.rangeClosed(1, parts.length)
                    .mapToDouble(i -> Double.parseDouble(parts[parts.length - i].trim()))
                    .toArray();
            startPositions.add(position);
        }

        // 5. 根据距离匹配：每组航线匹配最近的起飞点
        List<List<double[]>> finalGroups = new ArrayList<>();
        for (int i = 0; i < startPositions.size(); i++) {
            finalGroups.add(new ArrayList<>());
        }

        boolean[] usedGroups = new boolean[tempGroups.size()];

        for (int i = 0; i < startPositions.size(); i++) {
            double[] startPos = startPositions.get(i);

            int closestGroupIndex = -1;
            double minDistance = Double.MAX_VALUE;

            // 找到距离当前起飞点最近的航线组
            for (int j = 0; j < tempGroups.size(); j++) {
                if (usedGroups[j] || tempGroups.get(j).isEmpty()) {
                    continue;
                }

                // 计算该组第一个航点到起飞点的距离
                double[] firstWaypoint = tempGroups.get(j).get(0);

                double distance = calculateDistance(startPos, firstWaypoint);

                if (distance < minDistance) {
                    minDistance = distance;
                    closestGroupIndex = j;
                }
            }

            // 匹配成功
            if (closestGroupIndex != -1) {
                usedGroups[closestGroupIndex] = true;
                List<double[]> matchedWaypoints = tempGroups.get(closestGroupIndex);

                System.out.println("起飞点 " + (i + 1) + " 匹配到航线组 " + (closestGroupIndex + 1)
                        + "，距离: " + String.format("%.6f", minDistance));

                // 构建完整航线: 起点 + 航点 + 终点
                finalGroups.get(i).add(startPos);
                finalGroups.get(i).addAll(matchedWaypoints);
                finalGroups.get(i).add(startPos);
            } else {
                System.out.println("警告: 起飞点 " + (i + 1) + " 没有匹配到航线组");
                // 只有起点和终点
                finalGroups.get(i).add(startPos);
                finalGroups.get(i).add(startPos);
            }
        }

        // 6. 打印结果
        System.out.println("\n=== 最终分组结果 ===");
        for (int i = 0; i < finalGroups.size(); i++) {
            int waypointCount = finalGroups.get(i).size() - 2; // 减去起点和终点
            System.out.println("无人机 " + (i + 1) + " 的航点数: " + waypointCount);
        }

        return finalGroups;
    }

    /**
     * 计算两个坐标点之间的欧几里得距离
     * @param point1 坐标1 [lon, lat]
     * @param point2 坐标2 [lat, lon]
     * @return 距离
     */
    private static double calculateDistance(double[] point1, double[] point2) {
        double dx = point1[0] - point2[0];
        double dy = point1[1] - point2[1];
        return Math.sqrt(dx * dx + dy * dy);
    }

//    public static List<List<double[]>> text2arr(String text,MissionPlannerRequest request) {
//        // 提取各个无人机的 Waypoints 数量
//        Pattern waypointsPattern = Pattern.compile("Number of Waypoints for drone (\\d+): (\\d+)");
//        Matcher waypointsMatcher = waypointsPattern.matcher(text);
//
//        List<Integer> waypointsCounts = new ArrayList<>();
//        while (waypointsMatcher.find()) {
//            int droneIndex = Integer.parseInt(waypointsMatcher.group(1));
//            int waypointsCount = Integer.parseInt(waypointsMatcher.group(2));
//            System.out.println("无人机 " + droneIndex + " 的航点数量: " + waypointsCount);
//
//            while (waypointsCounts.size() < droneIndex) {
//                waypointsCounts.add(0); // 确保索引匹配
//            }
//            waypointsCounts.set(droneIndex - 1, waypointsCount);
//        }
//
//        // 提取坐标的正则表达式
//        // 正确的坐标正则：支持负数、逗号前后任意空格、末尾可选的分号
//        Pattern coordinatePattern = Pattern.compile("\\s*([-\\d.]+),\\s*([-\\d.]+)\\s*[;,]?");
//        Matcher coordinateMatcher = coordinatePattern.matcher(text);
//
//        // 提取所有坐标
//        List<double[]> coordinates = new ArrayList<>();
//        while (coordinateMatcher.find()) {
//            double lat = Double.parseDouble(coordinateMatcher.group(1));
//            double lon = Double.parseDouble(coordinateMatcher.group(2));
//            System.out.println("提取的坐标: " + lat + ", " + lon);
//            coordinates.add(new double[]{lat, lon});
//        }
//        Collections.reverse(coordinates);
//
//        // 根据 Waypoints 数量进行分割
//        List<List<double[]>> groups = new ArrayList<>(waypointsCounts.size());
//        int coordinateIndex = 0;
//        for(int i = 0;i<waypointsCounts.size();i++) {
//            int waypointsCount = waypointsCounts.get(i);
//            List<double[]> currentGroup = new ArrayList<>();
//            String[] location = request.getInitialLocations().get(i).split(",");
//
//
//
//            // 直接在Stream中反转
//            double[] doubleLocation = IntStream.rangeClosed(1, location.length)
//                    .mapToDouble(k -> Double.parseDouble(location[location.length - k].trim()))
//                    .toArray();
//
//            currentGroup.add(doubleLocation);
//            for (int j = 0; j < waypointsCount && coordinateIndex < coordinates.size(); j++) {
//                currentGroup.add(coordinates.get(coordinateIndex++));
//            }
//            currentGroup.add(doubleLocation);
//            groups.add(currentGroup);
//
//        }
//
//        System.out.println("航点数量: " + waypointsCounts);
//        System.out.println("提取到的坐标总数: " + coordinates.size());
//        return groups;
//    }


    private String executeCommand(String command,String workingDirectory) {
        try {
            // 使用 ProcessBuilder 执行外部命令
            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));

            // 设置工作目录
            if (workingDirectory != null && !workingDirectory.isEmpty()) {
                processBuilder.directory(new File(workingDirectory));
            }

            processBuilder.redirectErrorStream(true); // 将标准错误与标准输出合并
            // 启动进程
            Process process = processBuilder.start();

            // 使用 try-with-resources 确保 BufferedReader 被正确关闭
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                // 等待进程结束并获取退出码
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    // 命令成功执行
                    System.out.println("命令执行成功");
                    return output.toString();
                } else {
                    // 执行失败，输出错误信息
                    String errorMessage = "命令执行失败，退出码：" + exitCode + "\n错误信息：\n" + output.toString();
                    System.err.println(errorMessage);
                    return output.toString();
                }
            }
        } catch (IOException | InterruptedException e) {
            // 合并处理 IOException 和 InterruptedException
            System.err.println("执行命令时发生错误：" + e.getMessage());
            Thread.currentThread().interrupt();  // 恢复中断状态
        } catch (Exception e) {
            // 捕获其他异常
            System.err.println("未知错误发生: " + e.getMessage());
        }
        return command;
    }

    private List<Map<String, Double>> generatePolygon(List<List<Double>> points) {
        List<Map<String, Double>> polygon = new ArrayList<>();
        for (List<Double> point : points) {
            Map<String, Double> polygonPoint = new HashMap<>();
            polygonPoint.put("lat", point.get(1));
            polygonPoint.put("long", point.get(0));
            polygon.add(polygonPoint);
        }
        return polygon;
    }

    private List<List<Map<String, Double>>> generateObstacles(List<List<Double>> points) {
        List<Map<String, Double>> obstacles = new ArrayList<>();
        List<List<Map<String, Double>>> re = new ArrayList<>();
        if(points == null||points.isEmpty() ||  points.size() == 0) {
//            re.add(obstacles);
            return re;
        }
        for (List<Double> point : points) {
            Map<String, Double> obstaclePoint = new HashMap<>();
            obstaclePoint.put("lat", point.get(1));
            obstaclePoint.put("long", point.get(0));
            obstacles.add(obstaclePoint);
        }

        re.add(obstacles);
        return re;
    }

    private List<Map<String, Double>> generateInitialPositions(List<String> initialLocations) {
        List<Map<String, Double>> initialPos = new ArrayList<>();
        for (String initialLocation : initialLocations) {
            String[] location = initialLocation.split(",");
            Map<String, Double> startLocation = new HashMap<>();
            startLocation.put("long", Double.parseDouble(location[0]));
            startLocation.put("lat", Double.parseDouble(location[1]));
            initialPos.add(startLocation);
        }
        return initialPos;
    }

    private List<Double> generateDistributionRatios(List<Double> ratios) {
        List<Double> rPortions = new ArrayList<>();
        int numberDevice = ratios.size();
        int sum = 0;
        for (int i = 0; i <numberDevice; i++) {
            rPortions.add(  ratios.get(i) / 100);
            sum += ratios.get(i);
        }
        if (sum != 100) {
            return null;
        }
        return rPortions;
    }

    //把前端传回来的数据改为json
    private void generateMissionPlannerFile(MissionPlannerRequest request,
                                            String path
    )throws IOException {

        // 生成多边形、障碍物、起始位置、分配比例等数据
        List<Map<String, Double>> polygon = generatePolygon(request.getMissionLayerPointArr());//任务区
        List<List<Map<String, Double>>> obstacles = generateObstacles(request.getObstacleLayerPointArr());//障碍区
        List<Map<String, Double>> initialPos = generateInitialPositions(request.getInitialLocations());//起始位置
        List<Double> rPortions = generateDistributionRatios(request.getDistributionRatios());//比例

        if(rPortions == null){
            throw new RuntimeException("区域比例合不是100%");
        }

        // 读取扫描密度 0.txt
        List<String> lines = Files.readAllLines(Paths.get(py2jsonPath+"/cppFiles/0.txt"));
//        int scanningDensity = Integer.parseInt(lines.get(3).trim());

        Map<String, Object> missionPlannerFile = new HashMap<>();
        missionPlannerFile.put("droneNo", request.getNumberDevice());
        missionPlannerFile.put("scanningDensity", calculateScanningDensity(request.getUavConfigs().get(0), request.getOverlapDegree() ));
        missionPlannerFile.put("pathsStrictlyInPoly", request.isPathsStrictlyInPoly());
        missionPlannerFile.put("obstacles", obstacles);
        missionPlannerFile.put("polygon", polygon);
        missionPlannerFile.put("initialPos", initialPos);
        missionPlannerFile.put("rPortions", rPortions);

        // 使用 Gson 转换对象为 JSON 字符串
        Gson gson = new Gson();
        String jsonData = gson.toJson(missionPlannerFile);

        // 写入 JSON 到文件
        try (FileWriter fileWriter = new FileWriter(path)) {
            fileWriter.write(jsonData);
            System.out.println("JSON file has been successfully generated");
        } catch (IOException e) {
            System.err.println("An error occurred while writing the JSON file: " + e.getMessage());
        }

        // 写入 JSON 到文件
        try (FileWriter fileWriter = new FileWriter(py2jsonPath+"/cppFiles/last/cpp.json")) {
            fileWriter.write(jsonData);
            System.out.println("JSON file has been successfully generated");
        } catch (IOException e) {
            System.err.println("An error occurred while writing the JSON file: " + e.getMessage());
        }

    }

    private void saveTextToFile(String filePath, String text) {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(text);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (FileWriter writer = new FileWriter(py2jsonPath+"/cppFiles/last/cpp_waypoint.txt")) {
            writer.write(text);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Integer getDroneIndex(String filename){
        try {
            // 步骤1：基础格式校验（必须以wp开头、以.txt结尾）
            if (!filename.startsWith("wp")) {
                throw new IllegalArgumentException("文件名需以'wp'开头：" + filename);
            }
            if (!filename.endsWith(".txt")) {
                throw new IllegalArgumentException("文件名需以'.txt'结尾：" + filename);
            }

            // 步骤2：去掉后缀 ".txt"（截取到最后一个"."的位置）
            String withoutSuffix = filename.substring(0, filename.lastIndexOf("."));
            // 例：wp0.txt → wp0；wp1_2.txt → wp1_2

            // 步骤3：去掉前缀 "wp"（截取索引2及之后的内容）
            String withoutPrefixSuffix = withoutSuffix.substring(2);
            // 例：wp0 → 0；wp1_2 → 1_2

            // 步骤4：分情况提取i
            String iStr;
            if (withoutPrefixSuffix.contains("_")) {
                // 有下划线：取第一个"_"前的内容
                iStr = withoutPrefixSuffix.substring(0, withoutPrefixSuffix.indexOf("_"));
            } else {
                // 无下划线：直接取当前内容
                iStr = withoutPrefixSuffix;
            }

            // 步骤5：（可选）若i是数字，转为int类型（非数字则跳过此步）
            Integer i = null;
            try {
                i = Integer.parseInt(iStr);
            } catch (NumberFormatException e) {
                // 若i是字符串（如wpabc_3.txt），不报错，保留字符串形式
                System.out.println("文件名：" + filename + " → i（字符串） = " + iStr);
            }

            // 输出结果（数字类型的i）
            System.out.println("文件名：" + filename + " → i（数字） = " + i);
            return i;

        } catch (IllegalArgumentException e) {
            // 处理格式非法的文件
            System.err.println("跳过非法文件：" + e.getMessage());
        }
        return null;
    }



}
