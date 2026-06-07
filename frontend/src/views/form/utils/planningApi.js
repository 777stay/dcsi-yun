import axios from 'axios';
import L from 'leaflet';
import { drawMissionRoutes, fitMapToMissionRoutes, clearMissionPolylines} from './missionPlanner'
import { getBackendBaseUrl } from '@/utils/runtimeApi'

const API_BASE_URL = getBackendBaseUrl();

/**
 * 构建任务规划 API 请求的载荷（payload）。
 * 根据表单数据、任务区域、障碍区域和起点位置等信息，组装成后端所需的请求体。
 * @param {Object} vm - Vue 实例的引用，用于访问 `form`, `mission_layer_point_arr`, `obstacle_layer_point_arr`, `locations` 等数据。
 * @returns {Object} 格式化后的请求载荷对象。
 */
export function buildPayload(vm) {
  const payload = {
    missionId: vm.missionId, // 任务ID
    planMode: vm.form.plan_mode, // 规划模式 (1: 区域模式, 2: 线路模式)
    numberDevice: vm.form.number_device, // 设备数量
    scanDensity: vm.form.scan_density, // 扫描密度
    droneStart: vm.form.drone_start, // 线路模式下起点
    droneEnd: vm.form.drone_end, // 线路模式下终点
    pathsStrictlyInPoly: vm.form.pathsStrictlyInPoly, // 路径严格在多边形内
    flyMode: vm.form.fly_mode, // 飞行模式
    overlapDegree: vm.form.overlap_degree, // 重叠度
    // initialLocations: vm.form.initial_locations.map(loc => {
    //   const [long, lat] = loc.split(',').map(Number); // 解析经纬度字符串
    //   return { long, lat };
    // }),
    initialLocations: vm.form.initial_locations,
    distributionRatios: vm.form.distribution_ratios, // 分布比例
    uavConfigs: vm.form.uav_configs.map((config, index) => ({
      selectedUav: config.selectedUav ? { name: config.selectedUav.name, batteryLength: config.selectedUav.batteryLength } : null, // 假设selectedUav是一个对象，这里取其name和batteryLength
      droneSpeed: config.drone_speed,
      startHeight: config.start_height,
      flightRouteHeight: config.flight_route_height,
      initialLocation: vm.form.initial_locations[index] || null // 为每个飞机添加起始点，如果存在的话
    })),
  };

  // 如果是区域模式，则添加任务区域和障碍区域数据
  if (vm.form.plan_mode === 1) {
    payload.missionLayerPointArr = vm.mission_layer_point_arr; // 任务区域的坐标点数组
    if (vm.obstacle_layer_point_arr.length > 0) {
      payload.obstacleLayerPointArr = vm.obstacle_layer_point_arr; // 障碍区域的坐标点数组
    }
  }

  // 根据规划模式调整 payload
  if (vm.form.plan_mode === 2) {
    payload.kmldir = vm.kmldir; // 线路模式下 KML 文件路径
    delete payload.missionLayerPointArr; // 线路模式不需要任务区域
    delete payload.obstacleLayerPointArr; // 线路模式不需要障碍区域
  }
  
  if(vm.form.plan_mode === 3){
    payload.kmldir = vm.kmldir;
    // 根据 drone_start 和 drone_end 保留 kmlTowerPoints 数组
    const startIndex = parseInt(vm.form.drone_start); // 转换为数字
    const endIndex = parseInt(vm.form.drone_end);     // 转换为数字
    console.log('Debug: vm.kmlTowerPoints', vm.kmlTowerPoints); // DEBUG
    console.log('Debug: typeof vm.kmlTowerPoints', typeof vm.kmlTowerPoints); // DEBUG: Type
    console.log('Debug: startIndex', startIndex); // DEBUG
    console.log('Debug: typeof startIndex', typeof startIndex); // DEBUG: Type
    console.log('Debug: endIndex', endIndex); // DEBUG
    console.log('Debug: typeof endIndex', typeof endIndex); // DEBUG: Type
    if (Array.isArray(vm.kmlTowerPoints) && startIndex >= 0 && endIndex >= startIndex && endIndex < vm.kmlTowerPoints.length) {
      payload.kmlTowerPoints = vm.kmlTowerPoints.slice(startIndex, endIndex + 1); // slice 的 end 是不包含的，所以要 +1
      console.log("payload.kmlTowerPoints",payload.kmlTowerPoints);
    } else {
      // 如果索引无效或 kmlTowerPoints 不是数组，则发送完整的数组或者空数组，具体取决于业务逻辑
      payload.kmlTowerPoints = vm.kmlTowerPoints; // 默认发送完整数组
      console.warn('Invalid drone_start or drone_end indices, or kmlTowerPoints is not an array. Sending full kmlTowerPoints array.');
    }
  }
  return payload;
}

/**
 * 构建 Axios 请求的配置对象。
 * 主要用于设置请求头，特别是认证 token。
 * @param {Object} vm - Vue 实例的引用，用于访问 `this.$store.getters.token`。
 * @returns {Object} Axios 请求配置对象。
 */
export function buildConfig(vm) {
  return {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${vm.$store.getters.token}` // 携带认证 token
    }
  };
}

/**
 * 根据后端返回的数据绘制任务区域、障碍区域和无人机初始位置。
 * @param {Object} data - 后端返回的包含规划结果的数据对象。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Function} clearAllEventListeners - 清理所有事件监听器的函数。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 * @param {Function} setMissionLayerPointArr - 更新任务区域点数组的回调函数。
 * @param {Object} missionPolygonRef - 任务区域多边形图层的响应式引用 (ref)。
 * @param {Function} setMissionPolygon - 更新任务区域多边形图层对象的回调函数。
 * @param {Function} drawMissionRoutesRef - 绘制任务航线的函数。
 * @param {Array<Object>} missionPolylines - 任务航线数组，存储 Leaflet Polyline 对象。
 * @param {Array<Array<Array<number>>>} missionRoutes - 任务路线数据，格式为 List<List<double[]>>。
 * @param {Function} fitMapToMissionRoutesRef - 调整地图以适应任务航线的函数。
 * @param {Object} uav_configs - 无人机配置信息。
 * @param {Array<string>} routeColors - 航线颜色数组。
 * @param {Function} setObstacleLayerPointArr - 更新障碍区域点数组的回调函数。
 * @param {Function} setInitialLocations - 更新初始位置数组的回调函数。
 * @param {Number} planMode - 当前的规划模式。
 */
export function drawMissionByResponse(data, map, clearAllEventListeners, messageInstance, setMissionLayerPointArr, missionPolygonRef, setMissionPolygon, drawMissionRoutesRef, missionPolylines, missionRoutes, fitMapToMissionRoutesRef, uav_configs, routeColors, setObstacleLayerPointArr, setInitialLocations, planMode) { 
  // 将传入参数赋值给局部变量，以避免混淆，并确保在使用前初始化
  const localMap = map;
  const localClearAllEventListeners = clearAllEventListeners;
  const localMessageInstance = messageInstance;
  const localSetMissionLayerPointArr = setMissionLayerPointArr;
  const localMissionPolygonRef = missionPolygonRef;
  const localSetMissionPolygon = setMissionPolygon;
  const localDrawMissionRoutesRef = drawMissionRoutesRef;
  const localMissionPolylines = missionPolylines;
  const localMissionRoutes = missionRoutes;
  const localFitMapToMissionRoutesRef = fitMapToMissionRoutesRef;
  const localUavConfigs = uav_configs;
  const localRouteColors = routeColors;
  const localSetObstacleLayerPointArr = setObstacleLayerPointArr;
  const localSetInitialLocations = setInitialLocations;

  localClearAllEventListeners(); // 清理所有事件监听器，现在 localClearAllEventListeners 已经初始化

  let missionAreaPoints = [];
  let obstacleAreaPoints = [];
  let missionRoutesData = [];
  let initialLocations = [];

  // 根据 data.data 的类型来判断数据结构
  if (Array.isArray(data.data)) {
    // 如果 data.data 是数组，说明是航线数据 (plan 或 planTower 的返回值)
    missionRoutesData = data.data;
    // 在这种情况下，任务区域、障碍区域和初始位置应该为空
    missionAreaPoints = [];
    obstacleAreaPoints = [];
    initialLocations = [];
  } else if (typeof data.data === 'object' && data.data !== null) {
    // 如果 data.data 是对象，说明是任务区域数据 (uploadKML 的返回值)
    missionAreaPoints = data.data.missionLayerPointArr || [];
    obstacleAreaPoints = data.data.obstacleLayerPointArr || [];
    initialLocations = data.data.initialLocations || [];
    // 任务区域响应不应该包含 missionRoutesData
    missionRoutesData = []; 
  }

  // const uavConfigs = uav_configs; // 已作为局部变量 localUavConfigs

  // 绘制任务区域
  if (missionAreaPoints && missionAreaPoints.length > 0) {
    const latlngs = missionAreaPoints.map(coord => L.latLng(coord.lat, coord.long));
    
    // 检查并闭合多边形：如果最后一个点不是首点，则添加首点
    if (latlngs.length > 0) {
      const firstPoint = latlngs[0];
      const lastPoint = latlngs[latlngs.length - 1];
      if (firstPoint.lat !== lastPoint.lat || firstPoint.lng !== lastPoint.lng) {
        latlngs.push(firstPoint); // 添加首点以闭合多边形
      }
    }

    // 移除旧的任务区域图层（如果存在）
    if (localMissionPolygonRef) { 
      localMap.removeLayer(localMissionPolygonRef); 
    }
    const polygon = L.polygon(latlngs, {
      color: planMode === 3 ? "rgba(204, 204, 204, 0.1)" : "#4CAF50", // 沿塔模式下使用几乎透明的灰色边框
      fillColor: planMode === 3 ? "#F0F0F0" : "#81C784", // 沿塔模式下使用非常淡的填充色
      fillOpacity: planMode === 3 ? 0.0 : 0.3, // 沿塔模式下透明填充，但保持边界可见
      weight: 3
    }).addTo(localMap);
    localSetMissionPolygon(polygon); 
    // 保持与index.vue中的mission_layer_point_arr格式一致，需要转换为[long, lat]的数组
    localSetMissionLayerPointArr(missionAreaPoints.map(p => [p.long, p.lat]));
    localMap.fitBounds(polygon.getBounds()); 
  }

  // // 绘制障碍区域
  // if (obstacleAreaPoints && obstacleAreaPoints.length > 0) {
  //   // 障碍区域返回的是一个数组，每个元素是一个多边形，这里假设只有一个多边形且直接使用其内部的lat/long对象
  //   // 这里的 obstacleAreaPoints 可能是 List<List<Map<String, Double>>> 类型，需要调整
  //   // 假设是 List<Map<String, Double>>
  //   obstacleAreaPoints.forEach(singleObstaclePoints => {
  //     const latlngs = singleObstaclePoints.map(coord => L.latLng(coord.lat, coord.long));
  //     // 检查并闭合多边形
  //     if (latlngs.length > 0) {
  //       const firstPoint = latlngs[0];
  //       const lastPoint = latlngs[latlngs.length - 1];
  //       if (firstPoint.lat !== lastPoint.lat || firstPoint.lng !== lastPoint.lng) {
  //         latlngs.push(firstPoint); 
  //       }
  //     }
  //     L.polygon(latlngs, {
  //       color: "#F44336", 
  //       fillColor: "#EF5350", 
  //       fillOpacity: 0.4,
  //       weight: 3
  //     }).addTo(localMap);
  //     // 更新障碍区域点数组，保持格式一致
  //     localSetObstacleLayerPointArr(singleObstaclePoints.map(p => [p.long, p.lat])); 
  //   });
  // }

  // 绘制航线
  if (missionRoutesData && missionRoutesData.length > 0) {
    drawMissionRoutesOnMap(
      missionRoutesData,
      localUavConfigs, 
      localRouteColors,
      localMap,
      localMissionPolylines, 
      localMissionRoutes, // 直接传递 missionRoutes 数组
      localFitMapToMissionRoutesRef,
      localMessageInstance
    );
  }

  // 线路模式起点由前端手动绘制，忽略后端回传的 initialLocations
  if (planMode === 2) {
    initialLocations = [];
  }

  // 绘制无人机初始位置
  if (initialLocations && initialLocations.length) {
    const initialLocationsStrings = initialLocations.map(loc => `${loc.long},${loc.lat}`);
    localSetInitialLocations(initialLocationsStrings); 

    initialLocations.forEach((loc, idx) => {
      const marker = L.marker([loc.lat, loc.long], {
        icon: L.divIcon({
          className: 'custom-div-icon',
          html: `
            <div style="text-align:center;">
              <img src="${require('@/assets/drone.png')}" style="width:24px;height:24px;" />
              <div style="color:red; font-weight:bold; font-size:12px;">drone${idx}</div>
            </div>
          `,
          iconSize: [16, 16],
          iconAnchor: [8, 8]
        })
      }).addTo(localMap);
      marker.bindPopup(`<b>无人机 ${idx + 1}</b><br>初始位置`);
    });
  }
}

/**
 * 启动任务规划。
 * 调用后端 API 进行任务规划，并在成功后绘制航线和显示规划结果。
 * @param {Object} vm - Vue 实例的引用，用于访问数据、方法和消息提示。
 */
export async function startMissionPlanner(vm) {
  if (vm.planningInProgress) {
    vm.$message.warning('规划进行中，请勿重复操作');
    return;
  }

  // 验证模式1的必要条件：任务区已绘制且分配比例总和为100
  if (vm.form.plan_mode === 1) {
    if (vm.mission_layer_point_arr.length === 0) {
      vm.$message.error('请先绘制任务区域！');
      return;
    }
    if (vm.getRatioSum() !== 100) {
      vm.$message.error('无人机任务分配比例总和必须为100%');
      return;
    }
  }

  // 验证模式2的必要条件：KML 文件已上传
  if (vm.form.plan_mode === 2 && !vm.kmldir) {
    vm.$message.error('请先上传KML文件！');
    return;
  }

  if (vm.form.plan_mode === 2) {
    if (!vm.locations || vm.locations.length === 0) {
      vm.$message.error('线路模式请先手动添加起点！');
      return;
    }
    if (vm.form.number_device !== vm.locations.length) {
      vm.$message.error('设备数量需与手动起点数量一致！');
      return;
    }
    if (vm.getRatioSum() !== 100) {
      vm.$message.error('无人机任务分配比例总和必须为100%');
      return;
    }
  }

  vm.planningInProgress = true; // 设置规划进行中状态
  vm.missionRoutes = []; // 清空之前的航线
  vm.clearAllEventListeners(); // 清理地图上的绘制事件监听器
  clearMissionPolylines(vm.map, vm.missionPolylines); // 清除旧航线，移除多余回调

  try {
    vm.$message.info('开始任务规划...'); // 提示用户
    const payload = buildPayload(vm); // 构建请求载荷
    const config = buildConfig(vm); // 构建请求配置

    // 调用后端 API
    const response = await axios.post(`${API_BASE_URL}/api/mission/plan`, payload, config); // 使用API_BASE_URL拼接路径

    if (response.data.code === 200) {
      vm.$message.success('任务规划成功！'); // 提示成功
      // 根据后端返回数据绘制航线
      console.log('开始调用 drawMissionByResponse...'); // 添加日志
      drawMissionByResponse(
        response.data,
        vm.map,
        vm.clearAllEventListeners,
        vm.$message,
        (arr) => vm.mission_layer_point_arr = arr,
        vm.missionPolygon,
        (layer) => vm.missionPolygon = layer,
        vm.drawMissionRoutes,
        vm.missionPolylines,
        vm.missionRoutes,
        vm.fitMapToMissionRoutes,
        vm.form.uav_configs,
        vm.routeColors,
        (arr) => vm.obstacle_layer_point_arr = arr,
        (arr) => vm.form.initial_locations = arr,
        vm.form.plan_mode // 传递规划模式
      ); 
      console.log('drawMissionByResponse 调用完成。'); // 添加日志

      // vm.bindMissionAreaEventsAfterPlanning(); // 绑定任务区域点击事件
      vm.mission_planner_form = false; // 关闭规划抽屉
    } else {
      vm.$message.error('任务规划失败: ' + response.data.message);
    }
  } catch (error) {
    console.error('任务规划请求失败:', error);
    vm.$message.error('任务规划请求失败，请检查网络或联系管理员。');
  } finally {
    vm.planningInProgress = false; // 无论成功失败，都重置规划进行中状态
  }
}

/**
 * 通过 KML 文件生成任务区域。
 * 调用后端 API 解析 KML 文件，并根据返回数据绘制任务区域和障碍区域。
 * @param {Object} vm - Vue 实例的引用，用于访问数据、方法和消息提示。
 */
export async function generateMissionAreaByKml(vm) {
  if (!vm.kmldir) {
    vm.$message.error('请先上传KML文件！');
    return;
  }

  vm.clearAllEventListeners(); // 清理事件监听器
  clearMissionPolylines(vm.map, vm.missionPolylines, (val) => vm.missionPolylines = val); // 清除旧航线

  try {
    vm.$message.info('正在解析KML文件并生成任务区域...'); // 提示用户
    const payload = {
      missionId: vm.missionId,
      kmldir: vm.kmldir,
      numberDevice: vm.form.number_device,
      scanDensity: vm.form.scan_density,
      planMode: vm.form.plan_mode,
      droneStart: vm.form.drone_start,
      droneEnd: vm.form.drone_end,
      droneSpeed: vm.form.drone_speed,
      pathsStrictlyInPoly: vm.form.pathsStrictlyInPoly,
      distributionRatios: vm.form.distribution_ratios,
      selectUavs: vm.form.uav_configs.map(config => ({
        selectedUav: config.selectedUav ? { // Reconstruct the selectedUav object
          name: config.selectedUav.name,
          batteryLength: config.selectedUav.batteryLength
        } : null,
        drone_speed: config.drone_speed,
        start_height: config.start_height,
        flight_route_height: config.flight_route_height
      })).slice(0, vm.form.number_device)
    };
    const config = buildConfig(vm);

    // 调用后端 API
    const response = await axios.post(`${API_BASE_URL}/api/mission/uploadKML`, payload, config); // 使用API_BASE_URL拼接路径

    console.log('KML任务区域生成后端响应:', response.data); // 添加日志

    if (response.data.code === 200) {
      vm.$message.success('KML任务区域生成成功！'); // 提示成功
      // 根据后端返回数据绘制任务区域和障碍区域
      drawMissionByResponse(
        response.data,
        vm.map,
        vm.clearAllEventListeners,
        vm.$message,
        (arr) => vm.mission_layer_point_arr = arr,
        vm.missionPolygon,
        (layer) => vm.missionPolygon = layer,
        vm.drawMissionRoutes,
        vm.missionPolylines,
        vm.missionRoutes,
        vm.fitMapToMissionRoutes,
        vm.form.uav_configs,
        vm.routeColors,
        (arr) => vm.obstacle_layer_point_arr = arr,
        (arr) => vm.form.initial_locations = arr,
        vm.form.plan_mode // 传递规划模式
      );
    } else {
      vm.$message.error('KML任务区域生成失败: ' + response.data.message);
    }
  } catch (error) {
    console.error('KML任务区域生成请求失败:', error); // 打印详细错误
    vm.$message.error('KML任务区域生成请求失败，请检查网络或联系管理员。');
  }
}

/**
 * 在地图上绘制任务航线并调整地图视口。
 * @param {Array<Array<Array<number>>>} missionRoutesData - 航线数据，格式为 List<List<double[]>>。
 * @param {Object} uavConfigs - 无人机配置信息。
 * @param {Array<string>} routeColors - 航线颜色数组。
 * @param {Object} map - Leaflet 地图实例。
 * @param {Function} setMissionPolylines - 更新任务航线数组的回调函数。
 * @param {Function} setMissionRoutes - 更新任务路线数组的回调函数。
 * @param {Function} fitMapToMissionRoutesRef - 调整地图以适应任务航线的函数。
 * @param {Object} messageInstance - Vue 实例中的消息提示对象。
 */
export function drawMissionRoutesOnMap(missionRoutesData, uavConfigs, routeColors, map, missionPolylines, missionRoutes, fitMapToMissionRoutesRef, messageInstance) { // 将 fitMapToMissionRoutesFn 更改为 fitMapToMissionRoutesRef
  if (missionRoutesData && missionRoutesData.length > 0) {
    drawMissionRoutes(
      missionRoutesData,
      uavConfigs, 
      routeColors,
      map,
      missionPolylines, 
      missionRoutes // 直接传递 missionRoutes 数组
    );
    // fitMapToMissionRoutesRef(missionRoutesData, map); 
    messageInstance.success('航线已绘制');
  }
}

/**
 * 启动沿塔模式任务规划。
 * 调用后端 API 进行沿塔模式任务规划，并在成功后绘制航线和显示规划结果。
 * @param {Object} vm - Vue 实例的引用，用于访问数据、方法和消息提示。
 */
export async function startTowerPlanning(vm) {
  if (vm.planningInProgress) {
    vm.$message.warning('规划进行中，请勿重复操作');
    return;
  }

  // 验证沿塔模式的必要条件：KML 文件已上传且有杆塔信息
  if (!vm.kmldir) {
    vm.$message.error('请先上传KML文件！');
    return;
  }
  if (!vm.kmlTowerPoints || vm.kmlTowerPoints.length === 0) {
    vm.$message.error('KML文件中未解析到杆塔坐标，请检查文件内容！');
    return;
  }

  vm.planningInProgress = true; // 设置规划进行中状态
  vm.missionRoutes = []; // 清空之前的航线
  vm.clearAllEventListeners(); // 清理地图上的绘制事件监听器
  clearMissionPolylines(vm.map, vm.missionPolylines); // 清除旧航线，移除多余回调

  try {
    vm.$message.info('开始沿塔模式任务规划...'); // 提示用户
    const payload = buildPayload(vm); // 构建请求载荷，此处复用buildPayload
    const config = buildConfig(vm); // 构建请求配置

    // 调用后端 API (假设沿塔模式有独立的API接口)
    const response = await axios.post(`${API_BASE_URL}/api/mission/planTower`, payload, config); // 使用新的API接口

    if (response.data.code === 200) {
      vm.$message.success('沿塔模式任务规划成功！'); // 提示成功
      // 根据后端返回数据绘制航线
      drawMissionByResponse(response.data, vm.map, vm.clearAllEventListeners, vm.$message, (arr) => vm.mission_layer_point_arr = arr, vm.missionPolygon, (layer) => vm.missionPolygon = layer, vm.drawMissionRoutes, vm.missionPolylines, vm.missionRoutes, vm.fitMapToMissionRoutes, vm.form.uav_configs, vm.routeColors, (arr) => vm.obstacle_layer_point_arr = arr, (arr) => vm.form.initial_locations = arr, vm.form.plan_mode);      
      // vm.bindMissionAreaEventsAfterPlanning(); // 绑定任务区域点击事件
      vm.mission_planner_form = false; // 关闭规划抽屉
    } else {
      vm.$message.error('沿塔模式任务规划失败: ' + response.data.message);
    }
  } catch (error) {
    console.error('沿塔模式任务规划请求失败:', error); 
    vm.$message.error('沿塔模式任务规划请求失败，请检查网络或联系管理员。');
  } finally {
    vm.planningInProgress = false; // 无论成功失败，都重置规划进行中状态
  }
}

/**
 * 从后端获取推荐起点列表。
 * 返回格式约定为: [{ name: string, model: string, long: number, lat: number }, ...]
 * @param {Object} vm - Vue 实例引用。
 * @returns {Promise<Array<{name:string,model:string,long:number,lat:number}>>}
 */
export async function fetchStartingPointsFromBackend(vm) {
  const payload = {
    
  };
  const config = buildConfig(vm);
  const response = await axios.post(`${API_BASE_URL}/api/mission/getStartingPoints`, payload, config);

  if (response.data.code !== 200) {
    throw new Error(response.data.message || '获取起点失败');
  }

  const rawPoints = response.data.data || [];
  if (!Array.isArray(rawPoints)) {
    return [];
  }

  const normalizeModel = (model) => {
    if (!model) return '';
    if (model === '无人机M400' || model === 'M400' || model.toUpperCase() === 'M400') {
      return 'Matrice_400';
    }
    if (model === 'Matrice_400') {
      return model;
    }
    return model;
  };

  return rawPoints.map((p, index) => {
    const name = p.name || p.robotName || p.robotId || `robot_${String(index + 1).padStart(2, '0')}`;
    const model = normalizeModel(p.model || p.robotModel || p.type || '');
    const long = Number(p.long ?? p.lng ?? p.longitude ?? p.lon);
    const lat = Number(p.lat ?? p.latitude ?? p.latitute);
    return { name, model, long, lat };
  }).filter(p => Number.isFinite(p.long) && Number.isFinite(p.lat));
}
