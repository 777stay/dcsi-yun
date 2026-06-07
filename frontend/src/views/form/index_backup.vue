
<template>
  <div class="app-container">
    <div class="content-wrapper">
      <!-- 左侧地图区域 -->
      <div class="map-container">
        <!-- 顶部控制栏 -->
        <div class="control-bar">
          <div class="control-section">
            <h3 class="section-title">
              <i class="el-icon-map-location"></i>
              位置搜索
            </h3>
            <div class="search-controls">
              <el-input
                v-model="searchCoords.latitude"
                placeholder="纬度"
                size="small"
                class="coord-input"
              >
                <template slot="prepend">
                  <i class="el-icon-location-outline"></i>
                </template>
              </el-input>
              <el-input
                v-model="searchCoords.longitude"
                placeholder="经度"
                size="small"
                class="coord-input"
              >
                <template slot="prepend">
                  <i class="el-icon-location"></i>
                </template>
              </el-input>
              <el-button
                type="primary"
                size="small"
                icon="el-icon-search"
                @click="updateMapLocation"
                :loading="updating"
              >
                定位
              </el-button>
              <el-button
                type="info"
                size="small"
                icon="el-icon-aim"
                @click="getCurrentLocation"
                :loading="locating"
              >
                当前位置
              </el-button>
            </div>
          </div>

          <div class="control-section">
            <h3 class="section-title">
              <i class="el-icon-folder-opened"></i>
              KML文件
            </h3>
            <div class="kml-controls">
              <el-upload
                class="kml-uploader"
                action=""
                :show-file-list="false"
                :before-upload="handleKmlUpload"
                accept=".kml"
              >
                <el-button
                  type="success"
                  size="small"
                  icon="el-icon-upload"
                >
                  导入KML
                </el-button>
              </el-upload>

              <el-button
                type="danger"
                size="small"
                icon="el-icon-delete"
                @click="clearKmlLayers"
                :disabled="!hasKmlLayers"
              >
                清除图层
              </el-button>
            </div>
          </div>

          <div class="control-section">
            <h3 class="section-title">
              <i class="el-icon-folder-opened"></i>
              山火区域KML文件
            </h3>
            <div class="kml-controls">
              <el-upload
                class="kml-uploader"
                action=""
                :show-file-list="false"
                :before-upload="handleKmlFileUpload"
                accept=".kml"
              >
                <el-button
                  type="success"
                  size="small"
                  icon="el-icon-upload"
                >
                  导入KML
                </el-button>
              </el-upload>

              <el-button
                type="danger"
                size="small"
                icon="el-icon-delete"
                @click="clearKmlLayers"
                :disabled="!hasKmlLayers"
              >
                清除图层
              </el-button>
            </div>
          </div>
        </div>

        <!-- 地图主体 -->
        <div class="map-wrapper">
          <div id="RealMap"></div>

          <!-- 任务规划控制面板 -->
          <transition name="slide-fade">
            <div v-if="isCollectShow" class="planning-panel">
              <div class="panel-header">
                <span class="panel-title">
                  <i class="el-icon-edit"></i>
                  绘制工具
                </span>
                <el-button
                  type="text"
                  icon="el-icon-close"
                  @click="closePlanningPanel"
                  class="close-btn"
                ></el-button>
              </div>

              <div class="panel-content">
                <div class="tool-item" @click="draw_mission_area" :class="{active: currentTool === 'mission_area'}">
                  <div class="tool-icon mission-area">
                    <i class="el-icon-s-grid"></i>
                  </div>
                  <span class="tool-name">绘制任务区</span>
                </div>

                <div class="tool-item" @click="draw_obstacle_area" :class="{active: currentTool === 'obstacle_area'}">
                  <div class="tool-icon obstacle-area">
                    <i class="el-icon-warning"></i>
                  </div>
                  <span class="tool-name">绘制障碍区</span>
                </div>

                <div class="tool-item" @click="add_starting_point" :class="{active: currentTool === 'starting_point'}">
                  <div class="tool-icon start-point">
                    <i class="el-icon-location"></i>
                  </div>
                  <span class="tool-name">添加起点</span>
                  <el-button
                    v-if="currentTool === 'starting_point'"
                    type="text"
                    size="mini"
                    @click.stop="cancelStartingPoint"
                    class="cancel-btn"
                  >
                    取消
                  </el-button>
                </div>

                <div class="tool-item" @click="set_mission_planner_form">
                  <div class="tool-icon settings">
                    <i class="el-icon-setting"></i>
                  </div>
                  <span class="tool-name">参数设置</span>
                </div>

                <div class="tool-item" @click="disdraw">
                  <div class="tool-icon clear">
                    <i class="el-icon-refresh-left"></i>
                  </div>
                  <span class="tool-name">清除绘制</span>
                </div>
              </div>
            </div>
          </transition>

          <!-- 主控制按钮 -->
          <div class="main-controls">
            <el-button
              type="primary"
              size="large"
              icon="el-icon-s-promotion"
              @click="mission_planner"
              class="mission-btn"
              round
            >
              {{ isCollectShow ? '关闭规划' : '任务规划' }}
            </el-button>
          </div>

          <!-- 航线显示状态 -->
          <div v-if="missionRoutes.length > 0" class="route-status">
            <div class="status-header">
              <i class="el-icon-success"></i>
              航线规划完成
            </div>
            <div class="route-list">
              <div v-for="(route, index) in missionRoutes" :key="index" class="route-item">
                <span class="route-indicator" :style="{backgroundColor: routeColors[index]}"></span>
                <span class="route-name">航线 {{ index + 1 }}</span>
                <span class="route-points">{{ route.length }} 个航点</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 任务规划参数抽屉 - 重新设计布局 -->
      <el-drawer
        title="任务规划参数设置"
        :visible.sync="mission_planner_form"
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
                <span class="value" :class="mission_layer_point_arr.length > 0 ? 'success' : 'warning'">
                  {{ mission_layer_point_arr.length > 0 ? '已绘制' : '未绘制' }}
                </span>
              </div>
              <div class="status-item">
                <span class="label">障碍区域:</span>
                <span class="value" :class="obstacle_layer_point_arr.length > 0 ? 'success' : 'info'">
                  {{ obstacle_layer_point_arr.length > 0 ? '已绘制' : '未绘制' }}
                </span>
              </div>
              <div class="status-item">
                <span class="label">起始点:</span>
                <span class="value" :class="(location1 || location2 || location3) ? 'success' : 'warning'">
                  {{ getLocationCount() }} 个点
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
              <el-form :model="form" label-width="100px" size="small" class="parameter-form">

                <el-row :gutter="16">
                  <el-col :span="24">
                    <el-form-item label="规划模式">
                      <el-radio-group v-model="form.plan_mode">
                        <el-radio :label="1">区域模式</el-radio>
                        <el-radio :label="2">线路模式</el-radio>
                      </el-radio-group>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="16">
                  <el-col :span="24">
                    <el-form-item label="飞行模式">
                      <el-radio-group v-model="form.fly_mode">
                        <el-radio :label="1">仿地飞行</el-radio>
                        <el-radio :label="2">手动航高</el-radio>
                      </el-radio-group>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="16" v-if = "form.fly_mode === 2">
                  <el-col :span="12">
                    <el-form-item label="起飞点椭球高">
                      <el-input v-model="form.start_height">
                      </el-input>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="航线相对高">
                      <el-input v-model="form.flight_route_height">
                      </el-input>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="设备数量">
                      <el-input-number
                        v-model="form.number_device"
                        :min="1"
                        :max="8"
                        controls-position="right"
                        style="width: 100%"
                        :disabled="form.plan_mode === 1"
                      ></el-input-number>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="扫描密度(米)">
                      <el-input v-model="form.scan_density">

                      </el-input>
                    </el-form-item>
                  </el-col>

                </el-row>

                <el-row :gutter="16">
                  <el-col :span="24">
                    <el-form-item label="飞行速度">
                      <el-input v-model="form.drone_speed">
                        <template slot="append">m/s</template>
                      </el-input>
                    </el-form-item>
                  </el-col>
                </el-row>

                <!-- 动态生成多个 select -->
                <el-row :gutter="16" v-for="(item, index) in form.number_device" :key="index">
                  <el-col :span="24">
                    <el-form-item :label="`无人机 ${index + 1}`">
                      <el-select
                        v-model="form.selected_uav[index]"
                        placeholder="请选择无人机型号"
                        style="width: 100%"
                      >
                      <el-option
                          v-for="(opt, index) in uavOptions"
                          :key="index"
                          :label="opt.label"
                          :value="opt"
                      ></el-option>
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>

                <!-- 显示选中的数据 -->
              <el-card v-if="selectedUavList.length > 0" style="margin-top: 20px;">
                <div slot="header">已选择的无人机</div>
                <el-tag 
                  v-for="uav in selectedUavList" 
                  :key="uav.id"
                  type="success" 
                  style="margin: 5px;">
                  {{ uav.name }} ({{ uav.model }})
                </el-tag>
                <el-divider></el-divider>
                <!-- <pre>{{ JSON.stringify(selectedUavList, null, 2) }}</pre> -->
              </el-card>


              </el-form>
            </div>
          </div>

          <!-- KML文件上传卡片 -->
          <div class="parameter-card" v-if = "form.plan_mode === 2">
            <div class="card-header">
              <i class="el-icon-folder-opened"></i>
              <span>KML文件配置</span>
            </div>
            <div class="card-content">
              <el-upload
                class="kml-upload-area"
                drag
                action=""
                :before-upload="handleKmlFileSelect"
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
                <el-button type="text" size="mini" @click="clearKmlFile">清除</el-button>
              </div>
              <el-form :model="form"
                       label-width="80px"
                       size="small"
                       class="parameter-form"
                       style="margin-top: 20px;">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="起飞点">
                      <el-input v-model="form.drone_start" placeholder="起飞点ID"></el-input>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="降落点">
                      <el-input v-model="form.drone_end" placeholder="降落点ID"></el-input>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
              <el-button
                type="primary"
                @click="generateMissionAreaByKml"
                size="medium"
                :disabled="!canStartPlanning"
                class="start-planning-btn right-btn"
              >
                解析任务区
              </el-button>

            </div>
          </div>

          <!-- 初始位置卡片 -->
          <div class="parameter-card" v-if="form.plan_mode === 1">
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
                      @click="clearLocation(index)"
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
          <div class="parameter-card" v-if="form.number_device >= 1">
            <div class="card-header">
              <i class="el-icon-pie-chart"></i>
              <span>任务分配比例</span>
            </div>
            <div class="card-content">
              <div class="ratio-grid">
                <!-- 使用数组循环 -->
                <div v-for="(ratio, index) in form.distribution_ratios" :key="index" class="ratio-card">
                  <div class="ratio-header">
                    <span class="device-name">{{ index + 1 }}号设备</span>
                    <span class="ratio-value">{{ ratio }}%</span>
                  </div>
                  <el-slider
                    v-model="form.distribution_ratios[index]"
                    :max="100"
                    :show-tooltip="false"
                    class="ratio-slider"
                  ></el-slider>
                </div>
              </div>

              <div class="ratio-summary">
                <el-alert
                  :title="`总分配比例: ${getRatioSum()}%`"
                  :type="getRatioSum() === 100 ? 'success' : 'warning'"
                  :closable="false"
                  show-icon
                  :description="getRatioSum() !== 100 ? '请调整比例使总和等于100%' : '分配比例正确'"
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
                :class="{'valid': form.plan_mode === 1 ? mission_layer_point_arr.length > 0 : kmldir}"
              >
                <i
                  :class="form.plan_mode === 1
      ? (mission_layer_point_arr.length > 0 ? 'el-icon-check' : 'el-icon-close')
      : (kmldir ? 'el-icon-check' : 'el-icon-close')"
                ></i>
                <span>任务区域</span>
              </div>
              <div class="validation-item" :class="{'valid': getRatioSum() === 100}">
                <i :class="getRatioSum() === 100 ? 'el-icon-check' : 'el-icon-close'"></i>
                <span>分配比例</span>
              </div>
              <div class="validation-item" :class="{'valid': form.selected_uav}">
                <i :class="form.selected_uav ? 'el-icon-check' : 'el-icon-close'"></i>
                <span>基础参数</span>
              </div>
            </div>

            <div class="action-buttons">
              <el-button @click="mission_planner_form = false" size="medium">
                <i class="el-icon-close"></i>
                取消
              </el-button>
              <el-button
                type="primary"
                @click="startMissionPlanner"
                icon="el-icon-s-promotion"
                size="medium"
                :disabled="!canStartPlanning"
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

       <!-- 图片弹窗对话框 -->
      <el-dialog
        :visible.sync="imageDialogVisible"
        :title="imageDialogTitle"
        width="80%"
        center
      >
        <div style="text-align: center;">
          <img 
            :src="currentImageUrl" 
            alt="区域相关图片" 
            style="max-width: 100%; max-height: 600px;"
            @error="handleImageError"
          >
        </div>
        <div slot="footer" class="dialog-footer">
          <el-button @click="imageDialogVisible = false">关闭</el-button>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script>
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet.pm'
import axios from 'axios'
import * as toGeoJSON from '@mapbox/togeojson'
import { drawMissionRoutes, fitMapToMissionRoutes, clearMissionPolylines} from './missionPlanner'

export default {
  name: 'PathPlanning',
  data() {
    return {
      map: null,
      isCollectShow: false,
      mission_planner_form: false,
      rectangleLayer: null,
      LatLngjson: [],
      missionLayerPointArr: [],//用于任务规划函数的参数传输
      obstacleLayerPointArr: [],//用于任务规划函数的参数传输
      location1: '',
      location2: '',
      location3: '',
      kmldir: '',
      kmlFileName: '',
      mission_layer_point_arr: [],
      obstacle_layer_point_arr: [],
      missionRoutes: [],
      // routeColors: ['#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A', '#98D8C8'],
      // 8组高区分度颜色：红→蓝→绿→橙→紫→青→深紫→深绿，循环使用无压力
      routeColors : [
        '#FF4444', // 亮红（醒目，适合第一段）
        '#3366FF', // 深蓝（清晰，与红色对比强）
        '#2ECC71', // 亮绿（清爽，区分度高）
        '#FF8800', // 橙色（温暖，不与其他色混淆）
        '#9933FF', // 紫色（独特，避免视觉疲劳）
        '#00CCFF', // 青色（明快，适合多分段场景）
        '#663399', // 深紫（与浅紫区分，增加层次）
        '#1A9988'  // 深绿（与亮绿区分，适配多段后不重复）
      ],
      missionPolylines: [],//航线
      planningInProgress: false,
      // 新增：当前工具状态和事件监听器管理
      currentTool: '',
      clickListener: null,
      createListener: null,
      startingPointMarkers: [], // 存储起点标记
      form: {
        selected_uav: [],
        plan_mode: 1,
        number_device: 0,
        scan_density: 20,
        drone_speed: 10,
        drone_start: 0,
        drone_end: 5,
        pathsStrictlyInPoly: false,
        initial_locations:this.locations,
        distribution_ratios: [],
        fly_mode:1,
        start_height:20,
        flight_route_height:20,
      },
      searchCoords: {
        latitude: '',
        longitude: ''
      },
      updating: false,
      locating: false,
      currentMarker: null,
      kmlLayers: [],
      hasKmlLayers: false,
      locations:[],
      // 所有可选的无人机型号
      uavOptions: [
        { label: "Matrice 300 RTK", value: 3000 },
        { label: "Matrice 350 RTK", value: 10800 },
        { label: "Matrice 400", value: 12600 }
      ],
      imageDialogVisible: false,
      currentImageUrl: '',
      imageDialogTitle: '',
      missionId: 1, // 根据实际情况设置
      missionAreaLayers: [], // 存储所有绘制的任务区域图层
    }
  },
   created() {
  },
  watch:{
    // 当规划模式变化
    'form.plan_mode'(newVal) {
      if (newVal === 1) {
        // 模式1：跟随起点数量
        this.form.number_device = this.locations.length ||0;
        console.log(this.form.number_device)
        this.updateDistributionRatios(this.form.number_device);
      }else if (newVal === 2) {
        this.form.number_device = this.locations.length ||0;
      }
    },

    // 当无人机数量变化（仅模式2有效）
    'form.number_device'(newVal) {
      if (this.form.plan_mode === 2) {
        this.updateDistributionRatios(newVal);
      }
    },

    // 当起点变化（仅模式1有效）
    locations(newVal) {
      if (this.form.plan_mode === 1) {
        this.form.number_device = newVal.length || 0;
        this.updateDistributionRatios(this.form.number_device);
      }
    }
  },
  computed: {
    canStartPlanning() {
      if (this.form.plan_mode === 2) {
        return this.kmldir!=''
      }else if(this.form.plan_mode === 1) {
        return this.mission_layer_point_arr.length > 0 &&
          this.getRatioSum() === 100 &&
          this.form.selected_uav &&
          !this.planningInProgress
      }
      return false;
    },
    selectedUavList() {
      return this.form.selected_uav
        .filter(uav => uav !== null && uav !== undefined)
        .map((uav, index) => ({
          index: index + 1,
          id: uav.id,          // 包含 ID
          name: uav.label,     // label
          model: uav.value,    // value
          specs: uav.specs     // 额外信息
        }));
    }
  },
  mounted() {
    this.initMap()
  },
  methods: {
    initMap() {
      this.map = L.map("RealMap", {
        center: [23.3370, 113.0070],
        zoom: 17,
        zoomControl: true,
        doubleClickZoom: true,
        attributionControl: false,
      });

      L.tileLayer(
        'http://t{s}.tianditu.gov.cn/img_w/wmts?tk=5e3672fc0409d68d282e328ddd3db78a&SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TileMatrix={z}&TileCol={x}&TileRow={y}', {
        subdomains: ["0", "1", "2", "3", "4", "5", "6", "7"]
      }).addTo(this.map);
    },

    // 修复：清理所有事件监听器
    clearAllEventListeners() {
      // 清理点击监听器
      if (this.clickListener) {
        this.map.off('click', this.clickListener);
        this.clickListener = null;
      }

      // 清理创建监听器
      if (this.createListener) {
        this.map.off('pm:create', this.createListener);
        this.createListener = null;
      }

      // 禁用所有绘制模式
      this.map.pm.disableDraw();

      this.currentTool = '';
    },

    mission_planner() {
      if (this.isCollectShow) {
        this.closePlanningPanel();
      } else {
        this.isCollectShow = true;
      }
    },

    // 新增：关闭规划面板时清理所有状态
    closePlanningPanel() {
      this.isCollectShow = false;
      this.clearAllEventListeners();
      this.$message.info('已关闭任务规划面板');
    },

     handleKmlFileSelect(file) {
      this.handleKmlUpload(file);
      const reader = new FileReader();
      this.kmlFileName = file.name;

      reader.addEventListener("load", () => {
        this.kmldir = reader.result;
        this.$message.success('KML文件读取成功');
      }, false);

      if (file) {
        reader.readAsText(file);
      }
      return false;
    },

    clearKmlFile() {
      this.kmldir = '';
      this.kmlFileName = '';
      this.$message.info('已清除KML文件');
    },

    // 修复：绘制任务区域
    draw_mission_area() {
      // 先清理所有事件监听器
      this.clearAllEventListeners();

      this.currentTool = 'mission_area';
      this.$message.info('请在地图上绘制任务区域');

      const polygonOptions = {
        pathOptions: {
          color: "#4CAF50",
          fillColor: "#81C784",
          fillOpacity: 0.3,
          weight: 3
        },
      };
      
      this.map.pm.enableDraw("Polygon", polygonOptions);

      // 创建新的监听器函数
      this.createListener = (e) => {
        let target = [];
        if (e.shape === "Polygon" || e.shape === "Rectangle") {
          this.map.fitBounds(e.layer._latlngs);
          this.LatLngjson = e.layer._latlngs[0];
          for (let i = 0; i < this.LatLngjson.length; i++) {
            let arr = [this.LatLngjson[i].lng, this.LatLngjson[i].lat];
            target.push(arr);
          }
        }
        this.mission_layer_point_arr = target;

        // 绘制时 push 到数组
        this.missionPolygon=e.layer;
    

        this.$message.success('任务区域绘制完成');

        // 完成后清理状态
        this.clearAllEventListeners();
      };

      this.map.on("pm:create", this.createListener);
    },

    // 修复：绘制障碍区域
    draw_obstacle_area() {
      // 先清理所有事件监听器
      this.clearAllEventListeners();

      this.currentTool = 'obstacle_area';
      this.$message.info('请在地图上绘制障碍区域');

      const polygonOptions = {
        pathOptions: {
          color: "#F44336",
          fillColor: "#EF5350",
          fillOpacity: 0.4,
          weight: 3
        },
      };

      this.map.pm.enableDraw("Polygon", polygonOptions);

      // 创建新的监听器函数
      this.createListener = (e) => {
        let target = [];
        if (e.shape === "Polygon" || e.shape === "Rectangle") {
          this.LatLngjson = e.layer._latlngs[0];
          for (let i = 0; i < this.LatLngjson.length; i++) {
            let arr = [this.LatLngjson[i].lng, this.LatLngjson[i].lat];
            target.push(arr);
          }
        }
        this.obstacle_layer_point_arr = target;
        this.$message.warning('障碍区域绘制完成');

        // 完成后清理状态
        this.clearAllEventListeners();
      };

      this.map.on("pm:create", this.createListener);
    },

// 修复：添加起点
    add_starting_point() {
      // 清理之前的监听器
      this.clearAllEventListeners();

      this.currentTool = 'starting_point';
      this.$message.info('请在地图上点击添加起点（最多5个）');

      // 创建点击监听器
      this.clickListener = (e) => {
        // 如果达到上限，直接返回
        if (this.locations.length >= 5) {
          this.$message.warning('最多只能添加 5 个起点');
          return;
        }

        const lngLat = `${e.latlng.lng.toFixed(6)},${e.latlng.lat.toFixed(6)}`;

        // 添加到数组
        this.locations.push(lngLat);
        console.log("把起始点添加到locations"+this.locations);
        this.form.distribution_ratios = this.updateDistributionRatios(this.locations.length);
        this.form.initial_locations = this.locations;
        this.form.number_device = this.locations.length;
        const pointNumber = this.locations.length;

        // 创建 Leaflet 图标
        const startIcon = L.icon({
          iconUrl: require('leaflet/dist/images/marker-icon-2x.png'),
          iconSize: [25, 41],
          iconAnchor: [12, 41],
          popupAnchor: [1, -34],
          shadowUrl: require('leaflet/dist/images/marker-shadow.png'),
          shadowSize: [41, 41]
        });

        // 在地图上添加标记
        const marker = L.marker([e.latlng.lat, e.latlng.lng], { icon: startIcon })
          .addTo(this.map)
          .bindPopup(`起点${pointNumber}: ${lngLat}`);

        // 存储标记用于后续清理
        this.startingPointMarkers.push({
          marker: marker,
          index: pointNumber - 1,   // 与数组索引保持一致
          locationKey: `location${pointNumber}`
        });
        console.log(this.startingPointMarkers);

        this.$message.success(`已添加起点${pointNumber}`);

        // 如果达到最大数量，自动退出添加模式
        if (this.locations.length === 5) {
          this.clearAllEventListeners();
          this.$message.info('已达到最大起点数量，退出添加模式');
        }
      };

      this.map.on('click', this.clickListener);
    },


    // 新增：取消添加起点
    cancelStartingPoint() {
      this.clearAllEventListeners();
      this.$message.info('已取消添加起点');
    },

// 清除位置并清除对应地图标记
    clearLocation(index) {
      // index 是 0-based，和 locations 数组一致
      // 1. 从 locations 数组中移除对应位置
      this.locations.splice(index, 1);

      // 2. 清除对应的地图标记
      // 先找到要删除的那一个
      const markerObj = this.startingPointMarkers.find(m => m.index === index);
      if (markerObj) {
        this.map.removeLayer(markerObj.marker);
      }

      // 3. 把起点标记数组中过滤掉删除的那个
      this.startingPointMarkers = this.startingPointMarkers.filter(m => m.index !== index);

      // 4. 重新整理剩下标记的 index 和 popup（保持编号和 locations 数组一致）
      this.startingPointMarkers.forEach((m, i) => {
        m.index = i; // 更新索引
        const latlng = m.marker.getLatLng();
        m.marker.bindPopup(`起点${i + 1}: ${latlng.lng.toFixed(6)},${latlng.lat.toFixed(6)}`);
      });

      // 5. 同步表单
      this.form.initial_locations = [...this.locations];
      this.form.number_device = this.locations.length;
      this.form.distribution_ratios = this.updateDistributionRatios(this.locations.length);

      // 6. 提示
      this.$message.info(`已清除位置${index + 1}`);
    },


    // 修复：清除绘制时也要清理事件监听器
    disdraw() {
      this.clearAllEventListeners();

      // 清除地图上的所有绘制图层
      this.map.eachLayer((layer) => {
        if (layer.pm && layer !== this.map) {
          this.map.removeLayer(layer);
        }
      });

      // 清除起点标记
      this.startingPointMarkers.forEach(item => {
        this.map.removeLayer(item.marker);
      });
      this.startingPointMarkers = [];

      // 清除航线
      this.missionPolylines.forEach(polyline => {
        this.map.removeLayer(polyline);
      });
      this.missionPolylines = [];
      this.missionRoutes = [];

      // 重置数据
      this.LatLngjson = [];
      this.mission_layer_point_arr = [];
      this.obstacle_layer_point_arr = [];
      this.$message.success('已清除所有绘制');

      this.locations = [];
    },

    set_mission_planner_form() {
      // 清理当前工具状态
      this.clearAllEventListeners();
      this.mission_planner_form = true;
    },

    getLocationCount() {
      let count = 0;
      if (this.location1) count++;
      if (this.location2) count++;
      if (this.location3) count++;
      return count;
    },

    async startMissionPlanner() {
      if (!this.canStartPlanning) {
        this.$message.error('请完成必要的设置后再开始规划');
        return;
      }
      if(this.form.selected_uav.length <this.form.number_device) {
        this.$message.error('请选择无人机型号');
        return;
      }
      this.planningInProgress = true;
      try {
        let missionData; 
        try{
          const payload = this.buildPayload();
          const config = this.buildConfig();

          console.log('发送任务规划请求...', payload);
          const response = await axios.post(
            'http://192.168.1.110:8080/api/mission/plan',
            payload,
            config
          );
          console.log('后端返回数据:', response.data);
          missionData = response.data.data;
        }catch(error){
           // 捕获并处理所有可能的错误
          console.error('请求失败:', error);
          
          // 更详细的错误处理
          if (error.response) {
            // 服务器有响应，但状态码不是 2xx
            console.error('响应状态码:', error.response.status);
            console.error('响应数据:', error.response.data);
            // 可以根据不同状态码进行处理，例如 401 未授权、404 资源不存在等
          } 
        }
       

        // 清除之前的航线
        clearMissionPolylines(this.map, this.missionPolylines);
        this.missionRoutes = [];

        if (Array.isArray(missionData) && missionData.length > 0) {

          const uavType = this.form.selected_uav.slice(0, this.form.number_device)

          // 调用工具函数绘制航线
          drawMissionRoutes(
            missionData,
            uavType,
            this.routeColors,
            this.map,
            this.missionPolylines,
            this.missionRoutes
          );

          // 调整地图视口
          fitMapToMissionRoutes(this.missionRoutes, this.map);

          this.$notify({
            title: '规划成功',
            message: `成功规划${this.missionRoutes.length}条航线！`,
            type: 'success',
            duration: 4000
          });
          this.mission_planner_form = false;
        } else {
          this.$message.warning('后端未返回有效的航线数据');
        }

        // 关键：规划成功后，为之前存储的任务区图层绑定事件
      this.bindMissionAreaEventsAfterPlanning();

      } catch (error) {
        console.error('规划失败:', error);
        this.$notify({
          title: '规划失败',
          message: error.response?.data?.message || '航线规划失败，请检查参数设置',
          type: 'error',
          duration: 4000
        });
      } finally {
        this.planningInProgress = false;
      }
    },


    bindMissionAreaEventsAfterPlanning() {
      const layer = this.missionPolygon; // 单个图层对象，不是数组
      
      // 验证图层有效性
      if (!layer || typeof layer.on !== 'function') {
        console.error('任务区图层无效，无法绑定事件');
        this.$message.error('任务区图层异常，无法点击查看详情');
        return;
      }

      // 直接给单个图层绑定事件（不用forEach）
      layer.on('click', (clickEvent) => {
        this.showImageDialog(clickEvent.latlng);
      });
      layer.on('mouseover', () => {
        this.map.getContainer().style.cursor = 'pointer';
      });
      layer.on('mouseout', () => {
        this.map.getContainer().style.cursor = '';
      });

      this.$message.info('任务区域已可点击查看详情');
    },


    // 显示图片对话框
    showImageDialog(latlng) {
      // const imageUrl = this.getImageUrlByLocation(latlng);
      const imageUrl = require("@/assets/fire.png");
      this.imageDialogVisible = true;
      this.currentImageUrl = imageUrl;
      this.imageDialogTitle = `区域内位置: ${latlng.lng.toFixed(6)}, ${latlng.lat.toFixed(6)}`;
    },


    buildPayload() {
      return {
        numberDevice: this.form.number_device,
        scanDensity: this.form.scan_density,
        planMode: this.form.plan_mode,
        droneStart: this.form.drone_start,
        droneEnd: this.form.drone_end,
        droneSpeed: this.form.drone_speed,
        kmldir: this.kmldir,
        pathsStrictlyInPoly: this.form.pathsStrictlyInPoly,
        missionLayerPointArr: this.mission_layer_point_arr,
        obstacleLayerPointArr: this.obstacle_layer_point_arr,
        initialLocations: this.form.initial_locations,
        distributionRatios: this.form.distribution_ratios,
        flyMode:this.form.fly_mode,
        startHeight:this.form.start_height,
        flightRouteHeight:this.form.flight_route_height,
        uavType:this.form.selected_uav.slice(0, this.form.number_device)
      };
    },
    buildConfig() {
      const token = this.$store.getters.token;
      return {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      };
    },

    updateMapLocation() {
      const lat = parseFloat(this.searchCoords.latitude);
      const lng = parseFloat(this.searchCoords.longitude);

      if (isNaN(lat) || isNaN(lng)) {
        this.$message.error('请输入有效的经纬度');
        return;
      }

      if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
        this.$message.error('经纬度超出有效范围');
        return;
      }

      this.updating = true;

      if (this.currentMarker) {
        this.currentMarker.remove();
      }

      this.map.setView([lat, lng], 15);
      this.currentMarker = L.marker([lat, lng])
        .addTo(this.map)
        .bindPopup(`纬度: ${lat}<br>经度: ${lng}`)
        .openPopup();

      this.updating = false;
    },

    getCurrentLocation() {
      if (!navigator.geolocation) {
        this.$message.error('您的浏览器不支持地理位置功能');
        return;
      }

      this.locating = true;
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const { latitude, longitude } = position.coords;
          this.searchCoords.latitude = latitude.toFixed(6);
          this.searchCoords.longitude = longitude.toFixed(6);
          this.updateMapLocation();
          this.locating = false;
        },
        (error) => {
          this.$message.error('获取当前位置失败');
          this.locating = false;
        },
        {
          enableHighAccuracy: true,
          timeout: 5000,
          maximumAge: 0
        }
      );
    },

    async handleKmlUpload(file) {
      const reader = new FileReader();

      reader.onload = async (e) => {
        try {
          // 步骤1：解析KML为XML文档
          const parser = new DOMParser();
          const kml = parser.parseFromString(e.target.result, 'text/xml');

          // 步骤2：KML转GeoJSON（地图库通用格式）
          const geoJson = toGeoJSON.kml(kml);

          // 步骤3：用Leaflet渲染GeoJSON到地图
          const kmlLayer = L.geoJSON(geoJson, {
            // 重点：处理“点”的样式，替换为 KML 里的靶心图标
            pointToLayer: function (feature, latlng) {
              // return L.marker(latlng, {
              //   icon: L.icon({
              //     // 方式1：用 Google 官方图标（国内可能访问慢）
              //     // iconUrl: 'http://maps.google.com/mapfiles/kml/shapes/target.png',
              //     // 方式2：用项目本地图标（更稳定，推荐）
              //     iconUrl: require('@/assets/tower1.png'), // Vue 项目中引入本地图片
              //     iconSize: [24, 24], // 图标大小（适配地图）
              //     iconAnchor: [12, 12] // 图标中心对准坐标点
              //   })
              // }).bindPopup(`<b>节点：${feature.properties.name}</b>`); // 点击显示节点名称（61/62等）
              return L.marker(latlng, {
                icon: L.divIcon({
                  html: `
                          <div style="text-align:center;">
                            <img src="${require('@/assets/tower1.png')}" style="width:24px;height:24px;" />
                            <div style="color:red; font-weight:bold; font-size:12px;">${feature.properties.name || ''}</div>
                          </div>
                        `,
                  className: 'custom-div-icon', // 自定义类名（可选）
                  iconSize: [40, 40], // 图标整体大小（宽高）
                  iconAnchor: [20, 20] // 锚点位置（图标中心点对准坐标）
                })
              });
            },
            // 原有线/面样式（当前 KML 是“点”，此配置暂不影响）
            style: {
              color: '#3388ff',
              weight: 3,
              opacity: 0.8
            }
          }).addTo(this.map);

          // 【关键】保存 KML 内容到组件实例，供后端接口使用
          this.kmldir = e.target.result;

          // // 步骤4：调用后端接口（await 等待请求完成）
          // await this.generateMissionAreaByKml();

          // 步骤5：管理图层与地图视角
          this.kmlLayers.push(kmlLayer);
          this.hasKmlLayers = true;
          this.map.fitBounds(kmlLayer.getBounds());

          this.$message.success('KML文件导入成功');
        } catch (error) {
          // 异常处理：解析失败时提示
          console.error('KML解析错误:', error);
          this.$message.error('KML文件解析失败');
        }
      };
      reader.readAsText(file);
      return false;
    },

    clearKmlLayers() {
      this.$confirm('确定要清除所有KML图层吗?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.kmlLayers.forEach(layer => {
          this.map.removeLayer(layer);
        });
        this.kmlLayers = [];
        this.hasKmlLayers = false;
        this.$message.success('已清除所有KML图层');
      }).catch(() => {});
    },

    getRatioSum() {
      return this.form.distribution_ratios.reduce((sum, r) => sum + (r || 0), 0);
    },

    // 根据设备数量平分100
    updateDistributionRatios(count) {
      if (count <= 0) return;

      const base = Math.floor(100 / count);
      let remainder = 100 - base * count;

      // 分配比例数组
      this.form.distribution_ratios = Array.from({ length: count }, () => base);

      // 把余数分给最后几个
      for (let i = 0; remainder > 0; i++) {
        this.form.distribution_ratios[i] += 1;
        remainder--;
      }
    },

    //解析任务区函数
    async generateMissionAreaByKml() {
      try {
        // 1. 认证校验（若需登录态）
        const token = this.$store.getters.token;
        // if (!token) {
        //   this.$message.error('认证失败，请先登录');
        //   return;
        // }

        // 2. 构造请求头与参数
        const config = {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        };

        const payload = {
          numberDevice: this.form.number_device,
          scanDensity: this.form.scan_density,
          planMode: this.form.plan_mode,
          droneStart: this.form.drone_start,
          droneEnd: this.form.drone_end,
          droneSpeed: this.form.drone_speed,
          kmldir: this.kmldir,
          pathsStrictlyInPoly: this.form.pathsStrictlyInPoly,
          distributionRatios: this.form.distribution_ratios,
          selectUavs: (this.form.selected_uav || []).slice(0, this.form.number_device)
        };

        // 3. 调用后端接口
        console.log(payload);
        // 示例：axios 请求中调用绘制方法
        axios.post("http://192.168.1.110:8080/api/mission/uploadKML", payload, config)
          .then((response) => {
            // 箭头函数会继承外层 this（组件实例）
            console.log('后端返回数据BA:', response.data);
            this.drawMissionByResponse(response.data);

          })
          .catch((err) => {
            console.error("请求失败", err);
          });

        // （可选）根据后端返回数据更新页面，比如渲染任务路径
        // this.renderMissionPath(response.data);

      } catch (error) {
        // 4. 详细错误处理
        console.error('任务规划请求错误:', error);
        let errorMsg = '任务规划失败，请检查参数或网络';

        if (error.response) {
          // 后端返回了错误状态（如400、500）
          errorMsg = `后端错误（${error.response.status}）：${error.response.data.message || '未知错误'}`;
          console.log('响应数据:', error.response.data);
        } else if (error.request) {
          // 请求发出去了，但没收到响应（如后端挂了、网络不通）
          errorMsg = '请求超时或后端无响应，请检查服务';
        } else {
          // 请求配置错误（如参数类型错）
          errorMsg = `请求配置错误：${error.message}`;
        }

        this.$message.error(errorMsg);
      }
    },
    /**
     * 解析任务区子函数：获取任务区 根据后端返回数据自动绘制任务区、障碍区和无人机初始位置
     * @param {Object} data - 后端返回的 response.data
     */
    drawMissionByResponse(data) {
      // 现在 this.map 已经是初始化后的地图实例，可安全调用
      if (!Array.isArray(data.data.missionLayerPointArr)) {
        this.$message.error("任务区数据不是数组，请检查后端返回结构");
        return;
      }

      // 1. 清理旧图层和事件
      this.clearAllEventListeners();
      if (this.missionPolygon) this.map.removeLayer(this.missionPolygon);
      if (this.obstacleLayerPointArr) this.map.removeLayer(this.obstacleLayerPointArr);
      if (this.initialMarkers && this.initialMarkers.length) {
        this.initialMarkers.forEach(m => this.map.removeLayer(m));
        this.initialMarkers = [];
      }

      this.currentTool = 'mission_auto';
      this.$message.info('正在根据后端数据绘制任务区、障碍区和初始位置...');

      try {
        // 2. 绘制任务区
        const missionLatLngs = data.data.missionLayerPointArr.map(p => [p.lat, p.long]);
        if (missionLatLngs.length > 0) {
          // 闭合多边形
          const first = missionLatLngs[0];
          const last = missionLatLngs[missionLatLngs.length - 1];
          if (first[0] !== last[0] || first[1] !== last[1]) {
            missionLatLngs.push(first);
          }
          this.missionPolygon = L.polygon(missionLatLngs, {
            color: "#4CAF50",
            fillColor: "#81C784",
            fillOpacity: 0.3,
            weight: 3
          }).addTo(this.map);
          // this.missionPolygon.bindPopup(`
          //     <b>任务区域</b><br>
          //     无人机数量：${data.numberDevice}<br>
          //     扫描密度：${data.scanDensity}m
          //   `);
          
        }
        // 4. 绘制无人机初始位置
        this.initialMarkers = [];
        if (data.data.initialLocations && data.data.initialLocations.length) {
          const colors = ["#2196F3", "#4CAF50", "#FFC107"]; // 不同无人机不同颜色
          data.data.initialLocations.forEach((loc, idx) => {
            const marker = L.marker([loc.lat, loc.long], {
              icon: L.divIcon({
                className: 'custom-div-icon',
                // html: `<div style="background-color:${colors[idx % colors.length]};width:12px;height:12px;border-radius:50%;border:2px solid white;"></div>`,
                html: `
                          <div style="text-align:center;">
                            <img src="${require('@/assets/drone.png')}" style="width:24px;height:24px;" />
                            <div style="color:red; font-weight:bold; font-size:12px;">drone${idx}</div>
                          </div>
                        `,
                iconSize: [16, 16],
                iconAnchor: [8, 8]
              })
            }).addTo(this.map);
            marker.bindPopup(`<b>无人机 ${idx + 1}</b><br>初始位置`);
            this.initialMarkers.push(marker);
          });
        }

        // 5. 地图视野自适应
        if (this.missionPolygon) {
          this.map.fitBounds(this.missionPolygon.getBounds(), { padding: [50, 50] });
        }

        // 6. 保存任务区坐标（lng, lat 格式）
        this.mission_layer_point_arr = data.data.missionLayerPointArr.map(p => [p.long, p.lat]);

        this.$message.success('任务区、障碍区和初始位置绘制完成');

      } catch (err) {
        console.error("绘制任务区失败", err);
        this.$message.error(`绘制失败：${err.message}`);
      }
    },
    
    //山火kml文件解析
    handleKmlFileUpload(file) {
      // const loading = this.$message.loading('正在解析 KML 文件...', 0);
      const reader = new FileReader();

      reader.onload = () => {
        try {
          this.readKmlAndDrawMissionArea(reader.result);
        } catch (error) {
          console.error('山火区域KML解析错误:', error);
          this.$message.error('山火区域KML解析错误');
        } finally {
          loading.close();
        }
      };

      reader.readAsText(file);
      return false; // 阻止自动上传
    },
    //山火kml解析，子函数
    readKmlAndDrawMissionArea(kmlString) {
      const parser = new DOMParser();
      const kml = parser.parseFromString(kmlString, 'text/xml');
      const geoJson = toGeoJSON.kml(kml);

      // 提取所有 Point 坐标
      let points = geoJson.features
        .filter(f => f.geometry.type === 'Point')
        .map(f => {
          const lon = f.geometry.coordinates[0]; // 经度
          const lat = f.geometry.coordinates[1]; // 纬度
          return [lat, lon]; // 转成 Leaflet 需要的 [lat, lng]
        });

      if (points.length < 3) {
        this.$message.error('KML 中的点太少，无法构成多边形');
        return;
      }

      // 闭合多边形
      if (!(points[0][0] === points[points.length-1][0] &&
        points[0][1] === points[points.length-1][1])) {
        points.push(points[0]);
      }

      this.drawMissionAreaFromKml(points);
    },
    //山火解析 子函数的子函数
    drawMissionAreaFromKml(points) {
      // 清除之前的图层
      if (this.missionPolygon) {
        this.map.removeLayer(this.missionPolygon);
      }

      // 创建多边形
      this.missionPolygon = L.polygon(points, {
        color: "#4CAF50",
        fillColor: "#81C784",
        fillOpacity: 0.3,
        weight: 3
      }).addTo(this.map);

      // 缩放到多边形4
      this.map.fitBounds(this.missionPolygon.getBounds());

      this.mission_layer_point_arr = points.map(point => [point[1], point[0]]);
      this.hasKmlLayers = true; // 更新标志位
      this.$message.success(`已从 KML 绘制任务区域（${points.length - 1}个顶点）`);
    },
    // 其他方法...
    handleImageError(e) {
      // 图片加载失败时的处理
      e.target.src ='@/assets/tower1.png'; // 默认图片
    }

  }
}
</script>

<style scoped>
/* 主容器样式 */
.app-container {
  height: calc(100vh - 50px);
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 0;
}

.content-wrapper {
  height: 100%;
  padding: 20px;
}

/* 地图容器 */
.map-container {
  height: 100%;
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 控制栏 */
.control-bar {
  background: linear-gradient(to right, #f8f9fa, #e9ecef);
  padding: 15px 20px;
  border-bottom: 1px solid #dee2e6;
  display: flex;
  gap: 30px;
  flex-wrap: wrap;
}

.control-section {
  flex: 1;
  min-width: 300px;
}

.section-title {
  font-size: 14px;
  color: #495057;
  margin: 0 0 10px 0;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-title i {
  color: #007bff;
}

.search-controls, .kml-controls {
  display: flex;
  gap: 10px;
  align-items: center;
}

.coord-input {
  width: 140px;
}

/* 地图包装器 */
.map-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
}

#RealMap {
  width: 100%;
  height: 100%;
  border: none;
}

/* 规划面板 */
.planning-panel {
  position: absolute;
  top: 20px;
  right: 20px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  width: 280px;
  z-index: 1000;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

.panel-title {
  font-weight: 600;
  color: #2c3e50;
  display: flex;
  align-items: center;
  gap: 8px;
}

.close-btn {
  color: #6c757d;
  padding: 0;
}

.panel-content {
  padding: 20px;
  display: grid;
  gap: 15px;
}

.tool-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid transparent;
  position: relative;
}

.tool-item:hover {
  background: #f8f9fa;
  border-color: #e9ecef;
  transform: translateY(-1px);
}

/* 新增：活跃工具状态 */
.tool-item.active {
  background: linear-gradient(135deg, #e3f2fd 0%, #f3e5f5 100%);
  border-color: #2196F3;
  box-shadow: 0 2px 8px rgba(33, 150, 243, 0.3);
}

.tool-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: white;
}

.tool-icon.mission-area {
  background: linear-gradient(135deg, #4CAF50, #66BB6A);
}

.tool-icon.obstacle-area {
  background: linear-gradient(135deg, #F44336, #EF5350);
}

.tool-icon.start-point {
  background: linear-gradient(135deg, #2196F3, #42A5F5);
}

.tool-icon.settings {
  background: linear-gradient(135deg, #9C27B0, #BA68C8);
}

.tool-icon.clear {
  background: linear-gradient(135deg, #FF9800, #FFB74D);
}

.tool-name {
  font-size: 14px;
  color: #495057;
  font-weight: 500;
  flex: 1;
}

/* 新增：取消按钮样式 */
.cancel-btn {
  color: #f56c6c;
  font-size: 12px;
  padding: 2px 6px;
  border: 1px solid #f56c6c;
  border-radius: 4px;
  background: rgba(245, 108, 108, 0.1);
}

.cancel-btn:hover {
  background: rgba(245, 108, 108, 0.2);
}

/* 主控制按钮 */
.main-controls {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
}

.mission-btn {
  padding: 15px 30px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
  transition: all 0.3s ease;
}

.mission-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 35px rgba(102, 126, 234, 0.6);
}

/* 航线状态 */
.route-status {
  position: absolute;
  bottom: 90px;
  right: 20px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 8px;
  padding: 15px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  min-width: 200px;
  z-index: 1000;
}

.status-header {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #28a745;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 10px;
}

.route-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.route-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #6c757d;
}

.route-indicator {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}

.route-name {
  font-weight: 500;
  color: #495057;
}

.route-points {
  margin-left: auto;
  color: #6c757d;
}

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
.slide-fade-enter-active, .slide-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.5, 1);
}

.slide-fade-enter, .slide-fade-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .drawer-actions {
    width: 100%;
    right: 0;
  }

  .actions-content {
    flex-direction: column;
    gap: 16px;
  }

  .validation-info {
    justify-content: center;
  }
}

/* 滚动条美化 */
.drawer-container::-webkit-scrollbar {
  width: 6px;
}

.drawer-container::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.drawer-container::-webkit-scrollbar-thumb {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 3px;
}

.drawer-container::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%);
}

/* 加载动画 */
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
