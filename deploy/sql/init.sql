/*
 Navicat Premium Data Transfer

 Source Server         : sql
 Source Server Type    : MySQL
 Source Server Version : 80040
 Source Host           : localhost:3306
 Source Schema         : yun

 Target Server Type    : MySQL
 Target Server Version : 80040
 File Encoding         : 65001

 Date: 22/09/2025 15:04:44
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for access_logs
-- ----------------------------
DROP TABLE IF EXISTS `access_logs`;
CREATE TABLE `access_logs`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ip_address` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `http_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `class_method` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `request_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `execution_time` bigint NULL DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `timestamp` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 606 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for detection_results
-- ----------------------------
DROP TABLE IF EXISTS `detection_results`;
CREATE TABLE `detection_results`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_image_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `source_image_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `annotated_image_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `class_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `confidence` double NULL DEFAULT NULL,
  `box_xmin` int NULL DEFAULT NULL,
  `box_ymin` int NULL DEFAULT NULL,
  `box_xmax` int NULL DEFAULT NULL,
  `box_ymax` int NULL DEFAULT NULL,
  `detection_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for image_data
-- ----------------------------
DROP TABLE IF EXISTS `image_data`;
CREATE TABLE `image_data`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `robot_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `session_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `format` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `width` int NULL DEFAULT NULL,
  `height` int NULL DEFAULT NULL,
  `file_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `timestamp` datetime NOT NULL,
  `reception_count` bigint NULL DEFAULT NULL COMMENT '数据接收会话次数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 172959 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mission_plans
-- ----------------------------
DROP TABLE IF EXISTS `mission_plans`;
CREATE TABLE `mission_plans`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `number_device` int NULL DEFAULT NULL,
  `drone_start` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `drone_end` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `drone_speed` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `kml_file_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `scan_density` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `time` datetime NULL DEFAULT NULL,
  `reception_count` bigint NULL DEFAULT NULL COMMENT '数据接收会话次数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for odometry_data
-- ----------------------------
DROP TABLE IF EXISTS `odometry_data`;
CREATE TABLE `odometry_data`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `robot_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '机器人ID',
  `frame_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '数据帧ID',
  `pos_x` double NULL DEFAULT NULL COMMENT '位置X坐标',
  `pos_y` double NULL DEFAULT NULL COMMENT '位置Y坐标',
  `pos_z` double NULL DEFAULT NULL COMMENT '位置Z坐标',
  `orient_x` double NULL DEFAULT NULL COMMENT '姿态四元数X',
  `orient_y` double NULL DEFAULT NULL COMMENT '姿态四元数Y',
  `orient_z` double NULL DEFAULT NULL COMMENT '姿态四元数Z',
  `orient_w` double NULL DEFAULT NULL COMMENT '姿态四元数W',
  `timestamp` datetime NOT NULL COMMENT '数据时间戳',
  `reception_count` bigint NULL DEFAULT NULL COMMENT '数据接收会话次数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 165243 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '里程计数据表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for point_cloud_data
-- ----------------------------
DROP TABLE IF EXISTS `point_cloud_data`;
CREATE TABLE `point_cloud_data`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `robot_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '机器人ID',
  `frame_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '数据帧ID',
  `file_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '点云文件(.laz)的存储路径',
  `point_count` bigint NOT NULL COMMENT '点云中的点数量',
  `timestamp` datetime NOT NULL COMMENT '数据时间戳',
  `reception_count` bigint NULL DEFAULT NULL COMMENT '数据接收会话次数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19158 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '点云文件元数据表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for session_info
-- ----------------------------
DROP TABLE IF EXISTS `session_info`;
CREATE TABLE `session_info`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_count` bigint NOT NULL COMMENT '会话次数',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE, COMPLETED',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `session_count`(`session_count`) USING BTREE,
  INDEX `idx_session_count`(`session_count`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_start_time`(`start_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会话信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
