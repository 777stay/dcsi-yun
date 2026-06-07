
<template>
  <el-container class="main-container">
    <el-main class="main-content">
      <el-page-header class="page-header">
        <template #title>
          <span class="main-title-text">控制中心</span>
        </template>
        <template #content>
          <span class="sub-title-text">珞珈探索者无人集群</span>
        </template>
        <template #extra>
          <div class="header-extra">
            <el-tag :type="isConnected ? 'success' : 'danger'" effect="dark" size="medium">
              <i :class="isConnected ? 'el-icon-link' : 'el-icon-warning-outline'"></i>
              WebSocket: {{ isConnected ? '已连接' : '已断开' }}
            </el-tag>
          </div>
        </template>
      </el-page-header>
      
      <el-card class="box-card control-station-card">
        <div slot="header" class="card-header">
          <span><i class="el-icon-s-tools"></i> 全局指令控制站</span>
        </div>
        <div class="control-buttons-wrapper">
          <el-button type="primary" plain @click="sendCommand('robot', 'RUN_MULTI_ALIGNMENT')">
            <i class="el-icon-s-grid"></i> 多机配准
          </el-button>
          <el-button type="info" plain @click="sendCommand('robot', 'RUN_MULTI_DATA_VIEW')">
            <i class="el-icon-s-promotion"></i> 多机路径规划
          </el-button>
          <el-button type="warning" plain @click="sendCommand('robot', 'RUN_MULTI_DATA_RECORD')">
            <i class="el-icon-video-camera-solid"></i> 多机数据录制
          </el-button>
        </div>
      </el-card>

       <!-- 【修改】数据接收控制区域 - 增强显示信息 -->
       <el-card class="box-card data-flow-control-card">
        <div slot="header" class="card-header">
          <span><i class="el-icon-receiving"></i> 后端数据接收控制</span>
          <div class="header-status">
            <el-tag v-if="currentSessionInfo" type="success" size="small">
              会话 #{{ currentSessionInfo.sessionCount }} 进行中
            </el-tag>
            <el-tag v-else-if="isProcessingEnabled" type="warning" size="small">
              等待会话开始
            </el-tag>
            <el-tag v-else type="info" size="small">
              数据接收已关闭
            </el-tag>
          </div>
        </div>
        
        <div class="data-flow-controls">
          <div class="control-row">
            <div class="control-item">
              <span class="control-label">数据接收开关:</span>
              <el-switch
                v-model="isProcessingEnabled"
                active-text="开启"
                inactive-text="关闭"
                @change="handleProcessingToggle"
                :loading="isToggling"
              ></el-switch>
            </div>
            
            <div class="control-item">
              <el-tooltip class="item" effect="dark" content="从 Redis 中读取的持久化计数值" placement="top">
                <el-tag type="info" class="count-tag">
                  <i class="el-icon-s-data"></i>
                  总接收次数: {{ receptionCount }}
                </el-tag>
              </el-tooltip>
            </div>
          </div>

          <!-- 【新增】当前会话信息显示 -->
          <div v-if="currentSessionInfo" class="session-info">
            <el-divider content-position="left">
              <span class="divider-text">当前会话信息</span>
            </el-divider>
            <div class="session-details">
              <div class="session-item">
                <span class="session-label">会话编号:</span>
                <el-tag size="mini">#{{ currentSessionInfo.sessionCount }}</el-tag>
              </div>
              <div class="session-item">
                <span class="session-label">开始时间:</span>
                <span class="session-value">{{ formatTime(currentSessionInfo.startTime) }}</span>
              </div>
              <div class="session-item">
                <span class="session-label">持续时间:</span>
                <el-tag type="success" size="mini">{{ getDuration(currentSessionInfo.startTime) }}</el-tag>
              </div>
            </div>
          </div>

          <!-- 【新增】管理操作按钮 -->
          <div class="management-actions">
            <el-button size="small" icon="el-icon-refresh" @click="syncCount" :loading="isSyncing">
              同步计数
            </el-button>
            <el-button size="small" icon="el-icon-monitor" @click="checkHealth" :loading="isHealthChecking">
              健康检查
            </el-button>
          </div>
        </div>
      </el-card>

      <el-row :gutter="20">
        <el-col :xs="24" :sm="12" :md="12" :lg="8" v-for="(config, robotId) in actualRobots" :key="robotId">
          <el-card class="box-card robot-card" shadow="hover">
            <div slot="header" class="card-header">
              <RouterLink :to="`/robot/${robotId}`" class="robot-title-link">
                <i class="el-icon-s-platform"></i>
                <span>{{ config.displayName }} ({{ robotId }})</span>
              </RouterLink>
              <el-tag :type="statuses[robotId] && statuses[robotId].online ? 'success' : 'info'" size="small">
                {{ statuses[robotId] && statuses[robotId].online ? '在线' : '离线' }}
              </el-tag>
            </div>
            
            <div v-if="statuses[robotId]">
                <div v-if="statuses[robotId].online">
                    <p class="status-message">
                        <strong>当前状态:</strong> 
                        <span class="status-text">{{ statuses[robotId].currentStatusMessage }}</span>
                    </p>

                    <el-divider content-position="left"><span class="divider-text">系统状态</span></el-divider>
                    <div class="stats-container">
                        <div class="stat-item">
                            <span class="stat-label">CPU</span>
                            <el-progress 
                                :percentage="statuses[robotId].cpuPercent" 
                                :stroke-width="10" 
                                :format="(val) => `${val.toFixed(1)}%`"
                                class="stat-progress"
                            />
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">RAM</span>
                            <el-progress 
                                :percentage="statuses[robotId].ramPercent" 
                                :stroke-width="10" 
                                :color="'#67C23A'"
                                :format="(val) => `${val.toFixed(1)}%`"
                                class="stat-progress"
                            />
                        </div>
                    </div>

                    <el-divider content-position="left"><span class="divider-text">模块控制</span></el-divider>
                    <div class="commands-container">
                        <div class="command-row">
                            <span class="command-label">传感器</span>
                            <el-switch
                                :value="isSensorActive(robotId)"
                                @change="(newState) => handleToggle(newState, robotId, 'SENSOR')"
                                :disabled="isSwitchDisabled(robotId)"
                            />
                        </div>
                        <div class="command-row">
                            <span class="command-label">点云建图</span>
                            <el-switch
                                :value="isExploreActive(robotId)"
                                @change="(newState) => handleToggle(newState, robotId, 'EXPLORE')"
                                :disabled="isSwitchDisabled(robotId)"
                            />
                        </div>
                        <div class="command-row">
                            <span class="command-label">数据录制</span>
                            <el-switch
                                :value="isRecordingActive(robotId)"
                                @change="(newState) => handleToggle(newState, robotId, 'RECORD')"
                                :disabled="isRecordSwitchDisabled(robotId)"
                            />
                        </div>
                    </div>
                     <p class="timestamp"><small>最后更新: {{ statuses[robotId].timestamp }}</small></p>
                </div>
                <div v-else class="offline-overlay">
                    <i class="el-icon-connection"></i>
                    <span>设备已离线</span>
                </div>
            </div>

            <el-skeleton v-else :rows="6" animated />
          </el-card>
        </el-col>
      </el-row>
    </el-main>
  </el-container>
</template>

<script>
import axios from 'axios';

// API 服务器地址，定义为一个常量方便修改
import { getBackendBaseUrl, getWsBaseUrl } from '@/utils/runtimeApi';

const API_BASE_URL = getBackendBaseUrl();
const WS_BASE_URL = getWsBaseUrl();

export default {
  name: 'RobotControlPanel',
  
  data() {
    return {
      statuses: {},
      isConnected: false,
      robotConfigs: {},
      
      // --- 【修改】数据流控制状态 ---
      isProcessingEnabled: false, // 开关状态，默认为 false
      receptionCount: 0,          // 接收次数
      currentSessionInfo: null,   // 【新增】当前会话信息
      
      // --- 【新增】操作状态标识 ---
      isToggling: false,          // 开关切换加载状态
      isSyncing: false,           // 同步计数加载状态
      isHealthChecking: false,    // 健康检查加载状态
    };
  },

  computed: {
    actualRobots() {
      const robots = { ...this.robotConfigs };
      delete robots.robot; 
      return robots;
    },
  },

  methods: {
    // --- 【修改】获取初始数据流状态 ---
    async getInitialDataFlowStatus() {
      try {
        const token = this.$store.getters.token;
        const config = { headers: { 'Authorization': `Bearer ${token}` } };
        // 修改为新的API路径
        const response = await axios.get(`${API_BASE_URL}/api/data-flow/status`, config);
        
        if (response.data.code === 200) {
          const data = response.data.data;
          this.isProcessingEnabled = data.isEnabled;
          this.receptionCount = data.receptionCount;
          
          // 处理新增的会话信息
          if (data.currentSession) {
            this.currentSessionInfo = data.currentSession;
            console.log('当前会话信息:', this.currentSessionInfo);
          } else {
            this.currentSessionInfo = null;
          }
          
          // 显示状态消息
          if (data.statusMessage) {
            console.log('状态信息:', data.statusMessage);
          }
          
        } else {
          console.error('获取数据接收状态失败:', response.data.message);
          this.$message.error('获取数据接收状态失败: ' + response.data.message);
        }
      } catch (error) {
        console.error('请求数据接收状态失败:', error);
        this.$message.error('请求数据接收状态失败: ' + (error.response?.data?.message || error.message));
      }
    },

    // --- 【修改】处理开关状态变化 ---
    async handleProcessingToggle(newState) {
      this.isToggling = true;
      const action = newState ? 'enable' : 'disable';
      const actionText = newState ? '开启' : '关闭';
      
      try {
        const token = this.$store.getters.token;
        const config = { headers: { 'Authorization': `Bearer ${token}` } };
        // 修改为新的API路径
        const response = await axios.post(`${API_BASE_URL}/api/data-flow/${action}`, null, config);

        if (response.data.code === 200) {
          const data = response.data.data;
          
          // 显示详细的成功消息
          if (data.detailedMessage) {
            this.$message.success(data.detailedMessage);
          } else {
            this.$message.success(data.message || `数据接收已${actionText}`);
          }
          
          // 更新接收次数
          if (data.receptionCount !== undefined) {
            this.receptionCount = data.receptionCount;
          }
          
          // 处理会话信息
          if (newState) {
            // 启用时更新当前会话信息
            if (data.sessionInfo) {
              this.currentSessionInfo = data.sessionInfo;
              console.log('新会话已开始:', this.currentSessionInfo);
            }
          } else {
            // 禁用时处理已完成的会话
            if (data.completedSessionInfo) {
              console.log('会话已完成:', data.completedSessionInfo);
            }
            this.currentSessionInfo = null;
          }
          
        } else {
          console.error(`${actionText}数据接收失败:`, response.data);
          this.$message.error(`${actionText}数据接收失败: ${response.data.message || '未知错误'}`);
          // 操作失败，恢复开关状态
          this.isProcessingEnabled = !newState;
        }
      } catch (error) {
        console.error(`请求${actionText}数据接收失败:`, error);
        this.$message.error(`请求${actionText}数据接收失败: ${error.response?.data?.message || error.message}`);
        // 请求失败，恢复开关状态
        this.isProcessingEnabled = !newState;
      } finally {
        this.isToggling = false;
      }
    },

    // --- 【新增】手动同步计数 ---
    async syncCount() {
      this.isSyncing = true;
      try {
        const token = this.$store.getters.token;
        const config = { headers: { 'Authorization': `Bearer ${token}` } };
        const response = await axios.post(`${API_BASE_URL}/api/data-flow/sync-count`, null, config);
        
        if (response.data.code === 200) {
          const data = response.data.data;
          this.$message.success(data.message || '计数同步成功');
          
          // 更新本地状态
          if (data.receptionCount !== undefined) {
            this.receptionCount = data.receptionCount;
          }
          if (data.currentSession) {
            this.currentSessionInfo = data.currentSession;
          }
          
          console.log('计数同步完成，当前计数:', this.receptionCount);
        } else {
          this.$message.error('计数同步失败: ' + response.data.message);
        }
      } catch (error) {
        console.error('同步计数请求失败:', error);
        this.$message.error('同步计数请求失败: ' + (error.response?.data?.message || error.message));
      } finally {
        this.isSyncing = false;
      }
    },

    // --- 【新增】检查服务健康状态 ---
    async checkHealth() {
      this.isHealthChecking = true;
      try {
        const token = this.$store.getters.token;
        const config = { headers: { 'Authorization': `Bearer ${token}` } };
        const response = await axios.get(`${API_BASE_URL}/api/data-flow/health`, config);
        
        if (response.data.code === 200) {
          const health = response.data.data;
          console.log('服务健康状态:', health);
          
          if (health.database === 'UP') {
            this.$message.success('服务健康检查通过');
          } else {
            this.$message.warning('数据库连接异常: ' + health.error);
          }
        } else {
          this.$message.error('健康检查失败: ' + response.data.message);
        }
      } catch (error) {
        console.error('健康检查请求失败:', error);
        this.$message.error('健康检查请求失败: ' + (error.response?.data?.message || error.message));
      } finally {
        this.isHealthChecking = false;
      }
    },

    // --- 【新增】格式化时间显示 ---
    formatTime(timeStr) {
      if (!timeStr) return '';
      const date = new Date(timeStr);
      return date.toLocaleString('zh-CN');
    },

    // --- 【新增】计算持续时间 ---
    getDuration(startTime) {
      if (!startTime) return '';
      const start = new Date(startTime);
      const now = new Date();
      const diffMs = now - start;
      const diffMins = Math.floor(diffMs / 60000);
      const diffHours = Math.floor(diffMins / 60);
      
      if (diffHours > 0) {
        return `${diffHours}时${diffMins % 60}分`;
      } else {
        return `${diffMins}分钟`;
      }
    },

    // --- 以下方法保持原样 ---
    async fetchRobotConfigs() {
      try {
        const token = this.$store.getters.token;
        if (!token) {
          console.error("认证失败：找不到 token。请先登录。");
          return;
        }
        const config = { headers: { 'Authorization': `Bearer ${token}` } };
        const response = await axios.get(`${API_BASE_URL}/api/robots`, config);
        if (response.data && response.data.code === 200) {
          this.robotConfigs = response.data.data;
        } else {
          console.error("获取机器人配置失败:", response.data.message || "未知错误");
        }
      } catch (error) {
        console.error("请求机器人配置时发生错误:", error);
      }
    },

    connectWebSocket() {
      if (this.isConnected) return;
      const socket = new WebSocket(`${WS_BASE_URL}/ws/status`);

      socket.onopen = () => {
        this.isConnected = true;
        console.log('WebSocket 连接成功。');
      };

      socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.statuses = data;
        } catch (error) {
          console.error("解析 WebSocket 消息失败:", error);
        }
      };

      socket.onclose = () => {
        this.isConnected = false;
        for (const robotId in this.statuses) {
          this.$set(this.statuses[robotId], 'online', false);
        }
        console.log('WebSocket 连接已断开，尝试2秒后重连...');
        setTimeout(() => this.connectWebSocket(), 2000);
      };

      socket.onerror = (error) => {
        console.error('WebSocket 发生错误:', error);
      };
    },

    async sendCommand(robotId, commandName) {
      try {
        const token = this.$store.getters.token;
        if (!token) {
            console.error("无法发送命令：未找到 Token。");
            return;
        }
        const config = { headers: { 'Authorization': `Bearer ${token}` } };
        const response = await axios.post(`${API_BASE_URL}/api/command/${robotId}/${commandName}`, null, config);
        console.log('命令响应:', response.data);
      } catch (error) {
        console.error('发送命令失败:', error);
      }
    },

    handleToggle(newState, robotId, actionType) {
      let command;
      if (actionType === 'SENSOR') {
        command = newState ? 'RUN_WAYPOINT_MODE' : 'STOP_WAYPOINT_MODE';
      } else if (actionType === 'EXPLORE') {
        command = newState ? 'RUN_EXPLORE_MODE' : 'STOP_EXPLORE_MODE';
      } else { // 'RECORD'
        command = newState ? 'RUN_RECORD_DAYA' : 'STOP_RECORD_DAYA';
      }
      this.sendCommand(robotId, command);
    },

    // --- 状态判断方法 ---
    isSensorActive(robotId) {
      const status = this.statuses[robotId];
      return status && status.currentStatusMessage && status.currentStatusMessage.includes('Run_waypoint');
    },
    isExploreActive(robotId) {
      const status = this.statuses[robotId];
      return status && status.currentStatusMessage && status.currentStatusMessage.includes('Run_explore');
    },
    isRecordingActive(robotId) {
      const status = this.statuses[robotId];
      return status && status.currentStatusMessage && status.currentStatusMessage.includes('Run_data_record');
    },

    // --- 开关禁用逻辑判断方法 ---
    isSwitchDisabled(robotId) {
      const status = this.statuses[robotId];
      return !status || !status.online || (status.currentStatusMessage && status.currentStatusMessage.includes('Command Sent'));
    },
    isRecordSwitchDisabled(robotId) {
      const status = this.statuses[robotId];
      return !status || !status.online || (status.currentStatusMessage && status.currentStatusMessage.includes('Command Sent'));
    }
  },

  mounted() {
    this.getInitialDataFlowStatus();
    this.fetchRobotConfigs();
    this.connectWebSocket();
  },
};
</script>

<style scoped>
/* --- 全局布局 --- */
.main-container {
  background-color: #f0f2f5;
}
.main-content {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

/* --- 页面头部 --- */
.page-header {
  background-color: #ffffff;
  padding: 16px 24px;
  border-radius: 8px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.06);
}
.main-title-text {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.sub-title-text {
  font-size: 14px;
  color: #909399;
}
.header-extra .el-tag {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* --- 卡片通用样式 --- */
.box-card {
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  margin-bottom: 20px;
  transition: all 0.3s ease;
}
.card-header { 
  display: flex; 
  justify-content: space-between; 
  align-items: center; 
  font-weight: 600;
  color: #303133;
}
.card-header i {
  margin-right: 8px;
}
.header-status {
  display: flex;
  align-items: center;
}

/* --- 控制站卡片 --- */
.control-station-card {
  margin-bottom: 24px;
}
.control-buttons-wrapper {
  display: flex;
  justify-content: center;
  gap: 1.5rem;
  flex-wrap: wrap;
}
.control-buttons-wrapper .el-button i {
  margin-right: 5px;
}

/* --- 【新增】数据流控制卡片样式 --- */
.data-flow-control-card {
  margin-bottom: 24px;
}
.data-flow-controls {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.control-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}
.control-item {
  display: flex;
  align-items: center;
  gap: 12px;
}
.control-label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}
.count-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* --- 【新增】会话信息样式 --- */
.session-info {
  background-color: #f8f9fa;
  padding: 16px;
  border-radius: 6px;
  border: 1px solid #e9ecef;
}
.session-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.session-label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}
.session-value {
  font-size: 13px;
  color: #303133;
}

/* --- 【新增】管理操作样式 --- */
.management-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
}

/* --- 机器人卡片 --- */
.robot-card {
  min-height: 380px;
}
.robot-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 20px 0 rgba(0,0,0,0.08);
}
.robot-title-link {
  text-decoration: none;
  color: inherit;
  transition: color 0.3s;
  display: flex;
  align-items: center;
}
.robot-title-link:hover {
  color: #409eff;
}
.status-message {
  margin: 0 0 16px 0;
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.status-text {
    font-weight: 600;
    color: #303133;
}
.divider-text {
  font-size: 13px;
  font-weight: 500;
  color: #909399;
}
.el-divider {
  margin: 20px 0;
}

/* 机器人卡片 - 状态监控 */
.stats-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
}
.stat-label {
  font-size: 14px;
  color: #606266;
  width: 40px;
}
.stat-progress {
  width: 100%;
}

/* 机器人卡片 - 模块控制 */
.commands-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.command-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.command-label {
  font-size: 14px;
  color: #303133;
}

/* 机器人卡片 - 其他 */
.timestamp {
  margin-top: 16px;
  text-align: right;
  color: #c0c4cc;
}
.offline-overlay {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 250px;
  color: #909399;
  text-align: center;
}
.offline-overlay i {
  font-size: 48px;
  margin-bottom: 16px;
}

/* --- 【新增】响应式适配 --- */
@media (max-width: 768px) {
  .control-row {
    flex-direction: column;
    align-items: stretch;
  }
  .control-item {
    justify-content: space-between;
  }
  .management-actions {
    flex-direction: column;
  }
  .session-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>
