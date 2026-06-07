package com.whu.yun.service;



import com.example.robotbackend.comm.CommBaseProto;
import com.whu.yun.entity.PointCloudEntity;
import com.whu.yun.mapper.PointCloudMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class PointCloudProcessor {
    // 注入 StringRedisTemplate 用于与 Redis 交互
    private final DataFlowControlService dataFlowControlService;
    private static final Logger logger = LoggerFactory.getLogger(PointCloudProcessor.class);
    private final PointCloudMapper pointCloudMapper;
    private final ConcurrentHashMap<String, PointCloudBatch> dataCache = new ConcurrentHashMap<>();
    private static final long TIMEOUT_MS = 10 * 1000; // 10秒超时

    @Value("${ply.potreeStorge.path}")
    private String plyStoragePath;
    @Value("${potree.converter.path}")
    private String potreeConverterPath;

    @Value("${python.executable}")
    private String pythonExecutable;

    @Value("${python.script.path}")
    private String pythonScriptPath;
    // 内部类，用于存储批量数据和时间戳
    private static class PointCloudBatch {
        private final List<CommBaseProto.BasePacket> packets = new ArrayList<>();
        private long lastUpdateTime;
        PointCloudBatch() { this.lastUpdateTime = System.currentTimeMillis(); }
        void addPacket(CommBaseProto.BasePacket packet) {
            this.packets.add(packet);
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }

    /**
     * 将从 RocketMQ 收到的消息添加到缓存
     */
    public void addPacket(CommBaseProto.BasePacket packet) {
        String key = packet.getSender() + "_" + "potree"; // robot_id + frame
        dataCache.compute(key, (k, v) -> {
            if (v == null) v = new PointCloudBatch();
            v.addPacket(packet);
            return v;
        });
    }

    /**
     * 定时任务，每秒检查一次是否有超时的批次
     */
    @Scheduled(fixedRate = 1000)
    public void checkForTimeouts() {
        if (dataCache.isEmpty()) return;

        long now = System.currentTimeMillis();
        dataCache.forEach((key, batch) -> {
            if (now - batch.lastUpdateTime > TIMEOUT_MS) {
                logger.info("Timeout detected for key: [{}]. Processing batch...", key);
                PointCloudBatch batchToProcess = dataCache.remove(key);
                if (batchToProcess != null) {
                    processAndConvertToPly(key, batchToProcess.packets);
                }
            }
        });
    }

    /**
     * 异步处理数据批次：生成 PLY 文件并调用 PotreeConverter
     */
    @Async
    public void processAndConvertToPly(String key, List<CommBaseProto.BasePacket> packets) {
        String fileName = key + "_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
        File plyFile = new File(plyStoragePath, fileName + ".ply");
        try {
            FileUtils.forceMkdir(plyFile.getParentFile());
            writePlyFile(plyFile, packets);

            runConversionPipeline(plyFile);
        } catch (IOException e) {
            logger.error("Failed to process batch for key: " + key, e);
        }
    }

    private void writePlyFile(File file, List<CommBaseProto.BasePacket> packets) throws IOException {
        long totalPoints = packets.stream().mapToLong(p -> p.hasPclXyzi() ? p.getPclXyzi().getPointsCount() : p.getPclXyzrgb().getPointsCount()).sum();
        if (totalPoints == 0) return;

        boolean hasColor = packets.stream().anyMatch(CommBaseProto.BasePacket::hasPclXyzrgb);
        logger.info("Writing PLY file: {} with {} points.", file.getAbsolutePath(), totalPoints);

        try (PrintWriter writer = new PrintWriter(file)) {
            // 写入 PLY 文件头
            writer.println("ply");
            writer.println("format ascii 1.0");
            writer.println("element vertex " + totalPoints);
            writer.println("property float x");
            writer.println("property float y");
            writer.println("property float z");
            if (hasColor) {
                writer.println("property uchar red");
                writer.println("property uchar green");
                writer.println("property uchar blue");
            } else {
                writer.println("property float intensity");
            }
            writer.println("end_header");

            // 写入点数据
            for (CommBaseProto.BasePacket packet : packets) {
                if (packet.hasPclXyzi()) {
                    packet.getPclXyzi().getPointsList().forEach(p -> writer.printf("%.4f %.4f %.4f %.4f%n", p.getX(), p.getY(), p.getZ(), p.getIntensity()));
                } else if (packet.hasPclXyzrgb()) {
                    packet.getPclXyzrgb().getPointsList().forEach(p -> writer.printf("%.4f %.4f %.4f %d %d %d%n", p.getX(), p.getY(), p.getZ(), p.getR(), p.getG(), p.getB()));
                }
            }

        }
        // --- 4. 在数据库中创建记录 ---
        PointCloudEntity entity = new PointCloudEntity();
        entity.setRobotId(packets.get(0).getSender());
        entity.setFrameId("Potree点云"); // 使用正确的 frameId
        entity.setFilePath(file.getAbsolutePath());
        entity.setPointCount(totalPoints);
        entity.setTimestamp(LocalDateTime.now());
        entity.setReceptionCount(dataFlowControlService.getCurrentCount());

        pointCloudMapper.insertPointCloud(entity);
    }

    private void runConversionPipeline(File plyFile) {
        logger.info("开始为文件 [{}] 执行 Python 转换流程...", plyFile.getName());
        try {
            // 构造命令: python3 scripts/converter.py --input /path/to/file.ply --potree_converter /path/to/PotreeConverter
            ProcessBuilder processBuilder = new ProcessBuilder(
                    pythonExecutable,
                    pythonScriptPath,
                    "--input", plyFile.getAbsolutePath(),
                    "--potree_converter", potreeConverterPath,
                    "--output", plyStoragePath
            );

            // 将进程的工作目录设置为项目根目录，确保脚本能被正确找到
            processBuilder.directory(new File(System.getProperty("user.dir")));

            Process process = processBuilder.start();

            // 使用单独的线程实时捕获并打印 Python 脚本的日志输出，便于调试
            // 标准输出流 (PotreeConverter 的成功信息)
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("[Python STDOUT] {}", line);
                    }
                } catch (IOException e) {
                    logger.warn("读取 python 脚本标准输出时出错", e);
                }
            }).start();

            // 错误输出流 (我们 Python 脚本的日志和 PotreeConverter 的错误信息)
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 使用 error 级别记录，方便在日志中快速定位问题
                        logger.error("[Python STDERR] {}", line);
                    }
                } catch (IOException e) {
                    logger.warn("读取 python 脚本标准错误时出错", e);
                }
            }).start();

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Python 转换流程为 [{}] 执行成功。", plyFile.getName());
            } else {
                logger.error("Python 转换流程为 [{}] 执行失败，退出码: {}", plyFile.getName(), exitCode);
            }

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("执行 Python 转换流程时发生严重错误: ", e);
        }
    }
}