package com.whu.yun.service;


import com.example.robotbackend.comm.CommBaseProto;
import com.whu.yun.entity.ImageEntity;
import com.whu.yun.entity.OdometryEntity;
import com.whu.yun.entity.PointCloudEntity;
import com.whu.yun.mapper.ImageMapper;
import com.whu.yun.mapper.OdometryMapper;
import com.whu.yun.mapper.PointCloudMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataProcessingService {

    private final PointCloudMapper pointCloudMapper;
    private final OdometryMapper odometryMapper;
    private final ImageMapper imageMapper;
    private final DataFlowControlService dataFlowControlService;
    @Value("${app.file-storage.point-cloud-path}")
    private String pointCloudBasePath;

    @Value("${app.file-storage.image-path}")
    private String imageBasePath;
    // --- [新增] 用于记录每个机器人上次保存图片的时间戳 ---
    //private final Map<String, Long> lastImageSaveTimeMap = new ConcurrentHashMap<>();
    // --- [新增] 定义保存间隔为 3000 毫秒 (3秒) ---
    private static final long SAVE_INTERVAL_MS = 3000;
    public void processAndSaveOdometry(CommBaseProto.BasePacket packet) {
        CommBaseProto.OdomBody odom = packet.getOdom();
        OdometryEntity entity = new OdometryEntity();
        entity.setRobotId(packet.getSender());
        entity.setFrameId(packet.getFrame());
        entity.setPosX(odom.getX());
        entity.setPosY(odom.getY());
        entity.setPosZ(odom.getZ());
        entity.setOrientX(odom.getOrientation().getX());
        entity.setOrientY(odom.getOrientation().getY());
        entity.setOrientZ(odom.getOrientation().getZ());
        entity.setOrientW(odom.getOrientation().getW());
        entity.setTimestamp(toLocalDateTime(packet.getSecond(), packet.getNanosecond()));
        entity.setReceptionCount(dataFlowControlService.getCurrentCount());
        odometryMapper.insertOdometry(entity);
        //log.info("Saved odometry data for robot {}", packet.getSender());
    }

    public void processAndSaveImage(CommBaseProto.BasePacket packet) throws IOException {
        String robotId = packet.getSender();
        CommBaseProto.ImageBody image = packet.getImage();
        long currentTime = System.currentTimeMillis();

        // --- [新增] 3秒限流逻辑 ---
        // 获取该机器人上次保存的时间
//        String throttleKey = robotId + ":" + image.getSessionId();
//        Long lastTime = lastImageSaveTimeMap.get(throttleKey);

//        // 如果上次保存过，且距离现在不足 3 秒，则直接返回，不进行存储
//        if (lastTime != null && (currentTime - lastTime) < SAVE_INTERVAL_MS) {
//            return;
//        }

        // 更新该机器人的最后保存时间
//        lastImageSaveTimeMap.put(throttleKey, currentTime);
        // ------------------------

        if(!"jpeg".equals(image.getFormat())){
            // 这里的逻辑原代码为空，根据需要处理
        }
        String fileName = String.format("%s-%s.%s", packet.getSender(), UUID.randomUUID(), image.getFormat());
        Path filePath = Paths.get(imageBasePath, fileName);

        Files.createDirectories(filePath.getParent());
        Files.write(filePath, image.getData().toByteArray(), StandardOpenOption.CREATE);

        ImageEntity entity = new ImageEntity();
        entity.setRobotId(packet.getSender());
        entity.setSessionId(image.getSessionId());
        entity.setFormat(image.getFormat());
        entity.setWidth(image.getWidth());
        entity.setSessionId(packet.getFrame());
        entity.setHeight(image.getHeight());
        entity.setFilePath(filePath.toString());
        entity.setTimestamp(toLocalDateTime(packet.getSecond(), packet.getNanosecond()));
        entity.setReceptionCount(dataFlowControlService.getCurrentCount());

        imageMapper.insertImage(entity);
        //log.info("Saved image file {} and metadata for robot {}", filePath, packet.getSender());
    }
    
    public void processAndSavePointCloud(CommBaseProto.BasePacket packet) throws IOException {
        CommBaseProto.PointCloudXYZI pcl = packet.getPclXyzi();
        String fileName = String.format("%s-%s.laz", packet.getSender(), UUID.randomUUID());
        Path filePath = Paths.get(pointCloudBasePath, fileName);
        
        Files.createDirectories(filePath.getParent());
        byte[] dummyLazData = "This is a placeholder for LAZ data".getBytes();
        Files.write(filePath, dummyLazData, StandardOpenOption.CREATE);

        PointCloudEntity entity = new PointCloudEntity();
        entity.setRobotId(packet.getSender());
        entity.setFrameId(packet.getFrame());
        entity.setFilePath(filePath.toString());
        entity.setPointCount((long) pcl.getPointsCount());
        entity.setTimestamp(toLocalDateTime(packet.getSecond(), packet.getNanosecond()));
        entity.setReceptionCount(dataFlowControlService.getCurrentCount());

        pointCloudMapper.insertPointCloud(entity);
        log.info("Saved point cloud file {} and metadata for robot {}", filePath, packet.getSender());
    }
    // --- [新增] ---
    /**
     * 【已修改】处理并保存聚合后的点云数据，能智能判断并写入 XYZI 或 XYZRGB 格式的 .ply 文件。
     * @param robotId 机器人ID。
     * @param frameId 帧ID (例如 "强度点云" 或 "彩色点云")。
     * @param packets 在10秒窗口内收集到的所有点云数据包。
     * @throws IOException 文件写入异常。
     */
    public void processAndSaveAggregatedPointCloud(String robotId, String frameId, List<CommBaseProto.BasePacket> packets) throws IOException {
        if (packets == null || packets.isEmpty()) {
            return;
        }

        log.info("Aggregating {} point cloud packets for robot '{}', frame '{}'", packets.size(), robotId, frameId);

        // --- 1. 判断点云类型并计算总点数 ---
        boolean isRgbCloud = packets.get(0).getType() == CommBaseProto.PacketType.PACKET_POINTCLOUD_XYZRGB;
        long totalPoints = 0;
        for (CommBaseProto.BasePacket packet : packets) {
            totalPoints += isRgbCloud ? packet.getPclXyzrgb().getPointsCount() : packet.getPclXyzi().getPointsCount();
        }
        log.info("Total points in aggregated cloud: {}. Is RGB: {}", totalPoints, isRgbCloud);

        // --- 2. 创建 .ply 文件并写入 ---
        String fileName = String.format("%s-%s-%s.ply", robotId, frameId.replace(" ", "_"), UUID.randomUUID());
        Path filePath = Paths.get(pointCloudBasePath, fileName);
        Files.createDirectories(filePath.getParent());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
            // --- 写入 .ply 文件头 ---
            writer.write("ply\n");
            writer.write("format ascii 1.0\n");
            writer.write("element vertex " + totalPoints + "\n");
            writer.write("property float x\n");
            writer.write("property float y\n");
            writer.write("property float z\n");
            if (isRgbCloud) {
                writer.write("property uchar red\n");
                writer.write("property uchar green\n");
                writer.write("property uchar blue\n");
            } else {
                writer.write("property float intensity\n");
            }
            writer.write("end_header\n");

            // --- 3. 逐一写入点数据 ---
            for (CommBaseProto.BasePacket packet : packets) {
                if (isRgbCloud) {
                    for (CommBaseProto.PointXYZRGB grpcPoint : packet.getPclXyzrgb().getPointsList()) {
                        writer.write(String.format(Locale.US, "%.4f %.4f %.4f %d %d %d\n",
                                grpcPoint.getX(), grpcPoint.getY(), grpcPoint.getZ(),
                                grpcPoint.getR(), grpcPoint.getG(), grpcPoint.getB()));
                    }
                } else {
                    for (CommBaseProto.PointXYZI grpcPoint : packet.getPclXyzi().getPointsList()) {
                        writer.write(String.format(Locale.US, "%.4f %.4f %.4f %.4f\n",
                                grpcPoint.getX(), grpcPoint.getY(), grpcPoint.getZ(), grpcPoint.getIntensity()));
                    }
                }
            }
        }

        // --- 4. 在数据库中创建记录 ---
        PointCloudEntity entity = new PointCloudEntity();
        entity.setRobotId(robotId);
        entity.setFrameId(frameId); // 使用正确的 frameId
        entity.setFilePath(filePath.toString());
        entity.setPointCount(totalPoints);
        entity.setTimestamp(LocalDateTime.now());
        entity.setReceptionCount(dataFlowControlService.getCurrentCount());

        pointCloudMapper.insertPointCloud(entity);

        log.info("Successfully saved aggregated point cloud file {} for robot '{}'", filePath, robotId);
    }
    private LocalDateTime toLocalDateTime(double seconds, long nanos) {
        long epochSecond = (long) seconds;
        long nanoOfSecond = (long) ((seconds - epochSecond) * 1_000_000_000) + nanos;
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond, nanoOfSecond), ZoneId.systemDefault());
    }
}