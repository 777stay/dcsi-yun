import L from 'leaflet';
import * as toGeoJSON from '@mapbox/togeojson';

/**
 * 处理 KML 文件上传，并将其内容解析绘制到地图上。
 * 该函数会读取 KML 文件内容，将其转换为 GeoJSON 格式，然后在 Leaflet 地图上渲染。
 * 同时会更新父组件中关于 KML 文件内容和图层状态的响应式数据。
 * @param {File} file - KML 文件对象。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Array} kmlLayers - 存储当前地图上所有 KML 图层的数组。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象 (如 this.$message)。
 * @param {Function} setKmlDir - 用于更新父组件中 `kmldir` 状态的函数。
 * @param {Function} setHasKmlLayers - 用于更新父组件中 `hasKmlLayers` 状态的函数，表示是否有 KML 图层。
 * @param {Function} setKmlPoints - 用于更新父组件中 `kmlPoints` 状态的函数，传递 KML 中的点坐标。
 * @returns {boolean} - 阻止文件上传组件的默认上传行为。
 */
export function handleKmlUpload(file, map, kmlLayers, messageInstance, setKmlDir, setHasKmlLayers, setKmlPoints) {
  const reader = new FileReader();

  reader.onload = async (e) => {
    try {
      // 1. 解析 KML 为 XML 文档
      const parser = new DOMParser();
      const kml = parser.parseFromString(e.target.result, 'text/xml');

      // 2. KML 转 GeoJSON (地图库通用格式)
      const geoJson = toGeoJSON.kml(kml);

      // 提取所有 Point 坐标 (杆塔)
      const kmlPoints = geoJson.features
        .filter(f => f.geometry && f.geometry.type === 'Point')
        .map(f => ({
          long: f.geometry.coordinates[0],
          lat: f.geometry.coordinates[1]
        }));
      
      // 通过回调将 KML 点传递出去
      if (setKmlPoints) {
        setKmlPoints(kmlPoints);
      }

      // 3. 用 Leaflet 渲染 GeoJSON 到地图
      const kmlLayer = L.geoJSON(geoJson, {
        // 重点：处理“点”的样式，替换为 KML 里的靶心图标
        pointToLayer: function (feature, latlng) {
          return L.marker(latlng, {
            icon: L.divIcon({
              html: `
                      <div style="text-align:center;">
                        <img src="${require('@/assets/tower1.png')}" style="width:24px;height:24px;" />
                        <div style="color:red; font-weight:bold; font-size:12px;">${feature.properties.name || ''}</div>
                      </div>
                    `,
              className: 'custom-div-icon', // 自定义类名（可选）
              iconSize: [40, 40], // 图标整体大小（宽高）
              iconAnchor: [20, 20] // 锚点位置（图标中心点对准坐标）
            })
          }).bindPopup(`<b>节点：${feature.properties.name || '未知'}</b>`); // 点击显示节点名称
        },
        // 原有线/面样式 (当前 KML 是“点”，此配置暂不影响)
        style: {
          color: '#3388ff',
          weight: 3,
          opacity: 0.8
        }
      }).addTo(map);

      // 【关键】保存 KML 内容到组件实例，供后端接口使用
      setKmlDir(e.target.result);

      // 管理图层与地图视角
      kmlLayers.push(kmlLayer); // 添加到图层数组
      setHasKmlLayers(true); // 更新状态
      map.fitBounds(kmlLayer.getBounds()); // 调整地图视口以适应 KML 图层

      messageInstance.success('KML文件导入成功');
    } catch (error) {
      // 异常处理：解析失败时提示
      console.error('KML解析错误:', error);
      messageInstance.error('KML文件解析失败');
    }
  };
  reader.readAsText(file);
  return false;
}

/**
 * 清除地图上所有已导入的 KML 图层。
 * 会弹出一个确认对话框，用户确认后才会执行清除操作。
 * 清除后会更新父组件中关于 KML 文件内容和图层状态的响应式数据。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Array} kmlLayers - 存储当前地图上所有 KML 图层的数组。
 * @param {Object} confirmInstance - Vue 实例中的确认对话框对象 (如 this.$confirm)。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象 (如 this.$message)。
 * @param {Function} setKmlDir - 用于更新父组件中 `kmldir` 状态的函数。
 * @param {Function} setHasKmlLayers - 用于更新父组件中 `hasKmlLayers` 状态的函数。
 */
export function clearKmlLayers(map, kmlLayers, confirmInstance, messageInstance, setKmlDir, setHasKmlLayers) {
  confirmInstance('确定要清除所有KML图层吗?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    kmlLayers.forEach(layer => {
      map.removeLayer(layer);
    });
    kmlLayers.splice(0, kmlLayers.length); // 清空数组，而不是重新赋值，保持引用不变
    setHasKmlLayers(false); // 更新状态
    setKmlDir(''); // 清空 KML 内容
    messageInstance.success('已清除所有KML图层');
  }).catch(() => {});
}

/**
 * 处理山火区域 KML 文件上传，解析后在地图上绘制任务区域。
 * 该函数会读取 KML 文件内容，提取其中的点坐标，并以此绘制一个多边形作为任务区域。
 * @param {File} file - KML 文件对象。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 * @param {Function} setMissionLayerPointArr - 用于更新父组件中 `mission_layer_point_arr` 状态的函数。
 * @param {Function} setHasKmlLayers - 用于更新父组件中 `hasKmlLayers` 状态的函数。
 * @param {Object} missionPolygonRef - 对父组件中 `missionPolygon` 的引用，用于清除旧的任务区域多边形。
 * @param {Function} setMissionPolygon - 用于更新父组件中 `missionPolygon` 状态的函数。
 * @returns {boolean} - 阻止文件上传组件的默认上传行为。
 */
export function handleKmlFileUpload(file, map, messageInstance, setMissionLayerPointArr, setHasKmlLayers, missionPolygonRef, setMissionPolygon) {
  const reader = new FileReader();

  reader.onload = () => {
    try {
      readKmlAndDrawMissionArea(reader.result, map, messageInstance, setMissionLayerPointArr, setHasKmlLayers, missionPolygonRef, setMissionPolygon);
    } catch (error) {
      console.error('山火区域KML解析错误:', error);
      messageInstance.error('山火区域KML解析错误');
    } // finally { loading.close(); } // 如果有 loading 动画，可以在这里关闭
  };

  reader.readAsText(file);
  return false; // 阻止自动上传
}

/**
 * 解析 KML 字符串并绘制任务区域。
 * 这是一个内部函数，由 `handleKmlFileUpload` 调用。
 * @param {string} kmlString - KML 文件的字符串内容。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 * @param {Function} setMissionLayerPointArr - 用于更新父组件中 `mission_layer_point_arr` 状态的函数。
 * @param {Function} setHasKmlLayers - 用于更新父组件中 `hasKmlLayers` 状态的函数。
 * @param {Object} missionPolygonRef - 对父组件中 `missionPolygon` 的引用。
 * @param {Function} setMissionPolygon - 用于更新父组件中 `missionPolygon` 状态的函数。
 */
function readKmlAndDrawMissionArea(kmlString, map, messageInstance, setMissionLayerPointArr, setHasKmlLayers, missionPolygonRef, setMissionPolygon) {
  const parser = new DOMParser();
  const kml = parser.parseFromString(kmlString, 'text/xml');
  const geoJson = toGeoJSON.kml(kml);

  // 提取所有 Point 坐标
  let points = geoJson.features
    .filter(f => f.geometry.type === 'Point')
    .map(f => {
      const lon = f.geometry.coordinates[0]; // 经度
      const lat = f.geometry.coordinates[1]; // 纬度
      return [lat, lon]; // 转成 Leaflet 需要的 [lat, lng] 格式
    });

  if (points.length < 3) {
    messageInstance.error('KML 中的点太少，无法构成多边形');
    return;
  }

  // 闭合多边形：如果起点和终点不一致，则将起点添加到末尾以形成闭合多边形
  if (!(points[0][0] === points[points.length-1][0] &&
    points[0][1] === points[points.length-1][1])) {
    points.push(points[0]);
  }

  drawMissionAreaFromKml(points, map, messageInstance, setMissionLayerPointArr, setHasKmlLayers, missionPolygonRef, setMissionPolygon);
}

/**
 * 根据坐标点绘制任务区域多边形。
 * 这是一个内部函数，由 `readKmlAndDrawMissionArea` 调用。
 * @param {Array<Array<number>>} points - 任务区域的坐标点数组，格式为 [[lat, lng], ...]。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 * @param {Function} setMissionLayerPointArr - 用于更新父组件中 `mission_layer_point_arr` 状态的函数。
 * @param {Function} setHasKmlLayers - 用于更新父组件中 `hasKmlLayers` 状态的函数。
 * @param {Object} missionPolygonRef - 对父组件中 `missionPolygon` 的引用。
 * @param {Function} setMissionPolygon - 用于更新父组件中 `missionPolygon` 状态的函数。
 */
function drawMissionAreaFromKml(points, map, messageInstance, setMissionLayerPointArr, setHasKmlLayers, missionPolygonRef, setMissionPolygon) {
  // 清除之前的图层 (如果存在)
  if (missionPolygonRef.value) {
    map.removeLayer(missionPolygonRef.value);
  }

  // 创建并添加多边形到地图
  const newMissionPolygon = L.polygon(points, {
    color: "#4CAF50", // 绿色边框
    fillColor: "#81C784", // 浅绿色填充
    fillOpacity: 0.3, // 填充透明度
    weight: 3 // 边框粗细
  }).addTo(map);

  setMissionPolygon(newMissionPolygon); // 更新 missionPolygon 状态

  // 缩放到多边形，使整个任务区域可见
  map.fitBounds(newMissionPolygon.getBounds());

  // 保存任务区坐标到父组件状态 (注意这里转换回 [long, lat] 格式)
  setMissionLayerPointArr(points.map(point => [point[1], point[0]])); 
  setHasKmlLayers(true); // 更新标志位
  messageInstance.success(`已从 KML 绘制任务区域（${points.length - 1}个顶点）`);
}

/**
 * 清除 KML 文件相关的信息，包括文件名和 KML 内容字符串。
 * @param {Function} setKmlDir - 用于更新父组件中 `kmldir` 状态的函数。
 * @param {Function} setKmlFileName - 用于更新父组件中 `kmlFileName` 状态的函数。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 */
export function clearKmlFile(setKmlDir, setKmlFileName, messageInstance) {
  setKmlDir('');
  setKmlFileName('');
  messageInstance.info('已清除KML文件');
}
