<template>
  <div class="video-container" style="box-shadow: var(--el-box-shadow-light);">
    <!-- 视频元素 -->
    <video ref="video" controls @dblclick="toggleFullScreen">
      <source src="http://127.0.0.1:8888/stream/index.m3u8" type="application/vnd.apple.mpegurl">
      您的浏览器不支持视频标签。
    </video>
  </div>
</template>

<script>
import Hls from 'hls.js'; // 导入 HLS.js 库

export default {
  name: 'HlsStream', // 组件名称
  mounted() {
    const video = this.$refs.video; // 获取视频 DOM 元素

    if (Hls.isSupported()) {
      // 如果 HLS.js 被支持
      const hls = new Hls();
      hls.loadSource('http://127.0.0.1:8888/stream/index.m3u8'); // 加载 HLS 流
      hls.attachMedia(video); // 将 HLS 流绑定到视频元素
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        // HLS 文件解析完成后，自动播放视频
        video.play();
      });
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      // 如果浏览器本身支持 HLS (比如 Safari)
      video.src = 'http://127.0.0.1:8888/stream/index.m3u8';
      video.addEventListener('canplay', () => {
        video.play();
      });
    }
  },
  methods:{
    //双击全屏
    toggleFullScreen(){
      const video = this.$refs.video;

      // 判断是否已经全屏
      if (!document.fullscreenElement) {
        // 如果没有进入全屏，执行全屏操作
        if (video.requestFullscreen) {
          video.requestFullscreen();
        } else if (video.mozRequestFullScreen) {
          // Firefox
          video.mozRequestFullScreen();
        } else if (video.webkitRequestFullscreen) {
          // Chrome, Safari 和 Opera
          video.webkitRequestFullscreen();
        } else if (video.msRequestFullscreen) {
          // IE/Edge
          video.msRequestFullscreen();
        }
      } else {
        // 如果已经全屏，退出全屏
        if (document.exitFullscreen) {
          document.exitFullscreen();
        } else if (document.mozCancelFullScreen) {
          // Firefox
          document.mozCancelFullScreen();
        } else if (document.webkitExitFullscreen) {
          // Chrome, Safari 和 Opera
          document.webkitExitFullscreen();
        } else if (document.msExitFullscreen) {
          // IE/Edge
          document.msExitFullscreen();
        }
      }
    }
  }
};
</script>

<style scoped>
/* 这里可以添加样式 */
.video-container {
  width: 100%; /* 父容器宽度 */
  height: 50%;
  max-width: 1280px; /* 可选：设置最大宽度 */
  margin: 2px auto; /* 可选：居中对齐 */
 /* 为视频添加边框 */
   /* 给视频添加内边距 */
  box-shadow: var(--el-box-shadow-light);
  box-sizing: border-box; /* 确保内外边距不会影响到视频的总尺寸 */
}

video {
  width: 100%; /* 视频宽度占满父容器 */
  height: auto; /* 高度按比例调整 */
  display: block; /* 消除内边距空隙 */
}
</style>
