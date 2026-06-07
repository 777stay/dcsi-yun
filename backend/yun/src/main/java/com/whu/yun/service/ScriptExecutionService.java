package com.whu.yun.service;

import com.whu.yun.dto.TowerResultDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

import org.springframework.web.multipart.MultipartFile;

@Service
public class ScriptExecutionService {

    @Value("${python.path}")
    private String pythonExecutable;

    @Value("${python.script.detectPlyPath}")
    private String detectPlyScriptPath;

    @Value("${python.script.kmlCoordTransPath}")
    private String kmlCoordTransScriptPath;

    // 临时文件存储目录
    private final Path tempUploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "yun_uploads");

    public ScriptExecutionService() throws IOException {
        if (!Files.exists(tempUploadDir)) {
            Files.createDirectories(tempUploadDir);
        }
    }

    /**
     * 运行硬编码的 ply-changedetection.py 脚本
     * （此方法在新的需求中将不再使用，但保留以防万一）
     */
    @Deprecated
    public Map<String, String> runHardcodedChangeDetection() throws IOException, InterruptedException {

        Path scriptFile = Paths.get(detectPlyScriptPath);
        if (!Files.exists(scriptFile)) {
            throw new IOException("脚本未在服务器上找到: " + detectPlyScriptPath);
        }

        System.out.println("Executing hardcoded script: " + detectPlyScriptPath);

        ProcessBuilder pb = new ProcessBuilder(
                pythonExecutable,
                scriptFile.toAbsolutePath().toString()
        );

        // 脚本的硬编码路径 (F:/study/foshan/)  是在脚本内部处理的,
        // 我们不需要传递它们。

        executeProcess(pb);
        Map<String, String> result = new HashMap<>();
        result.put("message", "脚本在服务器上成功执行");
        return result;

    }

    public List<TowerResultDto> runChangeDetectionScripts(MultipartFile plyFile1, MultipartFile plyFile2, MultipartFile kmlFile) throws IOException, InterruptedException {
        String uniqueId = UUID.randomUUID().toString();

        // 路径设置
        Path kmlCoordTransScriptDir = Paths.get(kmlCoordTransScriptPath).getParent();
        Path detectPlyScriptDir = Paths.get(detectPlyScriptPath).getParent();

        Path ply1Temp = tempUploadDir.resolve(uniqueId + "_ply1.ply");
        Path ply2Temp = tempUploadDir.resolve(uniqueId + "_ply2.ply");
        Path kmlTemp = tempUploadDir.resolve(uniqueId + ".kml");
        Path jsonOutput = kmlCoordTransScriptDir.resolve(uniqueId + ".json");
        Path resultPlyOutput = detectPlyScriptDir.resolve(uniqueId + "_result.ply");

        // 基础输出目录
        Path baseOutputDir = detectPlyScriptDir.resolve(uniqueId + "_output");

        Files.copy(plyFile1.getInputStream(), ply1Temp, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(plyFile2.getInputStream(), ply2Temp, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(kmlFile.getInputStream(), kmlTemp, StandardCopyOption.REPLACE_EXISTING);

        try {
            // 1. 运行 KML 转换
            Path kmlCoordTransScriptFile = Paths.get(kmlCoordTransScriptPath);
            ProcessBuilder pbKml = new ProcessBuilder(
                    pythonExecutable,
                    kmlCoordTransScriptFile.toAbsolutePath().toString(),
                    kmlTemp.toAbsolutePath().toString(),
                    jsonOutput.toAbsolutePath().toString(),
                    "49N"
            );
            executeProcess(pbKml);

            // 2. 运行变化检测
            Path detectPlyScriptFile = Paths.get(detectPlyScriptPath);
            // 确保输出目录存在
            Files.createDirectories(baseOutputDir);

            ProcessBuilder pbPly = new ProcessBuilder(
                    pythonExecutable,
                    detectPlyScriptFile.toAbsolutePath().toString(),
                    ply1Temp.toAbsolutePath().toString(),   // arg 1
                    ply2Temp.toAbsolutePath().toString(),   // arg 2
                    jsonOutput.toAbsolutePath().toString(), // arg 3
                    resultPlyOutput.toAbsolutePath().toString(), // arg 4
                    baseOutputDir.toAbsolutePath().toString()    // arg 5: 基础目录
            );
            executeProcess(pbPly);

            // 3. 收集结果
            List<TowerResultDto> results = new ArrayList<>();

            // Python脚本在 baseOutputDir 下创建了 'changes' 和 'rgb' 两个文件夹
            File rgbDir = baseOutputDir.resolve("rgb").toFile();
            File changesDir = baseOutputDir.resolve("changes").toFile();

            if (rgbDir.exists() && rgbDir.isDirectory()) {
                File[] rgbFiles = rgbDir.listFiles((dir, name) -> name.endsWith(".ply"));

                if (rgbFiles != null) {
                    for (File rgbFile : rgbFiles) {
                        String filename = rgbFile.getName(); // e.g., T1.ply
                        String towerName = filename.replace(".ply", "");

                        // 读取 RGB 数据
                        byte[] rgbBytes = Files.readAllBytes(rgbFile.toPath());
                        String rgbBase64 = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(rgbBytes);

                        // 查找对应的 Changes 数据 (可能不存在)
                        String changeBase64 = null;
                        if (changesDir.exists()) {
                            File changeFile = new File(changesDir, filename);
                            if (changeFile.exists()) {
                                byte[] changeBytes = Files.readAllBytes(changeFile.toPath());
                                changeBase64 = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(changeBytes);
                            }
                        }

                        results.add(new TowerResultDto(towerName, changeBase64, rgbBase64));
                    }
                }
            }

            return results;

        } finally {
            // 清理
            Files.deleteIfExists(ply1Temp);
            Files.deleteIfExists(ply2Temp);
            Files.deleteIfExists(kmlTemp);
            // 视情况清理输出目录...
        }
    }

   /* public byte[] runChangeDetectionScripts(MultipartFile plyFile1, MultipartFile plyFile2, MultipartFile kmlFile) throws IOException, InterruptedException {
        // 1. 定义文件路径和唯一ID
        String uniqueId = UUID.randomUUID().toString();

        // 获取脚本所在目录
        Path kmlCoordTransScriptDir = Paths.get(kmlCoordTransScriptPath).getParent();
        Path detectPlyScriptDir = Paths.get(detectPlyScriptPath).getParent();

        // 上传的原始文件存储在临时目录
        Path ply1Temp = tempUploadDir.resolve(uniqueId + "_ply1.ply");
        Path ply2Temp = tempUploadDir.resolve(uniqueId + "_ply2.ply");
        Path kmlTemp = tempUploadDir.resolve(uniqueId + ".kml");

        // JSON文件和结果PLY文件存储在各自脚本的目录中，且不清理
        Path jsonOutput = kmlCoordTransScriptDir.resolve(uniqueId + ".json");
        Path resultPlyOutput = detectPlyScriptDir.resolve(uniqueId + "_result.ply");
        // 逐塔变化结果文件夹也存放在detectPly脚本同级目录
        Path perTowerOutputDir = detectPlyScriptDir.resolve(uniqueId + "_per_tower_changes");

        Files.copy(plyFile1.getInputStream(), ply1Temp, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(plyFile2.getInputStream(), ply2Temp, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(kmlFile.getInputStream(), kmlTemp, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Uploaded files saved to: " + tempUploadDir);

        try {
            // 2. 调用 kml-coord-trans.py 脚本
            Path kmlCoordTransScriptFile = Paths.get(kmlCoordTransScriptPath);
            if (!Files.exists(kmlCoordTransScriptFile)) {
                throw new IOException("KML 坐标转换脚本未找到: " + kmlCoordTransScriptFile);
            }
            ProcessBuilder pbKml = new ProcessBuilder(
                    pythonExecutable,
                    kmlCoordTransScriptFile.toAbsolutePath().toString(),
                    kmlTemp.toAbsolutePath().toString(),
                    jsonOutput.toAbsolutePath().toString(), // 将输出路径传递给脚本
                    "49N" // 硬编码的 UTM_ZONE，后续可以考虑作为参数传入
            );
            System.out.println("Executing KML transformation script:");
            executeProcess(pbKml);
            System.out.println("KML transformation script finished. JSON saved to: " + jsonOutput);

            // 3. 调用 ply-changedetection.py 脚本
            Path detectPlyScriptFile = Paths.get(detectPlyScriptPath);
            if (!Files.exists(detectPlyScriptFile)) {
                throw new IOException("点云变化检测脚本未找到: " + detectPlyScriptFile);
            }
            ProcessBuilder pbPly = new ProcessBuilder(
                    pythonExecutable,
                    detectPlyScriptFile.toAbsolutePath().toString(),
                    ply1Temp.toAbsolutePath().toString(),
                    ply2Temp.toAbsolutePath().toString(),
                    jsonOutput.toAbsolutePath().toString(), // 将 JSON 输出路径传递给脚本
                    resultPlyOutput.toAbsolutePath().toString(), // 将结果 PLY 输出路径传递给脚本
                    perTowerOutputDir.toAbsolutePath().toString() // 逐塔变化输出目录
            );
            System.out.println("Executing PLY change detection script:");
            executeProcess(pbPly);
            System.out.println("PLY change detection script finished. Result saved to: " + resultPlyOutput);

            // 4. 读取结果 PLY 文件并返回
            if (Files.exists(resultPlyOutput)) {
                return Files.readAllBytes(resultPlyOutput);
            } else {
                throw new IOException("结果 PLY 文件未生成: " + resultPlyOutput);
            }
        } finally {
            // 5. 清理临时文件 (只清理上传的原始文件，不清理生成的 JSON 和结果 PLY 文件)
            //Files.deleteIfExists(ply1Temp);
            //Files.deleteIfExists(ply2Temp);
            //Files.deleteIfExists(kmlTemp);
            // Files.deleteIfExists(jsonOutput); // 不再清理
            // Files.deleteIfExists(resultPlyOutput); // 不再清理
            System.out.println("Cleaned up temporary uploaded files for " + uniqueId);
        }
    }

    */

    // 辅助方法：执行进程并打印输出
    private void executeProcess(ProcessBuilder pb) throws IOException, InterruptedException {
        pb.redirectErrorStream(true); // 合并 stdout 和 stderr
        Process process = pb.start();

        // 实时打印 Python 脚本的输出到 Spring Boot 控制台
        try (InputStream is = process.getInputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                System.out.write(buffer, 0, read);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // 如果脚本失败 (例如 F: 盘不存在或文件未找到)，将抛出异常
            throw new RuntimeException("Python 脚本执行失败 (退出代码: " + exitCode + ")。请检查服务器日志。");
        }
    }
}