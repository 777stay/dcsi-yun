import L from 'leaflet';

/**
 * 初始化 Leaflet 地图。
 * 设置地图中心点、缩放级别、瓦片图层等。
 * @returns {Object} 初始化的 Leaflet 地图实例。
 */
export function initMap() {
  // 检查地图是否已存在，如果存在则先移除
  if (document.getElementById('RealMap')._leaflet_map) {
    document.getElementById('RealMap')._leaflet_map.remove();
  }

  const map = L.map("RealMap", {
    center: [23.3370, 113.0070],
    zoom: 17,
    zoomControl: true,
    doubleClickZoom: true,
    attributionControl: false,
  });

  L.tileLayer(
    'http://t{s}.tianditu.gov.cn/img_w/wmts?tk=5e3672fc0409d68d282e328ddd3db78a&SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TileMatrix={z}&TileCol={x}&TileRow={y}', {
    subdomains: ["0", "1", "2", "3", "4", "5", "6", "7"]
  }).addTo(map);

  // 初始化 Leaflet.PM (用于绘图模式)
  map.pm.addControls({
    position: 'topleft',
    drawPolygon: false, // 默认不显示多边形绘制工具
    drawMarker: false, // 默认不显示标记绘制工具
    drawCircleMarker: false,
    drawPolyline: false,
    drawRectangle: false,
    drawCircle: false,
    editMode: false,
    dragMode: false,
    cutPolygon: false,
    removalMode: false,
    rotateMode: false,
    syncLayers: false,
    // 其他绘制工具根据需要设置
  });

  return map;
}

/**
 * 根据输入的经纬度更新地图位置和标记。
 * 如果提供了有效的经纬度，地图会平移到该位置并添加一个标记；否则会清除现有标记。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Object} searchCoords - 包含 `latitude` 和 `longitude` 的搜索坐标对象。
 * @param {Object} state - 包含 `updating` 和 `currentMarker` 的响应式对象。
 * @param {Function} setUpdating - 更新 `updating` 状态的回调函数。
 * @param {Function} setCurrentMarker - 更新 `currentMarker` 的回调函数。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象 (如 this.$message)。
 */
export function updateMapLocation(map, searchCoords, state, setUpdating, setCurrentMarker, messageInstance) {
  setUpdating(true); // 设置更新状态为 true
  if (state.currentMarker) {
    map.removeLayer(state.currentMarker);
    setCurrentMarker(null);
  }

  const lat = parseFloat(searchCoords.latitude);
  const lng = parseFloat(searchCoords.longitude);

  if (!isNaN(lat) && !isNaN(lng)) {
    const newLatLng = L.latLng(lat, lng);
    map.setView(newLatLng, 15); // 平移地图并设置缩放级别

    // 添加新的标记
    const marker = L.marker(newLatLng).addTo(map);
    marker.bindPopup(`纬度: ${lat.toFixed(6)}<br>经度: ${lng.toFixed(6)}`).openPopup();
    setCurrentMarker(marker);
    messageInstance.success(`地图已更新至: ${lat.toFixed(6)}, ${lng.toFixed(6)}`);
  } else {
    messageInstance.warning('请输入有效的经纬度');
  }
  setUpdating(false); // 重置更新状态
}

/**
 * 获取当前用户位置。
 * 使用浏览器地理定位功能获取当前经纬度，并更新地图和搜索框。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Object} searchCoords - 包含 `latitude` 和 `longitude` 的搜索坐标对象。
 * @param {Object} state - 包含 `locating` 的响应式对象。
 * @param {Function} setLocating - 更新 `locating` 状态的回调函数。
 * @param {Function} updateMapLocationFn - 更新地图位置的函数（通常是 `updateMapLocation`）。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 * @param {Function} setSearchCoords - 更新 `searchCoords` 的回调函数。
 */
export function getCurrentLocation(map, searchCoords, state, setLocating, updateMapLocationFn, messageInstance, setSearchCoords) {
  setLocating(true); // 设置定位状态为 true
  messageInstance.info('正在获取当前位置...');

  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const lat = position.coords.latitude;
        const lng = position.coords.longitude;
        // 更新搜索坐标对象
        setSearchCoords({
          latitude: lat.toFixed(6),
          longitude: lng.toFixed(6)
        });
        updateMapLocationFn(); // 更新地图显示当前位置
        setLocating(false); // 重置定位状态
      },
      (error) => {
        console.error('获取位置失败:', error);
        messageInstance.error('获取当前位置失败，请检查浏览器权限设置。');
        setLocating(false); // 重置定位状态
      },
      { enableHighAccuracy: true, timeout: 5000, maximumAge: 0 } // 配置项
    );
  } else {
    messageInstance.error('您的浏览器不支持地理位置功能。');
    setLocating(false); // 重置定位状态
  }
}
