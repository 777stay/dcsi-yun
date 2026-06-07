<template>
    <div class="multi-view-layout" ref="layoutContainer">
      <div class="main-content">
        <el-card class="pointcloud-card">
          <div slot="header" class="card-header">
            <span>多机点云 ({{ formattedPointCount }} 点)</span>
            <div class="header-controls">
              <el-select v-model="selectedRobotForNav" placeholder="选择机器人下发目标" size="small" class="topic-selector" :disabled="isSettingGoal" clearable>
                <el-option v-for="robot in onlineRobots" :key="robot.id" :label="robot.config.displayName" :value="robot.id"/>
              </el-select>
              <el-button @click="startSetGoalMode" type="success" size="small" plain :disabled="!selectedRobotForNav || isSettingGoal">
                {{ isSettingGoal ? '设置中...' : '设定任务点' }}
              </el-button>
              <el-button @click="handleRefresh" type="primary" size="small" plain>刷新</el-button>
              <el-switch v-model="isAccumulating" active-text="累积显示" />
            </div>
          </div>
          <multi-point-cloud-viewer 
            ref="multiViewerRef"
            :is-accumulating="isAccumulating"
            :is-setting-goal="isSettingGoal"
            :target-robot-id-for-nav="selectedRobotForNav"
            :point-cloud-selections="pointCloudSelections"
            :point-size="pointSize"
            :max-points-per-cloud="maxPoints"
            :time-window-sec="timeWindow"
            :point-density="pointDensity" 
            @update:point-count="count => currentPointCount = count"
            @goal-captured="onGoalCaptured"
          />
        </el-card>
        
        <el-card class="controls-card">
          <div slot="header">显示控制</div>
          <el-divider>点云控制</el-divider>
          <div class="control-item">
              <span class="control-label">点云上限</span>
              <el-input-number v-model="maxPoints" :min="100000" :max="50000000" :step="1000000" size="small" controls-position="right" style="width: 100%;" />
          </div>
          <div class="control-item">
              <span class="control-label">点大小</span>
              <el-slider v-model="pointSize" :min="0.01" :max="0.5" :step="0.01" />
          </div>
          <div class="control-item">
              <span class="control-label">时间窗口(秒)</span>
              <el-slider v-model="timeWindow" :min="1" :max="3000" :step="1" :disabled="!isAccumulating" />
          </div>
          <div class="control-item">
              <span class="control-label">显示密度 (%)</span>
              <el-slider v-model="pointDensity" :min="1" :max="60" :step="1" />
          </div>
  
          <div v-for="robot in onlineRobots" :key="robot.id">
              <el-divider>{{ robot.config.displayName }}</el-divider>
              <div class="control-item" v-if="availableImageFrames[robot.id] ">
                <span>图像窗口</span>
                <el-switch :value="imageWindows[robot.id] && imageWindows[robot.id].visible" @input="val => toggleImageWindow(robot.id, val)"/>
              </div>
              <div class="control-item" v-if="availablePointCloudFrames[robot.id] ">
                <span>点云显示</span>
                <el-select v-model="pointCloudSelections[robot.id]" placeholder="选择点云" size="small" clearable style="width: 150px">
                    <el-option v-for="frame in availablePointCloudFrames[robot.id]" :key="frame" :label="frame" :value="frame" />
                </el-select>
              </div>
          </div>
          
          <!-- 替换 el-empty 为简单的提示 -->
          <div v-if="onlineRobots.length === 0" class="empty-state">
            <i class="el-icon-info" style="font-size: 48px; color: #c0c4cc;"></i>
            <p style="color: #909399; margin-top: 16px;">无在线机器人</p>
          </div>
        </el-card>
      </div>
  
      <!-- 可拖拽、可调整大小的图像窗口 -->
      <div v-for="robotId in Object.keys(availableImageFrames)" :key="robotId">
        <div 
          v-if="imageWindows[robotId] && imageWindows[robotId].visible" 
          :ref="`window-${robotId}`"
          class="draggable-window" 
          :class="{ maximized: imageWindows[robotId].isMaximized }"
          :style="{ 
            transform: `translate(${imageWindows[robotId].x}px, ${imageWindows[robotId].y}px)`,
            width: imageWindows[robotId].width + 'px',
            height: imageWindows[robotId].height + 'px'
          }"
        >
          <div class="window-header" @mousedown="startDrag(robotId, $event)">
            <el-select v-if="imageWindows[robotId].sources.length > 1" v-model="imageWindows[robotId].selectedSource" size="small" class="image-source-selector">
              <el-option v-for="src in imageWindows[robotId].sources" :key="src.frame" :label="src.frame" :value="src.frame" />
            </el-select>
            <span v-else class="window-title">{{ robotConfigs[robotId] && robotConfigs[robotId].displayName }}</span>
            <div class="window-buttons">
              <el-button @click="toggleMaximize(robotId)" type="primary" size="small" circle>
                <i :class="imageWindows[robotId].isMaximized ? 'el-icon-copy-document' : 'el-icon-full-screen'"></i>
              </el-button>
              <el-button @click="toggleImageWindow(robotId, false)" type="danger" size="small" circle>
                <i class="el-icon-close"></i>
              </el-button>
            </div>
          </div>
          <div class="window-content">
            <img :src="currentImageSrc(robotId)" v-if="currentImageSrc(robotId)" />
            <div v-else class="image-placeholder">等待图像...</div>
          </div>
          <!-- 调整大小的把手 -->
          <div class="resize-handle" @mousedown.stop="startResize(robotId, $event)"></div>
        </div>
      </div>
    </div>
  </template>
  
  <script>
  import axios from 'axios'
  import MultiPointCloudViewer from '@/components/MultiPointCloudViewer.vue'
  
  import { getBackendBaseUrl, getWsBaseUrl } from '@/utils/runtimeApi'

  const API_BASE_URL = getBackendBaseUrl()
  const WS_BASE_URL = getWsBaseUrl()
  
  export default {
    name: 'MultiViewLayout',
    components: {
      MultiPointCloudViewer
    },
    data() {
      return {
        // Store相关状态
        statuses: {},
        isConnected: false,
        robotConfigs: {},
        statusWebSocket: null,
        
        // 组件状态
        pointSize: 0.1,
        maxPoints: 2000000,
        timeWindow: 100,
        pointDensity: 10,
        currentPointCount: 0,
        isAccumulating: true,
        isSettingGoal: false,
        selectedRobotForNav: null,
        pointCloudSelections: {},
        availablePointCloudFrames: {},
        imageWindows: {},
        availableImageFrames: {},
        isImageViewerVisible: false,
        enlargedImageUrl: '',
        
        // WebSocket和拖拽相关
        dataSockets: {},
        activeDrag: {
          type: '',
          robotId: '',
          startX: 0,
          startY: 0,
          initialX: 0,
          initialY: 0,
          initialW: 0,
          initialH: 0
        }
      }
    },
    
    computed: {
      formattedPointCount() {
        return this.currentPointCount.toLocaleString()
      },
      
      onlineRobots() {
        return Object.keys(this.statuses)
          .filter(id => id.startsWith('robot_') && this.statuses[id] && this.statuses[id].online)
          .map(id => ({ 
            id, 
            config: this.robotConfigs[id] || { displayName: id }
          }))
      },
      
      actualRobots() {
        const robots = { ...this.robotConfigs }
        delete robots.robot
        return robots
      }
    },
    
    methods: {
      // Store相关方法
      async fetchRobotConfigs() {
        try {
          const token = this.$store.getters.token;
          const config = { headers: { 'Authorization': `Bearer ${token}` } };
          const response = await axios.get(`${API_BASE_URL}/api/robots`, config)
          console.log('返回的数据:', response.data.data)
          this.robotConfigs = response.data.data
          console.log('Robot configs:', this.robotConfigs)
        } catch (error) {
          console.error("Failed to fetch robot configs:", error)
        }
      },
      
      connectWebSocket() {
        if (this.isConnected) return
        
        this.statusWebSocket = new WebSocket(`${WS_BASE_URL}/ws/status`)
        
        this.statusWebSocket.onopen = () => {
          this.isConnected = true
          console.log('WebSocket connected successfully.')
        }
        
        this.statusWebSocket.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data)
            this.statuses = data
          } catch (error) {
            console.error("Failed to parse WebSocket message:", error)
          }
        }
        
        this.statusWebSocket.onclose = () => {
          this.isConnected = false
          Object.keys(this.statuses).forEach(robotId => {
            if (this.statuses[robotId]) {
              this.$set(this.statuses[robotId], 'online', false)
            }
          })
          console.log('WebSocket disconnected.')
        }
        
        this.statusWebSocket.onerror = (error) => {
          console.error('WebSocket Error:', error)
        }
      },
      
      async sendCommand(robotId, commandName) {
        try {
          const token = this.$store.getters.token;
          const config = { headers: { 'Authorization': `Bearer ${token}` } };
          const response = await axios.post(`${API_BASE_URL}/api/command/${robotId}/${commandName}`, config)
         
          console.log('Command response:', response.data)
        } catch (error) {
          console.error('Failed to send command:', error)
        }
      },
      
      // 组件方法
      currentImageSrc(robotId) {
        const win = this.imageWindows[robotId]
        if (!win || !win.selectedSource) return ''
        const source = win.sources.find(s => s.frame === win.selectedSource)
        return source ? source.src : ''
      },
      
      async setupAndConnect() {
        this.connectWebSocket()
        if (Object.keys(this.robotConfigs).length === 0) {
          await this.fetchRobotConfigs()
        }
        // 等待一下让robotConfigs加载完成
        setTimeout(() => {
          this.connectDataWebSockets()
        }, 500)
      },
      
      connectDataWebSockets() {
        const socketProtocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
        
        Object.keys(this.robotConfigs)
          .filter(id => id.startsWith('robot_'))
          .forEach((robotId, index) => {
            if (this.dataSockets[robotId]) return
            
            if (!this.imageWindows[robotId]) {
              this.$set(this.imageWindows, robotId, {
                sources: [], 
                selectedSource: null, 
                visible: false,
                x: 250 + index * 50, 
                y: 100 + index * 50,
                width: 320, 
                height: 280,
                isMaximized: false,
                lastState: null
              })
            }
            
            if (!this.availableImageFrames[robotId]) {
              this.$set(this.availableImageFrames, robotId, [])
            }
            if (!this.availablePointCloudFrames[robotId]) {
              this.$set(this.availablePointCloudFrames, robotId, [])
            }
            if (this.pointCloudSelections[robotId] === undefined) {
              this.$set(this.pointCloudSelections, robotId, null)
            }
            
            const socket = new WebSocket(`${WS_BASE_URL}/ws/data/${robotId}`)
            this.$set(this.dataSockets, robotId, socket)
            
            socket.onopen = () => console.log(`Multi-view data socket connected for ${robotId}`)
            socket.onclose = () => console.log(`Multi-view data socket disconnected for ${robotId}`)
            
            socket.onmessage = (event) => {
              const packet = JSON.parse(event.data)
             // console.log('收到的包:', packet)
              if (packet.type === 'status_update') return
              
              const senderId = packet.sender || robotId
              const frameId = packet.frame
              
              if (!frameId && packet.type !== 'PACKET_ODOM') {
                console.warn('Received packet without frameId:', packet)
                return
              }
              
              const typeName = packet.type
              
              if (typeName === 'PACKET_POINTCLOUD_XYZI' ) {
                if (!this.availablePointCloudFrames[senderId]) {
                  this.$set(this.availablePointCloudFrames, senderId, [])
                }
                if (!this.availablePointCloudFrames[senderId].includes(frameId)) {
                  this.availablePointCloudFrames[senderId].push(frameId)
                  if (this.pointCloudSelections[senderId] === null) {
                    this.$set(this.pointCloudSelections, senderId, frameId)
                  }
                }
                if (this.$refs.multiViewerRef) {
                  this.$refs.multiViewerRef.addPoints(frameId, packet.pclXyzi.points)
                }
              } else if(typeName === 'PACKET_POINTCLOUD_XYZRGB') {
                if (!this.availablePointCloudFrames[senderId]) {
                  this.$set(this.availablePointCloudFrames, senderId, [])
                }
                if (!this.availablePointCloudFrames[senderId].includes(frameId)) {
                  this.availablePointCloudFrames[senderId].push(frameId)
                }
                if (this.$refs.multiViewerRef) {
                  this.$refs.multiViewerRef.addPoints(frameId, packet.pclXyzrgb.points)
                }
              }
                else if (typeName === 'PACKET_IMAGE') {
                if (!this.availableImageFrames[senderId]) {
                  this.$set(this.availableImageFrames, senderId, [])
                }
                if (!this.availableImageFrames[senderId].includes(frameId)) {
                  this.availableImageFrames[senderId].push(frameId)
                }
                const win = this.imageWindows[robotId]
                if (win) {
                  let source = win.sources.find(s => s.frame === frameId)
                  if (!source) {
                    source = { frame: frameId, src: '' }
                    win.sources.push(source)
                    if (!win.selectedSource) {
                      win.selectedSource = frameId
                    }
                  }
                 
                  source.src = `data:image/${packet.format || 'jpeg'};base64,${packet.image.data}`
                }
              } else if (typeName === 'PACKET_ODOM') {
                if (this.$refs.multiViewerRef && packet.frame === 'odom') {
                  this.$refs.multiViewerRef.addOdom(packet)
                }
              }
            }
          })
      },
      
      handleRefresh() {
        if (this.$refs.multiViewerRef) {
          this.$refs.multiViewerRef.clearAll()
        }
      },
      
      startSetGoalMode() {
        if (!this.selectedRobotForNav) {
          this.$message.warning('请先选择一个机器人！')
          return
        }
        this.isSettingGoal = true
        this.$message.info('请在点云窗口中点击目标位置。')
      },
      
      async onGoalCaptured(goalPoint) {
        const robotId = this.selectedRobotForNav
        this.isSettingGoal = false
        
        if (!this.$refs.multiViewerRef) return
        
        if (!goalPoint || !robotId) {
          if (robotId) {
            this.$refs.multiViewerRef.clearNavGoal(robotId)
          }
          return
        }
        
        const displayName = this.robotConfigs[robotId] && this.robotConfigs[robotId].displayName || robotId
        this.$refs.multiViewerRef.setNavGoal(robotId, goalPoint, displayName)
        
        const goalPose = {
          x: goalPoint.x,
          y: goalPoint.y,
          z: goalPoint.z,
          orientation: { x: 0, y: 0, z: 0, w: 1.0 }
        }
        
        try {
          await this.$confirm(
            `确认发送任务目标给 ${displayName} 吗?`,
            '确认任务目标',
            { type: 'info' }
          )
          const token = this.$store.getters.token;
          const config = { headers: { 'Authorization': `Bearer ${token}` } };
          await axios.post(`${API_BASE_URL}/api/send_nav_goal/${robotId}`, goalPose, config)
          this.$message.success('任务目标已发送！')
        } catch (error) {
          if (this.$refs.multiViewerRef) {
            this.$refs.multiViewerRef.clearNavGoal(robotId)
          }
          if (error !== 'cancel') {
            this.$message.error('发送任务目标失败。')
          } else {
            this.$message.info('已取消发送。')
          }
        }
      },
      
      // 拖拽相关方法
      getWindowRef(robotId) {
        const refs = this.$refs[`window-${robotId}`]
        return refs && refs[0] ? refs[0] : null
      },
      
      startDrag(robotId, event) {
        if (event.target.closest('button, .el-select')) return
        const winState = this.imageWindows[robotId]
        if (winState.isMaximized) return
        
        this.activeDrag = {
          type: 'move',
          robotId,
          startX: event.clientX,
          startY: event.clientY,
          initialX: winState.x,
          initialY: winState.y,
          initialW: 0,
          initialH: 0
        }
        document.addEventListener('mousemove', this.onDragMove)
        document.addEventListener('mouseup', this.onDragEnd)
      },
      
      onDragMove(event) {
        if (this.activeDrag.type !== 'move') return
        const targetEl = this.getWindowRef(this.activeDrag.robotId)
        if (!targetEl) return
        
        const dx = event.clientX - this.activeDrag.startX
        const dy = event.clientY - this.activeDrag.startY
        const newX = this.activeDrag.initialX + dx
        const newY = this.activeDrag.initialY + dy
        
        targetEl.style.transform = `translate(${newX}px, ${newY}px)`
      },
      
      onDragEnd(event) {
        if (this.activeDrag.type === 'move') {
          const winState = this.imageWindows[this.activeDrag.robotId]
          const dx = event.clientX - this.activeDrag.startX
          const dy = event.clientY - this.activeDrag.startY
          winState.x = this.activeDrag.initialX + dx
          winState.y = this.activeDrag.initialY + dy
        }
        document.removeEventListener('mousemove', this.onDragMove)
        document.removeEventListener('mouseup', this.onDragEnd)
        this.activeDrag.type = ''
      },
      
      startResize(robotId, event) {
        const winState = this.imageWindows[robotId]
        this.activeDrag = {
          type: 'resize',
          robotId,
          startX: event.clientX,
          startY: event.clientY,
          initialX: 0,
          initialY: 0,
          initialW: winState.width,
          initialH: winState.height
        }
        document.addEventListener('mousemove', this.onResizeMove)
        document.addEventListener('mouseup', this.onResizeEnd)
      },
      
      onResizeMove(event) {
        if (this.activeDrag.type !== 'resize') return
        const targetEl = this.getWindowRef(this.activeDrag.robotId)
        if (!targetEl) return
        
        const dx = event.clientX - this.activeDrag.startX
        const dy = event.clientY - this.activeDrag.startY
        const newW = Math.max(200, this.activeDrag.initialW + dx)
        const newH = Math.max(150, this.activeDrag.initialH + dy)
        
        targetEl.style.width = `${newW}px`
        targetEl.style.height = `${newH}px`
      },
      
      onResizeEnd(event) {
        if (this.activeDrag.type === 'resize') {
          const winState = this.imageWindows[this.activeDrag.robotId]
          const dx = event.clientX - this.activeDrag.startX
          const dy = event.clientY - this.activeDrag.startY
          winState.width = Math.max(200, this.activeDrag.initialW + dx)
          winState.height = Math.max(150, this.activeDrag.initialH + dy)
        }
        document.removeEventListener('mousemove', this.onResizeMove)
        document.removeEventListener('mouseup', this.onResizeEnd)
        this.activeDrag.type = ''
      },
      
      toggleImageWindow(robotId, visible) {
        if (this.imageWindows[robotId]) {
          this.imageWindows[robotId].visible = visible
          if (visible) {
            this.$nextTick(() => {
              const el = this.getWindowRef(robotId)
              if (el) {
                const win = this.imageWindows[robotId]
                el.style.transform = `translate(${win.x}px, ${win.y}px)`
                el.style.width = `${win.width}px`
                el.style.height = `${win.height}px`
              }
            })
          }
        }
      },
      
      toggleMaximize(robotId) {
        const win = this.imageWindows[robotId]
        const container = this.$refs.layoutContainer
        if (!container) return
        
        if (win.isMaximized) {
          if (win.lastState) {
            Object.assign(win, win.lastState)
          }
          win.isMaximized = false
        } else {
          win.lastState = { 
            x: win.x, 
            y: win.y, 
            width: win.width, 
            height: win.height 
          }
          const rect = container.getBoundingClientRect()
          win.x = 0
          win.y = 0
          win.width = rect.width
          win.height = rect.height
          win.isMaximized = true
        }
      }
    },
    
    mounted() {
      this.setupAndConnect()
    },
    
    beforeDestroy() {
      // 关闭status websocket
      if (this.statusWebSocket && this.statusWebSocket.readyState === WebSocket.OPEN) {
        this.statusWebSocket.close()
      }
      
      // 关闭所有data websockets
      Object.values(this.dataSockets).forEach(socket => {
        if (socket && socket.readyState === WebSocket.OPEN) {
          socket.close()
        }
      })
      
      // 清理全局事件监听器
      document.removeEventListener('mousemove', this.onDragMove)
      document.removeEventListener('mouseup', this.onDragEnd)
      document.removeEventListener('mousemove', this.onResizeMove)
      document.removeEventListener('mouseup', this.onResizeEnd)
    }
  }
  </script>
  
  <style scoped>
  .control-label { 
    flex: 0 0 80px; 
    font-size: 14px; 
    color: #606266; 
    margin-right: 10px; 
  }
  .el-slider { 
    flex-grow: 1; 
  }
  .image-source-selector { 
    width: 150px; 
  }
  
  .multi-view-layout { 
    display: flex; 
    flex-direction: column; 
    height: 100vh; 
    min-height: 600px;
    padding: 15px; 
    box-sizing: border-box; 
    background-color: #f0f2f5; 
    position: relative; 
    overflow: hidden; 
  }
  .main-content { 
    flex-grow: 1; 
    display: flex; 
    gap: 15px; 
    min-height: 0; 
  }
  .pointcloud-card { 
    flex-grow: 1; 
    display: flex; 
    flex-direction: column; 
  }
  .pointcloud-card >>> .el-card__body { 
    flex-grow: 1; 
    padding: 0; 
    overflow: hidden; 
  }
  .card-header { 
    display: flex; 
    justify-content: space-between; 
    align-items: center; 
    width: 100%; 
  }
  .header-controls { 
    display: flex; 
    align-items: center; 
    gap: 10px; 
  }
  .topic-selector { 
    width: 220px; 
  }
  .controls-card { 
    flex-shrink: 0; 
    width: 250px; 
  }
  .control-item { 
    display: flex; 
    justify-content: space-between; 
    align-items: center; 
    margin-bottom: 10px; 
  }
  
  /* 空状态样式 */
  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 40px 20px;
    text-align: center;
  }
  
  /* Draggable Window Styles */
  .draggable-window {
    position: absolute;
    top: 0;
    left: 0;
    min-width: 200px;
    min-height: 150px;
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    background-color: white;
    z-index: 1000;
    display: flex;
    flex-direction: column;
    border: 1px solid #e4e7ed;
  }
  .draggable-window.maximized {
    border-radius: 0;
    box-shadow: none;
    z-index: 2000;
    transition: width 0.2s ease-in-out, height 0.2s ease-in-out, transform 0.2s ease-in-out;
  }
  .window-header { 
    display: flex; 
    justify-content: space-between; 
    align-items: center; 
    padding: 8px 12px; 
    background-color: #f5f7fa; 
    border-bottom: 1px solid #e4e7ed; 
    cursor: move; 
    border-top-left-radius: 8px; 
    border-top-right-radius: 8px; 
    font-weight: 500; 
    flex-shrink: 0; 
  }
  .maximized .window-header { 
    cursor: default; 
  }
  .window-title { 
    flex-grow: 1; 
    overflow: hidden; 
    text-overflow: ellipsis; 
    white-space: nowrap; 
    margin-right: 10px; 
  }
  .window-buttons { 
    display: flex; 
    align-items: center; 
    gap: 8px; 
    flex-shrink: 0; 
  }
  .window-content { 
    flex-grow: 1; 
    display: flex; 
    justify-content: center; 
    align-items: center; 
    background-color: #000; 
    overflow: hidden; 
  }
  .window-content img { 
    width: 100%; 
    height: 100%; 
    object-fit: contain; 
  }
  .image-placeholder { 
    color: #909399; 
    font-size: 14px; 
  }
  .resize-handle { 
    position: absolute; 
    bottom: 0; 
    right: 0; 
    width: 15px; 
    height: 15px; 
    cursor: nwse-resize; 
    z-index: 1001; 
  }
  .maximized .resize-handle { 
    display: none; 
  }
  </style>
