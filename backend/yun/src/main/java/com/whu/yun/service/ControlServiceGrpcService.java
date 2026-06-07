package com.whu.yun.service;
import com.example.robotbackend.robot_control.ControlServiceGrpc;
import com.example.robotbackend.robot_control.RobotControlProto;
import com.whu.yun.service.RobotControlService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * 这个 gRPC 服务实现了 robot_control.proto 中定义的 ControlService。
 * 它作为一个轻量级的入口点，将所有复杂的双向流处理逻辑委托给 RobotControlService。
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ControlServiceGrpcService extends ControlServiceGrpc.ControlServiceImplBase {

    private final RobotControlService robotControlService;

    /**
     * 当一个机器人客户端发起 StreamRobotData RPC 调用时，此方法被触发。
     * @param responseObserver 用于向客户端发送指令的流。
     * @return 返回一个用于接收客户端状态数据的流。
     */
    @Override
    public StreamObserver<RobotControlProto.RobotData> streamRobotData(StreamObserver<RobotControlProto.Command> responseObserver) {
        // 直接将处理权交给核心服务
        return robotControlService.handleRobotStream(responseObserver);
    }
}
