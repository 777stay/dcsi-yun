<template>
  <div class="dashboard-container">
    <el-card class="box-card">
      <div slot="header" class="clearfix">
        <span class="header-title">点云本地上传查看器</span>
      </div>

      <el-row :gutter="20" class="viewer-area">
        
        <el-col :span="8" class="viewer-column">
          <el-upload
            class="upload-dragger"
            drag
            action=""
            :auto-upload="false"
            :on-change="(file) => handleFileChange(file, 1)"
            :show-file-list="false"
            accept=".ply"
          >
            <i class="el-icon-upload"></i>
            <div class="el-upload__text">拖拽/点击上传 <b>T1 (旧时刻)</b></div>
            <div class="el-upload__tip" slot="tip" v-if="file1">已选择: {{ file1.name }}</div>
          </el-upload>

          <el-upload
            class="upload-dragger"
            drag
            action=""
            :auto-upload="false"
            :on-change="(file) => handleFileChange(file, 'kml')"
            :show-file-list="false"
            accept=".kml"
          >
            <i class="el-icon-upload"></i>
            <div class="el-upload__text">拖拽/点击上传 <b>KML 路径</b></div>
            <div class="el-upload__tip" slot="tip" v-if="kmlFile">已选择: {{ kmlFile.name }}</div>
          </el-upload>
          
          <ThreeViewer 
            :url="viewer1Url" 
            placeholder="窗口 1 - T1 预览"
            class="viewer-instance"
          />
        </el-col>
        
        <el-col :span="8" class="viewer-column">
          <el-upload
            class="upload-dragger"
            drag
            action=""
            :auto-upload="false"
            :on-change="(file) => handleFileChange(file, 2)"
            :show-file-list="false"
            accept=".ply"
          >
            <i class="el-icon-upload"></i>
            <div class="el-upload__text">拖拽/点击上传 <b>T2 (新时刻)</b></div>
            <div class="el-upload__tip" slot="tip" v-if="file2">已选择: {{ file2.name }}</div>
          </el-upload>

          <div class="result-header" v-if="currentTowerName">
             <span>杆塔变化 (Change)</span>
             <el-tag size="small" effect="dark">{{ currentTowerName }}</el-tag>
          </div>

          <ThreeViewer 
            :url="viewer2Url" 
            placeholder="窗口 2 - 等待结果 (变化点云)"
            class="viewer-instance"
          />
        </el-col>

        <el-col :span="8" class="viewer-column">
          <div style="height: 130px; display: flex; align-items: center; justify-content: center; color: #909399; border: 1px dashed #d9d9d9; margin-bottom: 10px; border-radius: 6px;">
            <span>结果展示区</span>
          </div>

          <div class="result-header" v-if="currentTowerName">
             <span>杆塔原貌 (RGB)</span>
          </div>

          <ThreeViewer 
            :url="resultViewerUrl" 
            placeholder="窗口 3 - 等待结果 (原始RGB)"
            class="viewer-instance"
          />
        </el-col>
        
      </el-row>

      <el-card class="control-panel" v-loading="isScriptRunning" element-loading-text="服务器正在运算中...">
        <div slot="header" class="clearfix">
          <span>处理与结果控制</span>
        </div>
        <div class="panel-content">
          <div class="button-container">
            <el-button 
              type="primary" 
              icon="el-icon-s-promotion"
              @click="runServerDetection"
              :disabled="isScriptRunning"
            >
              执行服务器变化检测
            </el-button>

            <div v-if="towerResults.length > 0" class="iterator-controls">
              <el-divider direction="vertical"></el-divider>
              <el-button-group>
                <el-button type="primary" plain icon="el-icon-arrow-left" @click="prevTower" :disabled="currentIndex <= 0">上一塔</el-button>
                <el-button type="primary" plain @click="nextTower" :disabled="currentIndex >= towerResults.length - 1">下一塔 <i class="el-icon-arrow-right el-icon--right"></i></el-button>
              </el-button-group>
              
              <span class="pagination-info">
                当前: <span class="highlight-text">{{ currentIndex + 1 }}</span> / {{ towerResults.length }}
              </span>
            </div>
          </div>
          
          <div v-if="scriptSuccess" class="success-indicator">
            <el-alert
              title="检测完成"
              type="success"
              show-icon
              :closable="false">
            </el-alert>
          </div>
        </div>
      </el-card>

    </el-card>
  </div>
</template>

<script>
import ThreeViewer from './ThreeViewer.vue'; 
import axios from 'axios';
import { getBackendBaseUrl } from '@/utils/runtimeApi';

const API_BASE_URL = getBackendBaseUrl();

export default {
  name: 'MultiWindowUploader',
  components: {
    ThreeViewer,
  },
  data() {
    return {
      // Blob URLs
      viewer1Url: null,       // 窗口1：本地预览 T1
      viewer2Url: null,       // 窗口2：本地预览 T2 -> 之后变为 变化结果
      resultViewerUrl: null,  // 窗口3：RGB结果

      // 原始文件对象
      file1: null,
      file2: null,
      kmlFile: null,
      
      // 状态控制
      isScriptRunning: false,
      scriptSuccess: false,

      // 结果数据
      towerResults: [], // 存放后端返回的 [{towerName, changeData, rgbData}, ...]
      currentIndex: 0,
    };
  },
  computed: {
    currentTowerName() {
      if (this.towerResults.length > 0 && this.towerResults[this.currentIndex]) {
        return this.towerResults[this.currentIndex].towerName;
      }
      return '';
    }
  },
  methods: {
    handleFileChange(file, windowIndex) {
      if (!this.validateFile(file, windowIndex === 'kml')) return;
      
      const fileRaw = file.raw;
      
      if (windowIndex === 'kml') {
        this.kmlFile = fileRaw;
        this.$message.success(`KML 文件: ${file.name} 已就绪`);
      } else {
        // 更新文件对象
        if (windowIndex === 1) {
            this.file1 = fileRaw;
            // 窗口1用于预览T1
            if (this.viewer1Url) URL.revokeObjectURL(this.viewer1Url);
            this.viewer1Url = URL.createObjectURL(fileRaw);
        } else if (windowIndex === 2) {
            this.file2 = fileRaw;
            // 窗口2初始用于预览T2，检测后将被覆盖
            if (this.viewer2Url) URL.revokeObjectURL(this.viewer2Url);
            this.viewer2Url = URL.createObjectURL(fileRaw);
        }
        this.$message.success(`窗口 ${windowIndex}: ${file.name} 已加载`);
      }
    },
    
    validateFile(file, isKml = false) {
      if (!file || !file.raw) return false;
      const fileName = file.name.toLowerCase();
      const isValid = isKml ? fileName.endsWith('.kml') : fileName.endsWith('.ply');
      if (!isValid) {
        this.$message.error(isKml ? '请上传 .kml 格式!' : '请上传 .ply 格式!');
        return false;
      }
      return true;
    },

    /**
     * 将 Base64 字符串转换为 Blob URL 以供 ThreeViewer 加载
     */
    base64ToBlobUrl(base64Data) {
      if (!base64Data) return null;
      try {
        // 如果后端传来的数据包含 "data:application/octet-stream;base64," 前缀，需根据情况处理
        // 下面的代码兼容有无前缀的情况
        const base64Content = base64Data.split(',').pop(); 
        
        const byteCharacters = atob(base64Content);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const byteArray = new Uint8Array(byteNumbers);
        const blob = new Blob([byteArray], { type: 'application/octet-stream' });
        return URL.createObjectURL(blob);
      } catch (e) {
        console.error("Base64 转换失败", e);
        return null;
      }
    },

    /**
     * 加载当前 currentIndex 指向的杆塔数据到 Viewer 2 和 3
     */
    loadCurrentTower() {
      if (this.towerResults.length === 0) return;
      
      const current = this.towerResults[this.currentIndex];
      
      // 1. 释放旧资源
      // 注意：如果是初始上传的本地预览URL，这里也会被释放掉，符合预期
      if (this.viewer2Url) URL.revokeObjectURL(this.viewer2Url);
      if (this.resultViewerUrl) URL.revokeObjectURL(this.resultViewerUrl);
      
      // 2. 加载新资源
      // 窗口 2: 变化点云 (changeData)
      if (current.changeData) {
        this.viewer2Url = this.base64ToBlobUrl(current.changeData);
      } else {
        this.viewer2Url = null; // 无变化数据
      }

      // 窗口 3: 原始 RGB (rgbData)
      if (current.rgbData) {
        this.resultViewerUrl = this.base64ToBlobUrl(current.rgbData);
      } else {
        this.resultViewerUrl = null;
      }
    },

    prevTower() {
      if (this.currentIndex > 0) {
        this.currentIndex--;
        this.loadCurrentTower();
      }
    },

    nextTower() {
      if (this.currentIndex < this.towerResults.length - 1) {
        this.currentIndex++;
        this.loadCurrentTower();
      }
    },
    
    async runServerDetection() {
      if (!this.file1 || !this.file2 || !this.kmlFile) {
        this.$message.warning('请确保所有文件 (T1, T2, KML) 都已上传。');
        return;
      }

      this.isScriptRunning = true;
      this.scriptSuccess = false;
      this.towerResults = [];
      this.currentIndex = 0;
      
      try {
        const formData = new FormData();
        formData.append('plyFile1', this.file1);
        formData.append('plyFile2', this.file2);
        formData.append('kmlFile', this.kmlFile);

        // 调用后端 API，期望返回 JSON 数组
        const response = await axios.post(`${API_BASE_URL}/api/script/run-detection`, formData, {
          headers: {
            'Content-Type': 'multipart/form-data'
          }
          // 注意：不要设置 responseType: 'blob'，默认是 json
        });
        
        if (response.data && Array.isArray(response.data)) {
          this.towerResults = response.data;
          this.scriptSuccess = true;
          this.$message.success(`脚本执行成功，检测到 ${this.towerResults.length} 个杆塔区域。`);

          if (this.towerResults.length > 0) {
            // 自动加载第一个结果
            this.loadCurrentTower();
          } else {
            this.$message.info('未检测到任何杆塔区域或变化。');
          }
        } else {
          console.warn('后端响应格式不正确:', response);
          this.$message.warning('脚本执行完成，但返回的数据格式不正确。');
        }

      } catch (error) {
        console.error('脚本执行失败:', error);
        const errMsg = error.response?.data?.message || error.message || '未知错误';
        this.$message.error(`执行失败: ${errMsg}`);
      } finally {
        this.isScriptRunning = false;
      }
    }
  },
  beforeDestroy() {
    if (this.viewer1Url) URL.revokeObjectURL(this.viewer1Url);
    if (this.viewer2Url) URL.revokeObjectURL(this.viewer2Url);
    if (this.resultViewerUrl) URL.revokeObjectURL(this.resultViewerUrl);
  }
};
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
  height: 100vh;
  box-sizing: border-box;
  background-color: #f0f2f5;
}
.box-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.box-card >>> .el-card__body {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.header-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.viewer-area {
  flex-grow: 1; 
  min-height: 500px; 
}
.viewer-column {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 上传控件样式微调 */
.upload-dragger {
  margin-bottom: 10px;
}
.upload-dragger >>> .el-upload {
  width: 100%;
}
.upload-dragger >>> .el-upload-dragger {
  width: 100%;
  height: 80px; /* 稍微减小高度以节省空间 */
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  border-color: #dcdfe6;
}
.upload-dragger >>> .el-icon-upload {
  font-size: 30px;
  margin: 0;
  line-height: 1;
  color: #c0c4cc;
}
.upload-dragger >>> .el-upload__text {
  font-size: 12px;
  line-height: 1.5;
}
.el-upload__tip {
  font-size: 12px;
  margin-top: 2px;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.viewer-instance {
  flex-grow: 1; 
  min-height: 300px;
  border: 1px solid #EBEEF5;
  background: #f9fafc;
}

/* 控制面板样式 */
.control-panel {
  margin-top: 20px;
  flex-shrink: 0;
}
.panel-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.button-container {
  display: flex;
  align-items: center;
  flex-grow: 1;
}

/* 遍历控制器样式 */
.iterator-controls {
  display: flex;
  align-items: center;
  margin-left: 20px;
}
.pagination-info {
  margin-left: 15px;
  font-size: 14px;
  color: #606266;
}
.highlight-text {
  color: #409EFF;
  font-weight: bold;
  font-size: 16px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 0;
  font-size: 14px;
  font-weight: bold;
  color: #303133;
}

.success-indicator {
  margin-left: 20px;
  flex-shrink: 0;
}
</style>
