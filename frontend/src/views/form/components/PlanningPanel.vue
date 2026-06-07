<template>
  <transition name="slide-fade">
    <div v-if="isCollectShow" class="planning-panel">
      <div class="panel-header">
        <span class="panel-title">
          <i class="el-icon-edit"></i>
          绘制工具
        </span>
        <el-button
          type="text"
          icon="el-icon-close"
          @click="emitClosePlanningPanel"
          class="close-btn"
        ></el-button>
      </div>

      <div class="panel-content">
        <div class="tool-item" @click="emitDrawMissionArea" :class="{active: currentTool === 'mission_area'}">
          <div class="tool-icon mission-area">
            <i class="el-icon-s-grid"></i>
          </div>
          <span class="tool-name">绘制任务区</span>
        </div>

        <div class="tool-item" @click="emitDrawObstacleArea" :class="{active: currentTool === 'obstacle_area'}">
          <div class="tool-icon obstacle-area">
            <i class="el-icon-warning"></i>
          </div>
          <span class="tool-name">绘制障碍区</span>
        </div>

        <div class="tool-item" @click="emitAddStartingPoint" :class="{active: currentTool === 'starting_point'}">
          <div class="tool-icon start-point">
            <i class="el-icon-location"></i>
          </div>
          <span class="tool-name">添加起点</span>
          <el-button
            v-if="currentTool === 'starting_point'"
            type="text"
            size="mini"
            @click.stop="emitCancelStartingPoint"
            class="cancel-btn"
          >
            取消
          </el-button>
        </div>

        <div class="tool-item" @click="emitFetchStartingPoints">
          <div class="tool-icon fetch-point">
            <i class="el-icon-download"></i>
          </div>
          <span class="tool-name">获取起点</span>
        </div>

        <div class="tool-item" @click="emitSetMissionPlannerForm">
          <div class="tool-icon settings">
            <i class="el-icon-setting"></i>
          </div>
          <span class="tool-name">参数设置</span>
        </div>

        <div class="tool-item" @click="emitDisdraw">
          <div class="tool-icon clear">
            <i class="el-icon-refresh-left"></i>
          </div>
          <span class="tool-name">清除绘制</span>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: 'PlanningPanel',
  props: {
    isCollectShow: {
      type: Boolean,
      required: true
    },
    currentTool: {
      type: String,
      required: true
    }
  },
  methods: {
    emitClosePlanningPanel() {
      this.$emit('closePlanningPanel');
    },
    emitDrawMissionArea() {
      this.$emit('drawMissionArea');
    },
    emitDrawObstacleArea() {
      this.$emit('drawObstacleArea');
    },
    emitAddStartingPoint() {
      this.$emit('addStartingPoint');
    },
    emitFetchStartingPoints() {
      this.$emit('fetchStartingPoints');
    },
    emitCancelStartingPoint() {
      this.$emit('cancelStartingPoint');
    },
    emitSetMissionPlannerForm() {
      this.$emit('setMissionPlannerForm');
    },
    emitDisdraw() {
      this.$emit('disdraw');
    }
  }
}
</script>

<style scoped>
/* 规划面板 */
.planning-panel {
  position: absolute;
  top: 20px;
  right: 20px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  width: 280px;
  z-index: 1000;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

.panel-title {
  font-weight: 600;
  color: #2c3e50;
  display: flex;
  align-items: center;
  gap: 8px;
}

.close-btn {
  color: #6c757d;
  padding: 0;
}

.panel-content {
  padding: 20px;
  display: grid;
  gap: 15px;
}

.tool-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid transparent;
  position: relative;
}

.tool-item:hover {
  background: #f8f9fa;
  border-color: #e9ecef;
  transform: translateY(-1px);
}

/* 新增：活跃工具状态 */
.tool-item.active {
  background: linear-gradient(135deg, #e3f2fd 0%, #f3e5f5 100%);
  border-color: #2196F3;
  box-shadow: 0 2px 8px rgba(33, 150, 243, 0.3);
}

.tool-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: white;
}

.tool-icon.mission-area {
  background: linear-gradient(135deg, #4CAF50, #66BB6A);
}

.tool-icon.obstacle-area {
  background: linear-gradient(135deg, #F44336, #EF5350);
}

.tool-icon.start-point {
  background: linear-gradient(135deg, #2196F3, #42A5F5);
}

.tool-icon.fetch-point {
  background: linear-gradient(135deg, #3f51b5, #5c6bc0);
}

.tool-icon.settings {
  background: linear-gradient(135deg, #9C27B0, #BA68C8);
}

.tool-icon.clear {
  background: linear-gradient(135deg, #FF9800, #FFB74D);
}

/* 新增：取消按钮样式 */
.cancel-btn {
  color: #f56c6c;
  font-size: 12px;
  padding: 2px 6px;
  border: 1px solid #f56c6c;
  border-radius: 4px;
  background: rgba(245, 108, 108, 0.1);
}

.cancel-btn:hover {
  background: rgba(245, 108, 108, 0.2);
}

/* 动画效果 */
.slide-fade-enter-active, .slide-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.5, 1);
}

.slide-fade-enter, .slide-fade-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>
