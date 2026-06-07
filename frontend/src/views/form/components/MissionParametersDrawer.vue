<template>
  <el-drawer
    title="任务规划参数设置"
    :visible="visible"
    @update:visible="val => $emit('update:visible', val)"
    direction="rtl"
    :wrapperClosable="true"
    size="480px"
    custom-class="mission-drawer"
  >
    <div class="drawer-container">
      <!-- 顶部状态卡片 -->
      <div class="status-card">
        <div class="card-header">
          <i class="el-icon-s-check"></i>
          <span>规划状态</span>
        </div>
        <div class="card-content">
          <div class="status-item">
            <span class="label">任务区域:</span>
            <span class="value" :class="missionLayerPointArr.length > 0 ? 'success' : 'warning'">
              {{ missionLayerPointArr.length > 0 ? '已绘制' : '未绘制' }}
            </span>
          </div>
          <div class="status-item">
            <span class="label">障碍区域:</span>
            <span class="value" :class="obstacleLayerPointArr.length > 0 ? 'success' : 'info'">
              {{ obstacleLayerPointArr.length > 0 ? '已绘制' : '未绘制' }}
            </span>
          </div>
          <div class="status-item">
            <span class="label">起始点:</span>
            <span class="value" :class="locations.length > 0 ? 'success' : 'warning'">
              {{ locations.length }} 个点
            </span>
          </div>
        </div>
      </div>



      <!-- 基础参数卡片 -->
      <div class="parameter-card">
        <div class="card-header">
          <i class="el-icon-s-operation"></i>
          <span>基础参数</span>
        </div>
        <div class="card-content">
          <el-form :model="currentForm" label-width="100px" size="small" class="parameter-form">

            <!-- 1.规划模式 -->
            <el-row :gutter="16">
              <el-col :span="24">
                <el-form-item label="规划模式">
                  <el-radio-group v-model="currentForm.plan_mode">
                    <el-radio :label="1">区域模式</el-radio>
                    <el-radio :label="2">线路模式</el-radio>
                    <el-radio :label="3">沿塔模式</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
            <!-- 2.飞行模式 -->
            <el-row :gutter="16">
              <el-col :span="24">
                <el-form-item label="飞行模式">
                  <el-radio-group v-model="currentForm.fly_mode">
                    <el-radio :label="1">仿地飞行</el-radio>
                    <el-radio :label="2">手动航高</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>


            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="设备数量">
                  <el-input-number
                    v-model="currentForm.number_device"
                    :min="1"
                    :max="8"
                    controls-position="right"
                    style="width: 100%"
                    :disabled="true"
                  ></el-input-number>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="扫描密度(米)">
                  <el-input v-model="currentForm.scan_density">

                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="24">
                <el-form-item label="重叠度">
                  <el-input v-model="currentForm.overlap_degree">
                    <template slot="append">m/s</template>
                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>
            <!-- 4. 动态无人机参数项 (核心) -->
            <el-row 
              :gutter="16" 
              v-for="(config, index) in currentForm.uav_configs" 
              :key="config.uid || index" 
              class="uav-item"
            >
              <el-col :span="24">
                <div class="uav-title">无人机 {{ index + 1 }} 配置</div>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="`型号`">
                  <el-select
                    v-model="config.selectedUav"
                    placeholder="请选择无人机型号"
                    style="width: 100%"
                    value-key="name"
                  >
                    <el-option
                      v-for="(opt, idx) in uavOptions"
                      :key="opt.name || idx"
                      :label="opt.name"
                      :value="opt"
                    ></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="`飞行速度`">
                  <el-input v-model="config.drone_speed" suffix="m/s"></el-input>
                </el-form-item>
              </el-col>
              <!-- 仅在手动航高模式下显示 -->
              <el-col :span="12" v-if="currentForm.fly_mode === 2">
                <el-form-item :label="`椭球高`">
                  <el-input v-model="config.start_height" suffix="m"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12" v-if="currentForm.fly_mode === 2">
                <el-form-item :label="`航线相对高`">
                  <el-input v-model="config.flight_route_height" suffix="m"></el-input>
                </el-form-item>
              </el-col>
            </el-row>

            <!-- 显示选中的数据 -->
            <el-card v-if="selectedUavList.length > 0" style="margin-top: 20px;">
              <div slot="header">已选择的无人机</div>
              <el-tag 
                v-for="(uav, uavIndex) in selectedUavList" 
                :key="uavIndex" 
                type="success" 
                style="margin: 5px;"
                v-if="uav && uav.name"
              >
                {{ uav.name }} ({{ uav.batteryLength }})
              </el-tag>
              <el-divider></el-divider>
            </el-card>
          </el-form>
        </div>
      </div>

      <!-- KML文件上传卡片 -->
      <div class="parameter-card" v-if = "currentForm.plan_mode === 2 || currentForm.plan_mode === 3">
        <div class="card-header">
          <i class="el-icon-folder-opened"></i>
          <span>KML文件配置</span>
        </div>
        <div class="card-content">
          <el-upload
            class="kml-upload-area"
            drag
            action=""
            :before-upload="emitHandleKmlFileSelect"
            accept=".kml"
            :show-file-list="false"
          >
            <div class="upload-content">
              <i class="el-icon-upload2"></i>
              <div class="upload-text">拖拽KML文件或点击上传</div>
            </div>
          </el-upload>
          <div v-if="kmlFileName" class="file-info">
            <i class="el-icon-document"></i>
            <span>{{ kmlFileName }}</span>
            <el-button type="text" size="mini" @click="emitClearKmlFile">清除</el-button>
          </div>
          <el-form :model="currentForm"
                   label-width="80px"
                   size="small"
                   class="parameter-form"
                   style="margin-top: 20px;">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="起飞点">
                  <el-input v-model="currentForm.drone_start" placeholder="起飞点ID"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="降落点">
                  <el-input v-model="currentForm.drone_end" placeholder="降落点ID"></el-input>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
          <el-button
            v-if="currentForm.plan_mode === 2 || currentForm.plan_mode === 3"
            type="primary"
            @click="emitGenerateMissionAreaByKml"
            size="medium"
            :disabled="!canStartPlanningComputed"
            class="start-planning-btn right-btn"
          >
            解析任务区
          </el-button>
          <el-button
            v-if="currentForm.plan_mode === 3"
            type="primary"
            @click="emitAddStartingPointForTowerMode"
            size="medium"
            :disabled="!canStartPlanningComputed"
            class="start-planning-btn right-btn"
          >
            添加起飞点
          </el-button>

        </div>
      </div>

      <!-- 初始位置卡片 -->
      <div class="parameter-card" v-if="currentForm.plan_mode === 1 || currentForm.plan_mode === 2">
        <div class="card-header">
          <i class="el-icon-location-outline"></i>
          <span>初始位置设置</span>
        </div>

        <div class="card-content">
          <div class="location-grid">
            <!-- 遍历locations数组 -->
            <div
              v-for="(loc, index) in locations"
              :key="index"
              class="location-card"
            >
              <div class="location-header">
                <i class="el-icon-location"></i>
                <span>位置 {{ index + 1 }}</span>
                <el-button
                  type="text"
                  size="mini"
                  @click="emitClearLocation(index)"
                >×</el-button>
              </div>
              <div class="location-coords">{{ loc }}</div>
            </div>

            <!-- 如果没有任何起点 -->
            <div v-if="locations.length === 0" class="empty-location">
              <i class="el-icon-map-location"></i>
              <p>请在地图上点击<br>"添加起点"设置位置</p>
            </div>
          </div>
        </div>
      </div>


      <!-- 分配比例卡片 -->
      <div class="parameter-card" v-if="currentForm.number_device >= 1">
        <div class="card-header">
          <i class="el-icon-pie-chart"></i>
          <span>任务分配比例</span>
        </div>
        <div class="card-content">
          <div class="ratio-grid">
            <!-- 使用数组循环 -->
            <div v-for="(ratio, index) in currentForm.distribution_ratios" :key="index" class="ratio-card">
              <div class="ratio-header">
                <span class="device-name">{{ index + 1 }}号设备</span>
                <span class="ratio-value">{{ ratio }}%</span>
              </div>
              <el-slider
                :value="ratio"
                @input="val => updateDistributionRatio(index, val)"
                :max="100"
                :show-tooltip="false"
                class="ratio-slider"
              ></el-slider>
            </div>
          </div>

          <div class="ratio-summary">
            <el-alert
              :title="`总分配比例: ${getRatioSum}%`"
              :type="getRatioSum === 100 ? 'success' : 'warning'"
              :closable="false"
              show-icon
              :description="getRatioSum !== 100 ? '请调整比例使总和等于100%' : '分配比例正确'"
            >
            </el-alert>
          </div>
        </div>
      </div>

    </div>

    <!-- 固定底部操作区 -->
    <div class="drawer-actions">
      <div class="actions-content">
        <div class="validation-info">
          <div
            class="validation-item"
            :class="{'valid': currentForm.plan_mode === 1 ? missionLayerPointArr.length > 0 : kmldir}"
          >
            <i
              :class="currentForm.plan_mode === 1
  ? (missionLayerPointArr.length > 0 ? 'el-icon-check' : 'el-icon-close')
  : (kmldir ? 'el-icon-check' : 'el-icon-close')"
            ></i>
            <span>任务区域</span>
          </div>
          <div class="validation-item" :class="{'valid': getRatioSum === 100}">
            <i :class="getRatioSum === 100 ? 'el-icon-check' : 'el-icon-close'"></i>
            <span>分配比例</span>
          </div>
          <div class="validation-item" :class="{'valid': currentForm.uav_configs}">
            <i :class="currentForm.uav_configs ? 'el-icon-check' : 'el-icon-close'"></i>
            <span>基础参数</span>
          </div>
        </div>

        <div class="action-buttons">
          <el-button @click="$emit('update:visible', false)" size="medium">
            <i class="el-icon-close"></i>
            取消
          </el-button>
          <el-button
            type="primary"
            @click="emitStartMissionPlanner"
            icon="el-icon-s-promotion"
            size="medium"
            :disabled="!canStartPlanningComputed"
            :loading="planningInProgress"
            class="start-planning-btn "
          >
            <span v-if="!planningInProgress">🚁 开始规划任务</span>
            <span v-else>规划中...</span>
          </el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script>
export default {
  name: 'MissionParametersDrawer',
  props: {
    visible: {
      type: Boolean,
      required: true
    },
    form: {
      type: Object,
      required: true
    },
    missionLayerPointArr: {
      type: Array,
      default: () => []
    },
    obstacleLayerPointArr: {
      type: Array,
      default: () => []
    },
    locations: {
      type: Array,
      default: () => []
    },
    kmldir: {
      type: String,
      default: ''
    },
    kmlFileName: {
      type: String,
      default: ''
    },
    uavOptions: {
      type: Array,
      default: () => []
    },
    planningInProgress: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    currentForm: {
      get() {
        return this.form;
      },
      set(val) {
        this.$emit('update:form', val);
      }
    },
    selectedUavList() {
      return this.currentForm.uav_configs
        .filter(config => config.selectedUav && typeof config.selectedUav === 'object' && config.selectedUav.name) // 确保 selectedUav 是一个对象且有 name 属性
        .map((config, index) => {
          const uav = config.selectedUav;
          // 移除了内部的 null 检查，因为 filter 已经处理了
          return {
            index: index + 1,
            name: uav.name,
            batteryLength: uav.batteryLength,
            droneSpeed: config.droneSpeed,
            startHeight: config.startHeight,
            flightRouteHeight: config.flightRouteHeight
          };
        });
    },
    getRatioSum() {
      return this.currentForm.distribution_ratios.reduce((sum, r) => sum + (r || 0), 0);
    },
    canStartPlanningComputed() {
      // 在每次计算属性更新时打印相关值
      console.log('MissionParametersDrawer: currentForm.number_device', this.currentForm.number_device);
      console.log('MissionParametersDrawer: locations.length', this.locations.length);

      if (this.currentForm.plan_mode === 2) {
        return this.kmldir !== '' &&
          this.locations.length > 0 &&
          this.getRatioSum === 100 &&
          !this.planningInProgress &&
          this.currentForm.number_device === this.locations.length;
      } else if (this.currentForm.plan_mode === 3) {
        return this.kmldir !== '' && !this.planningInProgress; // 沿塔模式也需要 KML
      } else if (this.currentForm.plan_mode === 1) {
        // 在区域模式下，设备数量应该等于起点数量
        return this.missionLayerPointArr.length > 0 &&
          this.getRatioSum === 100 &&
          this.currentForm.uav_configs &&
          !this.planningInProgress &&
          this.currentForm.number_device === this.locations.length; // 增加此条件以强制匹配
      }
      return false;
    }
  },
  methods: {
    updateDistributionRatio(index, val) {
      const newRatios = [...this.currentForm.distribution_ratios];
      newRatios[index] = val;
      this.$emit('update:form', { ...this.currentForm, distribution_ratios: newRatios });
    },
    emitHandleKmlFileSelect(file) {
      this.$emit('handleKmlFileSelect', file);
      return false;
    },
    emitClearKmlFile() {
      this.$emit('clearKmlFile');
    },
    emitGenerateMissionAreaByKml() {
      this.$emit('generateMissionAreaByKml');
    },
    emitClearLocation(index) {
      this.$emit('clearLocation', index);
    },
    emitStartMissionPlanner() {
      this.$emit('startMissionPlanner');
    },
    emitAddStartingPointForTowerMode() {
      this.$emit('addStartingPointForTowerMode');
    }
  }
}
</script>

<style scoped>
/* 抽屉样式优化 */
.mission-drawer {
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(20px);
}

/* 关键样式：让抽屉内容可滚动 */
::v-deep .mission-drawer .el-drawer__body {
  padding: 0 !important;
  height: 100%;
  overflow: hidden;
}

.drawer-container {
  padding: 0 20px 100px 20px;
  height: calc(100% - 100px);
  overflow-y: auto;
}

/* 状态卡片 */
.status-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  margin-bottom: 20px;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(102, 126, 234, 0.3);
}

.status-card .card-header {
  background: rgba(255, 255, 255, 0.15);
  padding: 16px 20px;
  color: white;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-card .card-content {
  padding: 20px;
  background: rgba(255, 255, 255, 0.1);
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 8px 0;
}

.status-item:last-child {
  margin-bottom: 0;
}

.status-item .label {
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
}

.status-item .value {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.status-item .value.success {
  background: rgba(76, 175, 80, 0.8);
  color: white;
}

.status-item .value.warning {
  background: rgba(255, 193, 7, 0.8);
  color: #333;
}

.status-item .value.info {
  background: rgba(33, 150, 243, 0.8);
  color: white;
}

/* 参数卡片 */
.parameter-card {
  background: #fff;
  border-radius: 16px;
  margin-bottom: 20px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border: 1px solid #f0f0f0;
}

.parameter-card .card-header {
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  padding: 16px 20px;
  color: #2c3e50;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid #dee2e6;
}

.parameter-card .card-header i {
  color: #667eea;
  font-size: 16px;
}

.parameter-card .card-content {
  padding: 20px;
}

/* KML上传区域 */
.kml-upload-area {
  width: 100%;
}

.upload-content {
  text-align: center;
  padding: 40px 20px;
}

.upload-content i {
  font-size: 48px;
  color: #c0c4cc;
  margin-bottom: 16px;
}

.upload-text {
  color: #606266;
  font-size: 14px;
}

.file-info {
  margin-top: 15px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #e3f2fd 0%, #f3e5f5 100%);
  border-radius: 8px;
  color: #1976d2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
}

/* 位置网格 */
.location-grid {
  display: grid;
  gap: 12px;
}

.location-card {
  background: linear-gradient(135deg, #e8f5e8 0%, #f1f8e9 100%);
  border-radius: 12px;
  padding: 16px;
  border-left: 4px solid #4caf50;
}

.location-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 600;
  color: #2e7d32;
}

.location-coords {
  font-family: 'Courier New', monospace;
  color: #1b5e20;
  background: rgba(255, 255, 255, 0.7);
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
}

.empty-location {
  text-align: center;
  padding: 40px 20px;
  background: #f8f9fa;
  border-radius: 12px;
  border: 2px dashed #dee2e6;
  color: #6c757d;
}

.empty-location i {
  font-size: 32px;
  color: #c0c4cc;
  margin-bottom: 12px;
}

/* 比例网格 */
.ratio-grid {
  display: grid;
  gap: 16px;
}

.ratio-card {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #e9ecef;
}

.ratio-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.device-name {
  font-weight: 600;
  color: #495057;
}

.ratio-value {
  background: #667eea;
  color: white;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.ratio-slider {
  margin: 0;
}

.ratio-summary {
  margin-top: 20px;
}

/* 固定底部操作区 */
.drawer-actions {
  position: fixed;
  bottom: 0;
  right: 0;
  width: 480px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
  z-index: 1001;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.15);
}

.actions-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.validation-info {
  display: flex;
  gap: 16px;
  flex: 1;
}

.validation-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
}

.validation-item.valid {
  color: #4caf50;
}

.validation-item.valid i {
  color: #4caf50;
}

.validation-item i {
  font-size: 14px;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.start-planning-btn {
  background: linear-gradient(135deg, #4caf50 0%, #66bb6a 100%) !important;
  border: none !important;
  font-weight: 600 !important;
  padding: 12px 24px !important;
  font-size: 16px !important;
  box-shadow: 0 4px 15px rgba(76, 175, 80, 0.4);
  transition: all 0.3s ease;
}

.start-planning-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(76, 175, 80, 0.6) !important;
}

.start-planning-btn:disabled {
  background: #9e9e9e !important;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
}

/* 动画效果 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.parameter-card {
  animation: fadeInUp 0.6s ease-out;
}

.parameter-card:nth-child(2) {
  animation-delay: 0.1s;
}

.parameter-card:nth-child(3) {
  animation-delay: 0.2s;
}

.parameter-card:nth-child(4) {
  animation-delay: 0.3s;
}

/* 让按钮靠右 */
.right-btn {
  display: block;
  margin-left: auto;
  margin-top: 10px; /* 可选 */
}
</style>
