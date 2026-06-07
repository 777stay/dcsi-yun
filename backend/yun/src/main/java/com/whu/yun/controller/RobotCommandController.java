package com.whu.yun.controller;

import com.example.robotbackend.robot_control.RobotControlProto;
import com.whu.yun.dto.ApiResponse;

import com.whu.yun.service.RobotControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/command")
@RequiredArgsConstructor
public class RobotCommandController {

    private final RobotControlService robotControlService;

    /**
     * 向指定的机器人发送一条指令。
     * @param robotId 目标机器人的ID。
     * @param commandName 命令的名称，与 .proto 文件中的枚举名一致。
     * @return 操作结果。
     */
    @PostMapping("/{robotId}/{commandName}")
    public ApiResponse<String> sendCommandToRobot(
            @PathVariable String robotId,
            @PathVariable String commandName) {

        try {
            // 1. 将 URL 中的字符串命令转换为 Protobuf 枚举
            RobotControlProto.Command.CommandType commandTypeEnum =
                    RobotControlProto.Command.CommandType.valueOf(commandName);

            // 2. 构建 Protobuf Command 对象
            RobotControlProto.Command command = RobotControlProto.Command.newBuilder()
                    .setCommandId(UUID.randomUUID().toString())
                    .setTargetRobotId(robotId)
                    .setCommandType(commandTypeEnum)
                    .build();

            // 3. 调用核心服务发送指令
            boolean success = robotControlService.sendCommand(robotId, command);

            if (success) {
                return ApiResponse.success("Command '" + commandName + "' sent successfully.");
            } else {
                return ApiResponse.error(404, "Failed to send command. Robot may be offline.");
            }

        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, "Invalid command type: " + commandName);
        }
    }
}
