<template>
  <div class="detail-layout" :class="{ 'is-fullscreen-active': isPointCloudFullscreen }">
    <el-page-header @back="goBack" class="page-header">
      <!-- Vue 2 修改: 使用 slot="content" -->
      <span slot="content" v-if="robotConfig" class="page-title">{{ robotConfig.displayName }} ({{ robotId }}) 详情</span>
      <span slot="content" v-else class="page-title">加载中...</span>
    </el-page-header>

    <div class="main-content">
      <div class="left-panel">
        <el-card class="status-card">
          <div slot="header">实时状态</div>
          <div v-if="robotStatus">
            <el-descriptions :column="1" border size="small">
              <!-- Vue 2 修改: 使用 <template slot="default"> -->
              <el-descriptions-item label-class-name="status-label" label="状态">
                <template slot="default">
                  <el-tag :type="robotStatus.online ? 'success' : 'info'" size="small">{{ robotStatus.online ? '在线' : '离线' }}</el-tag>
                </template>
              </el-descriptions-item>
              <!-- 【后端适配】字段名从 last_update 改为 timestamp -->
              <el-descriptions-item label-class-name="status-label" label="最后更新">{{ robotStatus.timestamp }}</el-descriptions-item>
              <!-- 【后端适配】字段名从 cpu_percent 改为 cpuPercent -->
              <el-descriptions-item label-class-name="status-label" label="CPU">{{ robotStatus.cpuPercent.toFixed(1) }}%</el-descriptions-item>
              <!-- 【后端适配】字段名从 ram_percent 改为 ramPercent -->
              <el-descriptions-item label-class-name="status-label" label="内存">{{ robotStatus.ramPercent.toFixed(1) }}%</el-descriptions-item>
              <!-- 【后端适配】字段名从 current_status_message 改为 currentStatusMessage -->
              <el-descriptions-item label-class-name="status-label" label="详细信息">{{ robotStatus.currentStatusMessage }}</el-descriptions-item>
            </el-descriptions>
          </div>
          <el-skeleton v-else :rows="4" animated />
        </el-card>

        <el-card class="image-card">
          <div slot="header" class="card-header">
            <span>图像数据</span>
            <el-select v-if="availableImageTopics.length > 1" v-model="selectedImageTopic" placeholder="选择图像源" size="small" class="topic-selector">
              <el-option v-for="topic in availableImageTopics" :key="topic" :label="topic" :value="topic"/>
            </el-select>
          </div>
          <canvas ref="canvasRef" class="image-view"></canvas>
          <div v-if="!displayImageData" class="image-slot-overlay">等待图像数据...</div>
        </el-card>

        <el-card class="controls-card">
          <div slot="header">点云显示控制</div>
          <div class="control-item">
            <span class="control-label">点云上限</span>
            <el-input-number v-model="maxPoints" :min="10000" :max="50000000" :step="1000000" size="small" controls-position="right" style="width: 100%;" />
          </div>
          <div class="control-item"><span class="control-label">累积显示</span><el-switch v-model="isAccumulating" /></div>
          <div class="control-item"><span class="control-label">时间窗口 (秒)</span><el-slider v-model="timeWindow" :min="1" :max="3000" :step="1" :disabled="!isAccumulating" show-input /></div>
          <div class="control-item"><span class="control-label">点大小</span><el-slider v-model="pointSize" :min="0.01" :max="0.5" :step="0.01" /></div>
          <div class="control-item"><span class="control-label">显示密度 (%)</span><el-slider v-model="pointDensity" :min="1" :max="60" :step="1" /></div>
        </el-card>
      </div>

      <div class="right-panel">
        <el-card class="pointcloud-card" :class="{ 'is-fullscreen': isPointCloudFullscreen }">
          <div slot="header" class="card-header">
            <span>点云 ({{ formattedPointCount }} 点)</span>
            <div class="header-controls">
              <el-button @click="handleRefresh" type="primary" size="small" plain>刷新</el-button>
              <el-button @click="togglePause" :type="isPaused ? 'success' : 'warning'" size="small" plain>{{ isPaused ? '继续接收' : '暂停接收' }}</el-button>
              <el-radio-group v-model="cameraFollowMode" size="small">
                <el-radio-button label="none">不跟踪</el-radio-button>
                <el-radio-button label="position">跟踪位置</el-radio-button>
              </el-radio-group>
              <el-button @click="toggleFullscreen" :icon="isPointCloudFullscreen ? 'el-icon-close' : 'el-icon-full-screen'" size="small" circle></el-button>
              <el-color-picker v-model="trajectoryColor" size="small" />
              <el-select v-if="availablePointCloudTopics.length > 1" v-model="selectedPointCloudTopic" placeholder="选择点云源" size="small" class="topic-selector">
                <el-option v-for="topic in availablePointCloudTopics" :key="topic" :label="topic" :value="topic" />
              </el-select>
            </div>
          </div>
          <!-- PointCloudViewer 子组件 -->
          <PointCloudViewer 
            ref="viewerRef"
            :is-accumulating="isAccumulating"
            :time-window-sec="timeWindow"
            :point-size="pointSize"
            :max-points="maxPoints"
            :camera-follow-mode="cameraFollowMode" 
            :trajectory-color="trajectoryColor" 
            :background-color="'#ffffff'"
            @update:point-count="count => currentPointCount = count"
          />
        </el-card>
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios';
import PointCloudViewer from '@/components/PointCloudViewer.vue'; // 确保路径正确

// API 服务器地址
import { getBackendBaseUrl, getWsBaseUrl } from '@/utils/runtimeApi';

const API_BASE_URL = getBackendBaseUrl();
const WS_BASE_URL = getWsBaseUrl();

export default {
  name: 'RobotDetailView',
  components: {
    PointCloudViewer
  },
  data() {
    return {
      // 基础状态
      statuses: {}, // 存储所有机器人的状态，由全局 WebSocket 更新
      robotConfigs: {}, // 存储所有机器人的配置
      
      // UI 控制状态
      isPointCloudFullscreen: false,
      isPaused: false,
      isAccumulating: true,
      timeWindow: 100,
      pointSize: 0.1,
      pointDensity: 10,
      maxPoints: 2000000,
      cameraFollowMode: 'none',
      trajectoryColor: '#0000ff',

      // 数据管理
      currentPointCount: 0,
      allStreamsData: { images: {} },
      availablePointCloudTopics: [],
      availableImageTopics: [],
      selectedPointCloudTopic: '',
      selectedImageTopic: '',
      
      // 内部管理
      dataSocket: null,
      canvasCtx: null,
      offscreenImage: new Image(),
      debounceTimer: null,
    };
  },
  computed: {
    robotId() {
      return this.$route.params.robotId;
    },
    robotStatus() {
      // 从全局状态中获取当前机器人的状态
      return this.statuses[this.robotId];
    },
    robotConfig() {
      return this.robotConfigs[this.robotId];
    },
    formattedPointCount() {
      return this.currentPointCount.toLocaleString();
    },
    displayImageData() {
      const image_data = this.allStreamsData.images[this.selectedImageTopic];
      return image_data ? image_data.src : '';
    }
  },
  methods: {
    // --- 核心方法 ---
    async fetchRobotConfigs() {
      try {
        const token = this.$store.getters.token;
        if (!token) {
          console.error("认证失败：找不到 token。");
          return;
        }
        const config = { headers: { 'Authorization': `Bearer ${token}` } };
        const response = await axios.get(`${API_BASE_URL}/api/robots`, config);
        if (response.data && response.data.code === 200) {
          this.robotConfigs = response.data.data;
        }
      } catch (error) {
        console.error("获取机器人配置失败:", error);
      }
    },
    connectGlobalWebSocket() {
        // 这个方法用于连接全局状态 WebSocket，以获取 CPU/RAM 等信息
        const socket = new WebSocket(`${WS_BASE_URL}/ws/status`);
        socket.onmessage = (event) => {
            try {
                this.statuses = JSON.parse(event.data);
            } catch (error) {
                console.error("解析全局状态 WebSocket 消息失败:", error);
            }
        };
    },
    connectDataWebSocket() {
      if (!this.robotId) return;
      this.dataSocket = new WebSocket(`${WS_BASE_URL}/ws/data/${this.robotId}`);
      
      this.dataSocket.onmessage = (event) => {
        if (this.isPaused) return;

        const packet = JSON.parse(event.data);
        const topic = packet.frame;
        if (!topic) return;

        const typeName = packet.type;
        
        if (typeName === 'PACKET_POINTCLOUD_XYZI' ) {
          //console.log(packet);
          if (!this.availablePointCloudTopics.includes(topic)) {
            this.availablePointCloudTopics.push(topic);
            console.log(this.availablePointCloudTopics);
            if (!this.selectedPointCloudTopic) this.selectedPointCloudTopic = topic;
          }
          if (this.$refs.viewerRef && this.selectedPointCloudTopic === topic && packet.pclXyzi.points && packet.pclXyzi.points.length > 0) {
            console.log(packet.pclXyzi.points);
            this.$refs.viewerRef.addPoints(packet.pclXyzi.points);
          }
        }else if (typeName === 'PACKET_POINTCLOUD_XYZRGB') {
          //consol.log(packet);
          if (!this.availablePointCloudTopics.includes(topic)) {
            this.availablePointCloudTopics.push(topic);
            console.log(this.availablePointCloudTopics);
            if (!this.selectedPointCloudTopic) this.selectedPointCloudTopic = topic;
          }
          if (this.$refs.viewerRef && this.selectedPointCloudTopic === topic && packet.pclXyzrgb.points && packet.pclXyzrgb.points.length > 0) {
            console.log(packet.pclXyzrgb.points);
            this.$refs.viewerRef.addPoints(packet.pclXyzrgb.points);
          }
        } 
        else if (typeName === 'PACKET_IMAGE') {
          this.$set(this.allStreamsData.images, topic, { src: `data:image/${packet.format || 'jpeg'};base64,${packet.image.data}` });
          if (!this.availableImageTopics.includes(topic)) {
            this.availableImageTopics.push(topic);
            if (!this.selectedImageTopic) this.selectedImageTopic = topic;
          }
        } else if (typeName === 'PACKET_ODOM') {
          if (this.$refs.viewerRef && packet.frame === 'odom') { 
            this.$refs.viewerRef.addOdom(packet);
          }
        }
      };
    },
    debounceUpdateSettings(newDensity) {
      clearTimeout(this.debounceTimer);
      this.debounceTimer = setTimeout(async () => {
        try {
            const token = this.$store.getters.token;
            if (!token) return;
            const config = { headers: { 'Authorization': `Bearer ${token}` } };
            // 后端 Python 代码期望的是 point_density，这里保持一致
            await axios.post(`${API_BASE_URL}/api/settings/${this.robotId}`, { point_density: newDensity / 100 }, config);
        } catch (err) {
            console.error("更新设置失败:", err);
        }
      }, 500);
    },

    // --- UI 控制方法 ---
    goBack() {
      this.$router.back();
    },
    handleRefresh() {
      if (this.$refs.viewerRef) {
        this.$refs.viewerRef.clearPoints();
      }
    },
    togglePause() {
      this.isPaused = !this.isPaused;
    },
    toggleFullscreen() {
      this.isPointCloudFullscreen = !this.isPointCloudFullscreen;
    },

    // --- 图像渲染 ---
    drawImageOnCanvas() {
        if (this.canvasCtx && this.$refs.canvasRef) {
            this.canvasCtx.clearRect(0, 0, this.$refs.canvasRef.width, this.$refs.canvasRef.height);
            this.canvasCtx.drawImage(this.offscreenImage, 0, 0, this.$refs.canvasRef.width, this.$refs.canvasRef.height);
        }
    }
  },
  watch: {
    pointDensity(newVal) {
      this.debounceUpdateSettings(newVal);
    },
    selectedPointCloudTopic(newTopic, oldTopic) {
      if (newTopic !== oldTopic && this.$refs.viewerRef) {
        this.$refs.viewerRef.clearPoints();
      }
    },
    displayImageData(newSrc) {
      if (newSrc) {
        this.offscreenImage.src = newSrc;
      } else if (this.canvasCtx && this.$refs.canvasRef) {
        this.canvasCtx.clearRect(0, 0, this.$refs.canvasRef.width, this.$refs.canvasRef.height);
      }
    }
  },
  async mounted() {
    await this.fetchRobotConfigs();
    this.connectGlobalWebSocket(); // 连接全局状态，获取CPU/RAM等
    this.connectDataWebSocket();   // 连接此机器人的专属数据流
    
    this.$nextTick(() => {
      if (this.$refs.canvasRef) {
        this.canvasCtx = this.$refs.canvasRef.getContext('2d');
      }
    });

    this.offscreenImage.onload = this.drawImageOnCanvas;
    this.debounceUpdateSettings(this.pointDensity);
  },
  beforeDestroy() {
    if (this.dataSocket) {
      this.dataSocket.close();
    }
    // 可以在这里也关闭全局 WebSocket，但这通常由 App.vue 管理
  }
};
</script>

<style scoped>
/* 您的所有 CSS 样式可以粘贴在这里 */
.header-controls {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    justify-content: flex-end;
}
.detail-layout.is-fullscreen-active {
  overflow: hidden;
}
.pointcloud-card.is-fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 2000;
  display: flex;
  flex-direction: column;
}
.pointcloud-card.is-fullscreen >>> .el-card__header {
  flex-shrink: 0;
}
.pointcloud-card.is-fullscreen >>> .el-card__body {
  flex-grow: 1;
  padding: 0;
}
.control-item { margin-bottom: 8px !important; }
.card-header{display:flex;justify-content:space-between;align-items:center;width:100%}.header-controls{display:flex;align-items:center;gap:10px}.topic-selector{width:180px}.topic-selector >>> .el-input__inner{background-color:#f5f7fa;box-shadow:none!important;border-radius:4px;height:28px;padding:0 8px;font-size:13px;line-height:26px}.topic-selector >>> .el-select__caret{font-size:14px}.topic-selector >>> .el-input__inner:hover{border-color:#c0c4cc}.detail-layout{display:flex;flex-direction:column;height:100vh;padding:24px;box-sizing:border-box;background-color:#f0f2f5}.page-header{flex-shrink:0;padding-bottom:20px}.page-title{font-size:1.2rem;font-weight:600}.main-content{flex-grow:1;display:flex;gap:5px;min-height:0;flex-direction:row}@media (max-width:992px){.main-content{flex-direction:column}.left-panel{flex:0 0 auto;display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:15px}.right-panel{min-height:400px}}@media (max-width:768px){.detail-layout{padding:10px}.page-header{padding-bottom:10px}}.left-panel{flex:0 0 400px;display:flex;flex-direction:column;gap:5px}.right-panel{flex-grow:1;min-width:0}.status-card >>> .status-label{width:80px}.image-card{position:relative}.image-view{width:100%;height:250px;display:block;background-color:#000}.image-slot-overlay{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);color:#c0c4cc;font-size:14px;pointer-events:none}.pointcloud-card{height:100%;display:flex;flex-direction:column}.pointcloud-card >>> .el-card__body{flex-grow:1;padding:0;overflow:hidden}.controls-card .control-item{display:flex;align-items:center;margin-bottom:1px}.controls-card .control-label{flex:0 0 100px;font-size:14px;color:#606266}.controls-card .el-slider{flex-grow:1}.left-panel >>> .el-card__header{padding-top:10px!important;padding-bottom:10px!important}.status-card >>> .el-descriptions-item__cell{padding-top:6px!important;padding-bottom:6px!important}
</style>
