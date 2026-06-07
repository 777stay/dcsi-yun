package com.whu.yun.service;


import com.example.robotbackend.comm.CommBaseProto;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

@Service
public class PointCloudConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(PointCloudConsumerService.class);

    @Value("${rocketmq.name-server}")
    private String nameServer;
    @Value("${rocketmq.topic}")
    private String topic;
    @Value("${rocketmq.consumer.group}")
    private String consumerGroup;

    @Autowired
    private PointCloudProcessor pointCloudProcessor;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void init() throws Exception {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);

        // 订阅 Topic，并使用 Tag 进行过滤
        // 我们只关心这两种点云数据
        final String POINT_CLOUD_TAGS = "PACKET_POINTCLOUD_XYZI || PACKET_POINTCLOUD_XYZRGB";
        consumer.subscribe(topic, POINT_CLOUD_TAGS);

        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                for (MessageExt msg : msgs) {
                    try {
                        // 将消息体反序列化回 BasePacket 对象
                        CommBaseProto.BasePacket packet = CommBaseProto.BasePacket.parseFrom(msg.getBody());
                        logger.debug("Received point cloud message. Robot: {}, Frame: {}, Seq: {}",
                                packet.getSender(), packet.getFrame(), packet.getSeq());
                        
                        // 将消息交给处理器进行缓存
                        pointCloudProcessor.addPacket(packet);

                    } catch (InvalidProtocolBufferException e) {
                        logger.error("Failed to parse message body.", e);
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        consumer.start();
        logger.info("RocketMQ Consumer started. Subscribing to tags: {}", POINT_CLOUD_TAGS);
    }

    @PreDestroy
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}