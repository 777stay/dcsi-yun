// File: src/main/java/com/whu/yun/service/RobotControlService.java
package com.whu.yun.service;


import com.example.robotbackend.robot_control.RobotControlProto;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotControlService {

    private final GlobalRobotStatusService globalStatusService;
    private final Map<String, StreamObserver<RobotControlProto.Command>> outboundCommandStreams = new ConcurrentHashMap<>();

    public StreamObserver<RobotControlProto.RobotData> handleRobotStream(StreamObserver<RobotControlProto.Command> responseObserver) {
        return new StreamObserver<RobotControlProto.RobotData>() {
            private String robotId = null;

            @Override
            public void onNext(RobotControlProto.RobotData robotData) {
                if (this.robotId == null) {
                    this.robotId = robotData.getRobotId();
                    log.info("Robot '{}' connected to control stream. Marking as ONLINE.", this.robotId);
                    outboundCommandStreams.put(this.robotId, responseObserver);
                }

                // --- [修改] --- 调用全局服务更新状态，并标记为在线
                globalStatusService.updateRobotStatus(robotData);
            }

            @Override
            public void onError(Throwable t) {
                log.error("Control stream error for robot '{}': {}. Marking as OFFLINE.", this.robotId, t.getMessage());
                if (this.robotId != null) {
                    outboundCommandStreams.remove(this.robotId);
                    // --- [修改] --- 调用全局服务标记为离线
                    globalStatusService.markRobotAsOffline(this.robotId);
                }
            }

            @Override
            public void onCompleted() {
                log.info("Robot '{}' disconnected from control stream. Marking as OFFLINE.", this.robotId);
                if (this.robotId != null) {
                    outboundCommandStreams.remove(this.robotId);
                    responseObserver.onCompleted();
                    // --- [修改] --- 调用全局服务标记为离线
                    globalStatusService.markRobotAsOffline(this.robotId);
                }
            }
        };
    }

    public boolean sendCommand(String robotId, RobotControlProto.Command command) {
        StreamObserver<RobotControlProto.Command> observer = outboundCommandStreams.get(robotId);
        if (observer != null) {
            try {
                observer.onNext(command);
                log.info("Successfully sent command '{}' to robot '{}'", command.getCommandType(), robotId);
                return true;
            } catch (Exception e) {
                log.error("Failed to send command to robot '{}'", robotId, e);
                outboundCommandStreams.remove(robotId);
                return false;
            }
        } else {
            log.warn("Attempted to send command to offline or unknown robot '{}'", robotId);
            return false;
        }
    }
}
