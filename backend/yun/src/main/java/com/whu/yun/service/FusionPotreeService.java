package com.whu.yun.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.yun.dto.FusionRunRequest;
import com.whu.yun.dto.FusionRunResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FusionPotreeService {

    private static final String RESULT_PREFIX = "FUSION_RESULT_JSON=";

    private final ObjectMapper objectMapper;

    @Value("${fusion.workspace-root}")
    private String workspaceRoot;

    @Value("${fusion.default-dataset}")
    private String defaultDataset;

    @Value("${fusion.runner-path}")
    private String runnerPath;

    @Value("${fusion.script-path}")
    private String fusionScriptPath;

    @Value("${fusion.public-prefix:dist/data}")
    private String publicPrefix;

    @Value("${python.path:python3}")
    private String pythonExecutable;

    @Value("${python.script.path:scripts/converter.py}")
    private String converterScriptPath;

    @Value("${potree.converter.path}")
    private String potreeConverterPath;

    @Value("${potree.metajsonUrl.path}")
    private String potreeOutputPath;

    @Value("${fusion.docker.enabled:false}")
    private boolean dockerEnabled;

    @Value("${fusion.docker.command:sudo docker}")
    private String dockerCommand;

    @Value("${fusion.docker.image:yun-deploy-backend-service:latest}")
    private String dockerImage;

    @Value("${fusion.docker.workspace-root:/app/change_potree_data}")
    private String dockerWorkspaceRoot;

    @Value("${fusion.docker.potree-output:/app/dist/data}")
    private String dockerPotreeOutputPath;

    @Value("${fusion.docker.runner-path:/app/scripts/fusion_runner.py}")
    private String dockerRunnerPath;

    @Value("${fusion.docker.fusion-script:/app/change_potree_data/fuse_cloud_instances_3d_multi_uav.py}")
    private String dockerFusionScriptPath;

    @Value("${fusion.docker.converter-script:/app/scripts/converter.py}")
    private String dockerConverterScriptPath;

    @Value("${fusion.docker.potree-converter:/app/yun/PotreeConverter/build/PotreeConverter}")
    private String dockerPotreeConverterPath;

    public FusionRunResult runFusion(FusionRunRequest request) throws Exception {
        String dataset = resolveDataset(request == null ? null : request.getDataset());
        Integer imageStride = request == null ? null : request.getImageStride();
        Integer maxImages = request == null ? null : request.getMaxImages();

        List<String> command = new ArrayList<>();
        if (dockerEnabled) {
            buildDockerCommand(command, dataset, imageStride, maxImages);
        } else {
            command.add(pythonExecutable);
            command.add(runnerPath);
            addRunnerArgs(
                    command,
                    workspaceRoot,
                    dataset,
                    fusionScriptPath,
                    converterScriptPath,
                    potreeConverterPath,
                    potreeOutputPath,
                    imageStride,
                    maxImages
            );
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(System.getProperty("user.dir")));
        processBuilder.redirectErrorStream(true);

        StringBuilder logBuilder = new StringBuilder();
        String resultJson = null;
        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logBuilder.append(line).append('\n');
                if (line.startsWith(RESULT_PREFIX)) {
                    resultJson = line.substring(RESULT_PREFIX.length());
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("融合算法执行失败，退出码: " + exitCode + "\n" + logBuilder);
        }
        if (!StringUtils.hasText(resultJson)) {
            throw new IllegalStateException("融合算法未返回结果 JSON\n" + logBuilder);
        }

        FusionRunResult result = objectMapper.readValue(resultJson, FusionRunResult.class);
        result.setLog(logBuilder.toString());
        return result;
    }

    private void buildDockerCommand(List<String> command, String dataset, Integer imageStride, Integer maxImages) {
        for (String token : dockerCommand.trim().split("\\s+")) {
            if (StringUtils.hasText(token)) {
                command.add(token);
            }
        }
        command.add("run");
        command.add("--rm");
        command.add("--entrypoint");
        command.add(pythonExecutable);
        command.add("-v");
        command.add(resolvePath(runnerPath) + ":" + dockerRunnerPath + ":ro");
        command.add("-v");
        command.add(workspaceRoot + ":" + dockerWorkspaceRoot);
        command.add("-v");
        command.add(potreeOutputPath + ":" + dockerPotreeOutputPath);
        command.add(dockerImage);
        command.add(dockerRunnerPath);
        addRunnerArgs(
                command,
                dockerWorkspaceRoot,
                dataset,
                dockerFusionScriptPath,
                dockerConverterScriptPath,
                dockerPotreeConverterPath,
                dockerPotreeOutputPath,
                imageStride,
                maxImages
        );
    }

    private void addRunnerArgs(
            List<String> command,
            String workspace,
            String dataset,
            String fusionScript,
            String converterScript,
            String potreeConverter,
            String potreeOutput,
            Integer imageStride,
            Integer maxImages
    ) {
        command.add("--workspace-root");
        command.add(workspace);
        command.add("--dataset");
        command.add(dataset);
        command.add("--fusion-script");
        command.add(fusionScript);
        command.add("--converter-script");
        command.add(converterScript);
        command.add("--potree-converter");
        command.add(potreeConverter);
        command.add("--potree-output");
        command.add(potreeOutput);
        command.add("--public-prefix");
        command.add(publicPrefix);
        command.add("--image-stride");
        command.add(String.valueOf(imageStride == null || imageStride < 1 ? 1 : imageStride));
        if (maxImages != null && maxImages > 0) {
            command.add("--max-images");
            command.add(String.valueOf(maxImages));
        }
    }

    private String resolvePath(String path) {
        Path resolved = Paths.get(path);
        if (!resolved.isAbsolute()) {
            resolved = Paths.get(System.getProperty("user.dir"), path);
        }
        return resolved.normalize().toString();
    }

    public FusionRunResult getDefaultResult(String dataset) throws Exception {
        String resolvedDataset = resolveDataset(dataset);
        String datasetName = resolvedDataset + "_fused";
        Path metadataPath = Paths.get(potreeOutputPath, datasetName, "pointclouds", datasetName, "metadata.json");
        FusionRunResult result = new FusionRunResult();
        result.setDataset(resolvedDataset);
        result.setName(resolvedDataset + " fused");
        result.setUrl(publicPrefix.replaceAll("/$", "") + "/" + datasetName + "/pointclouds/" + datasetName + "/metadata.json");
        result.setLog(Files.exists(metadataPath) ? "metadata exists" : "metadata not found: " + metadataPath);
        return result;
    }

    private String resolveDataset(String dataset) {
        if (StringUtils.hasText(dataset)) {
            return dataset.trim();
        }
        return defaultDataset;
    }
}
