package com.whu.yun.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.whu.yun.config.AppProperties;

import com.whu.yun.dto.DetectionResultDto;
import com.whu.yun.entity.DetectionResultEntity;
import com.whu.yun.mapper.DetectionResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DetectionService {

    private final AppProperties appProperties;
    private final DetectionResultMapper detectionResultMapper;
    private final ObjectMapper objectMapper; // Spring Boot 自动配置了 Jackson 的 ObjectMapper

    public DetectionResultDto detectObjects(MultipartFile imageFile) throws IOException, InterruptedException {
        // 1. 保存上传的原始图片
        String originalFilename = saveUploadedFile(imageFile);
        Path originalFilePath = Paths.get(appProperties.getFileStorage().getImagePath(), originalFilename);

        // 2. 准备并执行 Python 脚本
        log.info("准备执行 Python 目标检测脚本...");
        CommandLine cmdLine = new CommandLine(appProperties.getPython().getInterpreterPath());
        cmdLine.addArgument(appProperties.getPython().getDetectionScriptPath());
        cmdLine.addArgument("--image_path");
        cmdLine.addArgument(originalFilePath.toAbsolutePath().toString());
        cmdLine.addArgument("--model_path");
        cmdLine.addArgument(appProperties.getPython().getModelWeightPath());

        // 用于捕获 Python 脚本的标准输出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(new PumpStreamHandler(outputStream));

        int exitCode = executor.execute(cmdLine);
        if (exitCode != 0) {
            log.error("Python 脚本执行失败，退出码: {}. 输出: {}", exitCode, outputStream.toString());
            throw new RuntimeException("目标检测脚本执行失败。");
        }

        // 3. 解析 Python 脚本返回的 JSON 结果
        String jsonOutput = outputStream.toString();
        List<DetectionResultDto.Detection> detections = objectMapper.readValue(jsonOutput, new TypeReference<List<DetectionResultDto.Detection>>(){});
        log.info("从 Python 脚本成功解析出 {} 个目标。", detections.size());

        // 4. 在图片上绘制结果并保存
        String annotatedImageName = "annotated_" + originalFilename;
        Path annotatedImagePath = Paths.get(appProperties.getFileStorage().getAnnotatedImagePath(), annotatedImageName);
        drawAndSaveDetections(originalFilePath, annotatedImagePath, detections);

        // 5. 调用 Python 脚本生成 PDF 报告并转换为 PNG
        log.info("准备执行 Python 报告生成脚本...");
        String reportImageName = generateReportAndGetPngName(originalFilename, originalFilePath, annotatedImagePath, detections);

        // 6. 将检测结果存入数据库
        saveDetectionsToDb(originalFilename, originalFilePath, annotatedImagePath, detections);

        // 7. 准备并返回给前端的数据
        return new DetectionResultDto(originalFilename, annotatedImageName, reportImageName, detections);
    }

    private String saveUploadedFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名不能为空。");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String newFilename = UUID.randomUUID().toString() + extension;

        Path targetPath = Paths.get(appProperties.getFileStorage().getImagePath(), newFilename);
        Files.createDirectories(targetPath.getParent());
        file.transferTo(targetPath.toFile());
        log.info("上传的图片已保存至: {}", targetPath);
        return newFilename;
    }

    private void drawAndSaveDetections(Path sourcePath, Path destPath, List<DetectionResultDto.Detection> detections) throws IOException {
        BufferedImage image = ImageIO.read(sourcePath.toFile());
        Graphics2D g = image.createGraphics();

        // 设置绘制样式
        g.setStroke(new BasicStroke(2));
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();

        for (DetectionResultDto.Detection det : detections) {
            List<Integer> box = det.getBox();
            int x = box.get(0);
            int y = box.get(1);
            int width = box.get(2) - x;
            int height = box.get(3) - y;
            String label = String.format("%s: %.2f", det.getClassName(), det.getConfidence());
            int textWidth = fm.stringWidth(label);

            // 绘制边界框 (绿色)
            g.setColor(Color.GREEN);
            g.drawRect(x, y, width, height);

            // 绘制文字背景 (绿色)
            g.fillRect(x, y - fm.getHeight(), textWidth + 4, fm.getHeight());

            // 绘制文字 (红色)
            g.setColor(Color.RED);
            g.drawString(label, x + 2, y - fm.getDescent());
        }
        g.dispose();

        Files.createDirectories(destPath.getParent());
        ImageIO.write(image, "jpeg", destPath.toFile());
        log.info("带标注的图片已保存至: {}", destPath);
    }

    private void saveDetectionsToDb(String originalName, Path originalPath, Path annotatedPath, List<DetectionResultDto.Detection> detections) {
        LocalDateTime now = LocalDateTime.now();
        for (DetectionResultDto.Detection det : detections) {
            DetectionResultEntity entity = new DetectionResultEntity();
            entity.setSourceImageName(originalName);
            entity.setSourceImagePath(originalPath.toString());
            entity.setAnnotatedImagePath(annotatedPath.toString());
            entity.setClassName(det.getClassName());
            entity.setConfidence(det.getConfidence());
            List<Integer> box = det.getBox();
            entity.setBoxXmin(box.get(0));
            entity.setBoxYmin(box.get(1));
            entity.setBoxXmax(box.get(2));
            entity.setBoxYmax(box.get(3));
            entity.setDetectionTime(now);
            detectionResultMapper.insert(entity);
        }
        log.info("已将 {} 条检测结果存入数据库。", detections.size());
    }

    private String generateReportAndGetPngName(String originalFilename, Path originalFilePath, Path annotatedImagePath, List<DetectionResultDto.Detection> detections) throws IOException, InterruptedException {
        String imageStem = originalFilename.substring(0, originalFilename.lastIndexOf("."));
        String reportPdfName = imageStem + "_report.pdf";
        String reportPngName = imageStem + "_report.png";

        Path reportOutputDir = Paths.get(appProperties.getFileStorage().getReportImagePath());
        Files.createDirectories(reportOutputDir); // 确保报告输出目录存在

        CommandLine cmdLine = new CommandLine(appProperties.getPython().getInterpreterPath());
        cmdLine.addArgument(appProperties.getPython().getReportScriptPath()); // 假设 reportScriptPath 是 generate_pdf_report.py 的路径
        cmdLine.addArgument("--image_path");
        cmdLine.addArgument(originalFilePath.toAbsolutePath().toString());
        cmdLine.addArgument("--model_path");
        cmdLine.addArgument(appProperties.getPython().getModelWeightPath());
        cmdLine.addArgument("--output_dir");
        cmdLine.addArgument(reportOutputDir.toAbsolutePath().toString());
        cmdLine.addArgument("--conf");
        cmdLine.addArgument(String.valueOf(appProperties.getPython().getConfidenceThreshold())); // 使用配置的置信度阈值

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream(); // 捕获错误输出
        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(new PumpStreamHandler(outputStream, errorStream));

        int exitCode = executor.execute(cmdLine);
        if (exitCode != 0) {
            String errorOutput = errorStream.toString();
            log.error("Python 报告生成脚本执行失败，退出码: {}. 错误输出: {}", exitCode, errorOutput);
            throw new RuntimeException("报告生成脚本执行失败: " + errorOutput);
        }

        String jsonOutput = outputStream.toString();
        log.info("Python 报告生成脚本返回: {}", jsonOutput);

        try {
            // 解析 Python 脚本返回的 JSON 结果
            JsonNode rootNode = objectMapper.readTree(jsonOutput);
            String status = rootNode.get("status").asText();
            if ("success".equals(status)) {
                return rootNode.get("reportImageName").asText();
            } else {
                String errorMessage = rootNode.get("message").asText();
                throw new RuntimeException("报告生成失败: " + errorMessage);
            }
        } catch (Exception e) {
            log.error("解析 Python 报告脚本输出失败: {}", e.getMessage());
            throw new RuntimeException("解析报告脚本输出失败。", e);
        }
    }
}
