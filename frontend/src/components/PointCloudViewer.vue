<template>
  <div ref="container" class="viewer-container"></div>
</template>

<script>
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

export default {
  name: 'PointCloudViewer',
  props: {
    fileUrl: { type: String, default: null },
    odomData: { type: Array, default: null },
    isAccumulating: { type: Boolean, default: false },
    timeWindowSec: { type: Number, default: 99999 },
    pointSize: { type: Number, default: 0.1 },
    maxPoints: { type: Number, default: 5000000 },
    cameraFollowMode: { type: String, default: 'none' },
    trajectoryColor: { type: [String, Number], default: '#ff0000' },
  },
  data() {
    return {
      // --- Three.js 核心对象 ---
      scene: null,
      camera: null,
      renderer: null,
      controls: null,
      pointCloud: null,
      material: null,
      geometry: null,
      animationFrameId: null,

      // --- 轨迹线对象 ---
      trajectoryLine: null,
      trajectoryGeometry: null,
      trajectoryPositions: null, // Float32Array
      trajectoryPointIndex: 0,
      trajectoryHasWrapped: false,
      MAX_TRAJECTORY_POINTS: 50000,

      // --- 最新 Odom 位姿 ---
      lastOdom: null,

      // --- 相机跟随状态 ---
      cameraLocalOffset: new THREE.Vector3(),
      isOffsetSet: false,

      // --- 尺寸变化监听器 ---
      resizeObserver: null,

      // --- 高性能缓冲区管理 ---
      positions: null, // Float32Array
      colors: null,    // Float32Array
      timestamps: null, // Float32Array
      pointIndex: 0,
      hasWrapped: false,
      isRgbCloud: false,
      cullInterval: null,
    };
  },
  watch: {
    fileUrl: {
      immediate: true,
      handler(newUrl) { if (newUrl) this.loadFile(newUrl); }
    },
    odomData: {
      immediate: true,
      handler(newData) { if (newData && newData.length > 0) this.loadTrajectory(newData); }
    },
    pointSize(newSize) { if (this.material) this.material.size = newSize; },
    maxPoints() {
      // 当点云上限改变时，清理并重新初始化点云资源
      if (this.scene && this.pointCloud) this.scene.remove(this.pointCloud);
      if (this.geometry) this.geometry.dispose();
      if (this.material) this.material.dispose();
      this.pointIndex = 0;
      this.hasWrapped = false;
      this.$emit('update:point-count', 0);
      this.initializePointCloud();
    },
    trajectoryColor(newColor) {
      if (this.trajectoryLine && this.trajectoryLine.material) {
        this.trajectoryLine.material.color.set(newColor);
      }
    },
    cameraFollowMode() {
      // 重置相机偏移量，以便在切换模式时重新计算
      this.isOffsetSet = false;
    }
  },
  mounted() {
    const container = this.$refs.container;
    if (!container) return;

    const { clientWidth, clientHeight } = container;

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0xffffff);

    this.camera = new THREE.PerspectiveCamera(75, clientWidth / clientHeight, 0.1, 1000);
    this.camera.position.set(5, 5, 5);
    this.camera.up.set(0, 0, 1); // 设置 Z 轴朝上

    this.renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: "high-performance" });
    this.renderer.setSize(clientWidth, clientHeight);
    this.renderer.setPixelRatio(window.devicePixelRatio);
    container.appendChild(this.renderer.domElement);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;

    this.initializePointCloud();
    this.initializeTrajectoryLine();

    this.resizeObserver = new ResizeObserver(() => { this.handleResize(); });
    this.resizeObserver.observe(container);
    
    this.cullInterval = setInterval(this.cullPointsByTime, 2000);

    this.animate();
    this.handleResize();
  },
  beforeDestroy() {
    cancelAnimationFrame(this.animationFrameId);
    clearInterval(this.cullInterval);
    this.cleanupResources();
    if (this.renderer) this.renderer.dispose();
    if (this.resizeObserver && this.$refs.container) {
      this.resizeObserver.unobserve(this.$refs.container);
    }
  },
  methods: {
    // --- 核心公共方法 ---
    async loadFile(url) {
      try {
        const response = await fetch(url);
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
        const text = await response.text();
        this.parseAndRenderPly(text);
      } catch (error) {
        console.error("加载或解析 PLY 文件失败:", error);
      }
    },
    parseAndRenderPly(plyText) {
      const lines = plyText.split('\n');
      let headerEnded = false;
      let pointCount = 0;
      const points = [];

      for (const line of lines) {
        if (line.startsWith('element vertex')) {
          pointCount = parseInt(line.split(' ')[2], 10);
        }
        if (line === 'end_header') {
          headerEnded = true;
          continue;
        }
        if (headerEnded && points.length < pointCount) {
          const parts = line.trim().split(' ');
          if (parts.length >= 4) {
            points.push({
              x: parseFloat(parts[0]),
              y: parseFloat(parts[1]),
              z: parseFloat(parts[2]),
              intensity: parseFloat(parts[3])
            });
          }
        }
      }
      this.clearPoints();
      this.addPoints(points);
    },
    loadTrajectory(odomArray) {
      this.clearPoints();
      odomArray.forEach(odom => {
        const packet = {
          odom: {
            x: odom.posX, y: odom.posY, z: odom.posZ,
            orientation: { x: odom.orientX, y: odom.orientY, z: odom.orientZ, w: odom.orientW }
          }
        };
        this.addOdom(packet);
      });
    },
    addPoints(newPoints) {
      if (!this.pointCloud || newPoints.length === 0) return;
      if (!this.isAccumulating) this.clearPoints();
      if (this.pointIndex === 0 && !this.hasWrapped) this.isRgbCloud = newPoints[0].r !== undefined;
      
      const now = Date.now();
      const color = new THREE.Color();
      const limit = this.maxPoints;

      for (const p of newPoints) {
        if (this.pointIndex >= limit) {
          this.pointIndex = 0;
          if (!this.hasWrapped) this.hasWrapped = true;
        }
        const i3 = this.pointIndex * 3;
        this.positions[i3] = p.x;
        this.positions[i3 + 1] = p.y;
        this.positions[i3 + 2] = p.z;
        
        if (this.isRgbCloud) {
          this.colors[i3] = (p.r ?? 0) / 255.0;
          this.colors[i3 + 1] = (p.g ?? 0) / 255.0;
          this.colors[i3 + 2] = (p.b ?? 0) / 255.0;
        } else {
          const intensity = p.intensity ?? p.z;
          const norm = Math.min(Math.max(intensity, -2), 2) / 4 + 0.5;
          color.setHSL(0.7 * (1 - norm), 1.0, 0.5);
          this.colors[i3] = color.r;
          this.colors[i3 + 1] = color.g;
          this.colors[i3 + 2] = color.b;
        }
        this.timestamps[this.pointIndex] = now;
        this.pointIndex++;
      }
      
      this.geometry.attributes.position.needsUpdate = true;
      this.geometry.attributes.color.needsUpdate = true;
      
      const drawCount = this.hasWrapped ? limit : this.pointIndex;
      this.geometry.setDrawRange(0, drawCount);
      this.$emit('update:point-count', drawCount);
    },
    addOdom(receivedData) {
      if (!receivedData || !receivedData.odom || !receivedData.odom.orientation) return;
      
      const standardizedPacket = {
        pose: {
          position: { x: receivedData.odom.x, y: receivedData.odom.y, z: receivedData.odom.z },
          orientation: receivedData.odom.orientation
        }
      };

      if (!this.trajectoryGeometry) return;
      this.lastOdom = standardizedPacket;

      if (this.trajectoryPointIndex >= this.MAX_TRAJECTORY_POINTS) {
        this.trajectoryPointIndex = 0;
        if (!this.trajectoryHasWrapped) this.trajectoryHasWrapped = true;
      }

      const { x, y, z } = standardizedPacket.pose.position;
      const i3 = this.trajectoryPointIndex * 3;
      this.trajectoryPositions[i3] = x;
      this.trajectoryPositions[i3 + 1] = y;
      this.trajectoryPositions[i3 + 2] = z;
      this.trajectoryPointIndex++;

      this.trajectoryGeometry.attributes.position.needsUpdate = true;
      const drawCount = this.trajectoryHasWrapped ? this.MAX_TRAJECTORY_POINTS : this.trajectoryPointIndex;
      this.trajectoryGeometry.setDrawRange(0, drawCount);
    },
    clearPoints() {
      this.trajectoryPointIndex = 0;
      this.trajectoryHasWrapped = false;
      if (this.trajectoryGeometry) this.trajectoryGeometry.setDrawRange(0, 0);

      this.pointIndex = 0;
      this.hasWrapped = false;
      if (this.geometry) this.geometry.setDrawRange(0, 0);
      this.$emit('update:point-count', 0);
    },
    cullPointsByTime() {
      if (!this.isAccumulating || (this.pointIndex === 0 && !this.hasWrapped)) return;
      
      const expiryTime = Date.now() - this.timeWindowSec * 1000;
      const limit = this.maxPoints;
      const totalPoints = this.hasWrapped ? limit : this.pointIndex;
      
      let validPointIndex = 0;
      for (let i = 0; i < totalPoints; i++) {
        if (this.timestamps[i] >= expiryTime) {
          if (i !== validPointIndex) {
            const i3 = i * 3, v3 = validPointIndex * 3;
            this.positions[v3] = this.positions[i3]; this.positions[v3+1] = this.positions[i3+1]; this.positions[v3+2] = this.positions[i3+2];
            this.colors[v3] = this.colors[i3]; this.colors[v3+1] = this.colors[i3+1]; this.colors[v3+2] = this.colors[i3+2];
            this.timestamps[validPointIndex] = this.timestamps[i];
          }
          validPointIndex++;
        }
      }

      if (validPointIndex < totalPoints) {
        this.pointIndex = validPointIndex;
        this.hasWrapped = false;
        this.geometry.setDrawRange(0, this.pointIndex);
        this.geometry.attributes.position.needsUpdate = true;
        this.geometry.attributes.color.needsUpdate = true;
        this.$emit('update:point-count', this.pointIndex);
      }
    },
    updateCameraFollow() {
      if (this.cameraFollowMode === 'none' || !this.lastOdom) return;

      const odomPose = this.lastOdom.pose;
      const targetPosition = new THREE.Vector3(odomPose.position.x, odomPose.position.y, odomPose.position.z);

      if (this.cameraFollowMode === 'position') {
        this.controls.target.copy(targetPosition);
      }
    },
    initializePointCloud() {
        const limit = this.maxPoints;
        this.geometry = new THREE.BufferGeometry();
        this.positions = new Float32Array(limit * 3);
        this.colors = new Float32Array(limit * 3);
        this.timestamps = new Float32Array(limit);
        
        this.geometry.setAttribute('position', new THREE.BufferAttribute(this.positions, 3).setUsage(THREE.DynamicDrawUsage));
        this.geometry.setAttribute('color', new THREE.BufferAttribute(this.colors, 3).setUsage(THREE.DynamicDrawUsage));
        
        this.material = new THREE.PointsMaterial({ size: this.pointSize, vertexColors: true });
        this.pointCloud = new THREE.Points(this.geometry, this.material);
        this.pointCloud.frustumCulled = false;
        this.scene.add(this.pointCloud);
    },
    initializeTrajectoryLine() {
        this.trajectoryGeometry = new THREE.BufferGeometry();
        this.trajectoryPositions = new Float32Array(this.MAX_TRAJECTORY_POINTS * 3);
        this.trajectoryGeometry.setAttribute('position', new THREE.BufferAttribute(this.trajectoryPositions, 3).setUsage(THREE.DynamicDrawUsage));
        const trajectoryMaterial = new THREE.LineBasicMaterial({ color: this.trajectoryColor, linewidth: 2 });
        this.trajectoryLine = new THREE.Line(this.trajectoryGeometry, trajectoryMaterial);
        this.trajectoryLine.frustumCulled = false;
        this.scene.add(this.trajectoryLine);
    },
    cleanupResources() {
        if (this.pointCloud && this.scene) this.scene.remove(this.pointCloud);
        if (this.geometry) this.geometry.dispose();
        if (this.material) this.material.dispose();
        
        if (this.trajectoryLine && this.scene) this.scene.remove(this.trajectoryLine);
        if (this.trajectoryGeometry) this.trajectoryGeometry.dispose();
    },
    animate() {
      this.animationFrameId = requestAnimationFrame(this.animate);
      if (this.controls) this.controls.update();
      this.updateCameraFollow();
      if (this.renderer) this.renderer.render(this.scene, this.camera);
    },
    handleResize() {
      const container = this.$refs.container;
      if (!container || !this.renderer || !this.camera) return;
      const { clientWidth, clientHeight } = container;
      this.renderer.setSize(clientWidth, clientHeight);
      this.camera.aspect = clientWidth / clientHeight;
      this.camera.updateProjectionMatrix();
    },
  },
};
</script>

<style scoped>
.viewer-container {
  width: 100%;
  height: 100%;
  min-height: 600px;
  overflow: hidden;
}
</style>
