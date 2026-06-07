package com.whu.yun.service;

import com.cleaner.djuav.util.RouteFileUtils;
import com.google.gson.Gson;
import com.whu.yun.entity.FlyPoint;
import com.whu.yun.entity.MissionPlanEntity;
import com.whu.yun.entity.MissionPlannerRequested;
import com.whu.yun.mapper.MissionPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 这个类专门负责执行后台的、耗时的任务规划流程。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MissionPlanningWorkerImpl implements MissionPlanningWorker {

    // --- 这里注入所有执行任务所需的依赖 ---
    private final FlyPlanService flyPlanService;
    private final MissionPlanMapper missionPlanMapper;
    private final Gson gson = new Gson();

    @Value("${py2json.path}")
    private String py2jsonPath;
    @Value("${lib.path}")
    private String libPath;
    @Value("${utils.path}")
    private String utilsPath;
    @Value("${python.path}")
    private String pythonPath;


    @Override
    @Async("taskExecutor")
    public Future<List<List<double[]>>> executePlanningPipelineAsync(MissionPlannerRequested request, String taskId) {
        log.info("[Task ID: {}] 后台线程池开始执行任务规划流程...", taskId);
        try {
            // --- 所有耗时的逻辑都从 MissionPlannerServiceImpl 移到这里 ---
            int planMode = request.getPlanMode();
            int numberDevice = request.getNumberDevice();

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            String outFileDate = now.format(formatter);
            String missionPlannerFilePath = py2jsonPath + "/cppFiles/" + outFileDate + "/cpp.json";
            File directory = new File(py2jsonPath + "/cppFiles/" + outFileDate);
            directory.mkdirs();

            if (planMode == 1) {
                List<Map<String, Double>> polygon = generatePolygon(request.getMissionLayerPointArr());
                List<List<Map<String, Double>>> obstacles = generateObstacles(request.getObstacleLayerPointArr());
                List<Map<String, Double>> initialPos = generateInitialPositions(numberDevice, Arrays.asList(request.getLocation1(), request.getLocation2(), request.getLocation3()));
                List<Double> rPortions = generateDistributionRatios(numberDevice, Arrays.asList(request.getDistributionRatio1(), request.getDistributionRatio2(), request.getDistributionRatio3()));

                if (rPortions == null) {
                    throw new IllegalArgumentException("分配比例总和不为100%");
                }
                generateMissionPlannerFile(request, missionPlannerFilePath, polygon, obstacles, initialPos, rPortions);
            } else if (planMode == 2) {
                String py2jsonCommand = pythonPath + " " + py2jsonPath + "/cpp_input/py2json.py " + numberDevice + " " + request.getDroneStart() + " " + request.getDroneEnd() + " " + outFileDate;
                log.info("[Task ID: {}] 执行 Python 命令: {}", taskId, py2jsonCommand);
                executeCommand(py2jsonCommand, py2jsonPath + "/cpp_input");
            }

            String missionRouteResult = executeCommand("java -jar " + libPath + "/mCPP-optimized-DARP.jar " + missionPlannerFilePath, null);

            if (!StringUtils.hasText(missionRouteResult) || missionRouteResult.contains("Error")) {
                throw new RuntimeException("任务规划核心算法执行失败。");
            }
            
            List<List<double[]>> result = text2arr(missionRouteResult);
            
            saveTextToFile(py2jsonPath+"/cppFiles/"+outFileDate+"/cpp_waypoint.txt", missionRouteResult);

            executeCommand(pythonPath+" "+py2jsonPath+"/cpp_input/wp2kml.py",py2jsonPath+"/cpp_input");
            
            String lastWpsDir = py2jsonPath+"/cppFiles/last/wps";
            String targetWpsDir = py2jsonPath+"/cppFiles/"+outFileDate+"/wps";
            String[] wpsFilenames = {"wp0.txt", "wp1.txt", "wp2.txt"};
            moveFiles(lastWpsDir, targetWpsDir, wpsFilenames);

            String lastKmlDir = py2jsonPath+"/cppFiles/last/kml";
            String targetKmlDir = py2jsonPath+"/cppFiles/"+outFileDate+"/kml";

            String lastKmzDir = py2jsonPath+"/cppFiles/last/kmz";
            String targetKmzDir = py2jsonPath+"/cppFiles/"+outFileDate+"/kmz";

            for(int i = 0; i < numberDevice; i++){

                generateKml(targetWpsDir+"/"+wpsFilenames[i], targetKmlDir, "wp"+i,request.getDroneSpeed());
            }
            moveFiles(lastKmlDir, targetKmlDir, new String[]{"wp0.kml", "wp1.kml", "wp2.kml"});

            MissionPlanEntity missionPlan = new MissionPlanEntity();
            missionPlan.setNumberDevice(request.getNumberDevice());
            missionPlan.setDroneStart(request.getDroneStart()+"");
            missionPlan.setDroneEnd(request.getDroneEnd()+"");
            missionPlan.setDroneSpeed(request.getDroneSpeed()+"");
            missionPlan.setKmlFilePath(targetKmlDir);
            missionPlan.setScanDensity(request.getScanDensity()+"");
            missionPlan.setTime(LocalDateTime.now());
            missionPlanMapper.insertMissionPlan(missionPlan);

            // 使用 AsyncResult 包装最终结果并返回
            return new AsyncResult<>(result);

        } catch (Exception e) {
            log.error("[Task ID: {}] 后台任务规划流程执行失败", taskId, e);
            // 将异常包装在 Future 中返回
            CompletableFuture<List<List<double[]>>> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    // --- 所有辅助方法 ---

    public String generateKml(String txtPath, String folderPath,String fileName,int speed) throws IOException {
        Path dirPath = Paths.get(folderPath);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        List<FlyPoint> flyPoints = flyPlanService.loadFlyPoints(txtPath);
        flyPlanService.generateKmlFile(folderPath, fileName, flyPoints,speed);
        return fileName +" KML file generated successfully!";
    }
    
    public void moveFiles(String sourceDirPath, String targetDirPath, String[] filenames) throws IOException {
        Path sourceDir = Paths.get(sourceDirPath);
        Path targetDir = Paths.get(targetDirPath);
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        for (String filename : filenames) {
            Path sourceFile = sourceDir.resolve(filename);
            Path targetFile = targetDir.resolve(filename);
            if (Files.exists(sourceFile)) {
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static List<List<double[]>> text2arr(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        Pattern coordinatePattern = Pattern.compile("([\\d.]+), ([\\d.]+)");
        Matcher coordinateMatcher = coordinatePattern.matcher(text);

        List<double[]> coordinates = new ArrayList<>();
        while (coordinateMatcher.find()) {
            double lat = Double.parseDouble(coordinateMatcher.group(1));
            double lon = Double.parseDouble(coordinateMatcher.group(2));
            coordinates.add(new double[]{lat, lon});
        }

        Pattern waypointsPattern = Pattern.compile("Number of Waypoints for drone (\\d+): (\\d+)");
        Matcher waypointsMatcher = waypointsPattern.matcher(text);

        List<Integer> waypointsCounts = new ArrayList<>();
        while (waypointsMatcher.find()) {
            int droneIndex = Integer.parseInt(waypointsMatcher.group(1));
            int waypointsCount = Integer.parseInt(waypointsMatcher.group(2));
            while (waypointsCounts.size() < droneIndex) {
                waypointsCounts.add(0);
            }
            waypointsCounts.set(droneIndex - 1, waypointsCount);
        }

        List<List<double[]>> groups = new ArrayList<>();
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

    private String executeCommand(String command, String workingDirectory) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        if (workingDirectory != null && !workingDirectory.isEmpty()) {
            processBuilder.directory(new File(workingDirectory));
        }
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return output.toString();
            } else {
                throw new IOException("命令执行失败，退出码：" + exitCode + "\n错误信息：\n" + output.toString());
            }
        }
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
    
    private List<Map<String, Double>> generateInitialPositions(int numberDevice, List<String> initialLocations) {
        List<Map<String, Double>> initialPos = new ArrayList<>();
        for (int i = 0; i < numberDevice; i++) {
            if (i >= initialLocations.size() || initialLocations.get(i) == null || initialLocations.get(i).isEmpty()) continue;
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
        for (int i = 0; i < numberDevice; i++) {
            if (i >= ratios.size()) continue;
            rPortions.add(((double) ratios.get(i) / 100));
            sum += ratios.get(i);
        }
        if (sum != 100) {
            return null;
        }
        return rPortions;
    }

    private void generateMissionPlannerFile(MissionPlannerRequested request, String path,
                                            List<Map<String, Double>> polygon,
                                            List<List<Map<String, Double>>> obstacles,
                                            List<Map<String, Double>> initialPos,
                                            List<Double> rPortions) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(py2jsonPath + "/cppFiles/0.txt"));
        int scanningDensity = Integer.parseInt(lines.get(3).trim());
        Map<String, Object> missionPlannerFile = new HashMap<>();
        missionPlannerFile.put("droneNo", request.getNumberDevice());
        missionPlannerFile.put("scanningDensity", scanningDensity);
        missionPlannerFile.put("pathsStrictlyInPoly", request.isPathsStrictlyInPoly());
        missionPlannerFile.put("obstacles", obstacles);
        missionPlannerFile.put("polygon", polygon);
        missionPlannerFile.put("initialPos", initialPos);
        missionPlannerFile.put("rPortions", rPortions);
        String jsonData = gson.toJson(missionPlannerFile);
        try (FileWriter fileWriter = new FileWriter(path)) {
            fileWriter.write(jsonData);
        }
        try (FileWriter fileWriter = new FileWriter(py2jsonPath + "/cppFiles/last/cpp.json")) {
            fileWriter.write(jsonData);
        }
    }
    
    private void saveTextToFile(String filePath, String text) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(text);
        }
        try (FileWriter writer = new FileWriter(py2jsonPath + "/cppFiles/last/cpp_waypoint.txt")) {
            writer.write(text);
        }
    }
}

