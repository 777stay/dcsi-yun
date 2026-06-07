<template>
  <div class="potree-container">
    <div ref="potreeRenderArea" class="potree-render-area"></div>
  </div>
</template>

<script>
export default {
  name: "PotreeViewer",
  props: {
    // 接收父组件传递过来的点云 URL
    url: {
      type: String,
      required: true,
    },
  },
  // Vue 实例被挂载时执行 (每次重建都会执行)
  mounted() {
    console.log(`[PotreeViewer] Component Mounted with URL: ${this.url}`);
    this.initAndLoad();
  },
  // Vue 实例销毁前执行
  beforeDestroy() {
    // 这是非常重要的一步，防止内存泄漏
    if (window.viewer) {
      console.log("[PotreeViewer] Destroying old Potree instance.");
      if (typeof window.viewer.dispose === "function") {
        window.viewer.dispose();
      }
      window.viewer = null;
    }
  },
  methods: {
    ensurePotreeRangeFetchPatch() {
      if (window.__potreeRangeFetchPatched || typeof window.fetch !== "function") {
        return;
      }

      const originalFetch = window.fetch.bind(window);
      window.__potreeRangeFetchPatched = true;
      window.fetch = (resource, options = {}) => {
        const url = typeof resource === "string" ? resource : resource && resource.url;
        const isPotreeBinary = url && (url.includes("hierarchy.bin") || url.includes("octree.bin"));
        if (!isPotreeBinary || !options || !options.headers) {
          return originalFetch(resource, options);
        }

        const headers = new Headers(options.headers);
        if (headers.has("Range") || headers.has("range")) {
          headers.delete("content-type");
          headers.delete("Content-Type");
          return originalFetch(resource, {
            ...options,
            headers
          });
        }

        return originalFetch(resource, options);
      };
    },
    initAndLoad() {
      this.ensurePotreeRangeFetchPatch();
      // 检查 viewer 是否已存在，以防万一
      if (window.viewer) {
        console.warn("[PotreeViewer] Viewer instance already exists. Disposing it.");
        
      }
      
      console.log("[PotreeViewer] Initializing new Potree instance...");
      // 使用 this.$refs 来获取 DOM 元素，这是 Vue 的标准做法
      const viewerEl = this.$refs.potreeRenderArea;
      
      // 将 viewer 实例挂载到 window 对象上，或组件实例上
      const viewer = new Potree.Viewer(viewerEl);
      window.viewer = viewer;
      
      // --- 您可以在这里添加所有 viewer 的配置 ---
      viewer.setScene(new Potree.Scene());
      viewer.setEDLEnabled(true);
      viewer.setPointBudget(2_000_000);
      viewer.setBackground("white");
      viewer.setLanguage("zh");
      viewer.loadGUI(() => {
        viewer.setLanguage("en");
        if (typeof window.showNextSibling === "function") {
          window.showNextSibling("menu_tools");
        }
      });
      // --- 配置结束 ---

      console.log(`[PotreeViewer] Calling Potree.loadPointCloud...`);
      Potree.loadPointCloud(this.url, "Point Cloud", (e) => {
        if (!window.viewer) {
          // 如果在加载过程中组件被销毁，则中止
          console.log("[PotreeViewer] Viewer was destroyed during load. Aborting.");
          return;
        }

        if (e.type === "pointcloud_loaded") {
          console.log("[PotreeViewer] ✅ Point cloud data loaded successfully.");
          viewer.scene.addPointCloud(e.pointcloud);
          e.pointcloud.material.size = 1;
          e.pointcloud.material.pointSizeType = Potree.PointSizeType.ADAPTIVE;
          e.pointcloud.material.shape = Potree.PointShape.CIRCLE;
          viewer.fitToScreen();
          console.log("[PotreeViewer] ✅ Point cloud added to scene.");
        } else if (e.type === "loading_failed") {
          console.error("[PotreeViewer] ❌ Failed to load point cloud data.");
        }
      });
    },
  },
};
</script>

<style scoped>
.potree-container {
  width: 100%;
  height: 100%;
  position: absolute;
  top: 0;
  left: 0;
}
.potree-render-area {
  width: 100%;
  height: 100%;
}
</style>
