package com.whu.yun.service;


import com.example.robotbackend.comm.CommBaseProto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 这个服务专门负责聚合点云数据。
 * 它在内存中缓冲点云帧，并使用定时任务每10秒将聚合后的数据进行一次存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointCloudAggregationService {

    private final DataProcessingService dataProcessingService;
    // --- [新增] --- 注入我们自定义的线程池
    @Qualifier("pointCloudAggregationExecutor")
    private final Executor taskExecutor;
    // 使用 ConcurrentHashMap 作为线程安全的缓冲区。
    // Key: robotId, Value: 该机器人在当前时间窗口内发送的点云帧列表。
    private final Map<String, List<CommBaseProto.BasePacket>> pointCloudBuffer = new ConcurrentHashMap<>();

    /**
     * 将一个点云帧添加到正确的缓冲区。
     * @param packet 包含点云数据的 BasePacket。
     */
    public void addPointCloudFrame(CommBaseProto.BasePacket packet) {
        // --- [核心修改] ---
        // 检查包的类型，确保它确实是点云
        CommBaseProto.PacketType type = packet.getType();
        if (type != CommBaseProto.PacketType.PACKET_POINTCLOUD_XYZI && type != CommBaseProto.PacketType.PACKET_POINTCLOUD_XYZRGB) {
            return; // 如果不是点云包，则忽略
        }

        // 使用 "robotId::frameId" 作为唯一的键
        String key = packet.getSender() + "::" + packet.getFrame();

        // 将完整的 packet 添加到对应的缓冲区
        pointCloudBuffer.computeIfAbsent(key, k -> new ArrayList<>()).add(packet);
    }

    /**
     * 定时任务，每30秒执行一次。
     */
    @Async
    @Scheduled(fixedRate = 30000)
    public void processBufferedPointClouds() {
        log.info("Scheduler: Triggering processing for buffered point clouds...");
        if (pointCloudBuffer.isEmpty()) {
            log.info("Scheduler: No point clouds in buffer to process.");
            return;
        }

        // 遍历缓冲区中的每一个分类 (每个 key 代表一个机器人的一个 frame 类型)
        pointCloudBuffer.forEach((key, packets) -> {
            List<CommBaseProto.BasePacket> packetsToProcess;
            synchronized (packets) {
                if (packets.isEmpty()) {
                    return;
                }
                packetsToProcess = new ArrayList<>(packets);
                packets.clear();
            }

            // --- [核心修改] ---
            // 从组合键中解析出 robotId 和 frameId
            String[] parts = key.split("::");
            if (parts.length != 2) {
                log.error("Invalid buffer key format: {}", key);
                return;
            }
            String robotId = parts[0];
            String frameId = parts[1];

            // 将任务提交到线程池，并传入解析出的 robotId 和 frameId
            taskExecutor.execute(() -> {
                try {
                    log.info("Executor: Starting aggregation task for robot '{}', frame '{}'", robotId, frameId);
                    dataProcessingService.processAndSaveAggregatedPointCloud(robotId, frameId, packetsToProcess);
                } catch (IOException e) {
                    log.error("Executor: Failed to process aggregated point cloud for robot '{}', frame '{}'", robotId, frameId, e);
                }
            });
        });
    }
}
