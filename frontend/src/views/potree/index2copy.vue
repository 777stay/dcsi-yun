<template>
    <div class="app-container">
      <el-card class="box-card">
        <div slot="header" class="clearfix">
          <span>Potree 点云查看器示例</span>
        </div>
        <potree-viewer :point-cloud-path="pointCloudUrl" />
      </el-card>
    </div>
  </template>
  
  <script>
  import PotreeViewer from '@/views/potree/indexcopy';
  
  export default {
    name: 'PotreeDemo',
    components: {
      PotreeViewer
    },
    data() {
      return {
        // 请将此 URL 替换为您自己的点云数据路径
        // 这通常是一个指向 'cloud.js' 文件的 URL
        pointCloudUrl: 'https://raw.githubusercontent.com/potree/potree/develop/examples/pointclouds/lion_takanawa/cloud.js'
      };
    }
  };
  </script>
  
  <style scoped>
  .app-container {
    padding: 20px;
  }
  </style>