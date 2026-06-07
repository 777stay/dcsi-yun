
<template>
  <div class="map-page-container">
    <div class="location-controls">
      <el-select
        v-model="followedRobotId"
        placeholder="视角跟随"
        clearable
        @clear="panToDefault"
        size="small"
        style="width: 150px"
      >
        <el-option
          v-for="robotId in onlineRobotIds"
          :key="robotId"
          :label="robotConfigs[robotId] ? robotConfigs[robotId].displayName : robotId"
          :value="robotId"
        />
      </el-select>

      <el-button icon="el-icon-refresh-left" circle @click="panToDefault" title="回到初始位置" size="small" />
      
      <el-input
        v-model.number="inputLat"
        type="number"
        placeholder="纬度 (e.g., 30.53)"
        clearable
        style="width: 150px"
      />
      <el-input
        v-model.number="inputLon"
        type="number"
        placeholder="经度 (e.g., 114.36)"
        clearable
        style="width: 150px"
      />
      <el-button type="primary" icon="el-icon-position" @click="goToCoordinates">定位</el-button>
    </div>
    <div ref="mapContainer" class="map-container"></div>
    <div class="mouse-coordinates">{{ mouseCoordinates }}</div>
  </div>
</template>

<script>
// 导入所有需要的库
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import coordtransform from 'coordtransform';
import 'leaflet.chinatmsproviders';
import axios from 'axios'; // 引入 axios 用于 API 请求
import {
  STATION_SENSOR_ID,
  getStationPacketKey,
  isStationGnssPacket,
  normalizeStationPacket
} from '@/utils/stationMarker';
import { getBackendBaseUrl, getWsBaseUrl } from '@/utils/runtimeApi';

// --- 常量定义 ---
const API_BASE_URL = getBackendBaseUrl(); // 后端 API 地址
const WS_BASE_URL = getWsBaseUrl();
const CHINA_VIEW_CENTER = [34.0, 108.0];
const CHINA_VIEW_ZOOM = 4;
const DEFAULT_LAT = 30.53;
const DEFAULT_LON = 114.36;
const DEFAULT_ZOOM = 15;
const availableColors = ['#FF4136', '#0074D9', '#2ECC40', '#FFDC00', '#F012BE', '#B10DC9', '#FF851B'];

export default {
  name: 'SatelliteMapView',
  
  data() {
    return {
      // --- 原 Pinia Store 的 state ---
      statuses: {},
      isConnected: false,
      robotConfigs: {},

      // --- 原组件自身的 data ---
      map: null,
      inputLat: null,
      inputLon: null,
      mouseCoordinates: '移入地图查看坐标',
      gnssMarkers: new Map(),
      gnssTracks: new Map(),
      followedRobotId: null,
      dataSockets: {},
      stationSocket: null,
      stationMarkers: new Map(),
      tracksData: {},
      lastKnownPositions: {},
      defaultMapView: CHINA_VIEW_CENTER,
      robotColors: {},
    };
  },

  computed: {
    // --- 原 Pinia Store 的 getters ---
    actualRobots() {
      const robots = { ...this.robotConfigs };
      delete robots.robot;
      return robots;
    },

    // --- 原组件自身的 computed ---
    onlineRobotIds() {
      return Object.keys(this.statuses)
        .filter(id => id.startsWith('robot_') && this.statuses[id]?.online)
        .sort();
    },
  },

  watch: {
    followedRobotId(newId) {
      if (this.map && newId && this.lastKnownPositions[newId]) {
        this.map.flyTo(this.lastKnownPositions[newId], 18);
        this.$message.success(`视角已锁定于 ${this.robotConfigs[newId]?.displayName || newId}`);
      }
    },
    
    onlineRobotIds: {
      handler(newIds, oldIds) {
        const newIdSet = new Set(newIds);
        const oldIdSet = new Set(oldIds || []);
        
        newIds.forEach(id => {
          if (!oldIdSet.has(id)) this.connectDataWebSocket(id);
        });
        
        (oldIds || []).forEach(id => {
          if (!newIdSet.has(id)) this.disconnectDataWebSocket(id);
        });

        if ((!oldIds || oldIds.length === 0) && newIds.length > 0 && !this.followedRobotId) {
          this.followedRobotId = newIds[0];
          this.$message.info(`发现机器人信号，自动跟踪 ${this.robotConfigs[newIds[0]]?.displayName || newIds[0]}`);
        }
      },
      immediate: true,
      deep: true,
    },
  },
  
  async mounted() {
    // 直接调用组件内的方法
    this.connectWebSocket(); 
    
    if (Object.keys(this.robotConfigs).length === 0) {
      await this.fetchRobotConfigs();
    }
    
    this.initializeMap();
    this.connectStationWebSocket();
  },

  beforeDestroy() {
    if (this.map) {
      this.map.remove();
    }
    Object.values(this.dataSockets).forEach(socket => socket.close());
    if (this.stationSocket) {
      this.stationSocket.close();
    }
    this.stationMarkers.forEach(marker => marker.remove());
    this.stationMarkers.clear();
  },

  methods: {
    // --- 原 Pinia Store 的 actions ---
    async fetchRobotConfigs() {
      try {
        const token = this.$store.getters.token;
        const config = { headers: { 'Authorization': `Bearer ${token}` } };
        const response = await axios.get(`${API_BASE_URL}/api/robots`, config);
        this.robotConfigs = response.data;
      } catch (error) {
        console.error("Failed to fetch robot configs:", error);
      }
    },

    connectWebSocket() {
      if (this.isConnected) return;
      const socket = new WebSocket(`${WS_BASE_URL}/ws/status`);

      socket.onopen = () => {
        this.isConnected = true;
        console.log('WebSocket connected successfully.');
      };

      socket.onmessage = (event) => {
        try {
          this.statuses = JSON.parse(event.data);
        } catch (error) {
          console.error("Failed to parse WebSocket message:", error);
        }
      };

      socket.onclose = () => {
        this.isConnected = false;
        for (const robotId in this.statuses) {
          if (this.statuses.hasOwnProperty(robotId)) {
            this.statuses[robotId].online = false;
          }
        }
        console.log('WebSocket disconnected.');
      };

      socket.onerror = (error) => {
        console.error('WebSocket Error:', error);
      };
    },

    async sendCommand(robotId, commandName) {
      try {
        const token = this.$store.getters.token;
        const config = { headers: { 'Authorization': `Bearer ${token}` } };
        const response = await axios.post(`${API_BASE_URL}/api/command/${robotId}/${commandName}`, {}, config);
        console.log('Command response:', response.data);
      } catch (error) {
        console.error('Failed to send command:', error);
      }
    },

    // --- 原组件自身的方法 ---
    initializeMap() {
      if (this.$refs.mapContainer) {
        const gcj02Coords = coordtransform.wgs84togcj02(DEFAULT_LON, DEFAULT_LAT);
        this.defaultMapView = [gcj02Coords[1], gcj02Coords[0]];
        
        this.map = L.map(this.$refs.mapContainer).setView(CHINA_VIEW_CENTER, CHINA_VIEW_ZOOM);

        L.tileLayer.chinaProvider('GaoDe.Satellite.Map', { maxZoom: 18, minZoom: 3 }).addTo(this.map);
        L.tileLayer.chinaProvider('GaoDe.Satellite.Annotion', { maxZoom: 18, minZoom: 3 }).addTo(this.map);

        this.map.on('mousemove', (e) => {
          const wgs84Coords = coordtransform.gcj02towgs84(e.latlng.lng, e.latlng.lat);
          this.mouseCoordinates = `纬度: ${wgs84Coords[1].toFixed(5)}, 经度: ${wgs84Coords[0].toFixed(5)}`;
        });
        this.map.on('mouseout', () => { this.mouseCoordinates = '移入地图查看坐标'; });
        
        this.map.on('dragstart', () => {
          if (this.followedRobotId) {
            this.$message.info('已取消视角跟随');
            this.followedRobotId = null;
          }
        });
      }
    },

    panToDefault() {
      if (this.map) {
        if (this.followedRobotId) {
          this.followedRobotId = null;
        }
        this.map.flyTo(this.defaultMapView, DEFAULT_ZOOM);
        this.$message.info('视角已返回初始位置');
      }
    },
    
    goToCoordinates() {
      if (!this.map) return;
      const lat = this.inputLat;
      const lon = this.inputLon;
      if (lat === null || lon === null || isNaN(lat) || isNaN(lon)) {
        this.$message.error('请输入有效的 WGS-84 经纬度！');
        return;
      }
      const gcj02Coords = coordtransform.wgs84togcj02(lon, lat);
      this.map.flyTo([gcj02Coords[1], gcj02Coords[0]], 17);
    },

    getRobotColor(robotId) {
      if (!this.robotColors[robotId]) {
        const index = Object.keys(this.robotColors).length % availableColors.length;
        this.robotColors[robotId] = availableColors[index];
      }
      return this.robotColors[robotId];
    },

    createPulsingIconWithLabel(robotId) {
      const color = this.getRobotColor(robotId);
      const displayName = robotId;
      return L.divIcon({
        html: `<div class="pulsing-marker-wrapper"><div class="pulsing-marker" style="--marker-color: ${color}"></div><div class="marker-label" style="background-color: ${color}">${displayName}</div></div>`,
        className: '',
        iconSize: [80, 40],
        iconAnchor: [12, 40]
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
        } catch (e) {
          console.error('Error parsing station message:', e);
        }
      };

      socket.onclose = () => {
        this.stationSocket = null;
      };

      socket.onerror = (error) => {
        console.error('Station WebSocket Error:', error);
      };
    },

    connectDataWebSocket(robotId) {
      if (this.dataSockets[robotId]) return;
      
      const socket = new WebSocket(`${WS_BASE_URL}/ws/data/${robotId}`);
      this.dataSockets[robotId] = socket;

      socket.onmessage = (event) => {
        try {
          const packet = JSON.parse(event.data);
          //console.log(packet)
          if (packet.type === 'PACKET_ODOM' && packet.frame === 'gnss' ) {
            this.updateMapWithGnss(robotId, packet.odom);
          }
        } catch(e) { console.error('Error parsing message:', e); }
      };

      socket.onclose = () => { delete this.dataSockets[robotId]; };
    },

    disconnectDataWebSocket(robotId) {
      if (this.dataSockets[robotId]) {
        this.dataSockets[robotId].close();
      }
      this.gnssMarkers.get(robotId)?.remove();
      this.gnssMarkers.delete(robotId);
      this.gnssTracks.get(robotId)?.remove();
      this.gnssTracks.delete(robotId);
      delete this.tracksData[robotId];
    },

    addStationMarker(packet) {
      if (!this.map) return;

      const key = getStationPacketKey(packet);
      if (!key || this.stationMarkers.has(key)) return;

      const positionData = normalizeStationPacket(packet);
      if (!positionData) return;

      const gcj02Coords = coordtransform.wgs84togcj02(positionData.lon, positionData.lat);
      const position = [gcj02Coords[1], gcj02Coords[0]];
      const index = this.stationMarkers.size + 1;
      const marker = L.marker(position, {
        icon: this.createStationIcon(index)
      }).addTo(this.map);

      marker.bindPopup(`站点${index}<br>经度: ${positionData.lon.toFixed(6)}<br>纬度: ${positionData.lat.toFixed(6)}`);
      this.stationMarkers.set(key, marker);
    },

    updateMapWithGnss(robotId, positionData) {
      if (!this.map) return;
      
      const wgs84Lon = positionData.x;
      const wgs84Lat = positionData.y;

      // 初始化轨迹数据
      if (!this.tracksData[robotId]) {
        this.$set(this.tracksData, robotId, []);
      }
      this.tracksData[robotId].push({ lon: wgs84Lon, lat: wgs84Lat });
      if (this.tracksData[robotId].length > 1000) {
        this.tracksData[robotId].shift();
      }

      const gcj02Coords = coordtransform.wgs84togcj02(wgs84Lon, wgs84Lat);
      const position = [gcj02Coords[1], gcj02Coords[0]];
      
      this.lastKnownPositions[robotId] = position;

      // 🔥 新增：首次接收到GNSS数据时自动跟随并放大
      const isFirstGnssData = this.tracksData[robotId].length === 1;
      
      if (isFirstGnssData) {
        // 首次接收GNSS数据，自动设置跟随
        if (!this.followedRobotId) {
          this.followedRobotId = robotId;
          this.$message.success(`检测到GNSS信号，自动跟踪 ${this.robotConfigs[robotId]?.displayName || robotId}`);
        }
        
        // 自动放大到高精度视角
        this.map.flyTo(position, 18, {
          animate: true,
          duration: 2.0 // 2秒平滑过渡
        });
      }
      
      // 🔥 改进：视角跟随逻辑
      if (this.followedRobotId === robotId) {
        const currentZoom = this.map.getZoom();
        const targetZoom = Math.max(currentZoom, 16); // 确保最小缩放级别为16
        
        // 根据当前缩放级别决定移动方式
        if (currentZoom < 12) {
          // 低缩放级别时使用flyTo平滑过渡
          this.map.flyTo(position, targetZoom, {
            animate: true,
            duration: 1.5
          });
        } else {
          // 高缩放级别时使用panTo保持平滑跟随
          this.map.panTo(position, {
            animate: true,
            duration: 0.5,
            easeLinearity: 0.1
          });
          
          // 如果缩放级别太小，逐渐调整
          if (currentZoom < 16) {
            this.map.setZoom(Math.min(currentZoom + 0.5, 18));
          }
        }
      }

      // 更新标记位置
      let marker = this.gnssMarkers.get(robotId);
      if (marker) {
        marker.setLatLng(position);
      } else {
        marker = L.marker(position, { 
          icon: this.createPulsingIconWithLabel(robotId) 
        }).addTo(this.map);
        this.gnssMarkers.set(robotId, marker);
      }
      
      // 更新轨迹
      const trackLatLngs = this.tracksData[robotId].map(p => {
        const gcj = coordtransform.wgs84togcj02(p.lon, p.lat);
        return [gcj[1], gcj[0]];
      });

      let track = this.gnssTracks.get(robotId);
      if (track) {
        track.setLatLngs(trackLatLngs);
      } else {
        const trackColor = this.getRobotColor(robotId);
        track = L.polyline(trackLatLngs, { 
          color: trackColor, 
          weight: 5,
          opacity: 0.8
        }).addTo(this.map);
        this.gnssTracks.set(robotId, track);
      }

     
     
    },

    
   
  },
};
</script>

<style>
.pulsing-marker-wrapper { position: relative; width: 100%; height: 100%; }
.pulsing-marker { position: absolute; left: 0; bottom: 0; width: 24px; height: 24px; --marker-color: #007bff; }
.pulsing-marker::before { content: ''; position: absolute; top: 50%; left: 50%; width: 12px; height: 12px; background-color: var(--marker-color); border: 2px solid white; border-radius: 50%; transform: translate(-50%, -50%); box-shadow: 0 0 5px rgba(0, 0, 0, 0.5); z-index: 1; }
.pulsing-marker::after { content: ''; position: absolute; top: 50%; left: 50%; width: 24px; height: 24px; border-radius: 50%; background-color: var(--marker-color); transform: translate(-50%, -50%); animation: pulse 1.5s ease-out infinite; z-index: 0; opacity: 0.7; }
@keyframes pulse {
  0% { transform: translate(-50%, -50%) scale(0.5); opacity: 0.7; }
  100% { transform: translate(-50%, -50%) scale(2.5); opacity: 0; }
}
.marker-label { position: absolute; bottom: 22px; left: 12px; transform: translateX(-50%); padding: 3px 8px; background-color: var(--marker-color); color: white; font-size: 12px; font-weight: bold; border-radius: 4px; white-space: nowrap; box-shadow: 0 1px 3px rgba(0,0,0,0.4); }
.station-marker-wrapper { position: relative; width: 72px; height: 34px; }
.station-marker-dot { position: absolute; left: 0; bottom: 0; width: 20px; height: 20px; background: #111827; border: 3px solid #f59e0b; border-radius: 50%; box-shadow: 0 2px 8px rgba(0,0,0,0.35); }
.station-marker-dot::after { content: ''; position: absolute; left: 50%; top: 50%; width: 6px; height: 6px; background: #f59e0b; border-radius: 50%; transform: translate(-50%, -50%); }
.station-marker-label { position: absolute; left: 10px; bottom: 22px; transform: translateX(-50%); padding: 3px 7px; color: #111827; background: #fbbf24; border: 1px solid #92400e; border-radius: 4px; font-size: 12px; font-weight: 700; white-space: nowrap; box-shadow: 0 1px 3px rgba(0,0,0,0.35); }
</style>

<style scoped>
.map-page-container { 
  position: relative; 
  width: 100%; 
  height: 100vh; /* 使用视窗高度 */
  min-height: 600px; /* 最小高度保障 */
}
.map-container { 
  width: 100%; 
  height: 100%; 
  background-color: #f0f0f0; /* 添加背景色便于调试 */
}
.location-controls { 
  position: absolute; 
  top: 15px; 
  right: 15px; 
  z-index: 1000; 
  background-color: rgba(255, 255, 255, 0.85); 
  padding: 10px; 
  border-radius: 6px; 
  box-shadow: 0 2px 8px rgba(0,0,0,0.2); 
  display: flex; 
  align-items: center; 
  gap: 10px; 
}
.location-controls .el-input { 
  width: 150px; 
}
.mouse-coordinates { 
  position: absolute; 
  bottom: 10px; 
  left: 10px; 
  z-index: 1000; 
  background-color: rgba(0, 0, 0, 0.6); 
  color: white; 
  padding: 5px 10px; 
  border-radius: 4px; 
}
</style>
