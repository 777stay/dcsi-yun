package com.whu.yun.controller;

import com.whu.yun.dto.ApiResponse;
import com.whu.yun.dto.FusionRunRequest;
import com.whu.yun.dto.FusionRunResult;
import com.whu.yun.service.FusionPotreeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fusion")
@CrossOrigin
@RequiredArgsConstructor
public class FusionPotreeController {

    private final FusionPotreeService fusionPotreeService;

    @GetMapping("/result")
    public ApiResponse<FusionRunResult> getResult(@RequestParam(required = false) String dataset) throws Exception {
        return ApiResponse.success(fusionPotreeService.getDefaultResult(dataset));
    }

    @PostMapping("/run")
    public ApiResponse<FusionRunResult> run(@RequestBody(required = false) FusionRunRequest request) throws Exception {
        return ApiResponse.success(fusionPotreeService.runFusion(request));
    }
}
