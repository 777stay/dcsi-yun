<template>
  <div class="main-container">

    <div class="sidebar">
      <h4>点云模型</h4>
      <ul v-if="pointCloudList.length > 0" class="pointcloud-list">
        <li
          v-for="pc in pointCloudList"
          :key="pc.url"
          :class="{ 'active': pc.url === activeUrl }"
          class="pointcloud-list-item"
        >
          <span class="item-name">{{ pc.name }}</span>
          <button @click="renderPointCloud(pc.url)" class="render-button">
            渲染
          </button>
        </li>
      </ul>
      <div v-else class="loading-text">正在从服务器加载列表...</div>
    </div>

    <div class="viewer-container">
      <PotreeViewer v-if="urlToLoad" :key="viewerKey" :url="urlToLoad" />
    </div>

  </div>
</template>

<script>
// ----------------------------------------------------
// --- SCRIPT 部分无需任何修改，逻辑保持不变 ---
// ----------------------------------------------------
import axios from "axios";
// 注意: 您组件的实际路径可能是 './PotreeViewer.vue'，请根据您的项目结构确认
import PotreeViewer from "./PotreeCloudViewer.vue"; 
import { getBackendBaseUrl } from "@/utils/runtimeApi";

const API_BASE_URL = getBackendBaseUrl();

export default {
  name: "PointCloudSelector",
  components: {
    PotreeViewer,
  },
  data() {
    return {
      pointCloudList: [],
      urlToLoad: null,    // 将传递给子组件的URL
      viewerKey: 0,       // 控制子组件重建的Key
      activeUrl: null,    // 仅用于为当前活动项添加样式的URL
    };
  },
  watch: {
    // 当这个值改变时，我们增加 key 的值来触发子组件的重新渲染
    urlToLoad(newUrl, oldUrl) {
      if (newUrl && newUrl !== oldUrl) {
        console.log(`[选择器] URL已改变，增加key以强制重新渲染。`);
        this.viewerKey++;
      }
    },
  },
  mounted() {
    this.fetchPointCloudList();
  },
  methods: {
    async fetchPointCloudList() {
      try {
        console.log("[选择器] 正在从API获取点云列表...");
        // 您的后端API地址
        const response = await axios.get(`${API_BASE_URL}/api/potreeClouds`);
        this.pointCloudList = response.data;
        console.log("[选择器] ✅ 点云列表已加载:", this.pointCloudList);
        
        // 在首次加载时，自动渲染列表中的第一个点云
        if (this.pointCloudList.length > 0) {
          
        } else {
          console.warn("[选择器] ⚠️ 点云列表为空。");
        }
      } catch (error) {
        console.error("[选择器] ❌ 获取点云列表失败:", error);
      }
    },
    // 当“渲染”按钮被点击时，此方法会被调用
    renderPointCloud(url) {
      console.log(`[选择器] “渲染”按钮被点击，URL: ${url}`);
      // 设置当前活动的URL，用于添加高亮样式
      this.activeUrl = url;
      // 设置需要加载的URL，这将触发侦听器（watcher）
      this.urlToLoad = url;
    },
  },
};
</script>

<style scoped>
/* ================================================= */
/* --- STYLE 部分是主要修改区域 --- */
/* ================================================= */

/* 步骤 1: 将主容器设置为 flex 布局 */
.main-container {
  width: 100vw;
  height: 100vh;
  display: flex; /* 使用 Flexbox 布局 */
  flex-direction: row; /* 子元素水平排列 */
  background-color: #f0f2f5;
}

/* 步骤 2: 定义左侧侧边栏的样式 */
.sidebar {
  width: 500px; /* 您可以根据需要调整宽度 */
  height: 100vh;
  background: #ffffff;
  padding: 15px;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1); /* 右侧阴影 */
  z-index: 10;
  display: flex;
  flex-direction: column; /* 让标题和列表垂直排列 */
  overflow-y: hidden; /* 防止侧边栏自身出现滚动条 */
  box-sizing: border-box; /* 让 padding 不会增加宽度 */
}

/* 步骤 3: 定义右侧 PotreeViewer 容器的样式 */
.viewer-container {
  flex: 1; /* 占据所有剩余的可用空间 */
  height: 100vh;
  position: relative; /* Potree 内部元素可能需要相对定位 */
}

/* --- 侧边栏内部元素的样式微调 --- */
.sidebar h4 {
  margin-top: 5px;
  margin-bottom: 15px;
  text-align: center;
  color: #333;
  flex-shrink: 0; /* 防止标题被压缩 */
}

.loading-text {
  color: #666;
  text-align: center;
  margin-top: 20px;
}

.pointcloud-list {
  list-style: none;
  padding: 0;
  margin: 0;
  overflow-y: auto; /* 让列表内容超出时可以滚动 */
}

/* 列表项和按钮的样式保持不变，它们在新布局中同样适用 */
.pointcloud-list-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  border-bottom: 1px solid #eee;
}
.pointcloud-list-item:last-child {
  border-bottom: none;
}
.pointcloud-list-item .item-name {
  color: #444;
  margin-right: 20px;
  flex-shrink: 1;
}
.pointcloud-list-item .render-button {
  padding: 5px 12px;
  border: 1px solid #007bff;
  background-color: #007bff;
  color: white;
  border-radius: 5px;
  cursor: pointer;
  transition: background-color 0.2s;
  flex-shrink: 0;
}
.pointcloud-list-item .render-button:hover {
  background-color: #0056b3;
}
.pointcloud-list-item.active {
  background-color: #e7f3ff;
}
.pointcloud-list-item.active .item-name {
  font-weight: bold;
  color: #0056b3;
}
</style>
