<template>
  <div class="fusion-page">
    <aside class="fusion-sidebar">
      <div class="section-header">
        <h3>多机点云融合</h3>
        <el-tag size="mini" type="info">Fused</el-tag>
      </div>

      <el-form label-position="top" size="small" class="fusion-form">
        <el-form-item label="数据目录">
          <el-input v-model="dataset" />
        </el-form-item>
        <el-form-item label="图像步长">
          <el-input-number v-model="imageStride" :min="1" :max="50" controls-position="right" />
        </el-form-item>
        <el-form-item label="最大图像数">
          <el-input-number v-model="maxImages" :min="0" :max="500" controls-position="right" />
        </el-form-item>
      </el-form>

      <div class="actions">
        <el-button type="primary" size="small" :loading="running" @click="runFusion">运行算法</el-button>
        <el-button size="small" @click="loadExisting">加载已有</el-button>
      </div>

      <div v-if="result" class="result-panel">
        <div class="result-title">{{ result.name }}</div>
        <div class="result-url">{{ result.url }}</div>
        <el-button type="success" size="small" @click="renderFused">渲染 fused 点云</el-button>
      </div>

      <div v-if="robotRows.length" class="robot-list">
        <div v-for="robot in robotRows" :key="robot.robot" class="robot-row">
          <span>{{ robot.robot }}</span>
          <span>{{ robot.usable ? '可用' : '跳过' }}</span>
        </div>
      </div>

      <pre v-if="logText" class="log-box">{{ logText }}</pre>
    </aside>

    <main class="fusion-viewer">
      <PotreeViewer v-if="urlToLoad" :key="viewerKey" :url="urlToLoad" />
      <div v-else class="empty-viewer">等待 fused 点云</div>
    </main>
  </div>
</template>

<script>
import axios from 'axios'
import PotreeViewer from '@/views/potree/PotreeCloudViewer.vue'
import { getBackendBaseUrl } from '@/utils/runtimeApi'

const API_BASE_URL = getBackendBaseUrl()

export default {
  name: 'FusionPotreeView',
  components: {
    PotreeViewer
  },
  data() {
    return {
      dataset: '20260603-094908',
      imageStride: 1,
      maxImages: 0,
      running: false,
      result: null,
      urlToLoad: '',
      viewerKey: 0,
      logText: ''
    }
  },
  computed: {
    robotRows() {
      return this.result && Array.isArray(this.result.robots) ? this.result.robots : []
    }
  },
  mounted() {
    this.loadExisting()
  },
  methods: {
    async loadExisting() {
      try {
        const response = await axios.get(`${API_BASE_URL}/api/fusion/result`, {
          params: { dataset: this.dataset }
        })
        this.result = response.data.data
        this.logText = this.result.log || ''
        if (this.result && this.result.url) {
          this.renderFused()
        }
      } catch (error) {
        this.$message.error('加载 fused 结果失败')
        this.logText = error.response && error.response.data ? JSON.stringify(error.response.data, null, 2) : error.message
      }
    },
    async runFusion() {
      this.running = true
      this.logText = ''
      try {
        const payload = {
          dataset: this.dataset,
          imageStride: this.imageStride,
          maxImages: this.maxImages > 0 ? this.maxImages : null
        }
        const response = await axios.post(`${API_BASE_URL}/api/fusion/run`, payload)
        this.result = response.data.data
        this.logText = this.result.log || ''
        this.$message.success('融合完成')
        this.renderFused()
      } catch (error) {
        this.$message.error('融合运行失败')
        this.logText = error.response && error.response.data ? JSON.stringify(error.response.data, null, 2) : error.message
      } finally {
        this.running = false
      }
    },
    renderFused() {
      if (!this.result || !this.result.url) return
      this.urlToLoad = this.result.url
      this.viewerKey += 1
    }
  }
}
</script>

<style scoped>
.fusion-page {
  width: 100%;
  height: calc(100vh - 50px);
  display: flex;
  background: #f3f4f6;
}

.fusion-sidebar {
  width: 360px;
  height: 100%;
  padding: 16px;
  background: #fff;
  border-right: 1px solid #d1d5db;
  box-sizing: border-box;
  overflow-y: auto;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-header h3 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  font-weight: 700;
}

.fusion-form {
  margin-bottom: 12px;
}

.actions {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.result-panel {
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #f9fafb;
}

.result-title {
  margin-bottom: 6px;
  color: #111827;
  font-weight: 700;
}

.result-url {
  margin-bottom: 10px;
  color: #4b5563;
  font-size: 12px;
  word-break: break-all;
}

.robot-list {
  margin-bottom: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.robot-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 10px;
  color: #374151;
  font-size: 13px;
  border-bottom: 1px solid #e5e7eb;
}

.robot-row:last-child {
  border-bottom: none;
}

.log-box {
  max-height: 260px;
  padding: 10px;
  color: #d1d5db;
  background: #111827;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
}

.fusion-viewer {
  flex: 1;
  position: relative;
  min-width: 0;
}

.empty-viewer {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-size: 14px;
}
</style>
