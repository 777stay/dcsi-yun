import L from 'leaflet';

/**
 * 清理所有地图相关的事件监听器和绘制模式。
 * 此函数会关闭所有正在进行的绘图模式，并清除之前设置的点击和创建事件监听器，重置当前工具状态。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Object} state - 包含 `clickListener`, `createListener`, `currentTool` 的响应式对象。
 * @param {Function} clearClickListener - 用于清除 `clickListener` 的回调函数。
 * @param {Function} clearCreateListener - 用于清除 `createListener` 的回调函数。
 * @param {Function} setCurrentTool - 用于更新 `currentTool` 状态的回调函数。
 */
export function clearAllDrawingEventListeners(map, state, clearClickListener, clearCreateListener, setCurrentTool) {
  // 清理点击监听器
  if (state.clickListener) {
    map.off('click', state.clickListener);
    clearClickListener(null);
  }

  // 清理创建监听器 (pm:create 事件)
  if (state.createListener) {
    map.off('pm:create', state.createListener);
    clearCreateListener(null);
  }

  // 禁用所有 Leaflet.PM 绘制模式
  map.pm.disableDraw();

  // 重置当前工具状态
  setCurrentTool('');
}

/**
 * 在地图上启动绘制任务区域的功能。
 * 此函数会先清理所有现有事件监听器，然后启用多边形绘制模式，并设置一个 `pm:create` 监听器来捕获绘制完成的区域。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Function} clearAllEventListenersFn - 清理所有事件监听器的函数（通常是 `clearAllDrawingEventListeners`）。
 * @param {Function} setCurrentTool - 更新当前工具状态的回调函数。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象 (如 this.$message)。
 * @param {Function} setMissionLayerPointArr - 更新任务区域点数组的回调函数。
 * @param {Function} setMissionPolygon - 更新任务区域多边形图层对象的回调函数。
 * @param {Function} setLatLngjson - 更新原始 LatLng 数组的回调函数。
 * @param {Function} setCreateListener - 更新创建事件监听器引用的回调函数。
 */
export function drawMissionArea(map, clearAllEventListenersFn, setCurrentTool, messageInstance, setMissionLayerPointArr, setMissionPolygon, setLatLngjson, setCreateListener) {
  clearAllEventListenersFn(); // 清理之前的任何绘制模式和监听器

  setCurrentTool('mission_area'); // 设置当前工具为任务区域绘制
  messageInstance.info('请在地图上绘制任务区域'); // 提示用户操作

  // 配置多边形绘制样式
  const polygonOptions = {
    pathOptions: {
      color: "#4CAF50", // 边框颜色 (绿色)
      fillColor: "#81C784", // 填充颜色 (浅绿色)
      fillOpacity: 0.3, // 填充透明度
      weight: 3, // 边框粗细
      dashArray: '10, 10' // 设置为虚线
    },
  };

  map.pm.enableDraw("Polygon", polygonOptions); // 启用多边形绘制模式

  // 定义 `pm:create` 事件监听器
  const newCreateListener = (e) => {
    let target = [];
    // 提取绘制的多边形或矩形的经纬度坐标
    if (e.shape === "Polygon" || e.shape === "Rectangle") {
      map.fitBounds(e.layer._latlngs); // 调整地图视口以适应绘制区域
      setLatLngjson(e.layer._latlngs[0]); // 保存原始的 LatLng 数组
      for (let i = 0; i < e.layer._latlngs[0].length; i++) {
        let arr = [e.layer._latlngs[0][i].lng, e.layer._latlngs[0][i].lat];
        target.push(arr); // 将经纬度对转换为 [lng, lat] 格式
      }
    }
    setMissionLayerPointArr(target); // 更新任务区域点数组
    setMissionPolygon(e.layer); // 保存任务区域的 Leaflet 图层对象
    // 将绘制完成的图层设置为实线
    e.layer.setStyle({ dashArray: null });
    messageInstance.success('任务区域绘制完成'); // 提示绘制完成
    clearAllEventListenersFn(); // 清理事件监听器并禁用绘制模式
  };

  map.on("pm:create", newCreateListener); // 绑定 `pm:create` 事件
  setCreateListener(newCreateListener); // 保存监听器引用以便后续清理
}

/**
 * 在地图上启动绘制障碍区域的功能。
 * 功能与 `drawMissionArea` 类似，但使用不同的颜色样式表示障碍区域。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Function} clearAllEventListenersFn - 清理所有事件监听器的函数。
 * @param {Function} setCurrentTool - 更新当前工具状态的回调函数。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 * @param {Function} setObstacleLayerPointArr - 更新障碍区域点数组的回调函数。
 * @param {Function} setLatLngjson - 更新原始 LatLng 数组的回调函数。
 * @param {Function} setCreateListener - 更新创建事件监听器引用的回调函数。
 */
export function drawObstacleArea(map, clearAllEventListenersFn, setCurrentTool, messageInstance, setObstacleLayerPointArr, setLatLngjson, setCreateListener) {
  clearAllEventListenersFn(); // 清理之前的任何绘制模式和监听器

  setCurrentTool('obstacle_area'); // 设置当前工具为障碍区域绘制
  messageInstance.info('请在地图上绘制障碍区域'); // 提示用户操作

  // 配置多边形绘制样式 (红色)
  const polygonOptions = {
    pathOptions: {
      color: "#F44336", // 边框颜色 (红色)
      fillColor: "#EF5350", // 填充颜色 (浅红色)
      fillOpacity: 0.4, // 填充透明度
      weight: 3, // 边框粗细
      dashArray: '10, 10' // 设置为虚线
    },
  };

  map.pm.enableDraw("Polygon", polygonOptions); // 启用多边形绘制模式

  // 定义 `pm:create` 事件监听器
  const newCreateListener = (e) => {
    let target = [];
    // 提取绘制的多边形或矩形的经纬度坐标
    if (e.shape === "Polygon" || e.shape === "Rectangle") {
      setLatLngjson(e.layer._latlngs[0]); // 保存原始的 LatLng 数组
      for (let i = 0; i < e.layer._latlngs[0].length; i++) {
        let arr = [e.layer._latlngs[0][i].lng, e.layer._latlngs[0][i].lat];
        target.push(arr); // 将经纬度对转换为 [lng, lat] 格式
      }
    }
    setObstacleLayerPointArr(target); // 更新障碍区域点数组
    // 将绘制完成的图层设置为实线
    e.layer.setStyle({ dashArray: null });
    messageInstance.warning('障碍区域绘制完成'); // 提示绘制完成
    clearAllEventListenersFn(); // 清理事件监听器并禁用绘制模式
  };

  map.on("pm:create", newCreateListener); // 绑定 `pm:create` 事件
  setCreateListener(newCreateListener); // 保存监听器引用以便后续清理
}

/**
 * 在地图上添加起点。
 * 用户可以通过点击地图添加起点，最多支持5个起点。每个起点都会在地图上显示标记，并记录其经纬度。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Function} clearAllEventListenersFn - 清理所有事件监听器的函数。
 * @param {Function} setCurrentTool - 更新当前工具状态的回调函数。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 * @param {Function} getLocations - 获取当前起点经纬度字符串数组的回调函数。
 * @param {Function} setLocations - 更新 `locations` 数组的回调函数。
 * @param {Function} updateDistributionRatiosFn - 更新任务分配比例的回调函数。
 * @param {Object} form - 包含 `initial_locations` 和 `number_device` 的表单对象。
 * @param {Array} startingPointMarkers - 存储起点 Leaflet 标记对象的数组。
 * @param {Function} setStartingPointMarkers - 更新 `startingPointMarkers` 数组的回调函数。
 * @param {Function} setClickListener - 更新点击事件监听器引用的回调函数。
 */
export function addStartingPoint(map, clearAllEventListenersFn, setCurrentTool, messageInstance, getLocations, setLocations, updateDistributionRatiosFn, form, startingPointMarkers, setStartingPointMarkers, setClickListener, maxPoints = 5) {
  clearAllEventListenersFn(); // 清理之前的任何绘制模式和监听器

  setCurrentTool('starting_point'); // 设置当前工具为添加起点
  messageInstance.info(`请在地图上点击添加起点（最多${maxPoints}个）`); // 提示用户操作

  const newClickListener = (e) => {
    const currentLocations = getLocations();
    // 如果达到上限，直接返回
    if (currentLocations.length >= maxPoints) {
      messageInstance.warning(`最多只能添加 ${maxPoints} 个起点`);
      console.log('Max points reached:', currentLocations.length); // DEBUG
      return;
    }

    const lngLat = `${e.latlng.lng.toFixed(6)},${e.latlng.lat.toFixed(6)}`;
    console.log('Clicked at:', lngLat); // DEBUG

    // 添加到数组
    const updatedLocations = [...currentLocations, lngLat];
    setLocations(updatedLocations);
    messageInstance.success(`已添加起点${updatedLocations.length}`);
    updateDistributionRatiosFn(updatedLocations.length);
    console.log('Updated locations:', updatedLocations); // DEBUG

    // 同步表单数据
    form.initial_locations = updatedLocations;
    form.number_device = updatedLocations.length; // 更新设备数量
    form.distribution_ratios = Array.from({ length: updatedLocations.length }, (_, i) => form.distribution_ratios[i] || 0); // 确保比例数组长度匹配
    console.log('Form after update:', form); // DEBUG

    // 创建 Leaflet 图标
    const startIcon = L.icon({
      iconUrl: require('leaflet/dist/images/marker-icon-2x.png'),
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
      shadowUrl: require('leaflet/dist/images/marker-shadow.png'),
      shadowSize: [41, 41]
    });

    // 在地图上添加标记
    const marker = L.marker([e.latlng.lat, e.latlng.lng], { icon: startIcon })
      .addTo(map)
      .bindPopup(`起点${updatedLocations.length}: ${lngLat}`);

    // 存储标记用于后续清理
    setStartingPointMarkers([...startingPointMarkers, {
      marker: marker,
      index: updatedLocations.length - 1,
      locationKey: `location${updatedLocations.length}`
    }]);

    // 如果达到最大数量，自动退出添加模式
    if (updatedLocations.length >= maxPoints) {
      clearAllEventListenersFn();
      messageInstance.info(`已达到最大起点数量 (${maxPoints})，退出添加模式`);
    }
  };

  map.on('click', newClickListener); // 绑定地图点击事件
  setClickListener(newClickListener); // 保存监听器引用以便后续清理
}

/**
 * 取消当前正在进行的添加起点操作，并清理相关状态。
 * @param {Function} clearAllEventListenersFn - 清理所有事件监听器的函数。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 */
export function cancelStartingPoint(clearAllEventListenersFn, messageInstance) {
  clearAllEventListenersFn(); // 清理事件监听器和绘制模式
  messageInstance.info('已取消添加起点'); // 提示用户
}

/**
 * 清除指定索引的起点位置和对应的地图标记。
 * @param {number} index - 要清除的位置的索引（0-based）。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Array} locations - 存储起点经纬度字符串的数组。
 * @param {Function} setLocations - 更新 `locations` 数组的回调函数。
 * @param {Array} startingPointMarkers - 存储起点 Leaflet 标记对象的数组。
 * @param {Function} setStartingPointMarkers - 更新 `startingPointMarkers` 数组的回调函数。
 * @param {Object} form - 包含 `initial_locations` 和 `number_device` 的表单对象。
 * @param {Function} updateDistributionRatiosFn - 更新任务分配比例的回调函数。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 */
export function clearLocation(index, map, locations, setLocations, startingPointMarkers, setStartingPointMarkers, form, updateDistributionRatiosFn, messageInstance) {
  const newLocations = [...locations];
  newLocations.splice(index, 1); // 从数组中移除指定位置
  setLocations(newLocations); // 更新 `locations` 状态

  // 找到并移除对应的地图标记
  const markerObj = startingPointMarkers.find(m => m.index === index);
  if (markerObj) {
    map.removeLayer(markerObj.marker);
  }

  // 过滤掉已删除的标记，并重新索引剩余的标记
  const newStartingPointMarkers = startingPointMarkers.filter(m => m.index !== index);
  newStartingPointMarkers.forEach((m, i) => {
    m.index = i; // 更新索引
    const latlng = m.marker.getLatLng();
    m.marker.bindPopup(`起点${i + 1}: ${latlng.lng.toFixed(6)},${latlng.lat.toFixed(6)}`); // 更新弹出窗口内容
  });
  setStartingPointMarkers(newStartingPointMarkers); // 更新 `startingPointMarkers` 状态

  // 同步更新表单数据
  form.initial_locations = [...newLocations];
  form.number_device = newLocations.length;
  updateDistributionRatiosFn(newLocations.length);

  messageInstance.info(`已清除位置${index + 1}`); // 提示用户
}

/**
 * 清除地图上所有绘制的图层和相关数据，包括任务区、障碍区、起点标记和航线。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Function} clearAllEventListenersFn - 清理所有事件监听器的函数。
 * @param {Array} startingPointMarkers - 存储起点标记的数组。
 * @param {Function} setStartingPointMarkers - 更新 `startingPointMarkers` 数组的回调函数。
 * @param {Array} missionPolylines - 存储航线的数组。
 * @param {Function} setMissionPolylines - 更新 `missionPolylines` 数组的回调函数。
 * @param {Array} missionRoutes - 存储任务路线的数组。
 * @param {Function} setMissionRoutes - 更新 `missionRoutes` 数组的回调函数。
 * @param {Function} setLatLngjson - 更新 `LatLngjson` 状态的回调函数。
 * @param {Function} setMissionLayerPointArr - 更新任务区域点数组的回调函数。
 * @param {Function} setObstacleLayerPointArr - 更新障碍区域点数组的回调函数。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 * @param {Function} setLocations - 更新 `locations` 数组的回调函数。
 * @param {Function} setMissionPolygon - 更新 `missionPolygon` 的回调函数。
 * @param {Function} setCurrentMarker - 更新当前位置 marker 的回调函数。
 * @param {Function} setKmlLayers - 更新 `kmlLayers` 数组的回调函数。
 * @param {Function} setHasKmlLayers - 更新 KML 图层存在状态的回调函数。
 * @param {Function} setKmlDir - 更新 `kmldir` 的回调函数。
 * @param {Function} setKmlFileName - 更新 `kmlFileName` 的回调函数。
 * @param {Function} setKmlTowerPoints - 更新 `kmlTowerPoints` 的回调函数。
 */
export function clearAllDrawings(
  map,
  clearAllEventListenersFn,
  startingPointMarkers,
  setStartingPointMarkers,
  missionPolylines,
  setMissionPolylines,
  missionRoutes,
  setMissionRoutes,
  setLatLngjson,
  setMissionLayerPointArr,
  setObstacleLayerPointArr,
  messageInstance,
  setLocations,
  setMissionPolygon,
  setCurrentMarker,
  setKmlLayers,
  setHasKmlLayers,
  setKmlDir,
  setKmlFileName,
  setKmlTowerPoints
) {
  clearAllEventListenersFn(); // 清理所有事件监听器和绘制模式

  // 保留底图图层，移除其余所有覆盖层（任务区/障碍区/航线/点位/KML 等）
  const overlayLayers = [];
  map.eachLayer((layer) => {
    if (!(layer instanceof L.TileLayer)) {
      overlayLayers.push(layer);
    }
  });
  overlayLayers.forEach((layer) => map.removeLayer(layer));

  // 移除所有起点标记
  startingPointMarkers.forEach(item => {
    map.removeLayer(item.marker);
  });
  setStartingPointMarkers([]); // 清空起点标记数组

  // 移除所有航线图层
  missionPolylines.forEach(polyline => {
    map.removeLayer(polyline);
  });
  setMissionPolylines([]); // 清空航线数组
  setMissionRoutes([]); // 清空任务路线数据

  // 重置相关数据状态
  setLatLngjson([]);
  setMissionLayerPointArr([]);
  setObstacleLayerPointArr([]);
  setLocations([]); // 清空 locations 数组
  setMissionPolygon(null);
  setCurrentMarker(null);
  setKmlLayers([]);
  setHasKmlLayers(false);
  setKmlDir('');
  setKmlFileName('');
  setKmlTowerPoints([]);

  messageInstance.success('已清除所有绘制与覆盖图层'); // 提示用户
}
