<template>
  <div class="app-container">
    <div class="content-wrapper">
      <!-- 左侧地图区域 -->
      <div class="map-container">
        <!-- 顶部控制栏 -->
        <MapControls
          :searchCoords="searchCoords"
          :updating="updating"
          :locating="locating"
          :hasKmlLayers="hasKmlLayers"
          @update:searchCoords="val => searchCoords = val"
          @updateMapLocation="updateMapLocation"
          @getCurrentLocation="getCurrentLocation"
          @handleKmlFileSelect="handleKmlFileSelect"
          @clearKmlLayers="clearKmlLayers"
          @handleKmlFileUpload="handleKmlFileUpload"
        />

        <!-- 地图主体 -->
        <div class="map-wrapper">
          <div id="RealMap"></div>

          <!-- 任务规划控制面板 -->
          <PlanningPanel
            :isCollectShow="isCollectShow"
            :currentTool="currentTool"
            @closePlanningPanel="closePlanningPanel"
            @drawMissionArea="draw_mission_area"
            @drawObstacleArea="draw_obstacle_area"
            @addStartingPoint="add_starting_point"
            @fetchStartingPoints="fetchStartingPoints"
            @cancelStartingPoint="cancelStartingPoint"
            @setMissionPlannerForm="set_mission_planner_form"
            @disdraw="disdraw"
          />

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

      <!-- 任务规划参数抽屉 -->
      <MissionParametersDrawer
        :visible.sync="mission_planner_form"
        :form.sync="form"
        :missionLayerPointArr="mission_layer_point_arr"
        :obstacleLayerPointArr="obstacle_layer_point_arr"
        :locations="locations"
        :kmldir="kmldir"
        :kmlFileName="kmlFileName"
        :uavOptions="uavOptions"
        :planningInProgress="planningInProgress"
        @handleKmlFileSelect="handleKmlFileSelect"
        @clearKmlFile="clearKmlFile"
        @generateMissionAreaByKml="generateMissionAreaByKml"
        @clearLocation="clearLocation"
        @startMissionPlanner="startMissionPlanner"
        @addStartingPointForTowerMode="add_starting_point"
      />

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
import { drawMissionRoutes, 
  fitMapToMissionRoutes, 
  clearMissionPolylines
}from './utils/missionPlanner'
import {
  clearKmlLayers,
  clearKmlFile,
  handleKmlFileUpload,
  handleKmlUpload,
} from './utils/kmlUtils'
import {
  clearAllDrawingEventListeners,
  drawMissionArea,
  drawObstacleArea,
  addStartingPoint,
  cancelStartingPoint,
  clearLocation as clearLocationUtil,
  clearAllDrawings,
} from './utils/drawingUtils'
import {
  buildPayload,
  buildConfig,
  startMissionPlanner as startMissionPlannerApi,
  generateMissionAreaByKml as generateMissionAreaByKmlApi,
  drawMissionByResponse,
  startTowerPlanning, // 导入新的沿塔模式规划函数
  fetchStartingPointsFromBackend,
} from './utils/planningApi'
import {
  initMap as initMapUtil,
  updateMapLocation as updateMapLocationUtil,
  getCurrentLocation as getCurrentLocationUtil,
} from './utils/mapUtils'
import {
  STATION_SENSOR_ID,
  getStationPacketKey,
  isStationGnssPacket,
  normalizeStationPacket
} from '@/utils/stationMarker'
import { getWsBaseUrl } from '@/utils/runtimeApi'
import MapControls from './components/MapControls.vue'
import PlanningPanel from './components/PlanningPanel.vue'
import MissionParametersDrawer from './components/MissionParametersDrawer.vue'

const WS_BASE_URL = getWsBaseUrl()

export default {
  name: 'PathPlanning',
  components: {
    MapControls,
    PlanningPanel,
    MissionParametersDrawer
  },
  data() {
    return {
      map: null,
      isCollectShow: false,
      mission_planner_form: false,
      rectangleLayer: null,
      LatLngjson: [],
      missionLayerPointArr: [],//用于任务规划函数的参数传输
      obstacleLayerPointArr: [],//用于任务规划函数的参数传输
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
      nextUavConfigId: 2,
      form: {
        plan_mode: 1,//区域 or 线路模式
        number_device: 1,//无人机数量
        scan_density: 20,//扫描密度
        drone_start: 0,//线路模式杆塔起点
        drone_end: 5,//线路模式杆塔终点
        pathsStrictlyInPoly: false,//路径严格在多边形内
        initial_locations:[],
        distribution_ratios: [],//分布比例
        fly_mode:2,//飞行模式——手动航高，仿地飞行
        // start_height:20,
        // flight_route_height:20,
        overlap_degree:0.5,
        // 核心：无人机配置数组，每个元素是一架飞机的独立参数
        uav_configs: [
          {
            uid: 1,
            selectedUav: null,
            drone_speed: 10,
            start_height: 20,
            flight_route_height: 20
          }
        ]
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
      // 所有可选的无人机型号——不能有空格
      uavOptions: [
        { name: "Matrice_300_RTK", batteryLength: 10000 },
        { name: "Matrice_350_RTK", batteryLength: 10800 },
        { name: "Matrice_400", batteryLength: 12600 }
      ],
      imageDialogVisible: false,
      currentImageUrl: '',
      imageDialogTitle: '',
      missionId: 1, // 根据实际情况设置
      missionPolygon: null, // 存储绘制的任务区域多边形，用于绑定事件
      kmlTowerPoints: [], // 存储 KML 文件中解析出的杆塔经纬度坐标点数组
      stationSocket: null,
      stationMarkers: new Map(),
    }
  },
   created() {
    this.normalizeUavConfigSelections();
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
    'form.number_device'(newVal,oldVal){
      if (this.form.plan_mode === 2) {
        this.updateDistributionRatios(newVal);
      }
      // 监听设备数量变化，同步 uav_configs 数组长度
      const diff = newVal - oldVal;
      if (diff > 0) {
        // 增加设备
        for (let i = 0; i < diff; i++) {
          this.form.uav_configs.push(this.createUavConfig());
        }
      } else if (diff < 0) {
        // 减少设备
        this.form.uav_configs.splice(newVal);
      }
    },

    locations(newVal) {
      if (this.form.plan_mode === 1 || this.form.plan_mode === 2 || this.form.plan_mode === 3) { // 模式1/2/3下更新设备数量
        this.form.number_device = newVal.length || 0;
        this.form.initial_locations = newVal; // Sync initial_locations with locations
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
          this.form.uav_configs &&
          !this.planningInProgress
      }
      return false;
    },
    // 结合新的数据结构修改计算属性
    selectedUavList() {
     // 过滤掉未选择型号的无人机配置
     return this.form.uav_configs
      .filter(config => config.selectedUav && typeof config.selectedUav === 'object' && config.selectedUav.name) // 检查 name 属性
      .map((config, index) => {
        const uav = config.selectedUav; // 直接获取选中的对象
        
        return {
          index: index + 1,
          name: uav.name,        // 从选中对象中获取 name
          batteryLength: uav.batteryLength,       // 型号 ID
          // 将该无人机的所有独立参数都包含进去
          droneSpeed: config.droneSpeed,
          startHeight: config.startHeight,
          flightRouteHeight: config.flightRouteHeight
        };
      });
    }
  },
  mounted() {
    this.map = initMapUtil();
    this.connectStationWebSocket();
  },
  beforeDestroy() {
    if (this.stationSocket) {
      this.stationSocket.close();
    }
    this.stationMarkers.forEach(marker => marker.remove());
    this.stationMarkers.clear();
  },
  methods: {
    getDefaultUavOption() {
      return this.uavOptions.length > 0 ? { ...this.uavOptions[0] } : null;
    },
    createUavConfig() {
      return {
        uid: this.nextUavConfigId++,
        selectedUav: this.getDefaultUavOption(),
        drone_speed: 10,
        start_height: 20,
        flight_route_height: 20
      };
    },
    normalizeUavConfigSelections() {
      this.form.uav_configs = this.form.uav_configs.map((config) => {
        const matchedOption = config.selectedUav && config.selectedUav.name
          ? this.uavOptions.find(opt => opt.name === config.selectedUav.name)
          : null;

        return {
          ...config,
          uid: config.uid || this.nextUavConfigId++,
          selectedUav: matchedOption ? { ...matchedOption } : (config.selectedUav || null)
        };
      });
    },
    createStationIcon(index) {
      return L.divIcon({
        html: `<div class="station-marker-wrapper"><div class="station-marker-dot"></div><div class="station-marker-label">站点${index}</div></div>`,
        className: '',
        iconSize: [72, 34],
        iconAnchor: [12, 28]
      });
    },
    connectStationWebSocket() {
      if (this.stationSocket) return;

      const socket = new WebSocket(`${WS_BASE_URL}/ws/data/${STATION_SENSOR_ID}`);
      this.stationSocket = socket;

      socket.onmessage = (event) => {
        try {
          const packet = JSON.parse(event.data);
          if (isStationGnssPacket(packet)) {
            this.addStationMarker(packet);
          }
        } catch (error) {
          console.error('Error parsing station message:', error);
        }
      };

      socket.onclose = () => {
        this.stationSocket = null;
      };

      socket.onerror = (error) => {
        console.error('Station WebSocket Error:', error);
      };
    },
    addStationMarker(packet) {
      if (!this.map) return;

      const key = getStationPacketKey(packet);
      if (!key || this.stationMarkers.has(key)) return;

      const positionData = normalizeStationPacket(packet);
      if (!positionData) return;

      const markerIndex = this.stationMarkers.size + 1;
      const marker = L.marker([positionData.lat, positionData.lon], {
        icon: this.createStationIcon(markerIndex)
      }).addTo(this.map);

      marker.bindPopup(`站点${markerIndex}<br>经度: ${positionData.lon.toFixed(6)}<br>纬度: ${positionData.lat.toFixed(6)}`);
      this.stationMarkers.set(key, marker);
    },
    // 修复：清理所有事件监听器
    clearAllEventListeners() {
      clearAllDrawingEventListeners(
        this.map,
        {
          clickListener: this.clickListener,
          createListener: this.createListener,
          currentTool: this.currentTool,
        },
        (val) => this.clickListener = val,
        (val) => this.createListener = val,
        (val) => this.currentTool = val
      );
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
      handleKmlUpload(file, this.map, this.kmlLayers, this.$message, (kml) => this.kmldir = kml, (val) => this.hasKmlLayers = val, (points) => this.kmlTowerPoints = points);
      this.kmlFileName = file.name;
      return false;
    },

    clearKmlFile() {
      clearKmlFile((val) => this.kmldir = val, (val) => this.kmlFileName = val, this.$message);
    },

    // 绘制任务区域
    draw_mission_area() {
      drawMissionArea(
        this.map,
        this.clearAllEventListeners,
        (val) => this.currentTool = val,
        this.$message,
        (arr) => this.mission_layer_point_arr = arr,
        (layer) => this.missionPolygon = layer,
        (json) => this.LatLngjson = json,
        (listener) => this.createListener = listener
      );
    },

    // 绘制障碍区域
    draw_obstacle_area() {
      drawObstacleArea(
        this.map,
        this.clearAllEventListeners,
        (val) => this.currentTool = val,
        this.$message,
        (arr) => this.obstacle_layer_point_arr = arr,
        (json) => this.LatLngjson = json,
        (listener) => this.createListener = listener
      );
    },

    // 添加起点
    add_starting_point() {
      const maxPoints = 5; // 沿塔模式现在也允许设置多个起点，统一设置为5个
      addStartingPoint(
        this.map,
        this.clearAllEventListeners,
        (val) => this.currentTool = val,
        this.$message,
        () => this.locations, // 传递一个函数来获取最新 locations
        (newLocations) => this.locations = newLocations,
        this.updateDistributionRatios,
        this.form,
        this.startingPointMarkers,
        (newMarkers) => this.startingPointMarkers = newMarkers,
        (listener) => this.clickListener = listener,
        maxPoints // 传递最大起点数量限制
      );
      console.log(this.form.initial_locations);
      console.log("-------");
    },
    async fetchStartingPoints() {
      try {
        const points = await fetchStartingPointsFromBackend(this);
        if (!Array.isArray(points) || points.length === 0) {
          this.$message.warning('后端未返回起点数据');
          return;
        }

        const maxPoints = 5;
        const finalPoints = points.slice(0, maxPoints);
        if (points.length > maxPoints) {
          this.$message.warning(`后端返回起点超过${maxPoints}个，已截取前${maxPoints}个`);
        }

        // 清理旧起点标记
        this.startingPointMarkers.forEach(item => {
          this.map.removeLayer(item.marker);
        });

        const startIcon = L.icon({
          iconUrl: require('leaflet/dist/images/marker-icon-2x.png'),
          iconSize: [25, 41],
          iconAnchor: [12, 41],
          popupAnchor: [1, -34],
          shadowUrl: require('leaflet/dist/images/marker-shadow.png'),
          shadowSize: [41, 41]
        });

        const newLocations = [];
        const pointModels = [];
        const newMarkers = [];
        finalPoints.forEach((point, index) => {
          const lng = Number(point.long);
          const lat = Number(point.lat);
          const lngLat = `${lng.toFixed(6)},${lat.toFixed(6)}`;
          newLocations.push(lngLat);
          pointModels[index] = point.model || '';

          const marker = L.marker([lat, lng], { icon: startIcon })
            .addTo(this.map)
            .bindPopup(`起点${index + 1}: ${lngLat}`);

          newMarkers.push({
            marker,
            index,
            locationKey: `location${index + 1}`
          });
        });

        this.startingPointMarkers = newMarkers;
        this.locations = newLocations;
        this.form.initial_locations = newLocations;
        this.form.number_device = newLocations.length;
        this.updateDistributionRatios(newLocations.length);

        // 如果后端提供型号，尝试同步无人机型号
        pointModels.forEach((model, index) => {
          if (!model) return;
          const matchedUav = this.uavOptions.find(opt => opt.name === model);
          if (matchedUav && this.form.uav_configs[index]) {
            this.form.uav_configs[index].selectedUav = { ...matchedUav };
          }
        });
        this.$message.success(`已获取并设置 ${newLocations.length} 个起点`);
      } catch (error) {
        console.error('获取起点失败:', error);
        this.$message.error('获取起点失败，请检查后端接口');
      }
    },

    // 取消添加起点
    cancelStartingPoint() {
      cancelStartingPoint(this.clearAllEventListeners, this.$message);
    },

    // 清除位置并清除对应地图标记
    clearLocation(index) {
      clearLocationUtil(
        index,
        this.map,
        this.locations,
        (newLocations) => this.locations = newLocations,
        this.startingPointMarkers,
        (newMarkers) => this.startingPointMarkers = newMarkers,
        this.form,
        this.updateDistributionRatios,
        this.$message
      );
    },

    // 清除所有绘制
    disdraw() {
      clearAllDrawings(
        this.map,
        this.clearAllEventListeners,
        this.startingPointMarkers,
        (val) => this.startingPointMarkers = val,
        this.missionPolylines,
        (val) => this.missionPolylines = val,
        this.missionRoutes,
        (val) => this.missionRoutes = val,
        (val) => this.LatLngjson = val,
        (val) => this.mission_layer_point_arr = val,
        (val) => this.obstacle_layer_point_arr = val,
        this.$message,
        (val) => this.locations = val,
        (val) => this.missionPolygon = val,
        (val) => this.currentMarker = val,
        (val) => this.kmlLayers = val,
        (val) => this.hasKmlLayers = val,
        (val) => this.kmldir = val,
        (val) => this.kmlFileName = val,
        (val) => this.kmlTowerPoints = val
      );
    },

    set_mission_planner_form() {
      // 清理当前工具状态
      this.clearAllEventListeners(); // 恢复此行
      this.mission_planner_form = true;
    },

    async startMissionPlanner() {
      if (this.form.plan_mode === 3) {
        // 沿塔模式下调用新的规划函数
        await startTowerPlanning(
          this,
          this.drawMissionRoutes,
          (val) => this.missionPolylines = val,
          (val) => this.missionRoutes = val,
          this.fitMapToMissionRoutes
        );
      } else {
        // 区域模式和线路模式下调用原有的规划函数
        await startMissionPlannerApi(
          this,
          this.drawMissionRoutes,
          (val) => this.missionPolylines = val,
          (val) => this.missionRoutes = val,
          this.fitMapToMissionRoutes
        );
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


    updateMapLocation() {
      updateMapLocationUtil(
        this.map,
        this.searchCoords,
        { updating: this.updating, currentMarker: this.currentMarker },
        (val) => this.updating = val,
        (marker) => this.currentMarker = marker,
        this.$message
      );
    },

    getCurrentLocation() {
      getCurrentLocationUtil(
        this.map,
        this.searchCoords,
        { locating: this.locating },
        (val) => this.locating = val,
        this.updateMapLocation,
        this.$message,
        (coords) => this.searchCoords = coords
      );
    },

    // KML 文件上传（原有方法，现在调用外部函数）
    // handleKmlUpload(file) {
    //   handleKmlUpload(file, this.map, this.kmlLayers, this.$message, (kml) => this.kmldir = kml, (val) => this.hasKmlLayers = val);
    //   return false;
    // },

    // 清除 KML 图层（原有方法，现在调用外部函数）
    clearKmlLayers() {
      clearKmlLayers(this.map, this.kmlLayers, this.$confirm, this.$message, (val) => this.kmldir = val, (val) => this.hasKmlLayers = val);
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
      await generateMissionAreaByKmlApi(
        this,
        this.drawMissionRoutes,
        (val) => this.missionPolylines = val,
        (val) => this.missionRoutes = val,
        this.fitMapToMissionRoutes
      );
    },
    /**
     * 解析任务区子函数：获取任务区 根据后端返回数据自动绘制任务区、障碍区和无人机初始位置
     * @param {Object} data - 后端返回的 response.data
     */
    drawMissionByResponse(data) {
      drawMissionByResponse(
        data,
        this.map,
        this.clearAllEventListeners,
        this.$message,
        (arr) => this.mission_layer_point_arr = arr,
        { value: this.missionPolygon },
        (layer) => this.missionPolygon = layer,
        this.drawMissionRoutes,
        (val) => this.missionPolylines = val,
        (val) => this.missionRoutes = val,
        this.fitMapToMissionRoutes,
        this.form.uav_configs,
        this.routeColors
      );

      // // 绘制无人机初始位置
      // this.initialMarkers = []; // 清理旧标记
      // if (data.data.initialLocations && data.data.initialLocations.length) {
      //   data.data.initialLocations.forEach((loc, idx) => {
      //     const marker = L.marker([loc.lat, loc.long], {
      //       icon: L.divIcon({
      //         className: 'custom-div-icon',
      //         html: `
      //                   <div style="text-align:center;">
      //                     <img src="${require('@/assets/drone.png')}" style="width:24px;height:24px;" />
      //                     <div style="color:red; font-weight:bold; font-size:12px;">drone${idx}</div>
      //                   </div>
      //                 `,
      //         iconSize: [16, 16],
      //         iconAnchor: [8, 8]
      //       })
      //     }).addTo(this.map);
      //     marker.bindPopup(`<b>无人机 ${idx + 1}</b><br>初始位置`);
      //     this.initialMarkers.push(marker);
      //   });
      // }
    },
    
    //山火kml文件解析
    handleKmlFileUpload(file) {
      handleKmlFileUpload(
        file,
        this.map,
        this.$message,
        (arr) => this.mission_layer_point_arr = arr,
        (val) => this.hasKmlLayers = val,
        { value: this.missionPolygon }, // 传入引用
        (layer) => this.missionPolygon = layer // 更新回调
      );
      return false;
    },
    // 其他方法...
    handleImageError(e) {
      // 图片加载失败时的处理
      e.target.src ='@/assets/tower1.png'; // 默认图片
    }

  }
}
</script>

<style>
.station-marker-wrapper {
  position: relative;
  width: 72px;
  height: 34px;
}

.station-marker-dot {
  position: absolute;
  left: 0;
  bottom: 0;
  width: 20px;
  height: 20px;
  background: #111827;
  border: 3px solid #f59e0b;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
}

.station-marker-dot::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 6px;
  height: 6px;
  background: #f59e0b;
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.station-marker-label {
  position: absolute;
  left: 10px;
  bottom: 22px;
  transform: translateX(-50%);
  padding: 3px 7px;
  color: #111827;
  background: #fbbf24;
  border: 1px solid #92400e;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.35);
}

</style>

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
