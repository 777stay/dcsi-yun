<template>
  <div ref="container" class="viewer-container"></div>
</template>

<script>
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { CSS2DRenderer, CSS2DObject } from 'three/examples/jsm/renderers/CSS2DRenderer.js';
import { ColladaLoader } from 'three/examples/jsm/loaders/ColladaLoader.js';

// CustomNavMarker 类定义
const vertexShader = `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`;

const fragmentShader = `
  uniform float time;
  uniform vec3 color;
  varying vec2 vUv;

  void main() {
    float dist = distance(vUv, vec2(0.5));
    float ring1_inner = 0.2;
    float ring1_outer = 0.25;
    float ring2_inner = 0.4;
    float ring2_outer = 0.45;

    float wave1 = mod(time * 0.4 + (1.0 - dist * 2.0), 1.0);
    float pulse1 = pow(1.0 - wave1, 3.0);

    float wave2 = mod(time * 0.3 + (1.0 - dist * 2.0), 1.0);
    float pulse2 = pow(1.0 - wave2, 4.0);
    
    float alpha = 0.0;

    if (dist > ring1_inner && dist < ring1_outer) {
      alpha = pulse1;
    }

    if (dist > ring2_inner && dist < ring2_outer) {
      alpha = max(alpha, pulse2 * 0.7); 
    }

    if (alpha < 0.01) {
      discard;
    }

    gl_FragColor = vec4(color, alpha);
  }
`;

class CustomNavMarker extends THREE.Object3D {
  constructor(color = 0x007bff) {
    super();

    const pinColor = new THREE.Color(color);

    const bodyHeight = 1.8;
    const bodyRadius = 0.4;
    const bodyGeometry = new THREE.ConeGeometry(bodyRadius, bodyHeight, 32);
    const bodyMaterial = new THREE.MeshStandardMaterial({
        color: pinColor,
        metalness: 0.6,
        roughness: 0.4,
        emissive: pinColor,
        emissiveIntensity: 0.2
    });
    this.body = new THREE.Mesh(bodyGeometry, bodyMaterial);
    
    this.body.rotation.x = -Math.PI / 2;
    this.body.position.z = bodyHeight / 2;

    const ringGeometry = new THREE.PlaneGeometry(3.5, 3.5);
    this.uniforms = {
      time: { value: 0 },
      color: { value: pinColor },
    };
    const ringMaterial = new THREE.ShaderMaterial({
      uniforms: this.uniforms,
      vertexShader,
      fragmentShader,
      transparent: true,
      depthWrite: false,
    });
    this.pulseRing = new THREE.Mesh(ringGeometry, ringMaterial);
    
    this.add(this.body);
    this.add(this.pulseRing);
  }

  update(deltaTime) {
    this.uniforms.time.value += deltaTime;
  }

  setColor(color) {
    const newColor = new THREE.Color(color);
    this.body.material.color.copy(newColor);
    this.body.material.emissive.copy(newColor);
    this.uniforms.color.value.copy(newColor);
  }
}

// RobotPoseMarker 类定义
class RobotPoseMarker extends THREE.Object3D {
  constructor() {
    super();

    const arrowLength = 1.0;
    const arrowRadius = 0.1;

    const materialX = new THREE.MeshStandardMaterial({ color: 0xff0000, emissive: 0x660000 });
    const materialY = new THREE.MeshStandardMaterial({ color: 0x00ff00, emissive: 0x006600 });
    const materialZ = new THREE.MeshStandardMaterial({ color: 0x0000ff, emissive: 0x000066 });

    const arrowGeom = new THREE.ConeGeometry(arrowRadius, arrowLength, 16);

    const arrowX = new THREE.Mesh(arrowGeom, materialX);
    arrowX.rotation.z = -Math.PI / 2;
    arrowX.position.x = arrowLength / 2;
    this.add(arrowX);

    const arrowY = new THREE.Mesh(arrowGeom, materialY);
    arrowY.position.y = arrowLength / 2;
    this.add(arrowY);

    const arrowZ = new THREE.Mesh(arrowGeom, materialZ);
    arrowZ.rotation.x = Math.PI / 2;
    arrowZ.position.z = arrowLength / 2;
    this.add(arrowZ);

    const centerGeom = new THREE.SphereGeometry(arrowRadius * 1.5, 16, 16);
    const centerMat = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0xcccccc });
    const centerSphere = new THREE.Mesh(centerGeom, centerMat);
    this.add(centerSphere);
  }
}

export default {
  name: 'ThreeJsViewer',
  props: {
    isAccumulating: {
      type: Boolean,
      default: false
    },
    isSettingGoal: {
      type: Boolean,
      default: false
    },
    targetRobotIdForNav: {
      type: String,
      default: null
    },
    pointCloudSelections: {
      type: Object,
      default: () => ({})
    },
    pointSize: {
      type: Number,
      default: 0.1
    },
    maxPointsPerCloud: {
      type: Number,
      default: 10000
    },
    timeWindowSec: {
      type: Number,
      default: 30
    },
    pointDensity: {
      type: Number,
      default: 100
    }
  },
  data() {
    return {
      scene: null,
      camera: null,
      renderer: null,
      labelRenderer: null,
      controls: null,
      animationFrameId: null,
      clock: null,
      poseObjects: {},
      trajectoryLines: {},
      lastOdoms: {},
      navGoalObjects: {},
      raycaster: null,
      targetPlane: null,
      pointClouds: {},
      cullInterval: null,
      MODEL_PATH_PREFIX: '/robot_models/',
      MAX_TRAJECTORY_POINTS: 50000
    }
  },
  watch: {
    pointSize(newSize) {
      for(const frameId in this.pointClouds) {
        if (this.pointClouds[frameId] && this.pointClouds[frameId].points && this.pointClouds[frameId].points.material) {
          this.pointClouds[frameId].points.material.size = newSize;
        }
      }
    },
    
    timeWindowSec() {
      this.cullAllPointsByTime();
    },
    
    maxPointsPerCloud(newLimit) {
      console.log(`Max points per cloud changed to ${newLimit}. Rebuilding point clouds...`);
      for (const frameId in this.pointClouds) {
        const pc = this.pointClouds[frameId];
        if (pc) {
          this.scene.remove(pc.points);
          pc.geometry.dispose();
          
          const newGeometry = new THREE.BufferGeometry();
          const newPositions = new Float32Array(newLimit * 3);
          const newColors = new Float32Array(newLimit * 3);
          const newTimestamps = new Float32Array(newLimit);
          
          newGeometry.setAttribute('position', new THREE.BufferAttribute(newPositions, 3).setUsage(THREE.DynamicDrawUsage));
          newGeometry.setAttribute('color', new THREE.BufferAttribute(newColors, 3).setUsage(THREE.DynamicDrawUsage));
          
          pc.points.geometry = newGeometry;
          pc.geometry = newGeometry;
          pc.positions = newPositions;
          pc.colors = newColors;
          pc.timestamps = newTimestamps;
          pc.index = 0;
          pc.hasWrapped = false;
          
          this.scene.add(pc.points);
        }
      }
      this.updateTotalPointCount();
    },
    
    pointCloudSelections: {
      handler(newSelections) {
        for (const frameId in this.pointClouds) {
          if (this.pointClouds[frameId] && this.pointClouds[frameId].points) {
            const isSelected = Object.values(newSelections).some(selectedId => selectedId === frameId);
            this.pointClouds[frameId].points.visible = isSelected;
          }
        }
        this.updateTotalPointCount();
      },
      deep: true
    },
    
    isSettingGoal(isSetting) {
      if (this.controls) { 
        this.controls.enablePan = !isSetting; 
        this.controls.enableRotate = !isSetting; 
        this.controls.enableZoom = !isSetting;
      }
      if (this.$refs.container) {
        this.$refs.container.style.cursor = isSetting ? 'crosshair' : 'default';
      }
    }
  },
  
  mounted() {
    if (!this.$refs.container) return;
    const { clientWidth, clientHeight } = this.$refs.container;

    // Scene Setup
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0xffffff);
    this.scene.add(new THREE.AmbientLight(0xffffff, 0.7));
    const dirLight = new THREE.DirectionalLight(0xffffff, 0.8);
    dirLight.position.set(50, 50, 50);
    this.scene.add(dirLight);

    // Camera Setup
    this.camera = new THREE.PerspectiveCamera(75, clientWidth / clientHeight, 0.1, 2000);
    this.camera.position.set(10, 10, 10);
    this.camera.up.set(0, 0, 1);

    // Renderer Setup
    this.renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: "high-performance" });
    this.renderer.setSize(clientWidth, clientHeight);
    this.renderer.setPixelRatio(window.devicePixelRatio);
    this.$refs.container.appendChild(this.renderer.domElement);

    // Controls Setup
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;

    this.labelRenderer = new CSS2DRenderer();
    this.labelRenderer.setSize(clientWidth, clientHeight);
    this.labelRenderer.domElement.style.position = 'absolute';
    this.labelRenderer.domElement.style.top = '0px';
    this.labelRenderer.domElement.style.pointerEvents = 'none';
    this.$refs.container.appendChild(this.labelRenderer.domElement);

    // Raycaster for Goal Setting
    this.raycaster = new THREE.Raycaster();
    this.targetPlane = new THREE.Plane(new THREE.Vector3(0, 0, 1), 0);

    // Clock for animations
    this.clock = new THREE.Clock();

    // Event Listener for Goal Setting
    this.renderer.domElement.addEventListener('click', this.onCanvasClick, false);

    // Periodically cull points by time window
    this.cullInterval = setInterval(this.cullAllPointsByTime, 2000);

    // Animation Loop
    this.animate();

    // Handle window resize
    window.addEventListener('resize', this.onResize);
    this.onResize();
  },
  
  beforeDestroy() {
    cancelAnimationFrame(this.animationFrameId);
    clearInterval(this.cullInterval);

    // Remove event listeners
    this.renderer.domElement.removeEventListener('click', this.onCanvasClick);
    window.removeEventListener('resize', this.onResize);

    // Dispose all Three.js resources
    for(const frameId in this.pointClouds) {
      const pc = this.pointClouds[frameId];
      if (pc.points) this.scene.remove(pc.points);
      if (pc.geometry) pc.geometry.dispose();
    }

    for(const robotId in this.trajectoryLines) {
      const traj = this.trajectoryLines[robotId];
      if (traj.line) this.scene.remove(traj.line);
      if (traj.geometry) traj.geometry.dispose();
      if (traj.line && traj.line.material) traj.line.material.dispose();
    }

    for(const robotId in this.poseObjects) {
      const poseObj = this.poseObjects[robotId];
      if (poseObj) {
        this.scene.remove(poseObj);
        poseObj.traverse((obj) => {
          if (obj instanceof THREE.Mesh) {
            if (obj.geometry) obj.geometry.dispose();
            if (obj.material) {
              if (Array.isArray(obj.material)) {
                obj.material.forEach(m => m.dispose());
              } else {
                obj.material.dispose();
              }
            }
          }
        });
      }
    }

    for (const robotId in this.navGoalObjects) {
      const { marker, label } = this.navGoalObjects[robotId];
      if (marker) {
        marker.remove(label);
        this.scene.remove(marker);
      }
    }

    if (this.labelRenderer && this.labelRenderer.domElement.parentElement) {
      this.labelRenderer.domElement.parentElement.removeChild(this.labelRenderer.domElement);
    }
    
    if (this.renderer) this.renderer.dispose();
    if (this.controls) this.controls.dispose();

    while(this.scene.children.length > 0){ 
      this.scene.remove(this.scene.children[0]); 
    }
  },
  
  methods: {
    animate() {
      this.animationFrameId = requestAnimationFrame(this.animate);
      
      this.controls.update();
      
      const deltaTime = this.clock.getDelta();

      // Update Nav Goal Marker size (screen-space scaling)
      for (const robotId in this.navGoalObjects) {
        const { marker } = this.navGoalObjects[robotId];
        if (marker && marker.visible) {
          marker.update(deltaTime); 

          const desiredMarkerPixelHeight = 10;
          const distance = this.camera.position.distanceTo(marker.position);
          const canvasHeight = this.renderer.domElement.clientHeight;
          const scale = (desiredMarkerPixelHeight / canvasHeight) * 2 * Math.tan(this.camera.fov * (Math.PI / 180) / 2) * distance;
          marker.scale.set(scale, scale, scale);
        }
      }

      // Update All Robot Pose Object sizes (screen-space scaling)
      for (const robotId in this.poseObjects) {
        const poseObj = this.poseObjects[robotId];
        if (poseObj && poseObj.visible) {
          const desiredRobotPixelHeight = 30;
          const robotDistance = this.camera.position.distanceTo(poseObj.position);
          const canvasHeight = this.renderer.domElement.clientHeight;
          const robotScale = (desiredRobotPixelHeight / canvasHeight) * 2 * Math.tan(this.camera.fov * (Math.PI / 180) / 2) * robotDistance;
          poseObj.scale.set(robotScale, robotScale, robotScale);
        }
      }

      this.renderer.render(this.scene, this.camera);
      this.labelRenderer.render(this.scene, this.camera);
    },

    addPoints(frameId, newPoints) {
      if (!this.scene || newPoints.length === 0) return;

      if (!this.isAccumulating) {
        this.clearAllPoints();
      }

      const sampledPoints = this.downsample(newPoints, this.pointDensity / 100.0);
      if (sampledPoints.length === 0) return;

      if (!this.pointClouds[frameId]) {
        const geometry = new THREE.BufferGeometry();
        const positions = new Float32Array(this.maxPointsPerCloud * 3);
        const colors = new Float32Array(this.maxPointsPerCloud * 3);
        const timestamps = new Float32Array(this.maxPointsPerCloud);
        
        geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3).setUsage(THREE.DynamicDrawUsage));
        geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3).setUsage(THREE.DynamicDrawUsage));
        
        const material = new THREE.PointsMaterial({ size: this.pointSize, vertexColors: true });
        const points = new THREE.Points(geometry, material);
        points.frustumCulled = false;
        
        const isSelected = Object.values(this.pointCloudSelections).some(selectedId => selectedId === frameId);
        points.visible = isSelected;
        
        this.scene.add(points);
        this.$set(this.pointClouds, frameId, { points, geometry, positions, colors, timestamps, index: 0, hasWrapped: false });
      }

      const pc = this.pointClouds[frameId];
      const color = new THREE.Color();
      const now = Date.now();

      for (const p of sampledPoints) {
        if (pc.index >= this.maxPointsPerCloud) { 
          pc.index = 0;
          pc.hasWrapped = true; 
        }
        const i3 = pc.index * 3;
        pc.positions[i3] = p.x; 
        pc.positions[i3 + 1] = p.y; 
        pc.positions[i3 + 2] = p.z;
        
        const isRgb = p.r !== undefined;
        if (isRgb) {
          pc.colors[i3] = (p.r ?? 0) / 255.0; 
          pc.colors[i3 + 1] = (p.g ?? 0) / 255.0; 
          pc.colors[i3 + 2] = (p.b ?? 0) / 255.0;
        } else {
          const intensity = p.intensity ?? p.z;
          const norm = Math.min(Math.max(intensity, -2), 2) / 4 + 0.5;
          color.setHSL(0.7 * (1 - norm), 1.0, 0.5);
          pc.colors[i3] = color.r; 
          pc.colors[i3 + 1] = color.g; 
          pc.colors[i3 + 2] = color.b;
        }
        pc.timestamps[pc.index] = now;
        pc.index++;
      }
      
      pc.geometry.attributes.position.needsUpdate = true;
      pc.geometry.attributes.color.needsUpdate = true;
      pc.geometry.setDrawRange(0, pc.hasWrapped ? this.maxPointsPerCloud : pc.index);
      
      this.updateTotalPointCount();
    },

    downsample(points, ratio) {
      if (ratio >= 1.0 || points.length === 0) {
        return points;
      }
      const result = [];
      const step = 1.0 / ratio;
      for (let i = 0; i < points.length; i += step) {
        result.push(points[Math.floor(i)]);
      }
      return result;
    },

    addOdom(odomPacket) {
      const robotId = odomPacket.sender || odomPacket.robot_id;

      if (!odomPacket?.odom || !robotId) return;
      
      this.$set(this.lastOdoms, robotId, { ...odomPacket, sender: robotId });
      
      this._updateRobotPose(robotId, odomPacket.odom, odomPacket.odom.orientation);
      this._updateTrajectory(robotId, odomPacket.odom);
    },

    _updateRobotPose(robotId, position, orientation) {
      if (!this.scene) return;

      let poseObject = this.poseObjects[robotId];

      if (!poseObject) {
        poseObject = new THREE.Group();
        this.$set(this.poseObjects, robotId, poseObject);
        this.scene.add(poseObject);
        
        const modelUrl = `${this.MODEL_PATH_PREFIX}${robotId}.dae`;
        const loader = new ColladaLoader();
        
        console.log(`Attempting to load model for ${robotId} from ${modelUrl}`);
        
        loader.load(
          modelUrl,
          (collada) => {
            if (!collada || !collada.scene) {
              console.error(`Collada object is null or has no scene for ${robotId}. Falling back to axes.`);
              const fallbackAxes = new RobotPoseMarker();
              fallbackAxes.scale.set(0.5, 0.5, 0.5);
              poseObject.add(fallbackAxes);
              return;
            }

            console.log(`Successfully loaded DAE model for ${robotId}`);
            const model = collada.scene;
            
            const initialFixedScale = 0.02;
            model.scale.set(initialFixedScale, initialFixedScale, initialFixedScale);
            
            const box = new THREE.Box3().setFromObject(model);
            const center = box.getCenter(new THREE.Vector3());
            model.position.sub(center);

            const correctionGroup = new THREE.Group();
            correctionGroup.add(model);
            
            correctionGroup.rotation.x = Math.PI / 2;
            correctionGroup.rotation.y = Math.PI / 2;

            poseObject.add(correctionGroup);
          },
          undefined,
          (error) => {
            console.warn(`Failed to load DAE model for '${robotId}'. Falling back to axes. Error:`, error);
            const fallbackAxes = new RobotPoseMarker();
            fallbackAxes.scale.set(0.5, 0.5, 0.5); 
            poseObject.add(fallbackAxes);
          }
        );
      }

      poseObject.position.set(position.x, position.y, position.z + 0.5); 
      poseObject.quaternion.set(orientation.x, orientation.y, orientation.z, orientation.w);
      poseObject.visible = true;
    },

    _updateTrajectory(robotId, position) {
      if (!this.scene) return;
      
      if (!this.trajectoryLines[robotId]) {
        const geometry = new THREE.BufferGeometry();
        const positions = new Float32Array(this.MAX_TRAJECTORY_POINTS * 3);
        geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3).setUsage(THREE.DynamicDrawUsage));
        
        let hash = 0;
        for (let i = 0; i < robotId.length; i++) {
          hash = robotId.charCodeAt(i) + ((hash << 5) - hash);
        }
        const color = new THREE.Color().setHSL((hash % 100) / 100, 0.8, 0.5);
        
        const material = new THREE.LineBasicMaterial({ color, linewidth: 4 });
        const line = new THREE.Line(geometry, material);
        line.frustumCulled = false;
        this.scene.add(line);
        this.$set(this.trajectoryLines, robotId, { line, geometry, positions, index: 0, hasWrapped: false });
        console.log(`Trajectory created for robot ${robotId} with color`, color.getHexString());
      }

      const traj = this.trajectoryLines[robotId];
      if (traj.index >= this.MAX_TRAJECTORY_POINTS) { 
        traj.index = 0; 
        traj.hasWrapped = true; 
      }
      const i3 = traj.index * 3;
      traj.positions[i3] = position.x;
      traj.positions[i3 + 1] = position.y;
      traj.positions[i3 + 2] = position.z;
      traj.index++;
      
      traj.geometry.attributes.position.needsUpdate = true;
      traj.geometry.setDrawRange(0, traj.hasWrapped ? this.MAX_TRAJECTORY_POINTS : traj.index);
    },

    onCanvasClick(event) {
      if (!this.isSettingGoal || !this.targetRobotIdForNav) return;
      
      const targetOdom = this.lastOdoms[this.targetRobotIdForNav];
      if (!targetOdom) {
        alert(`尚未收到机器人 [${this.targetRobotIdForNav}] 的Odom数据，无法设置目标。`);
        this.$emit('goal-captured', null);
        return;
      }
      
      this.targetPlane.set(new THREE.Vector3(0, 0, 1), -targetOdom.pose.position.z);
      
      const rect = this.renderer.domElement.getBoundingClientRect();
      const mouse = new THREE.Vector2(
        ((event.clientX - rect.left) / rect.width) * 2 - 1, 
        -((event.clientY - rect.top) / rect.height) * 2 + 1
      );
      
      this.raycaster.setFromCamera(mouse, this.camera);
      
      const intersectionPoint = new THREE.Vector3();
      this.raycaster.ray.intersectPlane(this.targetPlane, intersectionPoint);
      
      if (intersectionPoint) {
        this.$emit('goal-captured', { x: intersectionPoint.x, y: intersectionPoint.y, z: intersectionPoint.z });
      } else {
        console.warn("Could not intersect with the target plane.");
      }
    },

    clearAll() {
      this.clearAllPoints();
      
      for(const robotId in this.trajectoryLines) { 
        const traj = this.trajectoryLines[robotId];
        traj.geometry.setDrawRange(0, 0); 
        traj.index = 0; 
        traj.hasWrapped = false; 
      }

      for(const robotId in this.poseObjects) { 
        this.poseObjects[robotId].visible = false; 
      }
      
      // 清空 lastOdoms
      for (const key in this.lastOdoms) {
        this.$delete(this.lastOdoms, key);
      }
      
      this.clearAllNavGoals();
    },

    clearAllPoints() {
      for(const frameId in this.pointClouds) {
        const pc = this.pointClouds[frameId];
        pc.index = 0;
        pc.hasWrapped = false;
        pc.geometry.setDrawRange(0, 0);
        pc.geometry.attributes.position.needsUpdate = true;
        pc.geometry.attributes.color.needsUpdate = true;
      }
      this.updateTotalPointCount();
    },

    cullAllPointsByTime() {
      if (!this.isAccumulating) return;
      const expiryTime = Date.now() - this.timeWindowSec * 1000;
      
      for (const frameId in this.pointClouds) {
        const pc = this.pointClouds[frameId];
        const totalPoints = pc.hasWrapped ? this.maxPointsPerCloud : pc.index;
        if (totalPoints === 0) continue;
        
        let validPointIndex = 0;
        for (let i = 0; i < totalPoints; i++) {
          if (pc.timestamps[i] >= expiryTime) {
            if (i !== validPointIndex) {
              const i3 = i * 3, v3 = validPointIndex * 3;
              pc.positions[v3] = pc.positions[i3]; 
              pc.positions[v3+1] = pc.positions[i3+1]; 
              pc.positions[v3+2] = pc.positions[i3+2];
              pc.colors[v3] = pc.colors[i3]; 
              pc.colors[v3+1] = pc.colors[i3+1]; 
              pc.colors[v3+2] = pc.colors[i3+2];
              pc.timestamps[validPointIndex] = pc.timestamps[i];
            }
            validPointIndex++;
          }
        }
        
        if (validPointIndex < totalPoints) {
          pc.index = validPointIndex;
          pc.hasWrapped = false;
          pc.geometry.setDrawRange(0, validPointIndex);
          pc.geometry.attributes.position.needsUpdate = true;
          pc.geometry.attributes.color.needsUpdate = true;
        }
      }
      this.updateTotalPointCount();
    },

    updateTotalPointCount() {
      let totalPoints = 0;
      Object.values(this.pointCloudSelections).forEach(selectedFrameId => {
        if (selectedFrameId && this.pointClouds[selectedFrameId]) {
          const pc = this.pointClouds[selectedFrameId];
          totalPoints += pc.hasWrapped ? this.maxPointsPerCloud : pc.index;
        }
      });
      this.$emit('update:pointCount', totalPoints);
    },

    _updateOrCreateNavGoal(robotId, position, displayName) {
      if (!this.scene) return;
      
      let goalObject = this.navGoalObjects[robotId];

      if (!goalObject) {
        let hash = 0;
        for (let i = 0; i < robotId.length; i++) {
          const char = robotId.charCodeAt(i);
          hash = ((hash << 5) - hash) + char;
          hash = hash & hash;
        }

        const GOLDEN_RATIO_CONJUGATE = 0.61803398875;
        const hue = (Math.abs(hash) * GOLDEN_RATIO_CONJUGATE) % 1;
        const saturation = 0.85;
        const lightness = 0.5;
        const color = new THREE.Color().setHSL(hue, saturation, lightness);
        const marker = new CustomNavMarker(color.getHex());
        this.scene.add(marker);
        
        const labelContainer = document.createElement('div');
        labelContainer.className = 'robot-nav-label-container';
        const nameDiv = document.createElement('div');
        nameDiv.className = 'robot-nav-label-name';
        nameDiv.textContent = displayName || robotId;
        nameDiv.style.color = color.getStyle();
        const idDiv = document.createElement('div');
        idDiv.className = 'robot-nav-label-id';
        idDiv.textContent = `(${robotId})`;
        labelContainer.appendChild(nameDiv);
        labelContainer.appendChild(idDiv);
        const label = new CSS2DObject(labelContainer);
        label.position.set(0, 1.5, 0);
        marker.add(label);

        goalObject = { marker, label };
        this.$set(this.navGoalObjects, robotId, goalObject);

        console.log(`Nav goal object created for ${robotId}`);
      }

      if (position) {
        goalObject.marker.position.set(position.x, position.y, position.z + 0.05);
        goalObject.marker.visible = true;
      } else {
        goalObject.marker.visible = false;
      }
    },

    setNavGoal(robotId, position, displayName) { 
      this._updateOrCreateNavGoal(robotId, position, displayName); 
    },

    clearNavGoal(robotId) {
      this._updateOrCreateNavGoal(robotId, null);
    },

    clearAllNavGoals() {
      for (const robotId in this.navGoalObjects) {
        this._updateOrCreateNavGoal(robotId, null);
      }
    },

    onResize() {
      if (!this.$refs.container || !this.renderer || !this.camera) return;
      const { clientWidth, clientHeight } = this.$refs.container;
      this.renderer.setSize(clientWidth, clientHeight);
      this.labelRenderer.setSize(clientWidth, clientHeight);
      this.camera.aspect = clientWidth / clientHeight;
      this.camera.updateProjectionMatrix();
    }
  }
}
</script>

<style>
.robot-nav-label {
  font-family: Arial, sans-serif;
  font-size: 14px;
  font-weight: bold;
  padding: 2px 6px;
  border-radius: 4px;
  background-color: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(0, 0, 0, 0.2);
  white-space: nowrap;
}

.robot-nav-label-container {
  font-family: Arial, sans-serif;
  font-size: 14px;
  font-weight: bold;
  padding: 2px 6px;
  border-radius: 4px;
  background-color: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(0, 0, 0, 0.2);
  white-space: nowrap;
}

.robot-nav-label-name {
  margin: 0;
}

.robot-nav-label-id {
  margin: 0;
  font-size: 12px;
  opacity: 0.7;
}
</style>

<style scoped>
.viewer-container { 
  position: relative;
  width: 100%; 
  height: 100%; 
  overflow: hidden; 
}
</style>