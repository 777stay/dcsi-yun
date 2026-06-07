<template>
  <div class="potree-viewer-container">
    <!-- 加载状态显示 -->
    <div v-if="isLoading" class="loading-overlay">
      <div class="loading-content">
        <div class="spinner"></div>
        <p v-if="loadingLibrary">正在检查库文件...</p>
        <p v-else-if="loadingProgress > 0">
          正在加载点云数据... {{ loadingProgress }}%
        </p>
        <p v-else>正在初始化查看器...</p>
      </div>
    </div>
    
    <!-- 错误显示 -->
    <div v-if="error" class="error-overlay">
      <div class="error-content">
        <h3>❌ 初始化失败</h3>
        <p>{{ error }}</p>
        <div class="error-actions">
          <button @click="retryInitialization" class="retry-btn">🔄 重试</button>
          <button @click="showDebugInfo = !showDebugInfo" class="debug-btn">
            🔍 调试信息
          </button>
        </div>
        
        <!-- 调试信息 -->
        <div v-if="showDebugInfo" class="debug-info">
          <h4>库加载状态:</h4>
          <ul>
            <li>Three.js: {{ debugInfo.three ? '✓' : '❌' }}</li>
            <li>Potree: {{ debugInfo.potree ? '✓' : '❌' }}</li>
            <li>Controls: {{ debugInfo.controls ? '✓' : '❌' }}</li>
          </ul>
          <h4>错误详情:</h4>
          <pre>{{ debugInfo.errors }}</pre>
        </div>
      </div>
    </div>
    
    <!-- 控制面板 -->
    <div v-if="viewer && !isLoading && !error" class="control-panel">
      <div class="panel-header">
        <h3>🎛️ 控制面板</h3>
        <button @click="showControls = !showControls" class="toggle-btn">
          {{ showControls ? '🔼' : '🔽' }}
        </button>
      </div>
      
      <div v-show="showControls" class="panel-content">
        <!-- 数据集选择 -->
        <div class="control-group">
          <label>📊 数据集:</label>
          <select v-model="selectedDataset" @change="loadPointCloud">
            <option value="">请选择数据集</option>
            <option v-for="dataset in availableDatasets" 
                    :key="dataset.id" 
                    :value="dataset.id">
              {{ dataset.name }} ({{ formatNumber(dataset.pointCount) }} 点)
            </option>
          </select>
        </div>
        
        <!-- 渲染控制 -->
        <div class="control-group">
          <label>🔘 点大小:</label>
          <input type="range" 
                 v-model="pointSize" 
                 @input="updatePointSize"
                 min="0.1" 
                 max="5" 
                 step="0.1"
                 class="slider">
          <span class="value">{{ pointSize }}</span>
        </div>
        
        <div class="control-group">
          <label>📈 点预算:</label>
          <input type="range" 
                 v-model="pointBudget" 
                 @input="updatePointBudget"
                 min="100000" 
                 max="10000000" 
                 step="100000"
                 class="slider">
          <span class="value">{{ formatNumber(pointBudget) }}</span>
        </div>
        
        <!-- 视角控制 -->
        <div class="control-group">
          <label>📷 视角控制:</label>
          <div class="button-group">
            <button @click="resetCamera" class="btn btn-small">🏠 重置</button>
            <button @click="fitView" class="btn btn-small">🔍 适应</button>
            <button @click="topView" class="btn btn-small">⬆️ 俯视</button>
          </div>
        </div>
        
        <!-- 背景设置 -->
        <div class="control-group">
          <label>🎨 背景:</label>
          <select v-model="backgroundColor" @change="updateBackground">
            <option value="gradient">渐变</option>
            <option value="black">黑色</option>
            <option value="white">白色</option>
            <option value="skybox">天空盒</option>
          </select>
        </div>
        
        <!-- 操作按钮 -->
        <div class="control-group">
          <div class="button-group">
            <button @click="refreshData" class="btn">🔄 刷新数据</button>
            <button @click="exportView" class="btn">📷 导出视图</button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 状态信息 -->
    <div v-if="viewer && !isLoading && !error" class="status-panel">
      <div class="status-item">
        <span class="label">可见点数:</span>
        <span class="value">{{ formatNumber(renderStats.visiblePoints) }}</span>
      </div>
      <div class="status-item">
        <span class="label">FPS:</span>
        <span class="value">{{ renderStats.fps }}</span>
      </div>
      <div class="status-item">
        <span class="label">渲染时间:</span>
        <span class="value">{{ renderStats.renderTime }}ms</span>
      </div>
    </div>
    
    <!-- 主容器 -->
    <div ref="potreeContainer" class="potree-container"></div>
  </div>
</template>

<script>
import axios from 'axios'

export default {
  name: 'PotreeViewer',
  data() {
    return {
      // 查看器相关
      viewer: null,
      scene: null,
      pointClouds: [],
      
      // 数据相关
      availableDatasets: [],
      selectedDataset: null,
      currentDatasetInfo: null,
      
      // 状态相关
      isLoading: false,
      loadingLibrary: false,
      loadingProgress: 0,
      error: null,
      showDebugInfo: false,
      
      // UI控制
      showControls: true,
      
      // 控制参数
      pointSize: 1.0,
      pointBudget: 1000000,
      backgroundColor: 'gradient',
      
      // 统计信息
      renderStats: {
        visiblePoints: 0,
        fps: 0,
        renderTime: 0
      },
      
      // 调试信息
      debugInfo: {
        three: false,
        potree: false,
        controls: false,
        errors: []
      },
      
      // API配置
      API_BASE_URL: process.env.VUE_APP_API_URL || 'http://localhost:3000/api'
    }
  },
  
  async mounted() {
    console.log('🚀 PotreeViewer 组件挂载')
    await this.initializeApplication()
  },
  
  beforeDestroy() {
    console.log('🧹 清理 PotreeViewer 组件')
    this.cleanup()
  },
  
  methods: {
    /**
     * 初始化应用
     */
    async initializeApplication() {
      try {
        this.isLoading = true
        this.loadingLibrary = true
        this.error = null
        
        console.log('📚 检查库文件加载状态...')
        
        // 等待库文件加载完成
        await this.waitForLibraries()
        
        this.loadingLibrary = false
        console.log('🔧 开始初始化查看器...')
        
        // 初始化Potree查看器
        await this.initializePotree()
        
        console.log('📊 加载数据集列表...')
        
        // 加载数据集列表
        await this.loadAvailableDatasets()
        
        console.log('🎉 应用初始化完成!')
        
      } catch (error) {
        console.error('❌ 应用初始化失败:', error)
        this.error = error.message
        this.updateDebugInfo()
      } finally {
        this.isLoading = false
        this.loadingLibrary = false
      }
    },
    
    /**
     * 等待库文件加载完成
     */
    waitForLibraries() {
      return new Promise((resolve, reject) => {
        let attempts = 0
        const maxAttempts = 50 // 5秒超时
        
        const checkLibraries = () => {
          attempts++
          
          // 检查库加载状态
          const statusAvailable = typeof window.LibraryStatus !== 'undefined'
          const allLoaded = statusAvailable && window.LibraryStatus.allLoaded
          
          console.log(`检查库状态 (${attempts}/${maxAttempts}):`, {
            statusAvailable,
            allLoaded,
            status: statusAvailable ? window.LibraryStatus : null
          })
          
          if (allLoaded) {
            console.log('✅ 所有库加载完成')
            this.updateDebugInfo()
            resolve()
          } else if (attempts >= maxAttempts) {
            console.error('⏰ 库加载检查超时')
            this.updateDebugInfo()
            reject(new Error('库文件加载检查超时，请检查文件是否存在于 public/libs/ 目录'))
          } else {
            setTimeout(checkLibraries, 100)
          }
        }
        
        checkLibraries()
      })
    },
    
    /**
     * 更新调试信息
     */
    updateDebugInfo() {
      if (typeof window.LibraryStatus !== 'undefined') {
        this.debugInfo = {
          three: window.LibraryStatus.three,
          potree: window.LibraryStatus.potree,
          controls: window.LibraryStatus.controls,
          errors: window.LibraryStatus.errors
        }
      } else {
        this.debugInfo = {
          three: typeof THREE !== 'undefined',
          potree: typeof Potree !== 'undefined',
          controls: typeof THREE !== 'undefined' && THREE.OrbitControls,
          errors: ['LibraryStatus 对象不可用']
        }
      }
    },
    
    /**
     * 初始化Potree查看器
     */
    async initializePotree() {
      try {
        const container = this.$refs.potreeContainer
        if (!container) {
          throw new Error('找不到容器元素')
        }
        
        // 使用全局初始化函数
        if (typeof window.initializePotreeViewer === 'function') {
          console.log('🎯 调用全局初始化函数')
          this.viewer = window.initializePotreeViewer(container)
          
          if (!this.viewer) {
            throw new Error('查看器创建失败')
          }
          
          // 设置基本配置
          if (this.viewer.setPointSize) {
            this.viewer.setPointSize(this.pointSize)
          }
          if (this.viewer.setPointBudget) {
            this.viewer.setPointBudget(this.pointBudget)
          }
          if (this.viewer.setBackground) {
            this.viewer.setBackground(this.backgroundColor)
          }
          if (this.viewer.setEDLEnabled) {
            this.viewer.setEDLEnabled(true)
          }
          if (this.viewer.setFOV) {
            this.viewer.setFOV(60)
          }
          
          // 获取场景引用
          this.scene = this.viewer.scene
          
          console.log('✅ Potree查看器初始化成功')
          
          // 开始统计信息更新
          this.startStatsUpdate()
          
        } else {
          throw new Error('全局初始化函数不可用')
        }
        
      } catch (error) {
        console.error('❌ 初始化Potree查看器失败:', error)
        throw new Error('初始化查看器失败: ' + error.message)
      }
    },
    
    /**
     * 加载可用数据集
     */
    async loadAvailableDatasets() {
      try {
        console.log('📡 请求数据集列表...')
        const response = await axios.get(`${this.API_BASE_URL}/pointcloud/datasets`)
        this.availableDatasets = response.data.data || response.data || []
        console.log('📊 数据集加载成功:', this.availableDatasets.length, '个数据集')
      } catch (error) {
        console.error('❌ 加载数据集失败:', error)
        // 不抛出错误，使用空数据集列表
        this.availableDatasets = []
        this.$message?.warning('无法加载数据集列表，请检查服务器连接')
      }
    },
    
    /**
     * 加载选中的点云数据
     */
    async loadPointCloud() {
      if (!this.selectedDataset || !this.viewer) {
        console.log('⚠️ 数据集未选择或查看器未初始化')
        return
      }
      
      try {
        this.isLoading = true
        this.loadingProgress = 10
        
        console.log('🔄 清除现有点云...')
        this.clearPointClouds()
        
        console.log('📊 获取数据集信息...')
        const infoResponse = await axios.get(`${this.API_BASE_URL}/pointcloud/dataset/${this.selectedDataset}`)
        this.currentDatasetInfo = infoResponse.data
        this.loadingProgress = 30
        
        console.log('📥 下载点云数据...')
        const dataResponse = await axios.get(`${this.API_BASE_URL}/pointcloud/data/${this.selectedDataset}`, {
          responseType: 'json',
          onDownloadProgress: (progressEvent) => {
            if (progressEvent.lengthComputable) {
              const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
              this.loadingProgress = 30 + (progress * 0.6) // 30-90%
            }
          }
        })
        
        this.loadingProgress = 90
        
        console.log('⚙️ 处理点云数据...')
        await this.processPointCloudData(dataResponse.data)
        
        this.loadingProgress = 100
        
        console.log('📷 调整相机视角...')
        this.fitCameraToPointCloud()
        
        setTimeout(() => {
          this.loadingProgress = 0
        }, 1000)
        
        console.log('✅ 点云加载完成!')
        
      } catch (error) {
        console.error('❌ 点云加载失败:', error)
        this.$message?.error('加载点云数据失败: ' + error.message)
        this.loadingProgress = 0
      } finally {
        this.isLoading = false
      }
    },
    
    /**
     * 处理点云数据
     */
    async processPointCloudData(data) {
      try {
        if (data.format === 'potree') {
          await this.loadPotreeFormat(data)
        } else {
          await this.loadRawPointData(data)
        }
      } catch (error) {
        throw new Error('处理点云数据失败: ' + error.message)
      }
    },
    
    /**
     * 加载Potree格式数据
     */
    async loadPotreeFormat(data) {
      try {
        if (typeof Potree !== 'undefined' && Potree.loadPointCloud) {
          const pointcloud = await Potree.loadPointCloud(data.url, data.name)
          if (this.viewer.addPointCloud) {
            this.viewer.addPointCloud(pointcloud)
          } else if (this.scene) {
            this.scene.add(pointcloud)
          }
          this.pointClouds.push(pointcloud)
          console.log('✅ Potree格式点云加载成功')
        } else {
          throw new Error('Potree.loadPointCloud 不可用')
        }
      } catch (error) {
        console.warn('⚠️ Potree格式加载失败，尝试原始数据处理:', error)
        // 回退到原始数据处理
        await this.loadRawPointData(data)
      }
    },
    
    /**
     * 加载原始点数据
     */
    async loadRawPointData(data) {
      try {
        const points = data.points
        if (!points || points.length === 0) {
          throw new Error('点数据为空')
        }
        
        console.log('📊 处理', points.length, '个点')
        
        // 创建几何体
        const geometry = new THREE.BufferGeometry()
        
        // 准备属性数组
        const positions = new Float32Array(points.length * 3)
        const colors = new Float32Array(points.length * 3)
        
        // 计算边界框
        let minX = Infinity, minY = Infinity, minZ = Infinity
        let maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity
        
        // 填充数据
        for (let i = 0; i < points.length; i++) {
          const point = points[i]
          const i3 = i * 3
          
          // 位置
          positions[i3] = point.x
          positions[i3 + 1] = point.y
          positions[i3 + 2] = point.z
          
          // 更新边界框
          minX = Math.min(minX, point.x)
          minY = Math.min(minY, point.y)
          minZ = Math.min(minZ, point.z)
          maxX = Math.max(maxX, point.x)
          maxY = Math.max(maxY, point.y)
          maxZ = Math.max(maxZ, point.z)
          
          // 颜色处理
          if (point.r !== undefined && point.g !== undefined && point.b !== undefined) {
            colors[i3] = point.r / 255
            colors[i3 + 1] = point.g / 255
            colors[i3 + 2] = point.b / 255
          } else {
            // 根据Z值着色
            const normalizedZ = (point.z - minZ) / (maxZ - minZ) || 0
            const color = this.intensityToColor(normalizedZ)
            colors[i3] = color.r
            colors[i3 + 1] = color.g
            colors[i3 + 2] = color.b
          }
        }
        
        // 设置几何体属性
        geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
        geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
        geometry.computeBoundingBox()
        
        // 创建材质
        const material = new THREE.PointsMaterial({
          size: this.pointSize,
          vertexColors: true,
          sizeAttenuation: true
        })
        
        // 创建点云对象
        const pointCloud = new THREE.Points(geometry, material)
        pointCloud.name = data.name || 'Point Cloud'
        
        // 添加到场景
        if (this.viewer.addPointCloud) {
          this.viewer.addPointCloud(pointCloud)
        } else if (this.scene) {
          this.scene.add(pointCloud)
        } else if (this.viewer.scene) {
          this.viewer.scene.add(pointCloud)
        }
        
        this.pointClouds.push(pointCloud)
        
        console.log('✅ 原始点云数据处理完成，包含', points.length, '个点')
        
      } catch (error) {
        throw new Error('加载原始点数据失败: ' + error.message)
      }
    },
    
    /**
     * 强度值转颜色
     */
    intensityToColor(intensity) {
      const hue = (1 - intensity) * 240 / 360 // 从蓝色到红色
      const color = new THREE.Color()
      color.setHSL(hue, 1.0, 0.5)
      return color
    },
    
    /**
     * 清除现有点云
     */
    clearPointClouds() {
      this.pointClouds.forEach(pointCloud => {
        // 清理几何体和材质
        if (pointCloud.geometry) {
          pointCloud.geometry.dispose()
        }
        if (pointCloud.material) {
          pointCloud.material.dispose()
        }
        
        // 从场景中移除
        if (this.viewer.removePointCloud) {
          this.viewer.removePointCloud(pointCloud)
        } else if (this.scene) {
          this.scene.remove(pointCloud)
        } else if (this.viewer.scene) {
          this.viewer.scene.remove(pointCloud)
        }
      })
      this.pointClouds = []
      console.log('🧹 点云已清除')
    },
    
    /**
     * 调整相机以适应点云
     */
    fitCameraToPointCloud() {
      if (!this.viewer || !this.currentDatasetInfo) return
      
      try {
        const bounds = this.currentDatasetInfo.bounds
        if (!bounds) return
        
        const center = {
          x: (bounds.min.x + bounds.max.x) / 2,
          y: (bounds.min.y + bounds.max.y) / 2,
          z: (bounds.min.z + bounds.max.z) / 2
        }
        
        const size = Math.max(
          bounds.max.x - bounds.min.x,
          bounds.max.y - bounds.min.y,
          bounds.max.z - bounds.min.z
        )
        
        if (this.viewer.camera) {
          const distance = size * 2
          this.viewer.camera.position.set(
            center.x + distance,
            center.y + distance,
            center.z + distance
          )
          this.viewer.camera.lookAt(center.x, center.y, center.z)
        }
        
        console.log('📷 相机已调整到合适位置')
        
      } catch (error) {
        console.error('❌ 调整相机失败:', error)
      }
    },
    
    /**
     * 更新点大小
     */
    updatePointSize() {
      if (this.viewer && this.viewer.setPointSize) {
        this.viewer.setPointSize(this.pointSize)
      }
      console.log('🔘 点大小已更新:', this.pointSize)
    },
    
    /**
     * 更新点预算
     */
    updatePointBudget() {
      if (this.viewer && this.viewer.setPointBudget) {
        this.viewer.setPointBudget(this.pointBudget)
      }
      console.log('📈 点预算已更新:', this.pointBudget)
    },
    
    /**
     * 更新背景
     */
    updateBackground() {
      if (this.viewer && this.viewer.setBackground) {
        this.viewer.setBackground(this.backgroundColor)
      }
      console.log('🎨 背景已更新:', this.backgroundColor)
    },
    
    /**
     * 重置相机
     */
    resetCamera() {
      this.fitCameraToPointCloud()
      console.log('🏠 相机已重置')
    },
    
    /**
     * 适应视图
     */
    fitView() {
      this.resetCamera()
    },
    
    /**
     * 俯视视角
     */
    topView() {
      if (this.viewer && this.viewer.camera && this.currentDatasetInfo) {
        const bounds = this.currentDatasetInfo.bounds
        const center = {
          x: (bounds.min.x + bounds.max.x) / 2,
          y: (bounds.min.y + bounds.max.y) / 2,
          z: (bounds.min.z + bounds.max.z) / 2
        }
        
        const size = Math.max(
          bounds.max.x - bounds.min.x,
          bounds.max.y - bounds.min.y
        )
        
        this.viewer.camera.position.set(center.x, center.y, center.z + size * 2)
        this.viewer.camera.lookAt(center.x, center.y, center.z)
        
        console.log('⬆️ 切换到俯视视角')
      }
    },
    
    /**
     * 导出视图
     */
    exportView() {
      if (this.viewer && this.viewer.renderer) {
        try {
          const canvas = this.viewer.renderer.domElement
          const link = document.createElement('a')
          link.download = `pointcloud_view_${Date.now()}.png`
          link.href = canvas.toDataURL()
          link.click()
          console.log('📷 视图已导出')
        } catch (error) {
          console.error('❌ 导出视图失败:', error)
          this.$message?.error('导出视图失败')
        }
      }
    },
    
    /**
     * 刷新数据
     */
    async refreshData() {
      console.log('🔄 刷新数据...')
      await this.loadAvailableDatasets()
      if (this.selectedDataset) {
        await this.loadPointCloud()
      }
    },
    
    /**
     * 重试初始化
     */
    async retryInitialization() {
      console.log('🔄 重试初始化...')
      this.showDebugInfo = false
      await this.initializeApplication()
    },
    
    /**
     * 开始统计信息更新
     */
    startStatsUpdate() {
      setInterval(() => {
        if (this.viewer) {
          // 这里可以根据实际的viewer API获取统计信息
          this.renderStats = {
            visiblePoints: this.calculateVisiblePoints(),
            fps: this.calculateFPS(),
            renderTime: this.calculateRenderTime()
          }
        }
      }, 1000)
    },
    
    /**
     * 计算可见点数
     */
    calculateVisiblePoints() {
      if (this.pointClouds.length > 0) {
        return this.pointClouds.reduce((total, pointCloud) => {
          if (pointCloud.geometry && pointCloud.geometry.attributes.position) {
            return total + pointCloud.geometry.attributes.position.count
          }
          return total
        }, 0)
      }
      return 0
    },
    
    /**
     * 计算FPS（简化版）
     */
    calculateFPS() {
      // 这里可以实现更精确的FPS计算
      return Math.floor(Math.random() * 10) + 50 // 临时值
    },
    
    /**
     * 计算渲染时间（简化版）
     */
    calculateRenderTime() {
      // 这里可以实现更精确的渲染时间计算
      return Math.floor(Math.random() * 10) + 5 // 临时值
    },
    
    /**
     * 格式化数字
     */
    formatNumber(num) {
      return num ? num.toLocaleString() : '0'
    },
    
    /**
     * 清理资源
     */
    cleanup() {
      this.clearPointClouds()
      if (this.viewer && this.viewer.dispose) {
        this.viewer.dispose()
      }
    }
  }
}
</script>

<style scoped>
/* 主容器 */
.potree-viewer-container {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 600px;
  background: #f0f0f0;
  overflow: hidden;
}

.potree-container {
  width: 100%;
  height: 100%;
}

/* 加载和错误覆盖层 */
.loading-overlay, .error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  color: white;
}

.loading-content, .error-content {
  text-align: center;
  padding: 30px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.9);
  max-width: 500px;
}

/* 加载动画 */
.spinner {
  border: 4px solid #f3f3f3;
  border-top: 4px solid #409eff;
  border-radius: 50%;
  width: 50px;
  height: 50px;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 错误显示 */
.error-content h3 {
  color: #f56565;
  margin-bottom: 15px;
  font-size: 20px;
}

.error-actions {
  margin: 20px 0;
}

.retry-btn, .debug-btn {
  background: #409eff;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 5px;
  cursor: pointer;
  margin: 0 10px;
  font-size: 14px;
}

.retry-btn:hover, .debug-btn:hover {
  background: #66b1ff;
}

.debug-info {
  text-align: left;
  background: rgba(255, 255, 255, 0.1);
  padding: 15px;
  border-radius: 5px;
  margin-top: 15px;
}

.debug-info h4 {
  margin: 10px 0 5px 0;
  color: #ffd700;
}

.debug-info ul {
  margin: 0;
  padding-left: 20px;
}

.debug-info pre {
  background: rgba(0, 0, 0, 0.3);
  padding: 10px;
  border-radius: 3px;
  overflow-x: auto;
  font-family: 'Courier New', monospace;
}

/* 控制面板 */
.control-panel {
  position: absolute;
  top: 20px;
  left: 20px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 100;
  min-width: 300px;
  max-width: 350px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  border-bottom: 1px solid #eee;
  background: #f8f9fa;
  border-radius: 10px 10px 0 0;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  color: #333;
}

.toggle-btn {
  background: none;
  border: none;
  font-size: 16px;
  cursor: pointer;
  padding: 5px;
  border-radius: 3px;
}

.toggle-btn:hover {
  background: rgba(0, 0, 0, 0.1);
}

.panel-content {
  padding: 20px;
}

.control-group {
  margin-bottom: 20px;
}

.control-group label {
  display: block;
  font-weight: bold;
  margin-bottom: 8px;
  color: #333;
  font-size: 14px;
}

.control-group select,
.control-group .slider {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.control-group select {
  background: white;
}

.slider {
  -webkit-appearance: none;
  height: 6px;
  border-radius: 3px;
  background: #ddd;
  outline: none;
}

.slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #409eff;
  cursor: pointer;
}

.slider::-moz-range-thumb {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #409eff;
  cursor: pointer;
  border: none;
}

.value {
  display: inline-block;
  margin-left: 10px;
  font-weight: bold;
  color: #409eff;
  min-width: 40px;
}

.button-group {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.btn {
  background: #409eff;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  flex: 1;
  min-width: 80px;
}

.btn:hover {
  background: #66b1ff;
}

.btn-small {
  padding: 6px 12px;
  font-size: 12px;
  min-width: 60px;
}

/* 状态面板 */
.status-panel {
  position: absolute;
  top: 20px;
  right: 20px;
  background: rgba(0, 0, 0, 0.8);
  color: white;
  padding: 15px;
  border-radius: 8px;
  z-index: 100;
  min-width: 200px;
}

.status-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}

.status-item:last-child {
  margin-bottom: 0;
}

.status-item .label {
  font-weight: normal;
}

.status-item .value {
  font-weight: bold;
  color: #ffd700;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .control-panel {
    position: relative;
    top: 0;
    left: 0;
    margin: 10px;
    max-width: none;
  }
  
  .status-panel {
    position: relative;
    top: 0;
    right: 0;
    margin: 10px;
    max-width: none;
  }
  
  .button-group {
    flex-direction: column;
  }
  
  .btn {
    min-width: none;
  }
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 8px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: #a1a1a1;
}
</style>