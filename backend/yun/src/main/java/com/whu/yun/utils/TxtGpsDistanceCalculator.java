package com.whu.yun.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Component
public class TxtGpsDistanceCalculator {
    // 地球半径（单位：米，赤道半径，适用于大多数场景）
    private static final double EARTH_RADIUS = 6371000;

    /**
     * 步骤1：读取 .txt 文件内容（支持 UTF-8 编码，避免中文/特殊字符乱码）
     * @param filePath .txt 文件的路径（如 "D:/data/gps.txt" 或 "/home/user/gps.txt"）
     * @return 文件的所有行内容（每行一个字符串）
     * @throws IOException 文件不存在、权限不足等IO异常
     */
    public static List<String> readTxtFile(String filePath) throws IOException {
        List<String> fileLines = new ArrayList<>();
        // 使用 BufferedReader 高效读取文本，指定 UTF-8 编码
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {
            String line;
            // 逐行读取，直到文件末尾（readLine() 返回 null 表示结束）
            while ((line = br.readLine()) != null) {
                String trimmedLine = line.trim(); // 去除行首尾的空格、换行符
                if (!trimmedLine.isEmpty()) { // 跳过空行（避免解析无效数据）
                    fileLines.add(trimmedLine);
                }
            }
        }
        System.out.println("成功读取 " + fileLines.size() + " 行有效数据");
        return fileLines;
    }

    /**
     * 步骤2：解析 txt 中的每行数据，提取经纬度（第二列=经度，第三列=纬度）
     * @param fileLines 从 txt 读取的所有行
     * @return 经纬度列表（每个元素是 [经度, 纬度]，单位：度）
     */
    public static List<double[]> parseLatLonFromLines(List<String> fileLines) {
        List<double[]> latLonList = new ArrayList<>();
        for (int lineNum = 0; lineNum < fileLines.size(); lineNum++) {
            String line = fileLines.get(lineNum);
            // 按逗号分割每行数据（假设字段中无嵌套逗号，符合 "abc,经度,纬度,..." 格式）
            String[] fields = line.split(",");

            // 校验字段数量：至少需要 3 个字段（第1列=abc，第2列=经度，第3列=纬度）
            if (fields.length < 3) {
                System.out.println("第 " + (lineNum + 1) + " 行格式错误（字段不足），跳过：" + line);
                continue;
            }

            try {
                // 提取第二列（索引1）为经度，第三列（索引2）为纬度（注意顺序：经度在前，纬度在后）
                double longitude = Double.parseDouble(fields[1]); // 经度（如 119.6304）
                double latitude = Double.parseDouble(fields[2]);  // 纬度（如 29.5517）
                latLonList.add(new double[]{longitude, latitude});
            } catch (NumberFormatException e) {
                // 捕获非数字格式的异常（如经纬度列是字符串）
                System.out.println("第 " + (lineNum + 1) + " 行经纬度格式错误，跳过：" + line);
                // 如需调试，可打印异常信息：e.printStackTrace();
            }
        }
        System.out.println("成功解析 " + latLonList.size() + " 组经纬度坐标");
        return latLonList;
    }

    /**
     * 步骤3：角度（Degree）转弧度（Radian）（三角函数计算必须用弧度）
     * @param degree 角度值（如 119.63 度）
     * @return 对应的弧度值
     */
    private static double degreeToRadian(double degree) {
        return degree * Math.PI / 180.0;
    }

    /**
     * 步骤4：用 Haversine 公式计算两点间距离（地球表面最短距离，单位：米）
     * @param pointA 第一点坐标 [经度A, 纬度A]
     * @param pointB 第二点坐标 [经度B, 纬度B]
     * @return 两点间距离（米）；若输入无效，返回 -1
     */
    public static double calculateHaversineDistance(double[] pointA, double[] pointB) {
        // 校验输入：确保两点坐标都包含经纬度（数组长度为2）
        if (pointA == null || pointA.length != 2 || pointB == null || pointB.length != 2) {
            System.out.println("经纬度坐标格式无效，无法计算距离");
            return -1;
        }

        // 将经纬度从角度转为弧度
        double lonA = degreeToRadian(pointA[0]);
        double latA = degreeToRadian(pointA[1]);
        double lonB = degreeToRadian(pointB[0]);
        double latB = degreeToRadian(pointB[1]);

        // 计算纬度差、经度差
        double deltaLat = latB - latA;
        double deltaLon = lonB - lonA;

        // Haversine 公式核心计算
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(latA) * Math.cos(latB)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)); // 计算圆心角

        // 距离 = 地球半径 * 圆心角
        return EARTH_RADIUS * c;
    }

    // 将指定行范围的内容保存为新文件
    // 将指定行范围的内容保存为新文件（按序号命名）
    private static void saveSegmentToFile(List<String> allLines, int start, int end,
                                          String basePath, String baseName, int segmentNumber) throws IOException {
        // 创建分段文件名称（例如：原文件名为data.txt，分段后为data_1.txt, data_2.txt...）
        int dotIndex = baseName.lastIndexOf('.');
        String prefix = (dotIndex > 0) ? baseName.substring(0, dotIndex) : baseName;
        String suffix = (dotIndex > 0) ? baseName.substring(dotIndex) : ".txt";
        // 使用传入的序号作为文件名的一部分，替代原来的结束行号
        String segmentFileName = prefix + "_" + segmentNumber + suffix;
        String segmentPath = basePath + segmentFileName;

        // 写入文件内容
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(segmentPath))) {
            for (int i = start; i <= end; i++) {
                bw.write(allLines.get(i));
                bw.newLine();
            }
        }
        System.out.printf("已生成分段文件：%s（包含第%d至第%d行）%n", segmentPath, start + 1, end + 1);
    }


    // ------------------------------ 测试示例 ------------------------------
    public int calculatorAndSaveTxtFile(String txtFilePath, String txtFileName, int maxDistance) {
        // 处理路径分隔符，确保兼容性
        if (!txtFilePath.endsWith("/") && !txtFilePath.endsWith("\\")) {
            txtFilePath += File.separator;
        }
        String fileAllPath = txtFilePath + txtFileName;

        try {
            // 步骤1：读取txt文件内容
            List<String> fileLines = readTxtFile(fileAllPath);
            if (fileLines.isEmpty()) {
                System.out.println("文件内容为空，无法处理");
                return 0;
            }

            // 步骤2：解析经纬度
            List<double[]> latLonList = parseLatLonFromLines(fileLines);

            int kmlNum = 0;
            int beginIndex = 0; // 当前分段的起始行索引
            double totalDistance = 0.0;

            // 步骤3：计算距离并分段
            if (latLonList.size() >= 2) {
                for (int i = 0; i < latLonList.size() - 1; i++) {
                    double distance = calculateHaversineDistance(latLonList.get(i), latLonList.get(i + 1));

                    if (distance <= 0) { // 跳过无效距离
                        System.out.printf("警告：第%d点与第%d点的距离无效，已跳过%n", i + 1, i + 2);
                        continue;
                    }

                    // 检查是否超过最大距离限制
                    if (totalDistance + distance > maxDistance) {
                        // 保存当前分段（从beginIndex到i行）
                        saveSegmentToFile(fileLines, beginIndex, i, txtFilePath, txtFileName,kmlNum);
                        kmlNum++;
                        // 重置起始点和累计距离
                        beginIndex = i;
                        totalDistance = distance;
                    } else {
                        totalDistance += distance;
                    }
                }

                // 处理最后一段数据
                if (beginIndex < latLonList.size() - 1) {
                    saveSegmentToFile(fileLines, beginIndex, latLonList.size() - 1, txtFilePath, txtFileName,kmlNum);
                    kmlNum++;
                }

                System.out.printf("总分段数量：%d 个%n", kmlNum);
            } else {
                System.out.println("解析的经纬度坐标不足2个，无法计算距离");
            }

            return kmlNum;
        } catch (IOException e) {
            System.out.println("文件操作失败：" + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }
}
