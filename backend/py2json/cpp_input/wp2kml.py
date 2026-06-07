# C:\Users\WHU_DCSI\Desktop\CNU-UAV(new)\server\bin\cpp_waypoint.txt
import re
import math
import os
# -*- coding: utf-8 -*-
from osgeo import gdal
# lsq
import sys

import numpy as np
from pyproj import Transformer
import sys
import json
import ast

# lsq
# 检查routesXY中的点是否在DEM范围内
def check_routes_in_dem(routesXY, dem_lat, dem_lon):
    """
    检查航线点是否在DEM范围内
    """
    # 获取DEM的经纬度范围
    dem_lat_min = np.min(dem_lat)
    dem_lat_max = np.max(dem_lat)
    dem_lon_min = np.min(dem_lon)
    dem_lon_max = np.max(dem_lon)

    print(dem_lat_min,dem_lat_max,dem_lon_min,dem_lon_max)

    # 检查每个航线的每个点
    for route in routesXY:
        for point in route:
            lon, lat = point  # 注意这里是经度,纬度
            # 坐标转换UTM49->WGS84
            transformer = Transformer.from_crs("epsg:4326", "epsg:32649")
            lat_utm, lon_utm = transformer.transform(lat, lon)

            # 检查点是否在DEM范围内
            if (lon_utm < dem_lon_min or lon_utm > dem_lon_max or
                    lat_utm < dem_lat_min or lat_utm > dem_lat_max):
                print("towers not found in DEM, please use mannual H")
                return False
    return True


def extract_lines_between(file_path, start_string, end_string):
    with open(file_path, 'r', encoding='utf-8') as file:
        lines = file.readlines()

    blocks = []
    current_start_index = -1

    for i, line in enumerate(lines):
        if start_string in line:
            # If a previous block was started but not ended, close it before starting a new one
            if current_start_index != -1:
                blocks.append((current_start_index, i))
            current_start_index = i + 1  # Start collecting lines after the start_string
        elif line == end_string and current_start_index != -1: # Found an end string for an active block
            blocks.append((current_start_index, i))
            current_start_index = -1  # Reset, indicating no active block
    
    # Handle the last block if it extends to the end of the file
    if current_start_index != -1:
        blocks.append((current_start_index, len(lines)))

    lines_return = []
    for start, end in blocks:
        lines_return.append(lines[start:end])
    
    routes=[]
    pattern = re.compile(r'\d+\.?\d*')
    # todo 分割
    for i in range(0,len(lines_return)):
        waypoints=[]
        for j in range(0,len(lines_return[i])):
            waypoint=[]
            matches = pattern.findall(lines_return[i][j])
            waypoint.append(float(matches[1]))
            waypoint.append(float(matches[0]))
            waypoints.append(waypoint)
        routes.append(waypoints)
    
    return routes

def read_H_Pd(file_path):
    with open(file_path, 'r', encoding='utf-8') as file:
        lines = file.readlines()
        
        # Read H (second line) - parse as a list
        H_str = lines[1].strip()
        H = ast.literal_eval(H_str) # Safely evaluate string to a Python literal (list)

        # Read Pd (third line) - parse as a list
        Pd_str = lines[2].strip()
        Pd = ast.literal_eval(Pd_str)

        # Rd is on the fourth line, but not used by calWaypoint, so we'll just read it
        Rd = float(lines[3].strip())
        
    return H, Pd

def calWaypoint(routesXY,H,Pd):
    routesAll=[]
    for i in range(0,len(routesXY)):
        routeAll=[]
        for j in range(0,len(routesXY[i])-1):
            start_pt=routesXY[i][j]
            end_pt=routesXY[i][j+1]
            # 104.09533254410344,30.785578515584916
            # routesXY[i][j][0],routesXY[i][j][1]
            deg=cal_deg(start_pt[1],start_pt[0],end_pt[1],end_pt[0])
            routeLen=cal_len(start_pt[1],start_pt[0],end_pt[1],end_pt[0])*1000
            det0=end_pt[0]-start_pt[0]
            det1=end_pt[1]-start_pt[1]
            rl=0
            # 一顿一顿的版本
            # while rl<routeLen:
            #     waypointAll=[]
            #     ratio=rl/routeLen
            #     waypointAll.append("abc")
            #     waypointAll.append(start_pt[0]+ratio*det0)
            #     waypointAll.append(start_pt[1]+ratio*det1)
            #     waypointAll.append(H[0])
            #     waypointAll.append(deg)
            #     routeAll.append(waypointAll)
            #     rl=rl+Pd[0]
            # 中间不停的版本
            waypointAll=[]
            waypointAll.append("abc")
            waypointAll.append(start_pt[0])
            waypointAll.append(start_pt[1])
            waypointAll.append(H[0])
            waypointAll.append(deg)
            routeAll.append(waypointAll)      
        routesAll.append(routeAll)
    return routesAll

def cal_deg(lat1, lon1, lat2, lon2):
    """
    计算从点A (lat1, lon1) 到点B (lat2, lon2) 的方向角（方位角）。
    所有参数均以度为单位。
    """
    # 将度转换为弧度
    lat1 = math.radians(lat1)
    lon1 = math.radians(lon1)
    lat2 = math.radians(lat2)
    lon2 = math.radians(lon2)

    # 计算变化量
    delta_lon = lon2 - lon1

    # 计算初始方位角
    x = math.sin(delta_lon) * math.cos(lat2)
    y = math.cos(lat1) * math.sin(lat2) - (math.sin(lat1) * math.cos(lat2) * math.cos(delta_lon))
    initial_bearing = math.atan2(x, y)

    # 将弧度转换为度
    initial_bearing = math.degrees(initial_bearing)
    
    # 将角度调整为0到360度之间
    compass_bearing = (initial_bearing + 360) % 360
    # 将角度调整为-180到180度之间
    if compass_bearing>180:
        compass_bearing=compass_bearing-360

    return compass_bearing

def cal_len(lat1, lon1, lat2, lon2):
    """
    计算从点A (lat1, lon1) 到点B (lat2, lon2) 的大圆距离。
    所有参数均以度为单位。
    返回距离以公里为单位。
    """
    # 将度转换为弧度
    lat1 = math.radians(lat1)
    lon1 = math.radians(lon1)
    lat2 = math.radians(lat2)
    lon2 = math.radians(lon2)

    # 地球半径（以公里为单位）
    R = 6371.0

    # 计算变化量
    delta_lat = lat2 - lat1
    delta_lon = lon2 - lon1

    # Haversine公式
    a = math.sin(delta_lat / 2)**2 + math.cos(lat1) * math.cos(lat2) * math.sin(delta_lon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    # 计算距离
    distance = R * c

    return distance

def read_dsm(dir):
    dataset = gdal.Open(dir)

    dem_XSize = dataset.RasterXSize  # 列数
    dem_YSize = dataset.RasterYSize  # 行数
    dem_bands = dataset.RasterCount  # 波段数
    # print(dem_XSize, dem_YSize, dem_bands)

    dem_geotrans = dataset.GetGeoTransform()  # 仿射矩阵
    # print("左上角地理坐标", dem_geotrans[0], dem_geotrans[3])
    dem_proj = dataset.GetProjection()  # 地图投影信息

    # 读取某一像素点的值
    # 读取一个波段，其参数为波段的索引号，波段索引号从1开始
    band = dataset.GetRasterBand(1)

    # 用ReadAsArray(<xoff>, <yoff>, <xsize>, <ysize>)，读出从(xoff,yoff)开始，大小为(xsize,ysize)的矩阵。以下为读取整幅图像
    dem_data = band.ReadAsArray(0, 0, dem_XSize, dem_YSize)
    # print(dem_data)

    # 获取经纬度
    def getxy(row, col, geotransform):
        px = geotransform[0] + col * geotransform[1] + row * geotransform[2]
        py = geotransform[3] + col * geotransform[4] + row * geotransform[5]
        return [px, py]
    
    rowLen = dem_YSize
    colLen = dem_XSize
    rowStep = 1
    colStep = 1
    lat_data = np.zeros((rowLen,colLen))
    lon_data = np.zeros((rowLen,colLen))
    curRow = 0  # 当前行
    while curRow < rowLen:
        curCol = 0  # 当前列
        while curCol < colLen:
            cur_pos = getxy(curRow, curCol, dem_geotrans) # 坐标
            cur_height = dem_data[curRow][curCol] # dem矩阵的值（高程）
            lat_data[curRow][curCol] = cur_pos[0]
            lon_data[curRow][curCol] = cur_pos[1]
            curCol += colStep
        curRow += rowStep
    # 释放内存。如果不释放，在arcgis或envi中打开该图像时显示文件已被占用
    del dataset
    return lat_data,lon_data,dem_data



def add_dem(lat_data,lon_data,dem_data,la,lo):
    def bi_interp(la_idx,lo_idx,lat_data,lon_data,dem_data,la,lo):
        # (a1, b1, x11), (_a1, b2, x12), (a2, _b1, x21), (_a2, _b2, x22)
        a1 = lat_data[la_idx+1]
        a2 = lat_data[la_idx]
        b1 = lon_data[lo_idx]
        b2 = lon_data[lo_idx+1]
        x11 = dem_data[la_idx + 1, lo_idx]
        x12 = dem_data[la_idx + 1, lo_idx+1]
        x21 = dem_data[la_idx, lo_idx]
        x22 = dem_data[la_idx, lo_idx+1]
        a=la
        b=lo
        Y = (x11 * (a2 - a) * (b2 - b)+ x21 * (a - a1) * (b2 - b)+ x12 * (a2 - a) * (b - b1)+ x22 * (a - a1) * (b - b1)) / ((a2 - a1) * (b2 - b1) + 0.0)
        return Y

    H_to_add=0
    # bi_linear
    lat_data = lat_data[0, :].tolist()
    lon_data = lon_data[:, 0].tolist()
    la_idx =0
    lo_idx = 0
    #坐标转换UTM49->WGS84
    transformer = Transformer.from_crs("epsg:4326","epsg:32649")
    la,lo=transformer.transform(la,lo)
    if la<max(lat_data) and la>min(lat_data) and lo<max(lon_data) and lo>min(lon_data):
        for i in range(0,len(lat_data)-1):
            if la>lat_data[i] and la<lat_data[i+1]:
                la_idx=i
                break
        for j in range(0,len(lon_data)-1):
            if lo<lon_data[j] and lo>lon_data[j+1]:
                lo_idx=j
                break
        print(la_idx,lo_idx)
        H_to_add=bi_interp(la_idx, lo_idx, lat_data, lon_data, dem_data, la, lo)
        print(H_to_add)
    return H_to_add


import os
# print(os.getcwd())

dsm_lat,dsm_lon,dsm_data = read_dsm('../source/dsm_test.tif')
dem_lat,dem_lon,dem_data = read_dsm('../source/dem_test.tif')
dsm_max=np.max(dsm_data)

# 提取航线 XY 坐标
file_path = '../cppFiles/last/cpp_waypoint.txt'
start_string = 'Number of Waypoints:'
end_string = '\n'
routesXY = extract_lines_between(file_path, start_string, end_string)

# 塔头,114.3894284790,30.2881967755,60.0449308877,0,0.000,0,0
# 读取 H 和 Pd (可能在仿地飞行中使用)
H,Pd = read_H_Pd("../cppFiles/0.txt")

# print(Pd)
routesAll=calWaypoint(routesXY,H,Pd)

dir_path="../cppFiles/last/wps"
if not os.path.exists(dir_path):
    os.makedirs(dir_path)

uav_configs = None
if len(sys.argv) > 1:
    try:
        # 从命令行第一个参数获取JSON字符串并解析
        json_string = sys.argv[1]
        print("lala:"+json_string)
        uav_configs = json.loads(json_string)
        print("成功解析UAV配置:")
        for config in uav_configs:
            # 1. 先获取selectedUav对象（如果不存在，返回空字典{}）
            selected_uav = config.get('selectedUav', {})
            # 2. 从selectedUav对象中获取label（如果不存在，返回默认值"未指定"）
            uav_name = selected_uav.get('name', '未指定')
            
            print(f"  - 无人机型号: {uav_name}, "
                  f"起飞高度: {config.get('startHeight', '未指定')}, "
                  f"航线高度: {config.get('flightRouteHeight', '未指定')}")
    except json.JSONDecodeError as e:
        print(f"错误：解析JSON参数失败！请检查参数格式。错误信息: {e}")
        sys.exit(1) # 如果参数错误，直接退出程序


for i in range(0,len(routesAll)):
    with open("../cppFiles/last/wps/wp{}.txt".format(i),"w") as f:
        print("参数长度：")
        print(len(sys.argv))
        if len(sys.argv) > 1:  # 手动输入航高
            print("wp2kml的模式为手动航高")
            # 从解析的配置中获取当前航线的航高
            config = uav_configs[i]
            
            # --- 【核心改动在这里】 ---
            # 从配置字典中获取 startHeight 和 flightRouteHeight 并相加
            start_height = config.get('startHeight')
            flight_route_height = config.get('flightRouteHeight')

            for j in range(0, len(routesAll[i])):
                # H_manual = int(sys.argv[1]) + int(sys.argv[2])
                H_manual = start_height + flight_route_height # 关键：两值相加
                f.write("{},{},{},{},{},{},{},{}\n".format(routesAll[i][j][0], 
                                                           routesAll[i][j][1], 
                                                           routesAll[i][j][2],
                                                           H_manual, 
                                                           routesAll[i][j][4], 
                                                           0, 0, 0))
        elif len(sys.argv)== 1:  # 仿地飞行
            print("wp2kml的模式为仿地飞行")
            # 添加检查（新增）
            towers_in_dem=check_routes_in_dem(routesXY, dem_lat, dem_lon)
            if towers_in_dem:  # 航点在DEM内
                H_zoff=-1
                H_adddem=[]
                for j in range(0,len(routesAll[i])):
                    H_adddem.append(add_dem(dem_lat,dem_lon,dem_data,routesAll[i][j][2],routesAll[i][j][1]))
                    if H_adddem[j]<-9999:
                       H_adddem[j]= H_adddem[j-1]
                    if H_adddem[j]<0 and abs(H_adddem[j])>H_zoff:
                        H_zoff=abs(H_adddem[j])
                # print(H_adddem[j])
                for j in range(0,len(routesAll[i])):
                    H_adddem[j] =  H_adddem[j]+H_zoff+dsm_max+10
                    # H_adddsm = H[0]
                    f.write("{},{},{},{},{},{},{},{}\n".format(routesAll[i][j][0],routesAll[i][j][1],routesAll[i][j][2],H_adddem[j],routesAll[i][j][4],0,0,0))
            else:
                print("towers not found in DEM, please use mannual H")
    f.close()

# print('py success')
#----------------------------------------
# dem_lat,dem_lon,dem_data = read_dsm('../source/dem_test.tif')
# dsm_lat,dsm_lon,dsm_data = read_dsm('../source/dsm_test.tif')
# dsm_max=np.max(dsm_data)
# dem_max=np.max(dem_data)

# # 调用
# file_path = '../cppFiles/cpp_waypoint.txt'
# start_string = 'Number of Waypoints:'
# end_string = '\n'
# routesXY = extract_lines_between(file_path, start_string, end_string)


# # ImageName,Longitude,Latitude,Elevation,FlyHeading(航向角,[-180,180]),VehicleHeading(俯仰角),HoveringTime(500),ImageCount(1)
# # 塔头,114.3894284790,30.2881967755,60.0449308877,0,0.000,0,0


# H,Pd = read_H_Pd("../cppFiles/0.txt")
# print(Pd)
# routesAll=calWaypoint(routesXY,H,Pd)

# dir_path="../cppFiles/wps"
# if not os.path.exists(dir_path):
#             os.makedirs(dir_path)

# for i in range(0,len(routesAll)):
#     with open("../cppFiles/wps/wp{}.txt".format(i),"w") as f:
#         H_zoff=-1
#         H_adddem=[]
#         for j in range(0,len(routesAll[i])):
#             H_adddem.append(add_dem(dem_lat,dem_lon,dem_data,routesAll[i][j][2],routesAll[i][j][1]))
#             if H_adddem[j]<-9999:
#                  H_adddem[j]= H_adddem[j-1]
#             if H_adddem[j]<0 and abs(H_adddem[j])>H_zoff:
#                 H_zoff=abs(H_adddem[j])
#         for j in range(0,len(routesAll[i])):
#             H_adddem[j] =  H_adddem[j]+H_zoff+dsm_max+10
#             # H_adddsm = H[0]
#             f.write("{},{},{},{},{},{},{},{}\n".format(routesAll[i][j][0],routesAll[i][j][1],routesAll[i][j][2],H_adddem[j],routesAll[i][j][4],0,500,1))        
#         f.close()
