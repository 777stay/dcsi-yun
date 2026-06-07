
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

          <!-- KML文件上传卡片 -->
          <div class="parameter-card">
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
                    <el-form-item label="无人机">
                      <el-select v-model="form.selected_uav" placeholder="请选择" style="width: 100%">
                        <el-option label="无人机1" value="选项1"></el-option>
                        <el-option label="无人机2" value="选项2"></el-option>
                        <el-option label="无人机3" value="选项3"></el-option>
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
                
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
                  <el-col :span="12">
                    <el-form-item label="设备数量">
                      <el-input-number 
                        v-model="form.number_device" 
                        :min="1" 
                        :max="8"
                        controls-position="right"
                        style="width: 100%"
                      ></el-input-number>
                    </el-form-item>
                  </el-col>
                  <el-col :span="16">
                    <el-form-item label="扫描密度">
                      <el-input v-model="form.scan_density">
                        <template slot="append">米</template>
                      </el-input>
                    </el-form-item>
                  </el-col>
                </el-row>
                
                <el-row :gutter="16">
                  <el-col :span="15">
                    <el-form-item label="飞行速度">
                      <el-input v-model="form.drone_speed">
                        <template slot="append">m/s</template>
                      </el-input>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="起飞点">
                      <el-input v-model="form.drone_start" placeholder="起飞点ID"></el-input>
                    </el-form-item>
                  </el-col>
                </el-row>
                
                <el-row :gutter="16">
                  <el-col :span="24">
                    <el-form-item label="降落点">
                      <el-input v-model="form.drone_end" placeholder="降落点ID"></el-input>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </div>
          </div>

          <!-- 初始位置卡片 -->
          <div class="parameter-card">
            <div class="card-header">
              <i class="el-icon-location-outline"></i>
              <span>初始位置设置</span>
            </div>
            <div class="card-content">
              <div class="location-grid">
                <div v-if="location1" class="location-card">
                  <div class="location-header">
                    <i class="el-icon-location"></i>
                    <span>位置 1</span>
                    <el-button type="text" size="mini" @click="clearLocation(1)">×</el-button>
                  </div>
                  <div class="location-coords">{{ location1 }}</div>
                </div>
                
                <div v-if="location2" class="location-card">
                  <div class="location-header">
                    <i class="el-icon-location"></i>
                    <span>位置 2</span>
                    <el-button type="text" size="mini" @click="clearLocation(2)">×</el-button>
                  </div>
                  <div class="location-coords">{{ location2 }}</div>
                </div>
                
                <div v-if="location3" class="location-card">
                  <div class="location-header">
                    <i class="el-icon-location"></i>
                    <span>位置 3</span>
                    <el-button type="text" size="mini" @click="clearLocation(3)">×</el-button>
                  </div>
                  <div class="location-coords">{{ location3 }}</div>
                </div>
                
                <div v-if="!location1 && !location2 && !location3" class="empty-location">
                  <i class="el-icon-map-location"></i>
                  <p>请在地图上点击<br>"添加起点"设置位置</p>
                </div>
              </div>
            </div>
          </div>

          <!-- 分配比例卡片 -->
          <div class="parameter-card" v-if="form.number_device > 1">
            <div class="card-header">
              <i class="el-icon-pie-chart"></i>
              <span>任务分配比例</span>
            </div>
            <div class="card-content">
              <div class="ratio-grid">
                <div v-for="i in form.number_device" :key="i" class="ratio-card">
                  <div class="ratio-header">
                    <span class="device-name">{{ i }}号设备</span>
                    <span class="ratio-value">{{ form['Distribution_ratio' + i] }}%</span>
                  </div>
                  <el-slider 
                    v-model="form['Distribution_ratio' + i]" 
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
              <div class="validation-item" :class="{'valid': mission_layer_point_arr.length > 0}">
                <i :class="mission_layer_point_arr.length > 0 ? 'el-icon-check' : 'el-icon-close'"></i>
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
                :loading="planningInProgress"
                class="start-planning-btn"
              >
                <span>🚁 开始规划任务</span>
     
              </el-button>
            </div>
          </div>
        </div>
      </el-drawer>
    </div>
  </div>
</template>

<script>
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet.pm'
import axios from 'axios'
import * as toGeoJSON from '@mapbox/togeojson'

export default {
  name: 'PathPlanning',
  data() {
    return {
      map: null,
      isCollectShow: false,
      mission_planner_form: false,
      rectangleLayer: null,
      LatLngjson: [],
      missionLayerPointArr: [],
      obstacleLayerPointArr: [],
      location1: '',
      location2: '',
      location3: '',
      kmldir: '',
      kmlFileName: '',
      mission_layer_point_arr: [],
      obstacle_layer_point_arr: [],
      missionRoutes: [],
      routeColors: ['#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A', '#98D8C8'],
      missionPolylines: [],
      planningInProgress: false,
      // 新增：当前工具状态和事件监听器管理
      currentTool: '',
      clickListener: null,
      createListener: null,
      startingPointMarkers: [], // 存储起点标记
      form: {
        selected_uav: '选项1',
        plan_mode: 2,
        number_device: 3,
        scan_density: '20',
        drone_speed: '10',
        drone_start: '0',
        drone_end: '10',
        pathsStrictlyInPoly: true,
        Distribution_ratio1: 34,
        Distribution_ratio2: 33,
        Distribution_ratio3: 33,
        Distribution_ratio4: 0,
        Distribution_ratio5: 0,
        Distribution_ratio6: 0,
        Distribution_ratio7: 0,
        Distribution_ratio8: 0,
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
    }
  },
  computed: {
   
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
      // 先清理所有事件监听器
      this.clearAllEventListeners();
      
      this.currentTool = 'starting_point';
      this.$message.info('请在地图上点击添加起点（最多3个）');
      
      // 创建新的点击监听器函数
      this.clickListener = (e) => {
        const lngLat = `${e.latlng.lng.toFixed(6)},${e.latlng.lat.toFixed(6)}`;
        let pointNumber = 1;
        let locationKey = '';

        if (!this.location1) {
          this.location1 = lngLat;
          pointNumber = 1;
          locationKey = 'location1';
        } else if (!this.location2) {
          this.location2 = lngLat;
          pointNumber = 2;
          locationKey = 'location2';
        } else if (!this.location3) {
          this.location3 = lngLat;
          pointNumber = 3;
          locationKey = 'location3';
        } else {
          this.$message.warning('最多只能添加3个起点');
          return;
        }

        const startIcon = L.icon({
          iconUrl: require('leaflet/dist/images/marker-icon-2x.png'),
          iconSize: [25, 41],
          iconAnchor: [12, 41],
          popupAnchor: [1, -34],
          shadowUrl: require('leaflet/dist/images/marker-shadow.png'),
          shadowSize: [41, 41]
        });

        const marker = L.marker([e.latlng.lat, e.latlng.lng], { icon: startIcon })
          .addTo(this.map)
          .bindPopup(`起点${pointNumber}: ${lngLat}`);
          
        // 存储标记以便后续清理
        this.startingPointMarkers.push({
          marker: marker,
          locationKey: locationKey,
          pointNumber: pointNumber
        });
          
        this.$message.success(`已添加起点${pointNumber}`);
        
        // 如果已添加3个点，自动退出添加模式
        if (pointNumber === 3) {
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
    
    // 修复：清除位置时同时清除地图标记
    clearLocation(index) {
      this[`location${index}`] = '';
      
      // 清除对应的地图标记
      this.startingPointMarkers = this.startingPointMarkers.filter(item => {
        if (item.locationKey === `location${index}`) {
          this.map.removeLayer(item.marker);
          return false;
        }
        return true;
      });
      
      this.$message.info(`已清除位置${index}`);
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
      this.location1 = '';
      this.location2 = '';
      this.location3 = '';
      
      this.$message.success('已清除所有绘制');
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
      

      this.planningInProgress = true;
      try {
        console.log('发送任务规划请求...', {
          mission_area: this.mission_layer_point_arr,
          obstacle_area: this.obstacle_layer_point_arr,
          params: this.form
        });

        const token = this.$store.getters.token;
        if (!token) {
            this.$message.error('认证失败，请先登录');
            return;
        }

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
          missionLayerPointArr: this.mission_layer_point_arr,
          obstacleLayerPointArr: this.obstacle_layer_point_arr,
          location1: this.location1,
          location2: this.location2,
          location3: this.location3,
          distributionRatio1: this.form.Distribution_ratio1,
          distributionRatio2: this.form.Distribution_ratio2,
          distributionRatio3: this.form.Distribution_ratio3
        };

        const response = await axios.post("http://localhost:8080/api/mission/plan", payload, config);

        console.log('后端返回数据:', response.data);

        // 清除之前的航线
        this.missionPolylines.forEach(polyline => {
          this.map.removeLayer(polyline);
        });
        this.missionPolylines = [];
        this.missionRoutes = [];

        const missionData = response.data.mission || 
                           response.data.data?.mission || 
                           response.data.data?.data || 
                           response.data.data || [];
        
        console.log('解析的航线数据:', missionData);

        if (Array.isArray(missionData) && missionData.length > 0) {
          missionData.forEach((route, index) => {
            if (route && Array.isArray(route) && route.length > 0) {
              const points = route.map(coord => [coord[0], coord[1]]);
              
              const polyline = L.polyline(points, {
                color: this.routeColors[index % this.routeColors.length],
                weight: 4,
                opacity: 0.8,
                
              }).addTo(this.map);
              
              points.forEach((point, pointIndex) => {
                if (pointIndex % 15 === 0) {
                  L.circleMarker(point, {
                    radius: 4,
                    fillColor: this.routeColors[index % this.routeColors.length],
                    color: '#fff',
                    weight: 2,
                    opacity: 1,
                    fillOpacity: 0.8
                  }).addTo(this.map)
                  .bindPopup(`航线${index + 1} - 航点${pointIndex + 1}`);
                }
              });
              
              this.missionPolylines.push(polyline);
              this.missionRoutes.push(route);
            }
          });

          if (this.missionRoutes.length > 0) {
            this.$notify({
              title: '规划成功',
              message: `成功规划${this.missionRoutes.length}条航线！`,
              type: 'success',
              duration: 4000
            });
            this.mission_planner_form = false;
            
            const allPoints = [];
            this.missionRoutes.forEach(route => {
              route.forEach(coord => {
                allPoints.push([coord[0], coord[1]]);
              });
            });
            if (allPoints.length > 0) {
              const bounds = L.latLngBounds(allPoints);
              this.map.fitBounds(bounds, { padding: [50, 50] });
            }
          } else {
            this.$message.warning('后端返回了空的航线数据');
          }
        } else {
          this.$message.warning('后端未返回有效的航线数据');
          console.warn('数据格式不正确:', response.data);
        }

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
    
    handleKmlUpload(file) {
      const reader = new FileReader();
      
      reader.onload = (e) => {
        try {
          const parser = new DOMParser();
          const kml = parser.parseFromString(e.target.result, 'text/xml');
          const geoJson = toGeoJSON.kml(kml);
          
          const kmlLayer = L.geoJSON(geoJson, {
            style: {
              color: '#3388ff',
              weight: 3,
              opacity: 0.8
            }
          }).addTo(this.map);

          this.kmlLayers.push(kmlLayer);
          this.hasKmlLayers = true;
          this.map.fitBounds(kmlLayer.getBounds());
          this.$message.success('KML文件导入成功');
        } catch (error) {
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
      let sum = 0;
      for (let i = 1; i <= this.form.number_device; i++) {
        sum += this.form[`Distribution_ratio${i}`] || 0;
      }
      return sum;
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
</style>
