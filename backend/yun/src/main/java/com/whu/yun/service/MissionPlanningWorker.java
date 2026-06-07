package com.whu.yun.service;

import com.whu.yun.entity.MissionPlannerRequested;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.concurrent.Future;

// 【新增】这是一个独立的接口
public interface MissionPlanningWorker {

    /**
     * 这是真正的异步方法，所有耗时操作都在这里执行。
     * @param request 前端传来的任务规划请求。
     * @param taskId 本次任务的唯一ID。
     * @return 一个 Future 对象，代表未来的计算结果。
     */
    @Async("taskExecutor")
    Future<List<List<double[]>>> executePlanningPipelineAsync(MissionPlannerRequested request, String taskId);
}