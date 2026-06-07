# C:\Users\WHU_DCSI\Desktop\CNU-UAV(new)\server\bin\cpp_waypoint.txt
import re
import math
import os

import numpy
# -*- coding: utf-8 -*-
from osgeo import gdal
import numpy as np

import rasterio
from pyproj import Transformer

def extract_lines_between(file_path, start_string, end_string):
    with open(file_path, 'r', encoding='utf-8') as file:
        lines = file.readlines()
        
    start_indexs = []
    end_indexs = []
    
    for i, line in enumerate(lines):
        if start_string in line:
            start_indexs.append(i)
            continue
        if line==end_string and len(start_indexs)-len(end_indexs)>0:
            end_indexs.append(i)
    end_indexs.append(len(lines))

    lines_return=[]
    for k in range(0,len(start_indexs)):
        lines_return.append(lines[start_indexs[k] + 1:end_indexs[k]])

    routes=[]
    pattern = re.compile(r'\d+\.?\d*')
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
        pattern = re.compile(r'(\d+), (\d+), (\d+)')
        print(lines)

        matches_H = pattern.findall(lines[1])
        H=[]
        for match_H in matches_H:
            H=list(map(int, match_H))

        matches_Pd = pattern.findall(lines[2])
        Pd=[]
        for match_Pd in matches_Pd:
            Pd=list(map(int, match_Pd))
        
    return H,Pd

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
    lat_data = numpy.zeros((rowLen,colLen))
    lon_data = numpy.zeros((rowLen,colLen))
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

dem_lat,dem_lon,dem_data = read_dsm('..\\..\\..\\source\\dem_test.tif')
dsm_lat,dsm_lon,dsm_data = read_dsm('..\\..\\..\\source\\dsm_test.tif')
dsm_max=np.max(dsm_data)

# 调用
file_path = '..\\..\\..\\cppFiles\\cpp_waypoint.txt'
start_string = 'Number of Waypoints:'
end_string = '\n'
routesXY = extract_lines_between(file_path, start_string, end_string)


# 塔头,114.3894284790,30.2881967755,60.0449308877,0,0.000,0,0


H,Pd = read_H_Pd("..\\..\\..\\cppFiles\\0.txt")
print(Pd)
routesAll=calWaypoint(routesXY,H,Pd)

dir_path="..\\..\\..\\cppFiles\\wps"
if not os.path.exists(dir_path):
            os.makedirs(dir_path)

for i in range(0,len(routesAll)):
    with open("..\\..\\..\\cppFiles\\wps\\wp{}.txt".format(i),"w") as f:
        H_zoff=-1
        H_adddem=[]
        for j in range(0,len(routesAll[i])):
            H_adddem.append(add_dem(dem_lat,dem_lon,dem_data,routesAll[i][j][2],routesAll[i][j][1]))
            if H_adddem[j]<-9999:
               H_adddem[j]= H_adddem[j-1]
            if H_adddem[j]<0 and abs(H_adddem[j])>H_zoff:
                H_zoff=abs(H_adddem[j])
        print(H_adddem[j])
        for j in range(0,len(routesAll[i])):
            H_adddem[j] =  H_adddem[j]+H_zoff+dsm_max+10
            # H_adddsm = H[0]
            f.write("{},{},{},{},{},{},{},{}\n".format(routesAll[i][j][0],routesAll[i][j][1],routesAll[i][j][2],H_adddem[j],routesAll[i][j][4],0,0,0))        
        f.close()

# print('py success')
#----------------------------------------
# dem_lat,dem_lon,dem_data = read_dsm('..\\source\\dem_test.tif')
# dsm_lat,dsm_lon,dsm_data = read_dsm('..\\source\\dsm_test.tif')
# dsm_max=np.max(dsm_data)
# dem_max=np.max(dem_data)

# # 调用
# file_path = '..\\cppFiles\\cpp_waypoint.txt'
# start_string = 'Number of Waypoints:'
# end_string = '\n'
# routesXY = extract_lines_between(file_path, start_string, end_string)


# # ImageName,Longitude,Latitude,Elevation,FlyHeading(航向角,[-180,180]),VehicleHeading(俯仰角),HoveringTime(500),ImageCount(1)
# # 塔头,114.3894284790,30.2881967755,60.0449308877,0,0.000,0,0


# H,Pd = read_H_Pd("..\\cppFiles\\0.txt")
# print(Pd)
# routesAll=calWaypoint(routesXY,H,Pd)

# dir_path="..\\cppFiles\\wps"
# if not os.path.exists(dir_path):
#             os.makedirs(dir_path)

# for i in range(0,len(routesAll)):
#     with open("..\\cppFiles\\wps\\wp{}.txt".format(i),"w") as f:
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