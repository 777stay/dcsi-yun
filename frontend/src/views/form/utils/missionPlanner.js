import L from 'leaflet'; // 假设你全局引入了 Leaflet，否则需要 import

/**
 * 计算经纬度距离（米）
 */
export function calcDistance(p1, p2) {
  const point1 = L.latLng(p1[1], p1[0]);
  const point2 = L.latLng(p2[1], p2[0]);
  return point1.distanceTo(point2);
}

/**
 * 绘制单条折线
 */
export function drawPolyline(points, colorIndex, routeColors, map, missionPolylines) {
  const polyline = L.polyline(points, {
    color: routeColors[colorIndex % routeColors.length],
    weight: 4,
    opacity: 0.8,
    dashArray: colorIndex === 0 ? null : '10, 10'
  }).addTo(map);

  points.forEach((point, idx) => {
    if (idx % 5 === 0) {
      L.circleMarker(point, {
        radius: 4,
        fillColor: routeColors[colorIndex % routeColors.length],
        color: '#fff',
        weight: 2,
        opacity: 1,
        fillOpacity: 0.8
      })
        .addTo(map)
        .bindPopup(`航点${idx + 1}`);
    }
  });

  missionPolylines.push(polyline);
}

/**
 * 绘制所有航线（支持按航程分段换色）
 */
export function drawMissionRoutes(missionData, uavType,routeColors, map, missionPolylines, missionRoutes) {
  missionData.forEach((route, routeIndex) => {
    if (!route || !Array.isArray(route) || route.length <= 1) return;

    // 每条航线取自己的最大航程
    let maxDistance = 500; // 默认500米
    if (uavType && uavType[routeIndex] && uavType[routeIndex].selectedUav && uavType[routeIndex].selectedUav.batteryLength) {
        maxDistance = uavType[routeIndex].selectedUav.batteryLength;
    }
    console.log(maxDistance);
    let sumDistance = 0;
    let currentRoute = [route[0]];
    let colorIndex = routeIndex;

    for (let i = 1; i < route.length; i++) {
      const p1 = route[i - 1];
      const p2 = route[i];
      const dist = calcDistance(p1, p2);

      if (sumDistance + dist > maxDistance) {
        drawPolyline(currentRoute, colorIndex, routeColors, map, missionPolylines);
        currentRoute = [p1, p2];
        sumDistance = dist;
        colorIndex = (colorIndex + 1) % routeColors.length;
      } else {
        currentRoute.push(p2);
        sumDistance += dist;
      }

    }
    console.log(sumDistance);

    if (currentRoute.length > 1) {
      drawPolyline(currentRoute, colorIndex, routeColors, map, missionPolylines);
    }

    missionRoutes.push(route);
  });
}

/**
 * 地图自适应所有航线
 */
export function fitMapToMissionRoutes(missionRoutes, map) {
  const allPoints = [];
  missionRoutes.forEach(route => {
    route.forEach(coord => {
      allPoints.push([coord[0], coord[1]]);
    });
  });
  if (allPoints.length > 0) {
    const bounds = L.latLngBounds(allPoints);
    map.fitBounds(bounds, { padding: [50, 50] });
  }
}

/**
 * 清除地图上的航线
 */
export function clearMissionPolylines(map, missionPolylines) {
  missionPolylines.forEach(polyline => {
    map.removeLayer(polyline);
  });
  missionPolylines.length = 0;
}


