<template>
  <div class="viewer-container">
    <div ref="canvasContainer" class="canvas-element"></div>
    
    <div v-if="!url && placeholder" class="placeholder">
      <i class="el-icon-camera-solid"></i>
      <span>{{ placeholder }}</span>
    </div>

    <div v-if="isLoading" class="loading-overlay">
      <i class="el-icon-loading"></i>
      <span>正在加载...</span>
    </div>
  </div>
</template>

<script>
import * as THREE from 'three';
import { PLYLoader } from 'three/examples/jsm/loaders/PLYLoader.js';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

export default {
  name: 'ThreeViewer',
  props: {
    url: {
      type: String,
      default: '',
    },
    placeholder: {
      type: String,
      default: '请选择一个点云文件',
    },
  },
  data() {
    return {
      scene: null,
      camera: null,
      renderer: null,
      controls: null,
      isLoading: false,
      currentModelName: 'loaded_model',
      animationFrameId: null, // 存储 requestAnimationFrame 的ID
    };
  },
  watch: {
    // 核心逻辑：监听 URL prop 的变化
    url(newUrl) {
      this.loadCloud(newUrl);
    },
  },
  mounted() {
    this.initThree();
    this.animate();
    // 如果初始有URL，则加载
    if (this.url) {
      this.loadCloud(this.url);
    }
  },
  beforeDestroy() {
    // 组件销毁时停止动画并清理资源
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
    }
    this.cleanup();
  },
  methods: {
    initThree() {
      const container = this.$refs.canvasContainer;
      if (!container) return;

      const width = container.clientWidth;
      const height = container.clientHeight;

      // scene
      this.scene = new THREE.Scene();
      // 1. 渲染器背景改为白色
      this.scene.background = new THREE.Color(0xffffff); 

      // camera
      this.camera = new THREE.PerspectiveCamera(75, width / height, 0.1, 20000);
      this.camera.position.set(30, 30, 30);
      this.scene.add(this.camera);

      // renderer
      this.renderer = new THREE.WebGLRenderer({ antialias: true });
      this.renderer.setSize(width, height);
      container.appendChild(this.renderer.domElement);

      // controls
      this.controls = new OrbitControls(this.camera, this.renderer.domElement);
      this.controls.enableDamping = true;

      // light
      // 将环境光改暗一点，点云颜色在白底上更突出
      this.scene.add(new THREE.AmbientLight(0x999999)); 
      
      // 2. 不需要坐标系
      // this.scene.add(new THREE.GridHelper(100, 20, 0x555555, 0x333333));
      
      window.addEventListener('resize', this.onWindowResize);
    },

    animate() {
      this.animationFrameId = requestAnimationFrame(this.animate);
      if (this.controls) this.controls.update();
      if (this.renderer) this.renderer.render(this.scene, this.camera);
    },

    onWindowResize() {
      if (this.$refs.canvasContainer) {
        const width = this.$refs.canvasContainer.clientWidth;
        const height = this.$refs.canvasContainer.clientHeight;
        
        this.camera.aspect = width / height;
        this.camera.updateProjectionMatrix();
        this.renderer.setSize(width, height);
      }
    },

    /**
     * 核心加载函数
     */
    loadCloud(urlToLoad) {
      // 1. 清理旧模型
      this.removeCloud(this.currentModelName);

      // 2. 如果 URL 为空，则不加载
      if (!urlToLoad) {
        return;
      }

      this.isLoading = true;
      const loader = new PLYLoader();
      
      // Blob URL 不需要时间戳
      loader.load(urlToLoad, (geometry) => {
        geometry.computeBoundingBox();
        geometry.center(); // 自动将模型居中

        const material = new THREE.PointsMaterial({
          size: 0.1,
          vertexColors: geometry.hasAttribute('color'),
        });
        
        if (!geometry.hasAttribute('color')) {
            material.color.setHex(0x333333); // 默认颜色改为深灰色
        }

        const points = new THREE.Points(geometry, material);
        points.name = this.currentModelName;
        
        this.scene.add(points);
        this.isLoading = false;
        this.focusOnObject(points); // 自动聚焦
        
      }, undefined, (error) => {
        console.error('加载点云失败:', error);
        this.$message.error(`加载点云失败`);
        this.isLoading = false;
      });
    },

    removeCloud(name) {
      const object = this.scene.getObjectByName(name);
      if (object) {
        if (object.geometry) object.geometry.dispose();
        if (object.material) object.material.dispose();
        this.scene.remove(object);
      }
    },

    focusOnObject(object) {
        const box = new THREE.Box3().setFromObject(object);
        const size = box.getSize(new THREE.Vector3()).length();
        const center = box.getCenter(new THREE.Vector3());
        
        this.controls.reset();
        this.controls.target.copy(center);
        this.camera.position.copy(center);
        this.camera.position.z += size * 1.5; // 调整相机距离
        this.camera.lookAt(center);
        this.controls.update();
    },

    cleanup() {
      // 彻底清理 Three.js 资源
      window.removeEventListener('resize', this.onWindowResize);
      this.removeCloud(this.currentModelName);
      if (this.renderer) {
        this.renderer.dispose();
        this.renderer.domElement = null;
        this.renderer = null;
      }
      if (this.controls) this.controls.dispose();
    },
  },
};
</script>

<style scoped>
.viewer-container {
  width: 100%;
  height: 100%;
  position: relative;
  /* 3. 修改背景色和边框 */
  background-color: #ffffff; 
  border: 1px solid #ebeef5; 
  border-radius: 4px;
  overflow: hidden;
}
.canvas-element {
  width: 100%;
  height: 100%;
}
.placeholder, .loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  /* 3. 修改占位符文本颜色 */
  color: #c0c4cc; 
  font-size: 16px;
  user-select: none;
}
.loading-overlay {
  /* 3. 修改加载遮罩 */
  background-color: rgba(255, 255, 255, 0.8);
  color: #606266;
  z-index: 10;
}
.placeholder i, .loading-overlay i {
  font-size: 40px;
  margin-bottom: 10px;
}
</style>