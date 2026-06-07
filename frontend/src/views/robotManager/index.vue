<template>
  <div class="data-management-layout">
    <div class="query-controls-header">
      <div class="query-mode-selector">
        <el-radio-group v-model="queryMode" @change="handleQueryModeChange">
          <el-radio-button label="count">按次数查询</el-radio-button>
          <el-radio-button label="time">按时间查询</el-radio-button>
        </el-radio-group>
      </div>

      <div v-if="queryMode === 'time'" class="time-query-controls">
        <el-date-picker
          v-model="selectedDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          format="yyyy-MM-dd"
          value-format="yyyy-MM-dd"
          @change="handleDateRangeChange"
          style="width: 300px;"
        >
        </el-date-picker>
        <el-button
          type="primary"
          icon="el-icon-search"
          @click="queryByDateRange"
          :loading="isQuerying"
        >
          查询
        </el-button>
        <el-button
          icon="el-icon-refresh"
          @click="resetTimeQuery"
        >
          重置
        </el-button>
      </div>

      <div class="query-result-info" v-if="queryMode === 'time' && filteredReceptionCounts.length > 0">
        <el-tag type="info" size="small">
          查询到 {{ filteredReceptionCounts.length }} 个任务会话
        </el-tag>
      </div>
    </div>

    <div class="main-content-layout">
      <el-aside width="300px" class="menu-aside">
        <div class="aside-header">
          <div class="header-title">
            <i class="el-icon-receiving"></i>
            <span>工程管理</span>
          </div>
          <div class="query-mode-indicator">
            <el-tag size="mini" :type="queryMode === 'time' ? 'success' : 'primary'">
              {{ queryMode === 'time' ? '时间查询模式' : '次数查询模式' }}
            </el-tag>
          </div>
        </div>

        <div v-if="queryMode === 'time'" class="calendar-container">
          <div class="calendar-legend">
            <div class="legend-item">
              <span class="legend-color normal-day"></span>
              <span class="legend-text">普通日期</span>
            </div>
            <div class="legend-item">
              <span class="legend-color task-day"></span>
              <span class="legend-text">有任务日期</span>
            </div>
            <div class="legend-item">
              <span class="legend-color selected-day"></span>
              <span class="legend-text">查询范围</span>
            </div>
          </div>

          <el-calendar v-model="calendarValue" @pick="handleCalendarPick">
            <template slot="dateCell" slot-scope="{date, data}">
              <div class="calendar-day" :class="getCalendarDayClass(date)" @click="handleDateClick(date)">
                <span class="day-number">{{ data.day.split('-').slice(-1)[0] }}</span>
                <div v-if="hasTasksOnDate(date)" class="task-indicator">
                  <i class="el-icon-folder-opened"></i>
                  <span class="task-count">{{ getTaskCountOnDate(date) }}</span>
                </div>
              </div>
            </template>
          </el-calendar>
        </div>

        <el-menu
          v-if="queryMode === 'count' || (queryMode === 'time' && filteredReceptionCounts.length > 0)"
          :default-active="activeMenuIndex"
          class="session-menu"
          @open="handleMenuOpen"
        >
          <el-submenu
            v-for="countInfo in displayReceptionCounts"
            :key="countInfo.receptionCount"
            :index="String(countInfo.receptionCount)"
          >
            <template slot="title">
              <i class="el-icon-folder-opened"></i>
              <div class="task-info">
                <span class="task-title">第{{ countInfo.receptionCount }}次任务</span>
                <div class="time-range" v-if="countInfo.timeRangeDisplay">
                  {{ countInfo.timeRangeDisplay }}
                </div>
                <div v-if="queryMode === 'time'" class="query-match-indicator">
                  <el-tag size="mini" type="success">时间匹配</el-tag>
                </div>
              </div>
            </template>
            <div v-loading="robotsByCount[countInfo.receptionCount] && robotsByCount[countInfo.receptionCount].loading" class="robot-submenu-content">
              <el-menu-item
                v-for="robotId in (robotsByCount[countInfo.receptionCount] && robotsByCount[countInfo.receptionCount].list || [])"
                :key="robotId"
                :index="`${countInfo.receptionCount}-${robotId}`"
                @click="handleRobotSelect(robotId, countInfo.receptionCount)"
              >
                <i class="el-icon-cpu"></i>
                <span>{{ robotId }}</span>
              </el-menu-item>
              <div v-if="robotsByCount[countInfo.receptionCount] && !robotsByCount[countInfo.receptionCount].loading && robotsByCount[countInfo.receptionCount].list && robotsByCount[countInfo.receptionCount].list.length === 0" class="no-data-tip">
                无机器人数据
              </div>
            </div>
          </el-submenu>
        </el-menu>

        <div v-if="queryMode === 'time' && filteredReceptionCounts.length === 0 && hasSearched" class="no-time-result">
          <el-empty description="选定时间范围内无任务数据" :image-size="80">
            <el-button type="primary" @click="resetTimeQuery" size="small">重新选择时间</el-button>
          </el-empty>
        </div>

        <div v-if="queryMode === 'time' && !hasSearched" class="time-query-tip">
          <el-empty description="请在日历中选择日期或使用日期选择器查询" :image-size="60">
            <div class="tip-actions">
              <el-button type="text" @click="setTodayRange" size="small">
                <i class="el-icon-date"></i> 查看今天
              </el-button>
              <el-button type="text" @click="setWeekRange" size="small">
                <i class="el-icon-date"></i> 查看本周
              </el-button>
              <el-button type="text" @click="setMonthRange" size="small">
                <i class="el-icon-date"></i> 查看本月
              </el-button>
            </div>
          </el-empty>
        </div>
      </el-aside>

      <el-container class="content-container">
        <el-main class="content-main">
          <div v-if="selectedRobotId" v-loading="isLoadingCategories" element-loading-text="正在加载分类数据...">
            <el-tabs v-model="activeTab" type="border-card" class="data-tabs">
              <el-tab-pane label="点云数据" name="pointcloud">
                <span slot="label"><i class="el-icon-cloudy"></i> 点云数据</span>
                <el-collapse v-model="activePointCloudCollapse" @change="handlePointCloudCollapseChange" accordion>
                  <el-collapse-item v-for="frame in pointCloudCategories" :key="frame" :title="frame" :name="frame">
                    <div v-if="pointCloudData[frame]">
                      <el-table :data="pointCloudData[frame].list" stripe height="300" v-loading="pointCloudData[frame].loading">
                        <el-table-column prop="timestamp" label="时间戳" width="180"></el-table-column>
                        <el-table-column prop="pointCount" label="点数"></el-table-column>
                        <el-table-column label="操作" width="180" align="center">
                          <template slot-scope="scope">
                            <template v-if="frame !== 'Potree点云'">
                              <el-button size="mini" type="primary" icon="el-icon-view" @click="viewPointCloud(scope.row, frame)">
                                渲染
                              </el-button>
                            </template>
                            <el-button size="mini" icon="el-icon-download" @click="downloadPointCloud(scope.row.filePath)">
                              下载
                            </el-button>
                          </template>
                        </el-table-column>
                      </el-table>
                      <el-pagination background layout="prev, pager, next, total"
                        :total="pointCloudData[frame].total" :page-size="8"
                        :current-page.sync="pointCloudData[frame].pageNum"
                        @current-change="(newPage) => handlePointCloudPageChange(frame, newPage)"
                        class="data-pagination">
                      </el-pagination>
                    </div>
                  </el-collapse-item>
                </el-collapse>
                <el-empty v-if="!pointCloudCategories.length" description="暂无点云分类数据"></el-empty>
              </el-tab-pane>

              <el-tab-pane name="image">
      <span slot="label"><i class="el-icon-picture-outline"></i> 图像数据</span>
      <el-collapse v-model="activeImageCollapse" @change="handleImageCollapseChange" accordion>
        <el-collapse-item v-for="session in imageCategories" :key="session" :title="session" :name="session">
          <div v-if="imageData[session]">
            <div 
              class="image-gallery-container" 
              v-loading="imageData[session].loading"
              element-loading-text="正在加载图片..."
            >
              <div v-for="image in imageData[session].list" :key="image.filePath" class="gallery-item-wrapper">
                <el-image
                  class="gallery-image-item"
                  :src="image.blobUrl"
                  :preview-src-list="imageData[session].list.map(img => img.blobUrl)"
                  fit="cover"
                >
                  <div slot="placeholder" class="image-slot">
                    <i class="el-icon-loading"></i>
                  </div>
                  <div slot="error" class="image-slot">
                    <i class="el-icon-picture-outline"></i>
                  </div>
                </el-image>
                <div class="image-info-footer">
                  <span class="image-filename">{{ getFileName(image.filePath) }}</span>
                  <el-button type="text" icon="el-icon-download" @click="downloadImageFromGallery(image.blobUrl, getFileName(image.filePath))">下载</el-button>
                </div>
              </div>
            </div>

            <el-pagination background layout="prev, pager, next, total"
              :total="imageData[session].total" :page-size="8"
              :current-page.sync="imageData[session].pageNum"
              @current-change="(newPage) => handleImagePageChange(session, newPage)"
              class="data-pagination">
            </el-pagination>
          </div>
        </el-collapse-item>
      </el-collapse>
      
    </el-tab-pane>
              <!-- <el-tab-pane label="路径数据 (Odom)" name="odom">
                <span slot="label"><i class="el-icon-position"></i> 路径数据</span>
                 </el-tab-pane> -->
            </el-tabs>
          </div>
          <el-empty v-else description="请从左侧选择一个机器人开始查看数据" class="full-height-placeholder"></el-empty>
        </el-main>
      </el-container>

      <div v-if="showViewer" class="resize-handle" @mousedown.prevent="startResize"></div>
      <el-aside v-if="showViewer" :width="viewerWidth + 'px'" class="viewer-aside">
        <div class="viewer-header">
          <el-tooltip :content="viewerTitle" placement="top" :disabled="!viewerTitle">
            <span class="viewer-title">{{ viewerTitle || '3D查看器' }}</span>
          </el-tooltip>
          <div class="viewer-controls">
            <el-button size="mini" @click="clearViewer">清空</el-button>
            <el-button size="mini" @click="resetCamera">重置视角</el-button>
            <el-button size="mini" @click="closeViewer" type="danger" icon="el-icon-close" circle></el-button>
          </div>
        </div>
        <div class="viewer-content">
          <PointCloudViewer
            ref="pointCloudViewer"
            :file-url="viewerFileUrl"
            :odom-data="viewerOdomData"
            :point-cloud-type="currentPointCloudType"
            @update:point-count="handlePointCountUpdate"
          />
        </div>
        <div class="viewer-status">
          <el-tag size="small" v-if="currentPointCount > 0">点数: {{ currentPointCount.toLocaleString() }}</el-tag>
          <el-tag size="small" type="info" v-if="viewerStatus">{{ viewerStatus }}</el-tag>
        </div>
      </el-aside>

      <div v-if="showPotreeViewer" class="resize-handle" @mousedown.prevent="startPotreeResize"></div>
      <el-aside v-if="showPotreeViewer" :width="potreeViewerWidth + 'px'" class="viewer-aside potree-viewer" ref="potreeAside">
        <div class="viewer-header">
          <span class="viewer-title">Potree 查看器</span>
          <div class="viewer-controls">
            <el-tooltip :content="isPotreeFullscreen ? '退出全屏' : '全屏'" placement="top">
              <el-button
                size="mini"
                :icon="isPotreeFullscreen ? 'el-icon-copy-document' : 'el-icon-full-screen'"
                @click="togglePotreeFullscreen"
                circle>
              </el-button>
            </el-tooltip>
            <el-button size="mini" @click="closePotreeViewer" type="danger" icon="el-icon-close" circle></el-button>
          </div>
        </div>
        <div class="viewer-content">
          <PotreeViewer
            v-if="potreeViewerUrl"
            ref="potreeViewer"
            :url="potreeViewerUrl"
          />
        </div>
      </el-aside>

    </div>
  </div>
</template>
<script>  
import axios from 'axios';  
import PointCloudViewer from '@/components/PointCloudViewer.vue';  
import PotreeViewer from '@/components/PotreeCloudViewer.vue';
import { getBackendBaseUrl } from '@/utils/runtimeApi';

const API_BASE_URL = getBackendBaseUrl();  

export default {  
  name: 'DataManagement',  
  components: {  
    PointCloudViewer, // 确保 PointCloudViewer 已在此处声明
    PotreeViewer // 新增 PotreeViewer 组件声明
  },  
  data() {  
    return {  
      // ==================== 查询模式相关 ====================  
      queryMode: 'count', // 'count' | 'time'  
      selectedDateRange: null, // [startDate, endDate]  
      isQuerying: false,  
      hasSearched: false,  
      calendarValue: new Date(),  
      
      // ==================== 数据相关 ====================  
      receptionCounts: [], // 所有任务数据  
      filteredReceptionCounts: [], // 时间查询过滤后的结果  
      robotsByCount: {},  
      activeMenuIndex: '',  
      
      // 任务日期映射 - 用于快速查找日期对应的任务  
      taskDateMap: new Map(), // date -> [tasks]  

      // ==================== 选中的数据 ====================  
      selectedRobotId: null,  
      selectedCount: null,  
      isLoadingCategories: false,  
      isLoadingOdom: false,  
      activeTab: 'pointcloud',  
      
      // ==================== 分类数据 ====================  
      pointCloudCategories: [],  
      imageCategories: [],  
      pointCloudData: {},  
      imageData: {},  
      odometryData: { list: [], total: 0, pageNum: 1 },  
      
      // ==================== 折叠面板状态 ====================  
      activePointCloudCollapse: '',  
      activeImageCollapse: '',  
      
      // ==================== 3D查看器相关 ====================  
      showViewer: false,  
      viewerWidth: 600,  
      isResizing: false,  
      
      viewerTitle: '',  
      viewerFileUrl: null,  
      viewerOdomData: null,  
      viewerStatus: '',  
      currentPointCount: 0,  
      // Potree 查看器相关状态
      showPotreeViewer: false,
      potreeViewerWidth: 800,
      isPotreeResizing: false,
      potreeViewerUrl: '',
      isPotreeFullscreen: false,
      currentPointCloudType: 'intensity', // 新增：当前点云类型，默认为强度
    };  
  },  

  computed: {  
    // 根据查询模式显示不同的数据  
    displayReceptionCounts() {  
      return this.queryMode === 'time' ? this.filteredReceptionCounts : this.receptionCounts;  
    }  
  },  

  methods: {  
    // NEW: 新增一个方法用于将 ply 路径转换为 meta.json 路径
    convertPlyPathToMetaJsonPath(plyPath) {
      if (!plyPath || typeof plyPath !== 'string') {
        console.error("无效的点云路径:", plyPath);
        return '';
      }

      // 1. 分离目录和带后缀的文件名
      const lastSlashIndex = plyPath.lastIndexOf('/');
      // ... (错误处理)
      const directory = plyPath.substring(0, lastSlashIndex);
      const filenameWithExt = plyPath.substring(lastSlashIndex + 1);

      // 2. 分离文件名和后缀
      const lastDotIndex = filenameWithExt.lastIndexOf('.');
      // ... (错误处理)
      const filenameWithoutExt = filenameWithExt.substring(0, lastDotIndex);

      // 3. 根据规则拼接新的 meta.json 路径
      const metaJsonPath = `${directory}/${filenameWithoutExt}/pointclouds/${filenameWithoutExt}/meta.json`;
      
      console.log(`路径转换: ${plyPath} -> ${metaJsonPath}`);
      return metaJsonPath;
    },
    // ==================== 查询模式管理 ====================  
    
    // 查询模式切换  
    handleQueryModeChange(mode) {  
      this.queryMode = mode;  
      if (mode === 'count') {  
        // 切换到次数查询，显示所有数据  
        this.filteredReceptionCounts = [];  
        this.selectedDateRange = null;  
        this.hasSearched = false;  
      } else {  
        // 切换到时间查询，清空当前选择  
        this.resetSelection();  
      }  
    },  

    // 日期范围变化处理  
    handleDateRangeChange(dateRange) {  
      this.selectedDateRange = dateRange;  
    },  

    // 按日期范围查询  
    async queryByDateRange() {  
      if (!this.selectedDateRange || this.selectedDateRange.length !== 2) {  
        this.$message.warning('请选择查询时间范围');  
        return;  
      }  

      this.isQuerying = true;  
      this.hasSearched = true;  
      
      try {  
        const [startDate, endDate] = this.selectedDateRange;  
        
        // 先尝试本地筛选，如果数据已存在  
        if (this.receptionCounts && this.receptionCounts.length > 0) {  
          this.filteredReceptionCounts = this.receptionCounts.filter(task => {  
            if (!task.minTime || !task.maxTime) return false;  
            
            const taskStartDate = this.formatDate(new Date(task.minTime));  
            const taskEndDate = this.formatDate(new Date(task.maxTime));  
            
            // 检查任务时间范围是否与查询范围有重叠  
            return (taskStartDate <= endDate && taskEndDate >= startDate);  
          });  
          
          this.$message.success(`查询到 ${this.filteredReceptionCounts.length} 个任务会话`);  
          this.resetSelection();  
          return;  
        }  
        
        // 如果本地没有数据，调用后端API  
        const token = this.$store.getters.token;  
        const config = { headers: { 'Authorization': `Bearer ${token}` } };  
        
        const response = await axios.get(`${API_BASE_URL}/api/query/reception-counts-by-date`, {  
          ...config,  
          params: {  
            startDate,  
            endDate  
          }  
        });  
        
        if (response.data.code === 200) {  
          this.filteredReceptionCounts = response.data.data || [];  
          this.$message.success(`查询到 ${this.filteredReceptionCounts.length} 个任务会话`);  
          this.resetSelection();  
        } else {  
          throw new Error(response.data.message || '查询失败');  
        }  
      } catch (error) {  
        console.error('按时间查询失败:', error);  
        this.$message.error('查询失败：' + (error.response?.data?.message || error.message));  
        this.filteredReceptionCounts = [];  
      } finally {  
        this.isQuerying = false;  
      }  
    },  

    // 重置时间查询  
    resetTimeQuery() {  
      this.selectedDateRange = null;  
      this.filteredReceptionCounts = [];  
      this.calendarValue = new Date();  
      this.hasSearched = false;  
      this.resetSelection();  
    },  

    // 重置选中状态  
    resetSelection() {  
      this.activeMenuIndex = '';  
      this.selectedRobotId = null;  
      this.selectedCount = null;  
      this.pointCloudCategories = [];  
      this.imageCategories = [];  
      this.pointCloudData = {};  
      this.imageData = {};  
      this.odometryData = { list: [], total: 0, pageNum: 1 };  
      this.activePointCloudCollapse = '';  
      this.activeImageCollapse = '';  
      
      if (this.showViewer) {  
        this.clearViewer();  
      }  
    },  

    // ==================== 日历相关方法 ====================  
    
    // 日历选择处理  
    handleCalendarPick(date) {  
      const selectedDate = this.formatDate(date);  
      this.selectedDateRange = [selectedDate, selectedDate];  
      this.queryByDateRange();  
    },  

    // 处理日历日期点击  
    handleDateClick(date) {  
      if (this.hasTasksOnDate(date)) {  
        const selectedDate = this.formatDate(date);  
        this.selectedDateRange = [selectedDate, selectedDate];  
        this.queryByDateRange();  
      }  
    },  

    // 检查指定日期是否有任务  
    hasTasksOnDate(date) {  
      const dateStr = this.formatDate(date);  
      
      // 使用缓存的映射表快速查找  
      if (this.taskDateMap.has(dateStr)) {  
        return this.taskDateMap.get(dateStr).length > 0;  
      }  
      
      // 如果没有缓存，实时计算  
      const tasksOnDate = this.receptionCounts.filter(task => {  
        if (!task.minTime || !task.maxTime) return false;  
        const taskStartDate = this.formatDate(new Date(task.minTime));  
        const taskEndDate = this.formatDate(new Date(task.maxTime));  
        return dateStr >= taskStartDate && dateStr <= taskEndDate;  
      });  
      
      // 缓存结果  
      this.taskDateMap.set(dateStr, tasksOnDate);  
      return tasksOnDate.length > 0;  
    },  

    // 获取指定日期的任务数量  
    getTaskCountOnDate(date) {  
      const dateStr = this.formatDate(date);  
      
      if (this.taskDateMap.has(dateStr)) {  
        return this.taskDateMap.get(dateStr).length;  
      }  
      
      const tasksOnDate = this.receptionCounts.filter(task => {  
        if (!task.minTime || !task.maxTime) return false;  
        const taskStartDate = this.formatDate(new Date(task.minTime));  
        const taskEndDate = this.formatDate(new Date(task.maxTime));  
        return dateStr >= taskStartDate && dateStr <= taskEndDate;  
      });  
      
      this.taskDateMap.set(dateStr, tasksOnDate);  
      return tasksOnDate.length;  
    },  

    // 获取日历日期样式类  
    getCalendarDayClass(date) {  
      const classes = [];  
      
      if (this.hasTasksOnDate(date)) {  
        classes.push('has-tasks');  
      }  
      
      if (this.selectedDateRange && this.selectedDateRange.length === 2) {  
        const dateStr = this.formatDate(date);  
        const [start, end] = this.selectedDateRange;  
        if (dateStr >= start && dateStr <= end) {  
          classes.push('in-selected-range');  
        }  
      }  
      
      return classes;  
    },  

    // 格式化日期  
    formatDate(date) {  
      if (!date) return '';  
      const d = new Date(date);  
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;  
    },  

    // 构建任务日期映射表  
    buildTaskDateMap() {  
      this.taskDateMap.clear();  
      
      this.receptionCounts.forEach(task => {  
        if (!task.minTime || !task.maxTime) return;  
        
        const startDate = new Date(task.minTime);  
        const endDate = new Date(task.maxTime);  
        
        // 遍历任务的每一天  
        const currentDate = new Date(startDate);  
        while (currentDate <= endDate) {  
          const dateStr = this.formatDate(currentDate);  
          
          if (!this.taskDateMap.has(dateStr)) {  
            this.taskDateMap.set(dateStr, []);  
          }  
          this.taskDateMap.get(dateStr).push(task);  
          
          currentDate.setDate(currentDate.getDate() + 1);  
        }  
      });  
    },  

    // ==================== 快捷日期选择 ====================  
    
    // 设置今天范围  
    setTodayRange() {  
      const today = this.formatDate(new Date());  
      this.selectedDateRange = [today, today];  
      this.queryByDateRange();  
    },  

    // 设置本周范围  
    setWeekRange() {  
      const today = new Date();  
      const firstDay = new Date(today.setDate(today.getDate() - today.getDay()));  
      const lastDay = new Date(today.setDate(today.getDate() - today.getDay() + 6));  
      
      this.selectedDateRange = [this.formatDate(firstDay), this.formatDate(lastDay)];  
      this.queryByDateRange();  
    },  

    // 设置本月范围  
    setMonthRange() {  
      const today = new Date();  
      const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);  
      const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0);  
      
      this.selectedDateRange = [this.formatDate(firstDay), this.formatDate(lastDay)];  
      this.queryByDateRange();  
    },  

    // ==================== 数据获取方法 ====================  

    // 获取会话时间区间信息  
    async fetchReceptionCounts() {  
      try {  
        const token = this.$store.getters.token;  
        const config = { headers: { 'Authorization': `Bearer ${token}` } };  
        const response = await axios.get(`${API_BASE_URL}/api/query/reception-counts`, config);  
        
        if (response.data.code === 200) {  
          this.receptionCounts = response.data.data || [];  
          // 构建日期映射表  
          this.buildTaskDateMap();  
          console.log('获取会话时间区间（MyBatis数据库直查）:', this.receptionCounts);  
        } else {  
          throw new Error(response.data.message || '获取失败');  
        }  
      } catch (error) {  
        console.error('获取接收会话列表失败:', error);  
        this.$message.error('获取接收会话列表失败：' + (error.response?.data?.message || error.message));  
      }  
    },  

    // 获取指定会话的机器人列表  
    async fetchRobotsForCount(count) {  
      if (this.robotsByCount[count] && this.robotsByCount[count].list) return;  

      this.$set(this.robotsByCount, count, { loading: true, list: [] });  
      try {  
        const token = this.$store.getters.token;  
        const config = { headers: { 'Authorization': `Bearer ${token}` } };  
        const response = await axios.get(`${API_BASE_URL}/api/query/robots-by-count/${count}`, config);  
        
        if (response.data.code === 200) {  
          this.$set(this.robotsByCount, count, { loading: false, list: response.data.data || [] });  
        } else {  
          throw new Error(response.data.message || '获取失败');  
        }  
      } catch (error) {  
        console.error(`获取会话 #${count} 的机器人列表失败:`, error);  
        this.$message.error(`获取会话 #${count} 的机器人列表失败`);  
        this.$set(this.robotsByCount, count, { loading: false, list: [] });  
      }  
    },  

    handleMenuOpen(index) {  
      this.fetchRobotsForCount(Number(index));  
    },  

    async handleRobotSelect(robotId, count) {  
      if (this.selectedRobotId === robotId && this.selectedCount === count) return;  
      
      this.activeMenuIndex = `${count}-${robotId}`;  
      this.selectedRobotId = robotId;  
      this.selectedCount = count;  
      this.isLoadingCategories = true;  

      // 重置所有数据  
      this.pointCloudCategories = [];   
      this.imageCategories = [];  
      this.pointCloudData = {};   
      this.imageData = {};  
      this.odometryData = { list: [], total: 0, pageNum: 1 };  
      this.activePointCloudCollapse = '';  
      this.activeImageCollapse = '';  

      if (this.showViewer) {  
        this.clearViewer();  
      }  

      try {  
        await Promise.all([  
          this.fetchPointCloudCategories(),  
          this.fetchImageCategories(),  
          this.fetchOdometry(1)  
        ]);  
      } catch(error) {  
        console.error('加载机器人数据分类时出错:', error);  
        this.$message.error('加载机器人数据分类时出错');  
      } finally {  
        this.isLoadingCategories = false;  
      }  
    },  

    // 获取点云分类  
    async fetchPointCloudCategories() {  
      if (!this.selectedRobotId || !this.selectedCount) return;  
      const { selectedRobotId, selectedCount } = this;  
      try {  
        const token = this.$store.getters.token;  
        const config = { headers: { 'Authorization': `Bearer ${token}` } };  
        const response = await axios.get(`${API_BASE_URL}/api/query/pointcloud-categories/${selectedRobotId}/${selectedCount}`, config);  
        if (response.data.code === 200) {  
          this.pointCloudCategories = response.data.data || [];  
        }  
      } catch (error) {   
        console.error('获取点云分类失败:', error);  
        this.$message.error('获取点云分类失败');   
      }  
    },  

    // 获取图像分类  
    async fetchImageCategories() {  
      if (!this.selectedRobotId || !this.selectedCount) return;  
      const { selectedRobotId, selectedCount } = this;  
      try {  
        const token = this.$store.getters.token;  
        const config = { headers: { 'Authorization': `Bearer ${token}` } };  
        const response = await axios.get(`${API_BASE_URL}/api/query/image-categories/${selectedRobotId}/${selectedCount}`, config);  
        if (response.data.code === 200) {  
          this.imageCategories = response.data.data || [];  
        }  
      } catch (error) {   
        console.error('获取图像分类失败:', error);  
        this.$message.error('获取图像分类失败');   
      }  
    },  

    // 获取指定分类的点云数据  
    async fetchPointCloudsByCategory(frame, pageNum) {  
      this.$set(this.pointCloudData, frame, { ...this.pointCloudData[frame], loading: true });  
      try {  
        const token = this.$store.getters.token;  
        const config = { headers: { 'Authorization': `Bearer ${token}` } };  
        const response = await axios.get(`${API_BASE_URL}/api/query/pointclouds/${this.selectedRobotId}/${this.selectedCount}/${frame}?pageNum=${pageNum}`, config);  
        if (response.data.code === 200) {  
          this.$set(this.pointCloudData, frame, { ...response.data.data, pageNum, loading: false });  
        }  
      } catch (error) {   
        console.error(`获取点云分类[${frame}]失败:`, error);  
        this.$message.error(`获取点云分类[${frame}]失败`);   
      }  
    },  

    // 获取指定分类的图像数据  
    async fetchImagesByCategory(session, pageNum) {  
      this.$set(this.imageData, session, { ...this.imageData[session], loading: true });  
      try {  
        const token = this.$store.getters.token;  
        const config = { headers: { 'Authorization': `Bearer ${token}` } };  
        const response = await axios.get(`${API_BASE_URL}/api/query/images/${this.selectedRobotId}/${this.selectedCount}/${session}?pageNum=${pageNum}`, config);  
        if (response.data.code === 200) {  
          const data = response.data.data;  
          // 清理旧的blob URL  
          const oldList = this.imageData[session] && this.imageData[session].list;  
          if (oldList) {  
            oldList.forEach(img => {  
              if (img.blobUrl) URL.revokeObjectURL(img.blobUrl);  
            });  
          }  
          this.$set(this.imageData, session, { ...data, pageNum, loading: false });  
          this.loadImagesAsBlobs(session);  
        }  
      } catch (error) {   
        console.error(`获取图像分类[${session}]失败:`, error);  
        this.$message.error(`获取图像分类[${session}]失败`);   
      }  
    },  
    
    // 加载图像为Blob URL  
    async loadImagesAsBlobs(session) {  
      const imagesToLoad = this.imageData[session].list;  
      if (!imagesToLoad) return;  
      const token = this.$store.getters.token;  
      const config = { headers: { 'Authorization': `Bearer ${token}` }, responseType: 'blob' };  
      for (const image of imagesToLoad) {  
        if (image.blobUrl) continue;  
        try {  
          const pathParts = image.filePath.replace(/\\/g, '/').split('/');  
          const filename = pathParts.pop();  
          const type = pathParts.pop();  
          const url = `${API_BASE_URL}/api/files/${type}/${filename}`;  
          const response = await axios.get(url, config);  
          this.$set(image, 'blobUrl', URL.createObjectURL(response.data));  
        } catch (error) {  
          console.error('加载图片失败:', error);  
          this.$set(image, 'blobUrl', '');  
        }  
      }  
    },  

    // 获取轨迹数据  
    async fetchOdometry(pageNum) {  
        this.isLoadingOdom = true;  
        try {  
            const token = this.$store.getters.token;  
            const config = { headers: { 'Authorization': `Bearer ${token}` } };  
            const response = await axios.get(`${API_BASE_URL}/api/query/odometry/${this.selectedRobotId}/${this.selectedCount}?pageNum=${pageNum}&pageSize=15`, config);  
            if (response.data.code === 200) {  
                this.odometryData = { ...response.data.data, pageNum };  
            }  
        } catch (error) {  
            console.error('获取路径数据失败:', error);  
            this.$message.error('获取路径数据失败');  
        } finally {  
            this.isLoadingOdom = false;  
        }  
    },  
    
    // ==================== 事件处理方法 ====================  
    
    // 折叠面板变化处理  
    handlePointCloudCollapseChange(activeName) {  
      if (activeName && !this.pointCloudData[activeName]) {  
        this.fetchPointCloudsByCategory(activeName, 1);  
      }  
    },  
    handleImageCollapseChange(activeName) {  
      if (activeName && !this.imageData[activeName]) {  
        this.fetchImagesByCategory(activeName, 1);  
      }  
    },  

    // 分页处理  
    handlePointCloudPageChange(frame, newPage) {  
      this.fetchPointCloudsByCategory(frame, newPage);  
    },  
    handleImagePageChange(session, newPage) {  
      this.fetchImagesByCategory(session, newPage);  
    },  
    handleOdomPageChange(newPage) {  
      this.fetchOdometry(newPage);  
    },  

    // ==================== 3D查看器方法 ====================  

    // 查看点云  
    async viewPointCloud(rowData, frameCategory) {  
          // 根据 frameCategory 判断点云类型
      if (frameCategory.includes('彩色')) {
        this.currentPointCloudType = 'color';
      } else if (frameCategory.includes('强度')) {
        this.currentPointCloudType = 'intensity';
      } else {
        this.currentPointCloudType = 'intensity'; // 默认设置为强度点云
      }

          if (frameCategory === 'Potree点云') {
        // NEW: 调用新方法进行路径转换
        const metaJsonPath = this.convertPlyPathToMetaJsonPath(rowData.filePath);

        if (metaJsonPath) {
          // 将转换后的 meta.json 路径传给Potree渲染页面
          this.potreeViewerUrl = metaJsonPath; 
          this.showPotreeViewer = true;
          
          // ...
        } else {
          this.$message.error('无法生成有效的 meta.json 路径，请检查点云文件路径格式。');
        }
      } else {
        const { filePath } = rowData;  
        this.showViewer = true;  
        this.viewerTitle = `点云: ${filePath.split(/[\\/]/).pop()}`;  
        this.viewerStatus = '正在加载点云...';  
        this.viewerOdomData = null;  

        try {  
            const token = this.$store.getters.token;  
            const config = { headers: { 'Authorization': `Bearer ${token}` }, responseType: 'blob' };  
            const pathParts = filePath.replace(/\\/g, '/').split('/');  
            const filename = pathParts.pop();  
            const type = pathParts.pop();  

            const url = `${API_BASE_URL}/api/files/${type}/${filename}`;  
            const response = await axios.get(url, config);  
            
            if (this.viewerFileUrl) {  
                URL.revokeObjectURL(this.viewerFileUrl);  
            }  

            this.viewerFileUrl = URL.createObjectURL(response.data);  
            this.viewerStatus = '加载成功';  

        } catch (error) {  
            console.error('加载点云文件失败:', error);
            this.$message.error('加载点云文件失败');  
            this.viewerStatus = '加载失败';  
        }  
      }
       
    },  
    startPotreeResize(event) {
      this.isPotreeResizing = true;
      const startX = event.clientX;
      const startWidth = this.potreeViewerWidth;

      const handleMouseMove = (e) => {
        if (!this.isPotreeResizing) return;
        const deltaX = startX - e.clientX;
        const newWidth = startWidth + deltaX;
        // 可以为Potree设置不同的宽度限制
        if (newWidth >= 400 && newWidth <= 1200) {
          this.potreeViewerWidth = newWidth;
        }
      };

      const handleMouseUp = () => {
        this.isPotreeResizing = false;
        document.removeEventListener('mousemove', handleMouseMove);
        document.removeEventListener('mouseup', handleMouseUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
        
        // 通知子组件更新其尺寸
        this.$nextTick(() => {
          if (this.$refs.potreeViewer && this.$refs.potreeViewer.handleResize) {
            this.$refs.potreeViewer.handleResize();
          }
        });
      };
      
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    },

    // NEW: Potree 查看器的全屏切换方法
    togglePotreeFullscreen() {
      const el = this.$refs.potreeAside; // 通过ref获取DOM元素
      if (!el) return;

      if (document.fullscreenElement) {
        // 如果当前已是全屏状态，则退出全屏
        document.exitFullscreen();
      } else {
        // 否则，请求进入全屏
        el.requestFullscreen().catch(err => {
          this.$message.error(`无法进入全屏模式: ${err.message}`);
        });
      }
    },

    // NEW: 用于监听浏览器全屏状态变化的处理器
    handleFullscreenChange() {
      this.isPotreeFullscreen = !!document.fullscreenElement;
      // 全屏状态改变后，可能需要通知Potree渲染器调整大小
      this.$nextTick(() => {
          if (this.$refs.potreeViewer && this.$refs.potreeViewer.handleResize) {
            this.$refs.potreeViewer.handleResize();
          }
      });
    },

    closePotreeViewer() {
      // 如果当前是全屏模式，先退出全屏再关闭
      if (this.isPotreeFullscreen) {
        document.exitFullscreen();
      }
      this.showPotreeViewer = false;
      this.potreeViewerUrl = '';
    },
    // 查看当前页轨迹  
    viewOdometry(odomData) {  
      if (!odomData || odomData.length === 0) {  
        this.$message.warning('当前页没有路径数据可渲染');  
        return;  
      }  
      this.showViewer = true;  
      this.viewerTitle = `轨迹: ${this.selectedRobotId} (当前页)`;  
      this.viewerFileUrl = null;  
      this.viewerOdomData = [...odomData];  
      this.viewerStatus = `已加载 ${odomData.length} 个轨迹点`;  
    },  

    // 查看完整轨迹  
    async viewAllOdometry() {  
      this.showViewer = true;  
      this.viewerStatus = '正在加载完整轨迹...';  
      try {  
        const token = this.$store.getters.token;  
        const config = { headers: { 'Authorization': `Bearer ${token}` } };  
        const response = await axios.get(`${API_BASE_URL}/api/query/odometry/${this.selectedRobotId}/${this.selectedCount}?pageNum=1&pageSize=99999`, config);  
        if (response.data.code === 200 && response.data.data.list.length > 0) {  
          const allOdomData = response.data.data.list;  
          this.viewerTitle = `轨迹: ${this.selectedRobotId} (完整)`;  
          this.viewerFileUrl = null;  
          this.viewerOdomData = [...allOdomData];  
          this.viewerStatus = `已加载完整轨迹 ${allOdomData.length} 点`;  
        } else {  
          this.$message.info('没有查询到完整轨迹数据');  
          this.viewerStatus = '无完整轨迹数据';  
        }  
      } catch (error) {  
        console.error('加载完整轨迹失败:', error);  
        this.$message.error('加载完整轨迹失败');  
        this.viewerStatus = '加载失败';  
      }  
    },  

    // 查看器控制  
    closeViewer() {  
      this.showViewer = false;  
      this.clearViewer();  
    },  
    clearViewer() {  
      this.viewerTitle = '3D查看器';  
      this.viewerOdomData = null;  
      this.viewerStatus = '';  
      this.currentPointCount = 0;  
      if (this.viewerFileUrl) {  
        URL.revokeObjectURL(this.viewerFileUrl);  
        this.viewerFileUrl = null;  
      }  
      if (this.$refs.pointCloudViewer) {  
        this.$refs.pointCloudViewer.clearScene();  
      }  
    },  
    resetCamera() {  
      if (this.$refs.pointCloudViewer) {  
        this.$refs.pointCloudViewer.resetCamera();  
      }  
    },  
    handlePointCountUpdate(count) {  
      this.currentPointCount = count;  
    },  
    
    // 查看器大小调整  
    startResize(event) {  
      this.isResizing = true;  
      const startX = event.clientX;  
      const startWidth = this.viewerWidth;  

      const handleMouseMove = (e) => {  
        if (!this.isResizing) return;  
        const deltaX = startX - e.clientX;  
        const newWidth = startWidth + deltaX;  
        if (newWidth >= 300 && newWidth <= 1000) {  
          this.viewerWidth = newWidth;  
        }  
      };  

      const handleMouseUp = () => {  
        this.isResizing = false;  
        document.removeEventListener('mousemove', handleMouseMove);  
        document.removeEventListener('mouseup', handleMouseUp);  
        document.body.style.cursor = '';  
        document.body.style.userSelect = '';  
        
        this.$nextTick(() => {  
          if (this.$refs.pointCloudViewer) {  
            this.$refs.pointCloudViewer.handleResize();  
          }  
        });  
      };  
      
      document.addEventListener('mousemove', handleMouseMove);  
      document.addEventListener('mouseup', handleMouseUp);  
      document.body.style.cursor = 'col-resize';  
      document.body.style.userSelect = 'none';  
    },

    // NEW: 获取文件名的方法
    getFileName(filePath) {
        if (!filePath) return '未知文件';
        const parts = filePath.split(/[\\/]/);
        return parts[parts.length - 1];
    },

    // NEW: 泛用文件下载方法
    downloadFile(fileUrl, filename) {
        if (!fileUrl) {
            this.$message.warning('没有文件可以下载。');
            return;
        }
        const link = document.createElement('a');
        link.href = fileUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        this.$message.success(`'${filename}' 已开始下载。`);
    },

    // NEW: 图片下载方法 (复用泛用下载逻辑)
    downloadImageFromGallery(imageUrl, filename) {
        this.downloadFile(imageUrl, filename);
    },

    // NEW: 点云文件下载方法 (复用泛用下载逻辑)
    async downloadPointCloud(filePath) {
        if (!filePath) {
            this.$message.warning('点云文件路径为空，无法下载。');
            return;
        }

        // 直接从后端获取文件，responseType设为'blob'
        try {
            const token = this.$store.getters.token;
            const config = { headers: { 'Authorization': `Bearer ${token}` }, responseType: 'blob' };
            
            // 假设filePath是完整的可下载路径或者可以通过某种规则构建
            // 例如: /api/files/pointcloud/20230101_robot1_frame1.ply
            // 这里需要根据实际后端文件服务路径进行调整
            const pathParts = filePath.replace(/\\/g, '/').split('/');
            const filename = pathParts.pop();
            const type = pathParts.pop(); // 例如 'pointcloud'
            const url = `${API_BASE_URL}/api/files/${type}/${filename}`;

            const response = await axios.get(url, config);
            const blobUrl = URL.createObjectURL(response.data);
            this.downloadFile(blobUrl, filename);
            URL.revokeObjectURL(blobUrl); // 下载完成后释放URL对象

        } catch (error) {
            console.error('下载点云文件失败:', error);
            this.$message.error('下载点云文件失败：' + (error.response?.data?.message || error.message));
        }
    }
  },  
  
  mounted() {  
    this.fetchReceptionCounts();  
  },  
  
  beforeDestroy() {  
    // 清理资源  
    if (this.viewerFileUrl) {  
      URL.revokeObjectURL(this.viewerFileUrl);  
    }  
    Object.values(this.imageData).forEach(sessionData => {  
        if (sessionData && sessionData.list) {  
            sessionData.list.forEach(img => {  
                if(img.blobUrl) URL.revokeObjectURL(img.blobUrl);  
            });  
        }  
    });  
    // 清理任务日期映射  
    this.taskDateMap.clear();  

    // NEW: 清理Potree viewer URL
    if (this.potreeViewerUrl) {
      URL.revokeObjectURL(this.potreeViewerUrl);
    }

    // NEW: 移除全屏事件监听器
    document.removeEventListener('fullscreenchange', this.handleFullscreenChange);
    document.removeEventListener('webkitfullscreenchange', this.handleFullscreenChange);
    document.removeEventListener('mozfullscreenchange', this.handleFullscreenChange);
    document.removeEventListener('MSFullscreenChange', this.handleFullscreenChange);
  }  
};  
</script>  

<style scoped>  
:root {  
  --primary-color: #409EFF;  
  --success-color: #67C23A;  
  --warning-color: #E6A23C;  
  --danger-color: #F56C6C;  
  --bg-color-base: #f5f7fa;  
  --bg-color-light: #ffffff;  
  --border-color: #e4e7ed;  
  --text-color-primary: #303133;  
  --text-color-regular: #606266;  
  --text-color-secondary: #909399;  
  --box-shadow-light: 0 2px 12px 0 rgba(0, 0, 0, 0.06);  
  --border-radius: 4px;  
}  

/* ==================== 主布局 ==================== */  
.data-management-layout {  
  display: flex;  
  flex-direction: column;  
  height: 100vh;  
  background-color: var(--bg-color-base);  
  overflow: hidden;  
}  

.main-content-layout {  
  flex: 1;  
  display: flex;  
  min-height: 0;  
}  

/* ==================== 顶部查询控制区域 ==================== */  
.query-controls-header {  
  display: flex;  
  align-items: center;  
  justify-content: space-between;  
  padding: 15px 20px;  
  background-color: var(--bg-color-light);  
  border-bottom: 1px solid var(--border-color);  
  flex-wrap: wrap;  
  gap: 15px;  
  flex-shrink: 0;  
  box-shadow: var(--box-shadow-light);  
  z-index: 10;  
}  

.query-mode-selector {  
  flex-shrink: 0;  
}  

.time-query-controls {  
  display: flex;  
  align-items: center;  
  gap: 10px;  
  flex-wrap: wrap;  
}  

.query-result-info {  
  flex-shrink: 0;  
}  

/* ==================== 左侧菜单样式 ==================== */  
.menu-aside {  
  border-right: 1px solid var(--border-color);  
  background-color: var(--bg-color-light);  
  flex-shrink: 0;  
  display: flex;  
  flex-direction: column;  
  height: 100%;  
}  

.aside-header {  
  padding: 16px;  
  border-bottom: 1px solid var(--border-color);  
  flex-shrink: 0;  
  background-color: #fcfcfc;  
  display: flex;  
  flex-direction: column;  
  align-items: center;  
  gap: 10px;  
}  

.header-title {  
  display: flex;  
  align-items: center;  
  font-size: 18px;  
  font-weight: 600;  
  color: var(--primary-color);  
}  

.header-title i {  
  margin-right: 8px;  
}  

.query-mode-indicator {  
  align-self: stretch;  
  text-align: center;  
}  

/* ==================== 日历容器样式 ==================== */  
.calendar-container {  
  padding: 10px;  
  border-bottom: 1px solid var(--border-color);  
  max-height: 400px;  
  overflow-y: auto;  
  flex-shrink: 0;  
}  

.calendar-legend {  
  display: flex;  
  justify-content: space-around;  
  margin-bottom: 10px;  
  padding: 8px;  
  background-color: #f8f9fa;  
  border-radius: var(--border-radius);  
  font-size: 12px;  
}  

.legend-item {  
  display: flex;  
  align-items: center;  
  gap: 4px;  
}  

.legend-color {  
  width: 12px;  
  height: 12px;  
  border-radius: 2px;  
  border: 1px solid #ddd;  
}  

.legend-color.normal-day {  
  background-color: #ffffff;  
}  

.legend-color.task-day {  
  background-color: #409EFF;  
}  

.legend-color.selected-day {  
  background-color: #67C23A;  
}  

.legend-text {  
  color: var(--text-color-secondary);  
  font-size: 11px;  
}  

/* ==================== 日历日期单元格样式 ==================== */  
.calendar-day {  
  position: relative;  
  width: 100%;  
  height: 100%;  
  display: flex;  
  flex-direction: column;  
  align-items: center;  
  justify-content: center;  
  cursor: pointer;  
  transition: all 0.3s ease;  
  border-radius: 4px;  
  padding: 4px;  
  min-height: 45px;  
}  

.calendar-day:hover {  
  background-color: rgba(64, 158, 255, 0.1);  
  transform: scale(1.05);  
}  

/* 有任务的日期 - 蓝色标出 */  
.calendar-day.has-tasks {  
  background-color: #409EFF !important;  
  color: white;  
  font-weight: bold;  
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);  
}  

.calendar-day.has-tasks:hover {  
  background-color: #337ecc !important;  
  transform: scale(1.08);  
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);  
}  

/* 查询范围内的日期 - 绿色标出 */  
.calendar-day.in-selected-range {  
  background-color: #67C23A !important;  
  color: white;  
  box-shadow: 0 2px 8px rgba(103, 194, 58, 0.3);  
}  

.calendar-day.in-selected-range:hover {  
  background-color: #529b2e !important;  
}  

/* 同时满足有任务和在选择范围内的日期 */  
.calendar-day.has-tasks.in-selected-range {  
  background: linear-gradient(45deg, #409EFF 50%, #67C23A 50%);  
  animation: pulse 2s ease-in-out infinite;  
}  

@keyframes pulse {  
  0%, 100% {  
    box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);  
  }  
  50% {  
    box-shadow: 0 4px 16px rgba(103, 194, 58, 0.5);  
  }  
}  

.day-number {  
  font-size: 14px;  
  font-weight: inherit;  
  z-index: 1;  
}  

.task-indicator {  
  position: absolute;  
  top: 2px;  
  right: 2px;  
  background-color: rgba(255, 255, 255, 0.9);  
  color: var(--primary-color);  
  border-radius: 10px;  
  padding: 1px 4px;  
  font-size: 9px;  
  line-height: 1;  
  min-width: 18px;  
  text-align: center;  
  display: flex;  
  align-items: center;  
  gap: 1px;  
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);  
}  

.calendar-day.has-tasks .task-indicator {  
  background-color: rgba(255, 255, 255, 0.95);  
  color: #409EFF;  
  font-weight: bold;  
  animation: taskPulse 2s ease-in-out infinite;  
}  

.calendar-day.in-selected-range .task-indicator {  
  background-color: rgba(255, 255, 255, 0.95);  
  color: #67C23A;  
}  

@keyframes taskPulse {  
  0%, 100% {  
    transform: scale(1);  
    opacity: 1;  
  }  
  50% {  
    transform: scale(1.1);  
    opacity: 0.8;  
  }  
}  

.task-count {  
  font-size: 8px;  
  font-weight: bold;  
}  

/* ==================== 会话菜单样式 ==================== */  
.session-menu {  
  flex-grow: 1;  
  overflow-y: auto;  
  border-right: none;  
}  

.task-info {  
  flex: 1;  
  min-width: 0;  
}  

.task-title {  
  display: block;  
  font-weight: 600;  
  color: var(--text-color-primary);  
  line-height: 1.4;  
}  

.time-range {  
  font-size: 11px;  
  color: var(--text-color-secondary);  
  margin-top: 2px;  
  line-height: 1.2;  
  font-weight: normal;  
  word-break: break-all;  
}  

.query-match-indicator {  
  margin-top: 4px;  
}  

.el-menu-item {  
  transition: all 0.2s ease;  
}  

.el-menu-item i {  
  margin-right: 8px;  
}  

.el-menu-item.is-active {  
  background-color: #ecf5ff !important;  
  color: var(--primary-color) !important;  
  font-weight: bold;  
}  

.robot-submenu-content {  
  padding: 5px 0;  
}  

.no-data-tip {  
  text-align: center;  
  color: var(--text-color-secondary);  
  font-size: 13px;  
  padding: 10px 20px;  
}  

/* ==================== 无结果和提示区域 ==================== */  
.no-time-result, .time-query-tip {  
  padding: 40px 20px;  
  text-align: center;  
  flex-grow: 1;  
  display: flex;  
  align-items: center;  
  justify-content: center;  
}  

.tip-actions {  
  display: flex;  
  flex-direction: column;  
  gap: 8px;  
  margin-top: 15px;  
}  

.tip-actions .el-button {  
  margin: 0;  
}  

/* ==================== 中间内容区样式 ==================== */  
.content-container {  
  flex-grow: 1;  
  display: flex;  
  flex-direction: column;  
  min-width: 0;  
}  

.content-main {  
  padding: 20px;  
  overflow-y: auto;  
  flex-grow: 1;  
}  

.full-height-placeholder {  
  height: 100%;  
}  

.data-tabs {  
  box-shadow: var(--box-shadow-light);  
}  

.el-tabs__item i {  
  margin-right: 4px;  
}  

.data-pagination {  
  margin-top: 15px;  
  text-align: right;  
}  

/* ==================== 图像画廊样式 ==================== */  
.image-gallery {  
  display: grid;  
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));  
  gap: 15px;  
}  

.image-item-card {  
  border-radius: var(--border-radius);  
  transition: all 0.3s ease;  
}  

.image-item-card:hover {  
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);  
  transform: translateY(-2px);  
}  

.gallery-image {  
  width: 100%;  
  height: 120px;  
  display: block;  
}  

.image-placeholder {  
  display: flex;  
  justify-content: center;  
  align-items: center;  
  width: 100%;  
  height: 100%;  
  background: var(--bg-color-base);  
  color: var(--text-color-secondary);  
}  

.image-card-footer {  
  padding: 10px;  
  font-size: 12px;  
  color: var(--text-color-regular);  
  text-align: center;  
  white-space: nowrap;  
  overflow: hidden;  
  text-overflow: ellipsis;  
}  

::v-deep .image-item-card .el-card__body {  
  padding: 0;  
}  

/* ==================== 轨迹数据底部样式 ==================== */  
.odom-footer {  
    display: flex;  
    justify-content: space-between;  
    align-items: center;  
    margin-top: 15px;  
}  

.odom-footer .data-pagination {  
    margin-top: 0;  
    padding: 0;  
}  

/* ==================== 调整手柄样式 ==================== */  
.resize-handle {  
  width: 5px;  
  background-color: #dcdfe6;  
  cursor: col-resize;  
  flex-shrink: 0;  
  position: relative;  
  transition: background-color 0.2s;  
}  

.resize-handle:hover,   
.resize-handle:active {  
  background-color: var(--primary-color);  
}  

/* ==================== 3D查看器样式 ==================== */  
.viewer-aside {  
  background-color: var(--bg-color-light);  
  display: flex;  
  flex-direction: column;  
  flex-shrink: 0;  
  min-width: 300px;  
  max-width: 1000px;  
  border-left: 1px solid var(--border-color);  
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.05);  
}  

.viewer-header {  
  padding: 12px 15px;  
  border-bottom: 1px solid var(--border-color);  
  display: flex;  
  justify-content: space-between;  
  align-items: center;  
  flex-shrink: 0;  
}  

.viewer-title {  
  font-size: 16px;  
  font-weight: 600;  
  color: var(--text-color-primary);  
  overflow: hidden;  
  text-overflow: ellipsis;  
  white-space: nowrap;  
}  

.viewer-controls {  
  display: flex;  
  gap: 8px;  
  flex-shrink: 0;  
}  

.viewer-content {  
  flex-grow: 1;  
  position: relative;  
  overflow: hidden;  
}  

.viewer-status {  
  padding: 8px 15px;  
  background-color: #fcfcfc;  
  border-top: 1px solid var(--border-color);  
  display: flex;  
  gap: 10px;  
  align-items: center;  
  flex-shrink: 0;  
  font-size: 12px;  
}  

/* ==================== 日历组件样式覆盖 ==================== */  
::v-deep .el-calendar {  
  --el-calendar-border: 1px solid var(--border-color);  
}  

::v-deep .el-calendar-table .el-calendar-day {  
  padding: 0;  
  height: 50px;  
}  

::v-deep .el-calendar__header {  
  display: flex;  
  justify-content: space-between;  
  padding: 12px 20px;  
  border-bottom: 1px solid var(--border-color);  
}  

::v-deep .el-calendar__body {  
  padding: 8px;  
}  

::v-deep .el-calendar-table thead th {  
  padding: 8px 0;  
  font-weight: 600;  
  color: var(--text-color-primary);  
}  

/* ==================== submenu样式调整 ==================== */  
::v-deep .el-submenu__title {  
  display: flex !important;  
  align-items: flex-start !important;  
  padding: 12px 20px !important;  
  line-height: 1.2 !important;  
  height: auto !important;  
  min-height: 56px;  
}  

/* ==================== 响应式适配 ==================== */  
@media (max-width: 768px) {  
  .data-management-layout {  
    flex-direction: column;  
  }  
  
  .main-content-layout {  
    flex-direction: column;  
  }  
  
  .query-controls-header {  
    flex-direction: column;  
    align-items: stretch;  
    padding: 10px;  
  }  
  
  .time-query-controls {  
    justify-content: center;  
    flex-direction: column;  
    width: 100%;  
  }  
  
  .time-query-controls .el-date-picker {  
    width: 100% !important;  
  }  
  
  .menu-aside {  
    width: 100% !important;  
    height: 300px;  
  }  
  
  .calendar-container {  
    max-height: 200px;  
    padding: 5px;  
  }  
  
  .calendar-legend {  
    font-size: 10px;  
    flex-wrap: wrap;  
  }  
  
  .legend-item {  
    flex-direction: column;  
    align-items: center;  
    text-align: center;  
  }  
  
  .viewer-aside {  
    width: 100% !important;  
    height: 300px;  
  }  
  .resize-handle {
    display: none;
  }
  
  .image-gallery {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  }
  
  .calendar-day {
    min-height: 35px;
    padding: 2px;
  }
  
  .day-number {
    font-size: 12px;
  }
  
  .task-indicator {
    font-size: 8px;
    min-width: 14px;
    padding: 0 2px;
  }
  
  .aside-header {
    padding: 10px;
  }
  
  .header-title {
    font-size: 16px;
  }
  
  .tip-actions {
    flex-direction: row;
    justify-content: space-around;
    flex-wrap: wrap;
  }
  
  .tip-actions .el-button {
    flex: 1;
    margin: 0 2px;
    font-size: 12px;
  }
}

@media (max-width: 480px) {
  .query-controls-header {
    padding: 8px;
  }
  
  .calendar-day {
    min-height: 28px;
    padding: 1px;
  }
  
  .day-number {
    font-size: 11px;
  }
  
  .task-indicator {
    top: 1px;
    right: 1px;
    font-size: 7px;
    min-width: 12px;
    padding: 0 1px;
  }
  
  .task-count {
    font-size: 7px;
  }
  
  .calendar-legend {
    font-size: 9px;
    padding: 4px;
  }
  
  .legend-color {
    width: 10px;
    height: 10px;
  }
  
  .content-main {
    padding: 10px;
  }
  
  .image-gallery {
    grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
    gap: 10px;
  }
  
  .gallery-image {
    height: 80px;
  }
}

/* ==================== 滚动条美化 ==================== */
.session-menu::-webkit-scrollbar,
.content-main::-webkit-scrollbar,
.calendar-container::-webkit-scrollbar {
  width: 6px;
}

.session-menu::-webkit-scrollbar-track,
.content-main::-webkit-scrollbar-track,
.calendar-container::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.session-menu::-webkit-scrollbar-thumb,
.content-main::-webkit-scrollbar-thumb,
.calendar-container::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.session-menu::-webkit-scrollbar-thumb:hover,
.content-main::-webkit-scrollbar-thumb:hover,
.calendar-container::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* ==================== 特殊效果和动画 ==================== */
.calendar-day.has-tasks::before {
  content: '';
  position: absolute;
  top: -2px;
  left: -2px;
  right: -2px;
  bottom: -2px;
  background: linear-gradient(45deg, #409EFF, #66b3ff, #409EFF);
  border-radius: 6px;
  z-index: -1;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.calendar-day.has-tasks:hover::before {
  opacity: 0.3;
}

/* 加载动画优化 */
.el-loading-mask {
  background-color: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(2px);
}

/* 表格样式优化 */
.el-table--stripe .el-table__body tr.el-table__row--striped td {
  background-color: #fafafa;
}

.el-table th {
  background-color: #f8f9fa;
  color: var(--text-color-primary);
  font-weight: 600;
}

.el-table .cell {
  word-break: break-word;
}

/* 按钮组优化 */
.el-button-group .el-button:hover {
  z-index: 1;
}

.el-button-group .el-button:focus {
  z-index: 2;
}

/* 标签样式优化 */
.el-tag {
  border-radius: 4px;
  font-weight: 500;
}

/* 空状态优化 */
.el-empty {
  padding: 40px 0;
}

.el-empty__description {
  color: var(--text-color-secondary);
  font-size: 14px;
  line-height: 1.5;
}

/* ==================== 辅助工具类 ==================== */
.text-center {
  text-align: center;
}

.text-left {
  text-align: left;
}

.text-right {
  text-align: right;
}

.flex-center {
  display: flex;
  align-items: center;
  justify-content: center;
}

.flex-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.flex-start {
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
}

.flex-end {
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
}

.mt-5 { margin-top: 5px; }
.mt-10 { margin-top: 10px; }
.mt-15 { margin-top: 15px; }
.mt-20 { margin-top: 20px; }

.mb-5 { margin-bottom: 5px; }
.mb-10 { margin-bottom: 10px; }
.mb-15 { margin-bottom: 15px; }
.mb-20 { margin-bottom: 20px; }

.ml-5 { margin-left: 5px; }
.ml-10 { margin-left: 10px; }
.ml-15 { margin-left: 15px; }
.ml-20 { margin-left: 20px; }

.mr-5 { margin-right: 5px; }
.mr-10 { margin-right: 10px; }
.mr-15 { margin-right: 15px; }
.mr-20 { margin-right: 20px; }

.p-5 { padding: 5px; }
.p-10 { padding: 10px; }
.p-15 { padding: 15px; }
.p-20 { padding: 20px; }

.full-width { width: 100%; }
.full-height { height: 100%; }

/* ==================== 过渡动画 ==================== */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter, .fade-leave-to {
  opacity: 0;
}

.slide-enter-active, .slide-leave-active {
  transition: transform 0.3s ease;
}

.slide-enter {
  transform: translateX(-100%);
}

.slide-leave-to {
  transform: translateX(100%);
}

.zoom-enter-active, .zoom-leave-active {
  transition: transform 0.3s ease;
}

.zoom-enter {
  transform: scale(0.8);
}

.zoom-leave-to {
  transform: scale(1.2);
}

/* ==================== 打印样式 ==================== */
@media print {
  .query-controls-header,
  .viewer-aside,
  .resize-handle,
  .viewer-controls {
    display: none !important;
  }
  
  .main-content-layout {
    flex-direction: column;
  }
  
  .menu-aside {
    width: 100% !important;
    border-right: none;
    page-break-inside: avoid;
  }
  
  .content-main {
    padding: 10px;
    page-break-inside: avoid;
  }
  
  .calendar-day {
    border: 1px solid #ddd;
    print-color-adjust: exact;
  }
  
  .calendar-day.has-tasks {
    background-color: #e6e6e6 !important;
    color: #000000 !important;
    print-color-adjust: exact;
  }
  
  .task-indicator {
    background-color: #ffffff !important;
    color: #000000 !important;
    border: 1px solid #000000;
  }
  
  .data-tabs {
    box-shadow: none;
    border: 1px solid #ddd;
  }
  
  .image-gallery {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  }
  
  .el-table,
  .el-pagination {
    page-break-inside: avoid;
  }
}

/* ==================== 高对比度支持 ==================== */
@media (prefers-contrast: high) {
  :root {
    --primary-color: #0066cc;
    --success-color: #006600;
    --border-color: #666666;
    --text-color-primary: #000000;
  }
  
  .calendar-day.has-tasks {
    background-color: #000080 !important;
    color: #ffffff !important;
    border: 2px solid #ffffff !important;
  }
  
  .calendar-day.in-selected-range {
    background-color: #006400 !important;
    color: #ffffff !important;
    border: 2px solid #ffffff !important;
  }
  
  .task-indicator {
    background-color: #ffffff !important;
    color: #000000 !important;
    border: 1px solid #000000 !important;
    font-weight: bold;
  }
  
  .el-button--primary {
    background-color: #0066cc !important;
    border-color: #0066cc !important;
  }
  
  .el-tag--success {
    background-color: #006600 !important;
    border-color: #006600 !important;
    color: #ffffff !important;
  }
}

/* ==================== 减少动画支持 ==================== */
@media (prefers-reduced-motion: reduce) {
  .calendar-day,
  .image-item-card,
  .task-indicator,
  .el-button,
  .el-menu-item {
    transition: none !important;
    animation: none !important;
  }
  
  .calendar-day:hover {
    transform: none !important;
  }
  
  .calendar-day.has-tasks .task-indicator {
    animation: none !important;
  }
  
  @keyframes pulse {
    0%, 100% { opacity: 1; }
  }
  
  @keyframes taskPulse {
    0%, 100% { transform: none; opacity: 1; }
  }
}

/* ==================== 深色模式支持 ==================== */
@media (prefers-color-scheme: dark) {
  :root {
    --primary-color: #79bbff;
    --success-color: #95d475;
    --warning-color: #f0c674;
    --danger-color: #f78989;
    --bg-color-base: #1a1a1a;
    --bg-color-light: #2d2d2d;
    --border-color: #4a4a4a;
    --text-color-primary: #e4e7ed;
    --text-color-regular: #cfcfcf;
    --text-color-secondary: #a8abb2;
  }
  
  .calendar-day.has-tasks {
    background-color: #79bbff !important;
    color: #1a1a1a !important;
  }
  
  .calendar-day.in-selected-range {
    background-color: #95d475 !important;
    color: #1a1a1a !important;
  }
  
  .legend-color.normal-day {
    background-color: #2d2d2d;
    border-color: #4a4a4a;
  }
  
  .legend-color.task-day {
    background-color: #79bbff;
  }
  
  .legend-color.selected-day {
    background-color: #95d475;
  }
  
  .el-table th {
    background-color: #3a3a3a;
    color: var(--text-color-primary);
  }
  
  .el-table--stripe .el-table__body tr.el-table__row--striped td {
    background-color: #353535;
  }
  
  .image-placeholder {
    background-color: #3a3a3a;
    color: var(--text-color-secondary);
  }
}

/* ==================== 焦点状态优化 ==================== */
.calendar-day:focus-visible,
.el-button:focus-visible,
.el-menu-item:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

.calendar-day.has-tasks:focus-visible {
  outline-color: #ffffff;
}

/* ==================== 选择状态优化 ==================== */
::selection {
  background-color: rgba(64, 158, 255, 0.3);
  color: inherit;
}

::-moz-selection {
  background-color: rgba(64, 158, 255, 0.3);
  color: inherit;
}

/* ==================== 性能优化 ==================== */
.calendar-day,
.image-item-card,
.viewer-content {
  will-change: transform;
}

.session-menu,
.content-main {
  contain: layout style paint;
}
.image-gallery-container {
  display: flex;
  flex-wrap: wrap; /* 允许项目换行 */
  
  /* --- ↓↓↓ 修改这里的值来调整图片间距 ↓↓↓ --- */
  gap: 15px; /* 将 10px 改为 15px，或者您希望的任何值 */
  
  min-height: 100px;
  padding: 10px;
}

.gallery-item-wrapper {
  width: calc((100% - 3 * 15px) / 4); /* 假设每行 4 张图，减去 3 个间距 */
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  overflow: hidden;
}

.gallery-image-item {
  width: 100%;
  height: 120px; /* 或根据需要调整高度 */
  display: block;
  flex-shrink: 0;
}

.image-info-footer {
  padding: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #f8f9fa;
  border-top: 1px solid var(--border-color);
}

.image-filename {
  font-size: 12px;
  color: var(--text-color-regular);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-grow: 1;
  margin-right: 5px;
}

.image-info-footer .el-button {
  padding: 0;
  height: auto;
}

@media (max-width: 768px) {
  .gallery-item-wrapper {
    width: calc((100% - 15px) / 2); /* 两列布局 */
  }
}

@media (max-width: 480px) {
  .gallery-item-wrapper {
    width: 100%; /* 单列布局 */
  }
}

/* ==================== 错误状态样式 ==================== */
.error-state {
  color: var(--danger-color);
  font-size: 14px;
  text-align: center;
  padding: 20px;
}

.error-state i {
  font-size: 24px;
  margin-bottom: 10px;
  display: block;
}

.loading-state {
  color: var(--text-color-secondary);
  font-size: 14px;
  text-align: center;
  padding: 20px;
}

.loading-state i {
  font-size: 24px;
  margin-bottom: 10px;
  display: block;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* ==================== 特殊情况处理 ==================== */
/* 处理长文本溢出 */
.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-break {
  word-break: break-word;
  word-wrap: break-word;
}

/* 处理空数据状态 */
.empty-data-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: var(--text-color-secondary);
}

.empty-data-container i {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.6;
}

.empty-data-container p {
  font-size: 16px;
  margin: 0;
  line-height: 1.5;
}

/* 处理网络错误状态 */
.network-error {
  background-color: #fef0f0;
  border: 1px solid #fde2e2;
  color: var(--danger-color);
  padding: 12px 16px;
  border-radius: var(--border-radius);
  margin: 16px 0;
}

.network-error i {
  margin-right: 8px;
}

/* 处理成功状态 */
.success-message {
  background-color: #f0f9ff;
  border: 1px solid #e1f5fe;
  color: var(--success-color);
  padding: 12px 16px;
  border-radius: var(--border-radius);
  margin: 16px 0;
}
.viewer-content {
  flex-grow: 1;
  position: relative;
  overflow: hidden;
}
.success-message i {
  margin-right: 8px;
}
</style>
