<template>
    <div class="detection-app-container">
      <el-card class="box-card">
        <div slot="header" class="clearfix">
          <span class="header-title">RT-DETR 图像目标检测</span>
        </div>
  
        <!-- 1. 图片上传区域 -->
        <el-upload
          class="image-uploader"
          drag
          action=""
          :show-file-list="false"
          :before-upload="handleBeforeUpload"
          accept="image/jpeg,image/png"
        >
          <i class="el-icon-upload"></i>
          <div class="el-upload__text">将图片拖到此处，或<em>点击上传</em></div>
          <div class="el-upload__tip" slot="tip">只能上传 jpg/png 文件，且不超过10MB</div>
        </el-upload>
  
        <!-- 2. 加载与结果展示区域 -->
        <div v-if="isLoading" class="loading-section">
          <el-progress type="circle" :percentage="progress"></el-progress>
          <p class="loading-text">{{ statusText }}</p>
        </div>
  
        <div v-if="detectionResult" class="result-section">
          <!-- 2.1 图片对比展示 -->
          <el-row :gutter="20" class="image-comparison">
            <el-col :span="8">
              <h3>原始图片 <el-button type="text" icon="el-icon-download" @click="downloadImage(originalImageUrl, 'original_image.jpg')">下载</el-button></h3>
              <el-image :src="originalImageUrl" fit="contain" style="width: 100%; height: 400px;"></el-image>
            </el-col>
            <el-col :span="8">
              <h3>检测结果 <el-button type="text" icon="el-icon-download" @click="downloadImage(annotatedImageUrl, 'annotated_image.jpg')">下载</el-button></h3>
              <el-image :src="annotatedImageUrl" fit="contain" style="width: 100%; height: 400px;"></el-image>
            </el-col>
            <!-- 2.3 检测报告图片展示 (移动到同一行) -->
            <el-col :span="8" v-if="reportImageUrl">
              <h3>检测报告 <el-button type="text" icon="el-icon-download" @click="downloadImage(reportImageUrl, 'detection_report.png')">下载</el-button></h3>
              <el-image :src="reportImageUrl" fit="contain" style="width: 100%; height: 400px;"></el-image>
            </el-col>
          </el-row>

          <!-- 2.2 检测结果表格 -->
          <div class="results-table-section">
            <h3>检测详情</h3>
            <el-table :data="detectionResult.detections" stripe border height="250">
              <el-table-column prop="className" label="目标类别" width="180"></el-table-column>
              <el-table-column prop="confidence" label="置信度" width="120">
                <template slot-scope="scope">
                  <el-progress :percentage="scope.row.confidence * 100" :format="() => `${(scope.row.confidence * 100).toFixed(1)}%`"></el-progress>
                </template>
              </el-table-column>
              <el-table-column prop="box" label="边界框 (xmin, ymin, xmax, ymax)">
                  <template slot-scope="scope">
                      {{ scope.row.box.join(', ') }}
                  </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
  
      </el-card>
    </div>
  </template>
  
  <script>
  import axios from 'axios';
  
  // API 服务器地址，请根据您的实际情况修改
  import { getBackendBaseUrl } from '@/utils/runtimeApi';

  const API_BASE_URL = getBackendBaseUrl(); 
  
  export default {
    name: 'DetectionPage',
    data() {
      return {
        isLoading: false,
        progress: 0,
        statusText: '',
        
        originalImageUrl: null,
        annotatedImageUrl: null,
        reportImageUrl: null, // 新增：检测报告图片URL
        detectionResult: null,
      };
    },
    methods: {
      // 文件上传前的处理
      handleBeforeUpload(file) {
        const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png';
        const isLt10M = file.size / 1024 / 1024 < 10;
  
        if (!isJpgOrPng) {
          this.$message.error('上传图片只能是 JPG 或 PNG 格式!');
          return false;
        }
        if (!isLt10M) {
          this.$message.error('上传图片大小不能超过 10MB!');
          return false;
        }
        
        this.resetState();
        this.originalImageUrl = URL.createObjectURL(file);
        this.startDetection(file);
        
        return false; // 返回 false, 手动上传
      },
  
      // 开始检测流程
      async startDetection(file) {
        this.isLoading = true;
        this.statusText = '正在上传图片...';
        this.progress = 20;
  
        const formData = new FormData();
        formData.append('image', file);
        
        try {
          const token = this.$store.getters.token; // 确保从本地存储获取 Token
          if (!token) {
            this.$message.error('认证失败，请先登录！');
            this.resetState();
            return;
          }
  
          const config = {
            headers: {
              'Content-Type': 'multipart/form-data',
              'Authorization': `Bearer ${token}`
            },
            onUploadProgress: (progressEvent) => {
              this.progress = 20 + Math.round((progressEvent.loaded * 100) / progressEvent.total) * 0.4; // 上传进度占 40%
            }
          };
  
          this.statusText = '图片上传完成，正在进行目标检测...';
          
          const response = await axios.post(`${API_BASE_URL}/api/detection/detect`, formData, config);
          
          if (response.data.code === 200) {
            this.progress = 100;
            this.statusText = '检测完成！';
            
            this.detectionResult = response.data.data;
            // 构建标注后图片的完整 URL
            
            this.annotatedImageUrl = `${API_BASE_URL}/api/files/annotated_images/${response.data.data.annotatedImageName}`;
            // 构建报告图片的完整 URL
            if (response.data.data.reportImageName) {
              this.reportImageUrl = `${API_BASE_URL}/api/files/report_images/${response.data.data.reportImageName}`;
            }
            
            this.$message.success('目标检测成功！');
            setTimeout(() => { this.isLoading = false; }, 1000);
  
          } else {
            throw new Error(response.data.message || '后端处理失败');
          }
  
        } catch (error) {
          this.$message.error('目标检测失败: ' + (error.response ? error.response.data.message : error.message));
          this.resetState();
        }
      },
      
      // 实现图片下载功能
      downloadImage(imageUrl, filename) {
        if (!imageUrl) {
          this.$message.warning('没有图片可以下载。');
          return;
        }
        const link = document.createElement('a');
        link.href = imageUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        this.$message.success(`'${filename}' 已开始下载。`);
      },

      // 重置组件状态
      resetState() {
          this.isLoading = false;
          this.progress = 0;
          this.statusText = '';
          
          if (this.originalImageUrl) {
              URL.revokeObjectURL(this.originalImageUrl);
          }
          this.originalImageUrl = null;
          this.annotatedImageUrl = null;
          this.reportImageUrl = null; // 清除报告图片URL
          this.detectionResult = null;
      }
    },
    beforeDestroy() {
        // 组件销毁前清理 blob URL
        if (this.originalImageUrl) {
            URL.revokeObjectURL(this.originalImageUrl);
        }
    }
  };
  </script>
  
  <style scoped>
  .detection-app-container {
    padding: 20px;
  }
  .header-title {
    font-size: 22px;
    font-weight: 600;
  }
  .image-uploader {
    margin-bottom: 20px;
  }
  .image-uploader >>> .el-upload {
    width: 100%;
  }
  .image-uploader >>> .el-upload-dragger {
    width: 100%;
    height: 200px;
  }
  .loading-section {
    text-align: center;
    margin: 40px 0;
  }
  .loading-text {
    margin-top: 15px;
    font-size: 16px;
    color: #606266;
  }
  .result-section {
    margin-top: 20px;
  }
  .image-comparison {
    margin-bottom: 30px;
  }
  .image-comparison h3 {
    text-align: center;
    margin-bottom: 10px;
    font-size: 18px;
    color: #303133;
  }
  .el-image {
    border: 1px solid #ebeef5;
    border-radius: 4px;
  }
  .results-table-section h3 {
    margin-bottom: 15px;
    font-size: 18px;
    color: #303133;
  }
  </style>
  
  
