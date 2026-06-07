<template>
  <div class="app-container">
    <el-card class="box-card">
      <div slot="header" class="clearfix">
        <span class="header-title">KML 航线任务管理</span>
        <el-button
          style="float: right;"
          type="primary"
          icon="el-icon-plus"
          @click="goToPlanner"
        >
          新增任务规划
        </el-button>
      </div>

      <!-- 任务列表表格 -->
      <el-table
        v-loading="isLoading"
        :data="missionPlans.list"
        stripe
        style="width: 100%;"
        empty-text="暂无任务规划数据"
      >
        <el-table-column prop="id" label="ID" width="80" align="center"></el-table-column>
        <el-table-column prop="time" label="规划时间" width="180" align="center"></el-table-column>
        <el-table-column prop="numberDevice" label="设备数量" width="100" align="center"></el-table-column>
        <el-table-column prop="droneSpeed" label="速度 (m/s)" width="120" align="center"></el-table-column>
        <el-table-column prop="scanDensity" label="扫描密度" width="120" align="center"></el-table-column>
        <el-table-column prop="kmlFilePath" label="KML 存储路径" show-overflow-tooltip></el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template slot-scope="scope">
            <el-button
              size="mini"
              type="success"
              icon="el-icon-download"
              @click="handleDownload(scope.row)"
            >
              下载
            </el-button>
            <el-button
              size="mini"
              type="danger"
              icon="el-icon-delete"
              @click="handleDelete(scope.row.id)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页控件 -->
      <el-pagination
        v-if="missionPlans.total > 0"
        background
        layout="prev, pager, next, total, jumper"
        :total="missionPlans.total"
        :page-size="pageSize"
        :current-page.sync="pageNum"
        @current-change="fetchMissionPlans"
        style="margin-top: 20px; text-align: center;"
      >
      </el-pagination>

    </el-card>

    <!-- ZIP文件列表对话框 -->
    <el-dialog 
      :title="zipFileDialog.title"
      :visible.sync="zipFileDialog.visible"
      width="80%"
      :close-on-click-modal="false">
      
      <!-- 任务信息 -->
      <div class="dialog-header" v-if="zipFileDialog.missionInfo">
        <el-tag type="primary">任务ID: {{ zipFileDialog.missionInfo.id }}</el-tag>
        <el-tag type="info">设备数量: {{ zipFileDialog.missionInfo.numberDevice }}</el-tag>
        <el-tag type="success">速度: {{ zipFileDialog.missionInfo.droneSpeed }} m/s</el-tag>
        <el-tag type="warning">扫描密度: {{ zipFileDialog.missionInfo.scanDensity }}</el-tag>
      </div>
      
      <!-- 文件列表表格 -->
      <el-table 
        :data="zipFileDialog.files" 
        border 
        stripe
        height="450"
        v-loading="zipFileDialog.loading"
        element-loading-text="正在解压文件...">
        
        <el-table-column type="index" label="#" width="60"/>
        
        <el-table-column prop="name" label="文件名" min-width="300" show-overflow-tooltip>
          <template slot-scope="scope">
            <div class="file-name-cell">
              <i class="el-icon-document file-icon"></i>
              <span class="file-name">{{ scope.row.name }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="sizeFormatted" label="文件大小" width="120"/>
        
        <el-table-column prop="lastModified" label="修改时间" width="160">
          <template slot-scope="scope">
            {{ formatDateTime(scope.row.lastModified) }}
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="120" fixed="right">
          <template slot-scope="scope">
            <el-button 
              type="primary" 
              size="small"
              icon="el-icon-download"
              @click="downloadSingleFile(scope.row)">
              下载
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 对话框底部信息 -->
      <div slot="footer" class="dialog-footer">
        <span class="file-count">共 {{ zipFileDialog.files.length }} 个文件</span>
        <el-button @click="zipFileDialog.visible = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import axios from 'axios';
import JSZip from 'jszip';
import { getBackendBaseUrl } from '@/utils/runtimeApi';

const API_BASE_URL = getBackendBaseUrl();

export default {
  name: 'MissionPlanManagement',
  data() {
    return {
      isLoading: false,
      missionPlans: {
        list: [],
        total: 0,
      },
      zipFileDialog: {
        visible: false,
        loading: false,
        title: '',
        missionInfo: null,
        files: []
      },
      pageNum: 1,
      pageSize: 10,
    };
  },
  methods: {
    // 获取任务规划列表
    async fetchMissionPlans() {
      this.isLoading = true;
      try {
        const token = this.$store.getters.token; // 确保从本地存储获取 Token
        if (!token) {
          this.$message.error('认证失败，请先登录');
          this.$router.push('/login'); // 跳转到登录页
          return;
        }
        const config = {
          headers: { 'Authorization': `Bearer ${token}` }
        };
        const response = await axios.get(`${API_BASE_URL}/api/mission-plans?pageNum=${this.pageNum}&pageSize=${this.pageSize}`, config);

        if (response.data.code === 200) {
          this.missionPlans = response.data.data;
        } else {
          this.$message.error('获取任务列表失败: ' + response.data.message);
        }
      } catch (error) {
        this.$message.error('请求任务列表失败');
        console.error(error);
      } finally {
        this.isLoading = false;
      }
    },

    // 跳转到任务规划页面
    goToPlanner() {
      // 请将 '/mission/planner' 替换为您项目中任务规划页面的实际路由
      this.$router.push('/mission/planner');
    },

    // 处理删除操作
    handleDelete(id) {
      this.$confirm('此操作将永久删除该任务记录及其生成的KML文件, 是否继续?', '警告', {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        // 用户点击了确定
        try {
          const token = this.$store.getters.token;
          const config = { headers: { 'Authorization': `Bearer ${token}` } };
          const response = await axios.delete(`${API_BASE_URL}/api/mission-plans/${id}`, config);

          if (response.data.code === 200) {
            this.$message.success('删除成功!');
            // 如果删除的是当前页的最后一条数据，且不是第一页，则返回上一页
            if (this.missionPlans.list.length === 1 && this.pageNum > 1) {
                this.pageNum--;
            }
            this.fetchMissionPlans(); // 刷新列表
          } else {
            this.$message.error('删除失败: ' + response.data.message);
          }
        } catch (error) {
          this.$message.error('请求删除失败');
          console.error(error);
        }
      }).catch(() => {
        // 用户点击了取消
        this.$message.info('已取消删除');
      });
    },

    /**
     * 处理 KML 文件下载 - 解压ZIP并显示文件列表
     */
    async handleDownload(row) {
      this.$message.info(`正在获取任务 ${row.id} 的KML文件...`);
      
      try {
        const token = this.$store.getters.token;
        const config = {
          headers: { 'Authorization': `Bearer ${token}` },
          responseType: 'blob'
        };

        // 获取ZIP文件
        const response = await axios.get(`${API_BASE_URL}/api/mission-plans/${row.id}/download`, config);
        
        // 显示对话框并开始解压
        this.zipFileDialog.loading = true;
        this.zipFileDialog.visible = true;
        this.zipFileDialog.missionInfo = row;
        this.zipFileDialog.title = `任务 ${row.id} - KML文件列表`;

        await this.extractZipFiles(response.data, row);

      } catch (error) {
        this.$message.error('获取KML文件失败');
        console.error('Download error:', error);
        this.zipFileDialog.visible = false;
      } finally {
        this.zipFileDialog.loading = false;
      }
    },

    /**
     * 解压ZIP文件并提取文件列表
     */
    async extractZipFiles(zipBlob, missionInfo) {
      try {
        this.$message.info('正在解压文件...');
        
        const zip = await JSZip.loadAsync(zipBlob);
        const files = [];

        // 遍历ZIP中的所有文件
        for (const [relativePath, zipEntry] of Object.entries(zip.files)) {
          // 跳过目录
          if (zipEntry.dir) continue;

          const fileName = zipEntry.name;
          const fileInfo = {
            name: fileName,
            size: zipEntry._data ? zipEntry._data.uncompressedSize : 0,
            lastModified: zipEntry.date || new Date(),
            sizeFormatted: this.formatFileSize(zipEntry._data ? zipEntry._data.uncompressedSize : 0),
            zipEntry: zipEntry, // 保存ZIP条目引用
            missionId: missionInfo.id
          };

          files.push(fileInfo);
        }

        // 按文件名排序
        files.sort((a, b) => a.name.localeCompare(b.name));

        this.zipFileDialog.files = files;

        if (files.length > 0) {
          this.$message.success(`找到 ${files.length} 个文件`);
        } else {
          this.$message.warning('压缩包中没有找到文件');
        }

      } catch (error) {
        console.error('解压文件失败:', error);
        this.$message.error('解压文件失败: ' + error.message);
        this.zipFileDialog.visible = false;
      }
    },

    /**
     * 下载单个文件
     */
    async downloadSingleFile(fileInfo) {
      try {
        this.$message.info(`正在下载文件: ${fileInfo.name}...`);

        // 从ZIP条目中提取文件内容
        const content = await fileInfo.zipEntry.async('blob');
        
        // 创建下载链接
        const url = URL.createObjectURL(content);
        const link = document.createElement('a');
        link.href = url;
        link.download = `mission_${fileInfo.missionId}_${fileInfo.name}`;
        
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        
        URL.revokeObjectURL(url);
        
        this.$message.success(`文件 ${fileInfo.name} 下载成功`);

      } catch (error) {
        console.error('下载文件失败:', error);
        this.$message.error(`下载文件失败: ${error.message}`);
      }
    },

    /**
     * 格式化文件大小
     */
    formatFileSize(size) {
      if (size < 1024) return size + ' B';
      if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB';
      if (size < 1024 * 1024 * 1024) return (size / 1024 / 1024).toFixed(1) + ' MB';
      return (size / 1024 / 1024 / 1024).toFixed(1) + ' GB';
    },

    /**
     * 格式化日期时间
     */
    formatDateTime(date) {
      if (!date) return '';
      return new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      }).format(new Date(date));
    }
  },
  mounted() {
    // 组件加载后，获取第一页数据
    this.fetchMissionPlans();
  }
};
</script>

<style scoped>
.app-container {
  padding: 20px;
}
.box-card {
  border-radius: 8px;
}
.clearfix::before,
.clearfix::after {
  display: table;
  content: "";
}
.clearfix::after {
  clear: both;
}
.header-title {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

/* 对话框样式 */
.dialog-header {
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #ebeef5;
}

.dialog-header .el-tag {
  margin-right: 10px;
  margin-bottom: 5px;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-icon {
  color: #409eff;
}

.file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.file-count {
  color: #909399;
  font-size: 14px;
}
</style>
