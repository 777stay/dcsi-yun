<template>
  <div class="control-bar">
    <div class="control-section">
      <h3 class="section-title">
        <i class="el-icon-map-location"></i>
        位置搜索
      </h3>
      <div class="search-controls">
        <el-input
          :value="searchCoords.latitude"
          @input="updateSearchCoords('latitude', $event)"
          placeholder="纬度"
          size="small"
          class="coord-input"
        >
          <template slot="prepend">
            <i class="el-icon-location-outline"></i>
          </template>
        </el-input>
        <el-input
          :value="searchCoords.longitude"
          @input="updateSearchCoords('longitude', $event)"
          placeholder="经度"
          size="small"
          class="coord-input"
        >
          <template slot="prepend">
            <i class="el-icon-location"></i>
          </template>
        </el-input>
        <el-button
          type="primary"
          size="small"
          icon="el-icon-search"
          @click="emitUpdateMapLocation"
          :loading="updating"
        >
          定位
        </el-button>
        <el-button
          type="info"
          size="small"
          icon="el-icon-aim"
          @click="emitGetCurrentLocation"
          :loading="locating"
        >
          当前位置
        </el-button>
      </div>
    </div>

    <div class="control-section">
      <h3 class="section-title">
        <i class="el-icon-folder-opened"></i>
        KML文件
      </h3>
      <div class="kml-controls">
        <el-upload
          class="kml-uploader"
          action=""
          :show-file-list="false"
          :before-upload="emitHandleKmlFileSelect"
          accept=".kml"
        >
          <el-button
            type="success"
            size="small"
            icon="el-icon-upload"
          >
            导入KML
          </el-button>
        </el-upload>

        <el-button
          type="danger"
          size="small"
          icon="el-icon-delete"
          @click="emitClearKmlLayers"
          :disabled="!hasKmlLayers"
        >
          清除图层
        </el-button>
      </div>
    </div>
<!-- 
    <div class="control-section">
      <h3 class="section-title">
        <i class="el-icon-folder-opened"></i>
        山火区域KML文件
      </h3>
      <div class="kml-controls">
        <el-upload
          class="kml-uploader"
          action=""
          :show-file-list="false"
          :before-upload="emitHandleKmlFileUpload"
          accept=".kml"
        >
          <el-button
            type="success"
            size="small"
            icon="el-icon-upload"
          >
            导入KML
          </el-button>
        </el-upload>

        <el-button
          type="danger"
          size="small"
          icon="el-icon-delete"
          @click="emitClearKmlLayers"
          :disabled="!hasKmlLayers"
        >
          清除图层
        </el-button>
      </div>
    </div> -->


  </div>
</template>

<script>
export default {
  name: 'MapControls',
  props: {
    searchCoords: {
      type: Object,
      required: true
    },
    updating: {
      type: Boolean,
      required: true
    },
    locating: {
      type: Boolean,
      required: true
    },
    hasKmlLayers: {
      type: Boolean,
      required: true
    }
  },
  methods: {
    updateSearchCoords(key, event) {
      this.$emit('update:searchCoords', { ...this.searchCoords, [key]: event });
    },
    emitUpdateMapLocation() {
      this.$emit('updateMapLocation');
    },
    emitGetCurrentLocation() {
      this.$emit('getCurrentLocation');
    },
    emitHandleKmlFileSelect(file) {
      this.$emit('handleKmlFileSelect', file);
      return false; // 阻止el-upload的默认上传行为
    },
    emitClearKmlLayers() {
      this.$emit('clearKmlLayers');
    },
    emitHandleKmlFileUpload(file) {
      this.$emit('handleKmlFileUpload', file);
      return false; // 阻止el-upload的默认上传行为
    }
  }
}
</script>

<style scoped>
.control-bar {
  background: linear-gradient(to right, #f8f9fa, #e9ecef);
  padding: 15px 20px;
  border-bottom: 1px solid #dee2e6;
  display: flex;
  gap: 30px;
  flex-wrap: wrap;
}

.control-section {
  flex: 1;
  min-width: 300px;
}

.section-title {
  font-size: 14px;
  color: #495057;
  margin: 0 0 10px 0;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-title i {
  color: #007bff;
}

.search-controls, .kml-controls {
  display: flex;
  gap: 10px;
  align-items: center;
}

.coord-input {
  width: 140px;
}
</style>
