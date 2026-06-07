package com.whu.yun.service.impl;

import com.whu.yun.entity.FlyPoint;
import com.whu.yun.entity.MissionPlanEntity;
import com.whu.yun.entity.MissionPlannerRequested;
import com.whu.yun.mapper.MissionPlanMapper;
import com.whu.yun.service.FlyPlanService;
import com.whu.yun.service.MissionPlannerService;
import com.whu.yun.service.MissionPlannerService1;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;

@Service
@Slf4j
public class MissionPlannerServiceImpled implements MissionPlannerService1 {

    @Value("${py2json.path}")
    private String py2jsonPath;

//    @Value("${kml.path}")
//    private String kmlPath;

    @Value("${lib.path}")
    private String libPath;

    @Value("${utils.path}")
    private String utilsPath;

    @Value("${python.path}")
    private String pythonPath;

    @Autowired
    private FlyPlanService flyPlanService;
    @Autowired
    private MissionPlanMapper missionPlanMapper;
    @Override
    public List<List<double[]>> startMissionPlanner(MissionPlannerRequested request) {
        // --- [新增] 保存 KML 文件逻辑 ---
        String savedKmlPath = null;

        // 从请求实体中提取数据
        int planMode = request.getPlanMode();
        int numberDevice = request.getNumberDevice();
        boolean pathStrictlyInPoly = request.isPathsStrictlyInPoly();

        // 获取当前日期和时间，格式为 "yyyyMMddHHmmss"
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String outFileDate = now.format(formatter);
        String missionPlannerFilePath = py2jsonPath+"/cppFiles/"+outFileDate+"/cpp.json";
        File directory = new File(py2jsonPath + "/cppFiles/"+outFileDate);
        directory.mkdirs();

//        1是区域模式自己画框    ————使用java生成json
//        2是kml文件的杆塔模式   ————使用python生成json
        String py2jsonCommand = "";
        if (planMode == 1) {
            // 生成多边形、障碍物、起始位置、分配比例等数据
            List<Map<String, Double>> polygon = generatePolygon(request.getMissionLayerPointArr());
            List<List<Map<String, Double>>> obstacles = generateObstacles(request.getObstacleLayerPointArr());
            List<Map<String, Double>> initialPos = generateInitialPositions(numberDevice, Arrays.asList(request.getLocation1(), request.getLocation2(), request.getLocation3()));
            List<Double> rPortions = generateDistributionRatios(numberDevice, Arrays.asList(request.getDistributionRatio1(), request.getDistributionRatio2(), request.getDistributionRatio3()));

            if(rPortions == null){
                return null;
            }
            try {
                // 生成任务规划JSON文件
                System.out.println(" 生成任务规划JSON文件  保存至cpp.json");
                generateMissionPlannerFile(request , missionPlannerFilePath,polygon,obstacles,initialPos,rPortions);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (planMode == 2) {
//          python文件主要是针对kml杆塔的方式   无人机数量，起始位置，结束位置，文件路径
            py2jsonCommand = pythonPath+" "+py2jsonPath+"/cpp_input/py2json.py " + numberDevice+" "+request.getDroneStart()+" "+request.getDroneEnd()+" "+outFileDate;
            System.out.println("python命令："+py2jsonCommand);
            String py2jsonOutput = executeCommand(py2jsonCommand,py2jsonPath+"/cpp_input");
//            System.out.println("py2jsonOutput:"+py2jsonOutput);
        }

        // 执行任务规划程序（mCPP任务）mission_route_result
        System.out.println(" 执行任务规划程序（mCPP任务）根据cpp.json生成 String mission_route_result ");
        String missionRouteResult = executeCommand("java -jar "+libPath+"/mCPP-optimized-DARP.jar "+missionPlannerFilePath,null);
        System.out.println("任务规划文件命令："+"java -jar "+libPath+"/mCPP-optimized-DARP.jar ");
//        System.out.println("任务规划结果："+missionRouteResult);
//        if (missionRouteResult == null) {
//            return "mCPP命令执行失败";
//        }
        List<List<double[]>> result = text2arr(missionRouteResult);

        // 打印分组结果
//        for (int i = 0; i < result.size(); i++) {
//            System.out.println("~ Number of Waypoints: " + result.get(i).size() + " ~");
//            for (double[] coordinate : result.get(i)) {
//                System.out.println(coordinate[0] + ", " + coordinate[1]);
//            }
//            System.out.println();
//        }

        System.out.println(" 保存规划结果到txt文件 输入String mission_route_result，保存输出cpp_waypoint.txt");
        saveTextToFile(py2jsonPath+"/cppFiles/"+outFileDate+"/cpp_waypoint.txt", missionRouteResult);


        System.out.println(" 执行wp2kml Python脚本 航点->kml  生成/cppFiles/last/wps/wp{}.txt 没看到执行结果");
        String wp2kml = executeCommand(pythonPath+" "+py2jsonPath+"/cpp_input/wp2kml.py",py2jsonPath+"/cpp_input");
        String lastWpsDir = py2jsonPath+"/cppFiles/last/wps";
        String targetWpsDir = py2jsonPath+"/cppFiles/"+outFileDate+"/wps";
        String[] wpsFilenames = {"wp0.txt", "wp1.txt", "wp2.txt"};
        // 调用方法移动文件
        moveFiles(lastWpsDir, targetWpsDir, wpsFilenames);

        String lastKmlDir = py2jsonPath+"/cppFiles/last/kml";
        String targetKmlDir = py2jsonPath+"/cppFiles/"+outFileDate+"/kml";
        for(int i = 0;i< wpsFilenames.length;i++){
            System.out.println("生成kml文件："+wpsFilenames[i].replace(".txt",".kml"));
            generateKml(lastWpsDir+"/"+wpsFilenames[i],lastKmlDir,"wp"+i,request.getDroneSpeed());
        }
        moveFiles(lastKmlDir, targetKmlDir, new String[]{"wp0.kml", "wp1.kml", "wp2.kml"});

        System.out.println(wp2kml);

//        System.out.println(" 执行dom4j-FlyPlan 输入kml_input.txt 输出 xunjian.kml");
//        executeCommand("javac "+utilsPath+"/FlyPlan_GBK.java");
//        System.out.println(" 编译Flyplan.java文件 Java命令路径："+"javac "+utilsPath+"/FlyPlan_GBK.java");
//        String java =  executeCommand("java -cp \""+libPath+"/dom4j-2.1.1.jar;"+utilsPath+"\" FlyPlan_GBK");
//        System.out.println(" 执行Flyplan Java命令路径："+"java -cp \""+libPath+"/dom4j-2.1.1.jar;"+utilsPath+"\" FlyPlan_GBK");
        MissionPlanEntity missionPlan = new MissionPlanEntity();
        missionPlan.setNumberDevice(request.getNumberDevice());
        missionPlan.setDroneStart(request.getDroneStart()+"");
        missionPlan.setDroneEnd(request.getDroneEnd()+"");
        missionPlan.setDroneSpeed(request.getDroneSpeed()+"");
        missionPlan.setKmlFilePath(targetKmlDir);
        missionPlan.setScanDensity(request.getScanDensity()+"");
        missionPlan.setTime(LocalDateTime.now());

        missionPlanMapper.insertMissionPlan(missionPlan);
        System.out.println("任务规划记录已成功存入数据库，存储的KML路径为: " + targetKmlDir);



        return result;
    }

    public String generateKml(String txtPath, String folderPath,String fileName,int speed) {

        Path dirPath = Paths.get(folderPath);
        try {
            // 如果目录不存在，则创建
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                System.out.println("目录已创建: " + folderPath);
            } else {
                System.out.println("目录已存在: " + folderPath);
            }
        } catch (IOException e) {
            System.err.println("创建目录时发生错误: " + e.getMessage());
        }

        try {
            // 加载飞行点数据
            List<FlyPoint> flyPoints = flyPlanService.loadFlyPoints(txtPath);
            // 生成 KML 文件
            flyPlanService.generateKmlFile(folderPath, fileName, flyPoints,speed);
            return fileName +" KML file generated successfully!";
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "Error occurred while generating KML file.";
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将指定的文件从源目录移动到目标目录。
     *
     * @param sourceDirPath 源目录路径
     * @param targetDirPath 目标目录路径
     * @param filenames     要移动的文件名数组
     */
    public void moveFiles(String sourceDirPath, String targetDirPath, String[] filenames) {
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
    /**
     * 【新增】准备任务规划的 KML 文件以供下载。
     * @param id 任务ID
     * @return 包含 ZIP 文件流的 Spring Resource
     * @throws IOException 如果文件读取或打包失败
     */
    public Resource prepareKmlDownload(Long id) throws IOException {
        // 1. 查找任务记录
        MissionPlanEntity missionPlan = missionPlanMapper.findById(id);
        if (missionPlan == null || missionPlan.getKmlFilePath() == null) {
            throw new RuntimeException("未找到任务记录或关联的KML路径。");
        }

        Path kmlDirectory = Paths.get(missionPlan.getKmlFilePath());
        if (!Files.isDirectory(kmlDirectory)) {
            throw new RuntimeException("KML路径不是一个有效的目录: " + kmlDirectory);
        }

        // 2. 创建一个临时的 ZIP 文件
        Path tempZipPath = Files.createTempFile("mission_" + id + "_", ".zip");

        // 3. 使用 Zip4j 将目录下的所有 .kml 文件添加到 ZIP 包中
        try (ZipFile zipFile = new ZipFile(tempZipPath.toFile())) {
            Files.walk(kmlDirectory)
                    .filter(path -> !Files.isDirectory(path) && path.toString().toLowerCase().endsWith(".kml"))
                    .forEach(path -> {
                        try {
                            zipFile.addFile(path.toFile());
                        } catch (IOException e) {
                            // 在 lambda 表达式中，需要将受检异常包装为运行时异常
                            throw new RuntimeException("添加到ZIP包失败: " + path, e);
                        }
                    });
        }

        log.info("已成功创建临时ZIP包: {}", tempZipPath);

        // 4. 将临时 ZIP 文件包装成 Spring 的 Resource 以便返回
        // Spring 会在响应发送完毕后自动处理这个 InputStream
        return new InputStreamResource(new FileInputStream(tempZipPath.toFile()));
    }


    public static List<List<double[]>> text2arr(String text) {
        // 提取坐标的正则表达式
        Pattern coordinatePattern = Pattern.compile("([\\d.]+), ([\\d.]+)");
        Matcher coordinateMatcher = coordinatePattern.matcher(text);

        // 提取所有坐标
        List<double[]> coordinates = new ArrayList<>();
        while (coordinateMatcher.find()) {
            double lat = Double.parseDouble(coordinateMatcher.group(1));
            double lon = Double.parseDouble(coordinateMatcher.group(2));
            coordinates.add(new double[]{lat, lon});
        }

        // 提取各个无人机的 Waypoints 数量
        Pattern waypointsPattern = Pattern.compile("Number of Waypoints for drone (\\d+): (\\d+)");
        Matcher waypointsMatcher = waypointsPattern.matcher(text);

        List<Integer> waypointsCounts = new ArrayList<>();
        while (waypointsMatcher.find()) {
            int droneIndex = Integer.parseInt(waypointsMatcher.group(1));
            int waypointsCount = Integer.parseInt(waypointsMatcher.group(2));
            while (waypointsCounts.size() < droneIndex) {
                waypointsCounts.add(0); // 确保索引匹配
            }
            waypointsCounts.set(droneIndex - 1, waypointsCount);
        }

        // 根据 Waypoints 数量进行分割
        List<List<double[]>> groups = new ArrayList<>();
        int currentIndex = 0;
        int coordinateIndex = 0;

        for (int waypointsCount : waypointsCounts) {
            List<double[]> currentGroup = new ArrayList<>();
            for (int i = 0; i < waypointsCount && coordinateIndex < coordinates.size(); i++) {
                currentGroup.add(coordinates.get(coordinateIndex++));
            }
            groups.add(currentGroup);
        }

        return groups;
    }


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
        for (List<Double> point : points) {
            Map<String, Double> obstaclePoint = new HashMap<>();
            obstaclePoint.put("lat", point.get(1));
            obstaclePoint.put("long", point.get(0));
            obstacles.add(obstaclePoint);
        }
        re.add(obstacles);
        return re;
    }

    private List<Map<String, Double>> generateInitialPositions(int numberDevice,List<String> initialLocations) {
        List<Map<String, Double>> initialPos = new ArrayList<>();
        for (int i = 0; i < numberDevice; i++) {
            String[] location = initialLocations.get(i).split(",");
            Map<String, Double> startLocation = new HashMap<>();
            startLocation.put("lat", Double.parseDouble(location[1]));
            startLocation.put("long", Double.parseDouble(location[0]));
            initialPos.add(startLocation);
        }
        return initialPos;
    }

    private List<Double> generateDistributionRatios(int numberDevice, List<Integer> ratios) {
        List<Double> rPortions = new ArrayList<>();
        int sum = 0;
        for (int i = 0; i <numberDevice; i++) {
            rPortions.add( ((double) ratios.get(i) / 100));
            sum += ratios.get(i);
        }
        if (sum != 100) {
            return null;
        }
        return rPortions;
    }

    //把前端传回来的数据改为json
    private void generateMissionPlannerFile(MissionPlannerRequested request, String path,
                                            List<Map<String, Double>> polygon,
                                            List<List<Map<String, Double>>> obstacles,
                                            List<Map<String, Double>> initialPos,
                                            List<Double> rPortions
    )throws IOException {

        // 读取扫描密度 0.txt
        List<String> lines = Files.readAllLines(Paths.get(py2jsonPath+"/cppFiles/0.txt"));
        int scanningDensity = Integer.parseInt(lines.get(3).trim());

        Map<String, Object> missionPlannerFile = new HashMap<>();
        missionPlannerFile.put("droneNo", request.getNumberDevice());
        missionPlannerFile.put("scanningDensity", scanningDensity);
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


}
