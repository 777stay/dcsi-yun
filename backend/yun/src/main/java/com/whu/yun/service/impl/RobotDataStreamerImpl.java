package com.whu.yun.service.impl;


import com.example.robotbackend.comm.CommBaseProto;
import com.example.robotbackend.comm.RobotDataStreamerGrpc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicInteger;

@GrpcService
@RequiredArgsConstructor
public class RobotDataStreamerImpl extends RobotDataStreamerGrpc.RobotDataStreamerImplBase {

    private static final Logger logger = LoggerFactory.getLogger(RobotDataStreamerImpl.class);
    // 注入 Spring 提供的 RocketMQ 操作模板
    private final RocketMQTemplate rocketMQTemplate;

    // 定义 RocketMQ 的主题 (Topic)，所有机器人数据都将发送到这个主题
    public static final String TOPIC_ROBOT_DATA = "topic-robot-data";
    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.topic}")
    private String topic;

    private DefaultMQProducer producer;

    @PostConstruct
    public void init() throws Exception {
        // 初始化 RocketMQ 生产者
        producer = new DefaultMQProducer("grpc_server_producer_group");
        producer.setNamesrvAddr(nameServer);
        producer.start();
        logger.info("RocketMQ Producer started.");
    }

    @PreDestroy
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
            logger.info("RocketMQ Producer shut down.");
        }
    }

    @Override
    public StreamObserver<CommBaseProto.BasePacket> streamData(StreamObserver<CommBaseProto.StatusReply> responseObserver) {
        return new StreamObserver<CommBaseProto.BasePacket>() {
            private final AtomicInteger packetCount = new AtomicInteger(0);

            @Override
            public void onNext(CommBaseProto.BasePacket packet) {
                packetCount.incrementAndGet();
                logger.debug("Received packet from '{}', seq: {}. Forwarding to RocketMQ.", packet.getSender(), packet.getSeq());

                // 将接收到的数据包转发到 RocketMQ
                forwardToRocketMQ(packet);
                try {
                    String payload = JsonFormat.printer().print(packet);

                    // 2. 将消息发送到 RocketMQ 的指定主题
                    rocketMQTemplate.convertAndSend(TOPIC_ROBOT_DATA, payload);
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {
                logger.info("Client stream completed. Total packets received: {}", packetCount.get());
                CommBaseProto.StatusReply reply = CommBaseProto.StatusReply.newBuilder()
                        .setSuccess(true)
                        .setMessage("Stream processed successfully. Received " + packetCount.get() + " packets.")
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            }
        };
    }

    private void forwardToRocketMQ(CommBaseProto.BasePacket packet) {
        // 根据 PacketType 设置 Tag，用于后续过滤
        String tag = packet.getType().name();

        try {
            // 将 protobuf 对象序列化为字节数组
            byte[] body = packet.toByteArray();
            // 使用 sender (robot_id) 作为消息的 Key
            Message msg = new Message(topic, tag, packet.getSender(), body);

            // 异步发送，避免阻塞gRPC线程
            producer.send(msg, new org.apache.rocketmq.client.producer.SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                     logger.debug("Sent message to RocketMQ successfully. Tag: {}, MsgId: {}", tag, sendResult.getMsgId());
                }
                @Override
                public void onException(Throwable e) {
                     logger.error("Failed to send message to RocketMQ for robot: {}", packet.getSender(), e);
                }
            });

        } catch (Exception e) {
            logger.error("Exception while sending message to RocketMQ", e);
        }
    }
}