package com.whu.yun.service;

import com.example.robotbackend.comm.CommBaseProto;

import com.whu.yun.handler.RobotWebSocketHandler;
import com.whu.yun.service.DataProcessingService;
import com.whu.yun.service.PointCloudAggregationService; // --- [新增] 导入聚合服务
import com.whu.yun.service.RobotOdometryCacheService;
import com.google.protobuf.util.JsonFormat;
import com.whu.yun.service.impl.RobotDataStreamerImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RobotDataStreamerImpl.TOPIC_ROBOT_DATA, consumerGroup = "robot-data-consumer-group")
public class RobotDataConsumer implements RocketMQListener<String> {

    private final DataProcessingService dataProcessingService;
    private final StringRedisTemplate redisTemplate;
    private final RobotWebSocketHandler webSocketHandler;
    private final MyBatisDataFlowControlService dataFlowControlService;
    private final PointCloudAggregationService aggregationService; // --- [新增] 注入聚合服务
    private final RobotOdometryCacheService odometryCacheService;

    @Override
    public void onMessage(String message) {
        while (!dataFlowControlService.isProcessingEnabled()) {
            try {
                // 打印一条日志，说明消费已暂停
                log.info("数据处理功能已禁用，消费线程暂停1秒...");
                // 让当前消费线程休眠1秒，避免空转消耗过多CPU
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // 如果线程在休眠时被中断，恢复中断状态并退出
                Thread.currentThread().interrupt();
                log.warn("消费线程在暂停期间被中断。");
                return;
            }
        }
        try {
            CommBaseProto.BasePacket.Builder builder = CommBaseProto.BasePacket.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(message, builder);
            CommBaseProto.BasePacket packet = builder.build();

            // --- [开始修改] ---
            // 根据包类型进行分发处理
            switch (packet.getType()) {
                case PACKET_POINTCLOUD_XYZI:
                case PACKET_POINTCLOUD_XYZRGB: // 同样可以处理 XYZRGB 点云
                    // 如果是点云数据，交给聚合服务进行缓冲
                    aggregationService.addPointCloudFrame(packet);
                    break;
                case PACKET_ODOM:
                    // 其他类型的数据，像以前一样立即处理
                    odometryCacheService.update(packet.getSender(), packet.getOdom().getX(), packet.getOdom().getY());
                    dataProcessingService.processAndSaveOdometry(packet);
                    break;
                case PACKET_IMAGE:
                    dataProcessingService.processAndSaveImage(packet);
                    break;
                default:
                    //log.warn("MQ-Consumer: Received unhandled packet type: {}", packet.getType());
            }
            // --- [结束修改] ---

            // 实时数据推送和缓存更新逻辑保持不变
            String robotId = packet.getSender();
            String redisKey = "robot:latest_data:" + robotId;
            redisTemplate.opsForValue().set(redisKey, message);
            webSocketHandler.broadcastToRobot(robotId, message);

        } catch (Exception e) {
            log.error("MQ-Consumer: Failed to process message from MQ: {}", message, e);
        }
    }
}
