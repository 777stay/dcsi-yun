import json
import math
import os
import shutil
# import requests
import geotable
import sys
import matplotlib.pyplot as plt
from scipy.spatial import ConvexHull
import numpy as np
import ast

# 假设 type_para 是一个全局字典，存储了不同飞机型号的参数
# 请确保在代码的适当位置定义了 type_para 字典
# 均使用海康600万镜头，MVL-HF0628M-6MPE
# 常见的600万像素分辨率有 3088 × 2076 或 3072 × 2048
# D 8.96mm 73.49°，H 7.38mm 63.11°，V 4.92mm 44.59°
# D 对角线视场角 是这三个角中最大的一个
# H 水平视场角 通常是我们最关心的，尤其是在计算地面覆盖宽度时
# V 垂直视场角 通常小于水平视场角
# 使用init_from_FOVandH
# fov_l = 水平视场角H = 63.11° fov_w = 垂直视场角V = 44.59°
# 格式示例: type_para = {'M400': [f, fov_w, fov_l], 'M500': [f, fov_w, fov_l]}
type_para = {'Matrice_300_RTK': [0.006,np.deg2rad(44.59), np.deg2rad(63.11)], 'Matrice_350_RTK': [0.006,np.deg2rad(44.59), np.deg2rad(63.11)], 'Matrice_400': [0.006,np.deg2rad(44.59), np.deg2rad(63.11)]}

# 计算无人机飞行参数模型(多台无人机)
class CppModel:
    # 经典摄影测量模型
    def __init__(self, reso_cam=None, f=None, nw=None, nl=None, reso_GSD_requ=None, f_ol=None, s_ol=None, hd=None, Hl=40, Hh=300):
        self.reso_cam = reso_cam
        self.f = f
        self.nw = nw
        self.nl = nl
        self.reso_GSD_requ = reso_GSD_requ

        self.f_ol = f_ol
        self.s_ol = s_ol

        self.hd = hd

        self.Hl = Hl
        self.Hh = Hh

        self.H = 0.0
        self.reso_GSD = 0.0
        self.W = 0.0
        self.L = 0.0
        self.Rd = 0.0
        self.Pd = 0.0
        self.fov_w = 0.0
        self.fov_l = 0.0

        if all(arg is not None for arg in [reso_cam, f, nw, nl, reso_GSD_requ, f_ol, s_ol, hd]):
            self.reso2HWL()
            self.overlap2Rd()
            self.overlap2Pd()

    # 经典模型：根据相机分辨率和GSD要求计算最大飞行高度、实际飞行高度、地面采样距离和覆盖范围
    def reso2HWL(self):
        Hmax = min(self.reso_GSD_requ * self.f / self.reso_cam, self.Hh)
        self.H = Hmax
        self.reso_GSD = self.H * self.reso_cam / self.f
        self.W = self.nw * self.reso_GSD
        self.L = self.nl * self.reso_GSD

    # 计算航带间距(基于侧向重叠率)
    def overlap2Rd(self):
        self.Rd = self.W * (1 - self.s_ol)

    # to photo density: 航线上的拍照点间隔
    def overlap2Pd(self):
        self.Pd = math.ceil(self.L * (1 - self.f_ol))

    # 定制模型：根据需求定制的init函数
    @classmethod
    def init_from_FOVandH(cls, fov_w_list, fov_l_list, H_list, f_list, overlay_rate):
        all_cm_models = []
        for i in range(len(f_list)):
            # Create an instance for each drone
            # Assuming reso_cam, nw, nl, reso_GSD_requ, hd can be default or derived if not provided
            # For now, let's pass f_ol and s_ol from overlay_rate
            instance = cls(f=f_list[i], f_ol=overlay_rate, s_ol=overlay_rate)
            instance.H = H_list[i]
            instance.fov_w = fov_w_list[i]
            instance.fov_l = fov_l_list[i]

            instance.FOVH2WL()
            instance.WL2RdPd(overlay_rate)
            all_cm_models.append(instance)
        return all_cm_models

    def FOVH2WL(self):
        self.L = 2 * self.H * math.tan(self.fov_l / 2)
        self.W = 2 * self.H * math.tan(self.fov_w / 2)

    def WL2RdPd(self, overlay_rate):
        self.Rd = self.W * (1 - overlay_rate)
        self.Pd = math.ceil(self.L * (1 - overlay_rate))


    # 将计算参数输出到文本文件供C++程序使用
    @staticmethod
    def py2txt0(cm_list):
        file_path="../cppFiles/0.txt"
        dir_path=os.path.dirname(file_path)
        if not os.path.exists(dir_path):
            os.makedirs(dir_path)

        H_values = [cm.H for cm in cm_list]
        Pd_values = [cm.Pd for cm in cm_list]
        Rd_min = min(cm.Rd for cm in cm_list)

        with open(file_path,"w") as f:
            f.write("H, Pd, Rd\n")
            f.write("{}\n".format(H_values))
            f.write("{}\n".format(Pd_values))
            f.write("{}\n".format(Rd_min))
        f.close()

class CppJson:
    def __init__(self,droneNo,scanningDensity,polygon,obstacles,pathsStrictlyInPoly,initialPos,rPortions,outFileDate,coordinates,type_list,overlayrate=-1):
        self.droneNo = droneNo
        self.scanningDensity = scanningDensity
        self.polygon = polygon
        self.obstacles = obstacles
        self.pathsStrictlyInPoly = pathsStrictlyInPoly
        self.initialPos = initialPos
        self.rPortions = rPortions
        self.outFileDate = outFileDate
        self.coordinates = coordinates
        self.type_list = type_list # Added type_list
        self.overlayrate = overlayrate # Added overlayrate
    # 将无人机任务参数(无人机编号、扫描密度、多边形区域、障碍物等)转换为JSON格式并保存
    def py2json(self, cm_list):
        text1 = {}
        text1["droneNo"] = self.droneNo
        # Assuming scanningDensity is now a list or needs to be calculated per drone
        # If scanningDensity is meant to be a single value for all drones, you might need to adjust.
        # For now, I'll assume it's calculated based on the first drone's Rd if overlayrate > 0
        if self.overlayrate > 0 and cm_list:
            text1["scanningDensity"] = math.ceil(cm_list[0].W * self.overlayrate) # Assuming cm_list[0].W is appropriate
        else:
            text1["scanningDensity"] = math.ceil(self.scanningDensity)

        text1["polygon"] = []
        for i in range(math.floor(len(self.polygon)/2)):
            p={}
            p["lat"] = self.polygon[2*i]
            p["long"] = self.polygon[2*i+1]
            text1["polygon"].append(p)

        text1["obstacles"] = []
        for i in range(len(self.obstacles)):
            ob = []
            for j in range(math.floor(len(self.obstacles[i])/2)):
                p={}
                p["lat"] = self.obstacles[i][2*j]
                p["long"] = self.obstacles[i][2*j+1]
                ob.append(p)
            text1["obstacles"].append(ob)

        text1["pathsStrictlyInPoly"] = self.pathsStrictlyInPoly


        text1["initialPos"] = []
        # 根据起飞点选择最近的起点 (考虑顶点和边)
        for i in range(len(self.coordinates)):
            # 获取当前无人机的起飞点坐标
            takeoff_lat = self.coordinates[i][0]
            takeoff_lon = self.coordinates[i][1]

            min_distance = float('inf')
            nearest_point = None

            num_polygon_points = len(self.polygon) // 2
            
            # 遍历所有顶点
            for j in range(num_polygon_points):
                vertex_lat = self.polygon[2 * j]
                vertex_lon = self.polygon[2 * j + 1]
                
                delta_lat = vertex_lat - takeoff_lat
                delta_lon = vertex_lon - takeoff_lon
                distance = math.sqrt(delta_lat**2 + delta_lon**2)

                if distance < min_distance:
                    min_distance = distance
                    nearest_point = {"lat": vertex_lat, "long": vertex_lon}

            # 遍历所有边
            for j in range(num_polygon_points):
                # 获取边的两个端点
                idx1 = j
                idx2 = (j + 1) % num_polygon_points # 确保最后一个点和第一个点相连
                
                x1 = self.polygon[2 * idx1 + 1] # lon1
                y1 = self.polygon[2 * idx1]     # lat1
                x2 = self.polygon[2 * idx2 + 1] # lon2
                y2 = self.polygon[2 * idx2]     # lat2
                
                # 计算点到线段的最短距离
                px = takeoff_lon
                py = takeoff_lat
                
                # 向量计算
                dx = x2 - x1
                dy = y2 - y1
                
                if dx == 0 and dy == 0:
                    # 点点重合，跳过
                    continue
                
                # 投影参数 t
                t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
                
                # 限制 t 在 [0, 1] 区间内，确保垂足在线段上
                t = max(0, min(1, t))
                
                # 计算垂足坐标
                closest_x = x1 + t * dx
                closest_y = y1 + t * dy
                
                # 计算距离
                distance = math.sqrt((px - closest_x)**2 + (py - closest_y)**2)
                
                if distance < min_distance:
                    min_distance = distance
                    nearest_point = {"lat": closest_y, "long": closest_x}
            
            # 将找到的最近点添加到 initialPos 列表中
            if nearest_point is not None:
                text1["initialPos"].append(nearest_point)

        # 原来的起点
        # for i in range(math.floor(len(self.initialPos)/2)):
        #     p={}
        #     p["lat"] = self.initialPos[2*i]
        #     p["long"] = self.initialPos[2*i+1]
        #     text1["initialPos"].append(p)

        text1["rPortions"]=[]
        for i in range(len(self.rPortions)):
            text1["rPortions"].append(self.rPortions[i])
        last_filename = '../cppFiles/last/cpp.json'
        date_file_name = '../cppFiles/'+self.outFileDate+'/cpp.json'

        print(date_file_name)

        # 获取文件所在的目录
        directory = os.path.dirname(last_filename)
        date_dir = os.path.dirname(date_file_name)
        # 如果目录不存在，则创建
        if not os.path.exists(directory):
            os.makedirs(directory)
        if not os.path.exists(date_dir):
            os.makedirs(date_dir)

        with open (last_filename,'w') as f:
            # json.dump(x,f)
            jtext = json.dump(text1,f,ensure_ascii=False,indent=2)
            # print(jtext)

        with open (date_file_name,'w') as f:
            jtext = json.dump(text1,f,ensure_ascii=False,indent=2)

class Point:
    def __init__(self):
        self.j=0
        self.w=0
        self.h=0
        self.x=0
        self.y=0
        self.z=0

    def jwh_init(self,j,w,h=10):
        self.j = j
        self.w = w
        self.h = h
        self.JWH2XYZ()

    def xyh_init(self,x,y,h=10):
        self.x = x
        self.y = y
        self.h = h
        self.XYH2JWH()

    def xyz_init(self,x,y,z):
        self.x = x
        self.y = y
        self.z = z
        self.XYZ2JWH()


    def JWH2XYZ(self):
        # WGS-84 ellipsoid parameters
        a = 6378137.0  # Semi-major axis (meters)
        e = 0.081819190842622  # First eccentricity

        # Convert latitude and longitude from degrees to radians
        lat_rad = math.radians(self.w)
        lon_rad = math.radians(self.j)

        # Calculate N (prime vertical radius of curvature)
        N = a / math.sqrt(1 - e ** 2 * math.sin(lat_rad) ** 2)

        # Calculate X, Y, Z
        self.x = (N + self.h) * math.cos(lat_rad) * math.cos(lon_rad)
        self.y = (N + self.h) * math.cos(lat_rad) * math.sin(lon_rad)
        self.z = ((1 - e ** 2) * N + self.h) * math.sin(lat_rad)

    def XYZ2JWH(self):
        # WGS-84 ellipsoid parameters
        a = 6378137.0  # Semi-major axis (meters)
        e = 0.081819190842622  # First eccentricity
        b = a * math.sqrt(1 - e ** 2)  # Semi-minor axis

        # Calculate p and theta
        p = math.sqrt(self.x ** 2 + self.y ** 2)
        theta = math.atan2(self.z * a, p * b)

        # Calculate longitude (lambda)
        lon = math.atan2(self.y, self.x)

        # Calculate latitude (phi)
        phi = math.atan2(self.z + (e ** 2 * b * math.sin(theta) ** 3), p - (e ** 2 * a * math.cos(theta) ** 3))

        # Calculate N
        N = a / math.sqrt(1 - e ** 2 * math.sin(phi) ** 2)

        # Calculate altitude (h)
        h = p / math.cos(phi) - N

        # Convert latitude and longitude from radians to degrees
        lat = math.degrees(phi)
        lon = math.degrees(lon)

        self.w=lat
        self.j=lon
        self.h=h

    def XYH2JWH(self):
        # WGS-84 ellipsoid parameters
        a = 6378137.0  # Semi-major axis (meters)
        e = 0.081819190842622  # First eccentricity

        # Calculate p
        p = math.sqrt(self.x ** 2 + self.y ** 2)

        # Initial guess of phi
        phi = math.atan2(self.h, p)

        # Iterative computation
        phi_prev = 0
        epsilon = 1e-12  # Convergence criterion
        N = a / math.sqrt(1 - e ** 2 * math.sin(phi) ** 2)
        while abs(phi - phi_prev) > epsilon:
            phi_prev = phi
            N = a / math.sqrt(1 - e ** 2 * math.sin(phi) ** 2)
            h = p / math.cos(phi) - N
            phi = math.atan2(self.h + e ** 2 * N * math.sin(phi), p)

        # Calculate longitude
        lon = math.atan2(self.y, self.x)

        # Calculate altitude
        h = p / math.cos(phi) - N

        # Convert latitude and longitude from radians to degrees
        lat = math.degrees(phi)
        lon = math.degrees(lon)

        self.w=lat
        self.j=lon

class tool:
    @staticmethod
    def perpendicular_line( m, x1, y1):
        # Calculate the slope of the perpendicular line
        m_perpendicular = -1 / m

        # Calculate the intercept of the perpendicular line using the point (x1, y1)
        b_perpendicular = y1 - m_perpendicular * x1

        return m_perpendicular, b_perpendicular

    @staticmethod
    def line_intersection(m1, b1, m2, b2,z_ave):
        # Ensure the lines are not parallel
        if m1 == m2:
            return None  # Parallel lines do not intersect

        # Calculate the intersection point
        x = (b2 - b1) / (m1 - m2)
        y = m1 * x + b1

        p=Point()
        p.xyz_init(x,y,z_ave)

        return p

    @staticmethod
    def line_intersection_jwh(m1, b1, m2, b2):
        # Ensure the lines are not parallel
        if m1 == m2:
            return None  # Parallel lines do not intersect

        # Calculate the intersection point
        x = (b2 - b1) / (m1 - m2)
        y = m1 * x + b1

        p=Point()
        p.jwh_init(x,y)

        return p

    @staticmethod
    def on_segment(p, q, r):
        """Given three collinear points p, q, r, checks if point q lies on segment pr."""
        # if q[0] <= max(p[0], r[0]) and q[0] >= min(p[0], r[0]) and q[1] <= max(p[1], r[1]) and q[1] >= min(p[1], r[1]):
        if q.j <= max(p.j, r.j) and q.j >= min(p.j, r.j) and q.w <= max(p.w, r.w) and q.w >= min(p.w,r.w):
            return True
        return False

    @staticmethod
    def orientation(p, q, r):
        """Finds the orientation of the ordered triplet (p, q, r).
           Returns:
           0 -> p, q and r are collinear
           1 -> Clockwise
           2 -> Counterclockwise"""
        val = (q.w - p.w) * (r.j - q.j) - (q.j - p.j) * (r.w - q.w)
        if val == 0:
            return 0
        elif val > 0:
            return 1
        else:
            return 2

    @staticmethod
    def do_intersect(p1, q1, p2, q2):
        """Main function to check whether the line segments p1q1 and p2q2 intersect"""
        # Find the four orientations needed for the general and special cases
        o1 = tool.orientation(p1, q1, p2)
        o2 = tool.orientation(p1, q1, q2)
        o3 = tool.orientation(p2, q2, p1)
        o4 = tool.orientation(p2, q2, q1)

        # General case
        if o1 != o2 and o3 != o4:
            return True

        # Special cases
        # p1, q1 and p2 are collinear and p2 lies on segment p1q1
        if o1 == 0 and tool.on_segment(p1, p2, q1):
            return True

        # p1, q1 and q2 are collinear and q2 lies on segment p1q1
        if o2 == 0 and tool.on_segment(p1, q2, q1):
            return True

        # p2, q2 and p1 are collinear and p1 lies on segment p2q2
        if o3 == 0 and tool.on_segment(p2, p1, q2):
            return True

        # p2, q2 and q1 are collinear and q1 lies on segment p2q2
        if o4 == 0 and tool.on_segment(p2, q1, q2):
            return True

        # Doesn't fall in any of the above cases
        return False

    @staticmethod
    def is_point_on_line(x, y, m, b):
        """Check if the point (x, y) lies on the line y = mx + b"""
        return y == m * x + b

    @staticmethod
    def point_side_of_line(x, y, m, b):
        """Determine the side of the point (x, y) relative to the line y = mx + b"""
        return y - (m * x + b)

    @staticmethod
    def line_segment_not_intersect_line(x1, y1, x2, y2, m, b):
        """Check if the line segment (x1, y1) to (x2, y2) does not intersect with the line y = mx + b"""
        side1 = tool.point_side_of_line(x1, y1, m, b)
        side2 = tool.point_side_of_line(x2, y2, m, b)

        # Check if both points are on the same side or one point is on the line
        if side1 * side2 > 0 or (tool.is_point_on_line(x1, y1, m, b) and side2 == 0) or (
                tool.is_point_on_line(x2, y2, m, b) and side1 == 0):
            return True
        return False

    @staticmethod
    def are_segments_intersecting(p1, p2, q1, q2):
        """Check if two line segments (p1, p2) and (q1, q2) intersect"""

        def orientation(p, q, r):
            """Return the orientation of the triplet (p, q, r):
            0 -> p, q, and r are collinear
            1 -> Clockwise
            2 -> Counterclockwise
            """
            val = (q[1] - p[1]) * (r[0] - q[0]) - (q[0] - p[0]) * (r[1] - q[1])
            if val == 0:
                return 0
            elif val > 0:
                return 1
            else:
                return 2

        def on_segment(p, q, r):
            """Check if point q lies on segment pr"""
            if q[0] <= max(p[0], r[0]) and q[0] >= min(p[0], r[0]) and \
                    q[1] <= max(p[1], r[1]) and q[1] >= min(p[1], r[1]):
                return True
            return False

        o1 = orientation(p1, p2, q1)
        o2 = orientation(p1, p2, q2)
        o3 = orientation(q1, q2, p1)
        o4 = orientation(q1, q2, p2)

        if o1 != o2 and o3 != o4:
            return True

        if o1 == 0 and on_segment(p1, q1, p2):
            return True

        if o2 == 0 and on_segment(p1, q2, p2):
            return True

        if o3 == 0 and on_segment(q1, p1, q2):
            return True

        if o4 == 0 and on_segment(q1, p2, q2):
            return True

        return False

    @staticmethod
    def is_ray_intersect_segment(rx, ry, dx, dy, p1, p2):
        x1=p1.j
        y1=p1.w
        x2 = p2.j
        y2 = p2.w
        """Check if the ray (rx, ry) + t*(dx, dy) intersects the segment (x1, y1) to (x2, y2)"""
        # Represent the ray as a line segment from (rx, ry) to a far point (rx + dx * t, ry + dy * t) for large t
        far_point = (rx + dx * 1e10, ry + dy * 1e10)
        ray_start = (rx, ry)

        # Use the segments intersection function to check if they intersect
        return tool.are_segments_intersecting(ray_start, far_point, (x1, y1), (x2, y2))

    @staticmethod
    def intersection_of_line_and_ray(m, b, rx, ry, dx, dy):
        """Find the intersection point of a line y = mx + b and a ray starting at (rx, ry) with direction (dx, dy)"""
        if dx == 0:  # Vertical ray
            x = rx
            y = m * x + b
            t = (y - ry) / dy
        else:
            t = (m * rx - ry + b) / (dy - m * dx)
            x = rx + t * dx
            y = ry + t * dy

        if t >= 0:
            return x, y
        else:
            return None

    @staticmethod
    def is_line_intersect_ray(m, b, rx, ry, dx, dy):
        """Check if the line y = mx + b intersects with the ray starting at (rx, ry) with direction (dx, dy)"""
        intersection = tool.intersection_of_line_and_ray(m, b, rx, ry, dx, dy)
        return intersection is not None

    @staticmethod
    def intersection_point(p1, p2, p3, p4):
        """Return the intersection point of two line segments if they intersect"""

        def det(a, b, c, d):
            return a * d - b * c

        x1 = p1.j
        y1 = p1.w
        x2 = p2.j
        y2 = p2.w
        x3 = p3.j
        y3 = p3.w
        x4 = p4.j
        y4 = p4.w

        denominator = det(x1 - x2, y1 - y2, x3 - x4, y3 - y4)
        if denominator == 0:
            return None  # Parallel lines

        intersect_x = det(det(x1, y1, x2, y2), x1 - x2, det(x3, y3, x4, y4), x3 - x4) / denominator
        intersect_y = det(det(x1, y1, x2, y2), y1 - y2, det(x3, y3, x4, y4), y3 - y4) / denominator

        p=Point()
        p.jwh_init(intersect_x,intersect_y)
        return p

class Kml2Area:
    def __init__(self, geometries):
        self.towerList = []
        self.towerListDistance=[]
        self.towerListDistance.append(0)
        for g in geometries:
            p=Point()
            p.jwh_init(g.x,g.y)
            self.towerList.append(p)

    def kml2area(self,d,l,n_drones):
        self.keypoints=[]
        # W=m*J+b
        # M={m1,m2,m3,....}, B={b1,b2,b3,....}
        M=[]
        B=[]
        Bup=[]
        Bdown=[]
        for i in range(0,len(self.towerList)-1):
            dety=self.towerList[i + 1].y-self.towerList[i].y
            detx=self.towerList[i + 1].x-self.towerList[i].x
            m = (dety)/(detx)
            b = self.towerList[i].y-m*self.towerList[i].x
            M.append(m)
            B.append(b)
            Bup.append(b+d*math.sqrt(1+m*m))
            Bdown.append(b - d * math.sqrt(1 + m * m))
            self.towerListDistance.append(self.towerListDistance[-1]+math.sqrt(detx*detx+dety*dety))

        towerListUp=[]
        towerListDown=[]
        # start point
        len_start=math.sqrt(self.towerList[0].x*self.towerList[0].x+self.towerList[0].y*self.towerList[0].y)
        ratio=(len_start-l)/len_start
        mppd, bppd = tool.perpendicular_line(M[0], self.towerList[0].x*ratio, self.towerList[0].y*ratio)
        towerListUp.append(tool.line_intersection(mppd,bppd,M[0],Bup[0],self.towerList[0].z))
        towerListDown.append(tool.line_intersection(mppd, bppd, M[0], Bdown[0],self.towerList[0].z))

        # middle points
        for i in range(0, len(M) - 1):
            towerListUp.append(tool.line_intersection(M[i], Bup[i], M[i + 1], Bup[i + 1],self.towerList[i].z))
            towerListDown.append(tool.line_intersection(M[i], Bdown[i], M[i + 1], Bdown[i + 1],self.towerList[i].z))

        # end point
        len_end = math.sqrt(self.towerList[-1].x * self.towerList[-1].x + self.towerList[-1].y * self.towerList[-1].y)
        ratio = (len_end + l) / len_end
        mppd, bppd = tool.perpendicular_line(M[-1], self.towerList[-1].x * ratio, self.towerList[-1].y * ratio)
        towerListUp.append(tool.line_intersection(mppd, bppd, M[-1], Bup[-1],self.towerList[-1].z))
        towerListDown.append(tool.line_intersection(mppd, bppd, M[-1], Bdown[-1],self.towerList[-1].z))

        # construct polygon
        self.polygon=[]
        for tlu in towerListUp:
            self.polygon.append(tlu.w)
            self.polygon.append(tlu.j)
        towerListDown.reverse()
        for tld in towerListDown:
            self.polygon.append(tld.w)
            self.polygon.append(tld.j)
        #  ~ Number of Waypoints: 35 ~ 
        print(' ~ Number of Waypoints')
        print(' ~ Number of Waypoints: {} ~ '.format(len(self.polygon)/2))
        for i in range(0,len(self.polygon)/2):
            print('{}, {};'.format(self.polygon(2*i),self.polygon(2*i+1)))
        # ploting
        W=[]
        J=[]
        i=0
        while i<=(len(self.polygon)-1):
            W.append(self.polygon[i])
            J.append(self.polygon[i+1])
            i=i+2
        plt.plot(J,W)

        # construct convexHull
        keypoints=np.array([J,W]).T
        hull = ConvexHull(keypoints)
        # 绘制凸包的顶点（逆时针排列）
        plt.plot(keypoints[hull.vertices, 0], keypoints[hull.vertices, 1], 'r--', lw=2)
        plt.plot(keypoints[hull.vertices[0], 0], keypoints[hull.vertices[0], 1], 'ro')

        # ploting towerList
        tW = []
        tJ = []
        for tl in self.towerList:
            tW.append(tl.w)
            tJ.append(tl.j)
        plt.plot(tJ,tW,'y')

        # plt.show()


        # construct obstacle
        self.obstacles=[]
        w0 = towerListUp[0].w
        j0 = towerListUp[0].j
        w1 = towerListUp[1].w
        j1 = towerListUp[1].j
        w2 = (towerListUp[0].w-self.towerList[0].w)*2+self.towerList[0].w
        j2 = j0
        obstacle=[w0,j0,w1,j1,w2,j2]
        self.obstacles.append(obstacle)

        # cal start fly point
        self.initialPos=[]
        dis_thresh=0
        dis_interval=max(self.towerListDistance)/n_drones
        for i in range(len(self.towerListDistance)-1):
            if self.towerListDistance[i]>=dis_thresh:
                self.initialPos.append(towerListDown[i].w)
                self.initialPos.append(towerListDown[i].j)
                dis_thresh=dis_thresh+dis_interval

        # cal rPortions
        self.rPortions=[]
        r1=(int((1/n_drones)*100))/100
        r2=(round((1-r1*(n_drones-1))*100))/100
        self.rPortions.append(r2)
        for i in range(0,n_drones-1):
            self.rPortions.append(r1)

    def kml2area_jwh(self,d,l,n_drones):
        d=d/111111
        l=l/111111
        self.keypoints=[]
        # W=m*J+b
        # M={m1,m2,m3,....}, B={b1,b2,b3,....}
        M=[]
        B=[]
        Bup=[]
        Bdown=[]
        for i in range(0,len(self.towerList)-1):
            detw=self.towerList[i + 1].w-self.towerList[i].w
            detj=self.towerList[i + 1].j-self.towerList[i].j
            m = (detw)/(detj)
            b = self.towerList[i].w-m*self.towerList[i].j
            M.append(m)
            B.append(b)
            Bup.append(b+d*math.sqrt(1+m*m))
            Bdown.append(b - d * math.sqrt(1 + m * m))
            self.towerListDistance.append(self.towerListDistance[-1]+math.sqrt(detj*detj+detw*detw))

        # to trans up(+b) and down(-b)
        TRANS=False

        towerListUp=[]
        towerListDown=[]
        # start point
        detj10 = self.towerList[0].j - self.towerList[1].j
        detw10 = self.towerList[0].w - self.towerList[1].w
        len_start=math.sqrt(detj10*detj10+detw10*detw10)
        ratio=(len_start+l)/len_start
        mppd, bppd = tool.perpendicular_line(M[0], self.towerList[1].j+detj10*ratio, self.towerList[1].w+detw10*ratio)
        towerListUp.append(tool.line_intersection_jwh(mppd,bppd,M[0],Bup[0]))
        towerListDown.append(tool.line_intersection_jwh(mppd, bppd, M[0], Bdown[0]))

        # middle points
        for i in range(0, len(M) - 1):
            if TRANS:
                Bup_trans=Bdown[i + 1]
                Bdown_trans = Bup[i + 1]
            else:
                Bdown_trans = Bdown[i + 1]
                Bup_trans = Bup[i + 1]
            pUp=tool.line_intersection_jwh(M[i], Bup[i], M[i + 1], Bup_trans)
            pDown=tool.line_intersection_jwh(M[i], Bdown[i], M[i + 1], Bdown_trans)
            do_intersect_all=False
            for k in range(0,i+1):
            # if (tool.do_intersect(towerListUp[-1],pUp,self.towerList[k],self.towerList[k+1]) or tool.do_intersect(towerListDown[-1],pDown,self.towerList[k],self.towerList[k+1])):
            # if (~tool.line_segment_not_intersect_line(self.towerList[k].j,self.towerList[k].w,self.towerList[k+1].j,self.towerList[k+1].w,M[i+1],Bup_trans) or
            # ~tool.line_segment_not_intersect_line(self.towerList[k].j, self.towerList[k].w, self.towerList[k + 1].j,self.towerList[k + 1].w, M[i+1], Bdown_trans)):
                if tool.is_ray_intersect_segment(pUp.j, pUp.w, self.towerList[i+2].j-self.towerList[i+1].j, self.towerList[i+2].w-self.towerList[i+1].w,self.towerList[k], self.towerList[k+1]) or \
                tool.is_ray_intersect_segment(pDown.j, pDown.w, self.towerList[i + 2].j - self.towerList[i + 1].j,self.towerList[i + 2].w - self.towerList[i + 1].w, self.towerList[k],self.towerList[k + 1]):
                    do_intersect_all=True
                    break
            if do_intersect_all:
                TRANS=~TRANS
                # print(i)
                if TRANS:
                    Bup_trans = Bdown[i + 1]
                    Bdown_trans = Bup[i + 1]
                else:
                    Bdown_trans = Bdown[i + 1]
                    Bup_trans = Bup[i + 1]
                # if i>1:
                #     if ~tool.is_line_intersect_ray(M[i+1], Bup_trans,towerListUp[-1].j, towerListUp[-1].w, towerListUp[-1].j-towerListUp[-2].j, towerListUp[-1].w-towerListUp[-2].w):
                #         # towerListUp.pop()
                #         towerListUp.append(tool.line_intersection_jwh(M[i-1], Bup[i-1], M[i + 1], Bup_trans))
                #     else:
                #         towerListUp.append(tool.line_intersection_jwh(M[i], Bup[i], M[i + 1], Bup_trans))
                #     if ~tool.is_line_intersect_ray(M[i+1], Bdown_trans,towerListDown[-1].j, towerListDown[-1].w, towerListDown[-1].j-towerListDown[-2].j, towerListDown[-1].w-towerListDown[-2].w):
                #         # towerListDown.pop()
                #         towerListDown.append(tool.line_intersection_jwh(M[i-1], Bdown[i-1], M[i + 1], Bdown_trans))
                #     else:
                #         towerListDown.append(tool.line_intersection_jwh(M[i], Bdown[i], M[i + 1], Bdown_trans))
                # else:
                towerListUp.append(tool.line_intersection_jwh(M[i], Bup[i], M[i + 1], Bup_trans))
                towerListDown.append(tool.line_intersection_jwh(M[i], Bdown[i], M[i + 1], Bdown_trans))
            else:
                towerListUp.append(tool.line_intersection_jwh(M[i], Bup[i], M[i + 1], Bup_trans))
                towerListDown.append(tool.line_intersection_jwh(M[i], Bdown[i], M[i + 1], Bdown_trans))

            Bup[i+1]=Bup_trans
            Bdown[i+1]=Bdown_trans

        # end point
        if TRANS:
            Bup_trans = Bdown[-1]
            Bdown_trans = Bup[-1]
        else:
            Bdown_trans = Bdown[-1]
            Bup_trans = Bup[-1]
        detj10 = self.towerList[-1].j - self.towerList[-2].j
        detw10 = self.towerList[-1].w - self.towerList[-2].w
        len_end = math.sqrt(detj10 * detj10 + detw10 * detw10)
        ratio = (len_end + l) / len_end
        mppd, bppd = tool.perpendicular_line(M[-1], self.towerList[-1].j + detj10*ratio, self.towerList[-1].w + detw10* ratio)
        pUp=tool.line_intersection_jwh(mppd, bppd, M[-1], Bup_trans)
        pDown=tool.line_intersection_jwh(mppd, bppd, M[-1], Bdown_trans)
        if tool.do_intersect(towerListUp[-1],pUp,self.towerList[-2],self.towerList[-1]) or \
        tool.do_intersect(towerListDown[-1], pDown, self.towerList[-2], self.towerList[-1]):
            TRANS=~TRANS
            if TRANS:
                Bup_trans = Bdown[-1]
                Bdown_trans = Bup[-1]
            else:
                Bdown_trans = Bdown[-1]
                Bup_trans = Bup[-1]
        towerListUp.append(tool.line_intersection_jwh(mppd, bppd, M[-1], Bup_trans))
        towerListDown.append(tool.line_intersection_jwh(mppd, bppd, M[-1], Bdown_trans))


        # check and eliminate intersection
        def  eliminate_intersection(towerListUp):
            eliminate_list_up=[]
            insert_list=[]
            for m in range(0,len(towerListUp)-2):
                for n in range (m+2, len(towerListUp)-1):
                    if tool.do_intersect(towerListUp[m],towerListUp[m+1],towerListUp[n],towerListUp[n+1]):
                        eliminate_list_up.append(m)
                        eliminate_list_up.append(n)
                        p=tool.intersection_point(towerListUp[m],towerListUp[m+1],towerListUp[n],towerListUp[n+1])
                        insert_list.append(p)
            # print(eliminate_list_up)
            if eliminate_list_up:
                eliminate_flag=[]
                elen = int(len(eliminate_list_up) / 2)
                eidx = 0
                for i in range(0,len(towerListUp)):
                    if eidx<elen:
                        if i<=eliminate_list_up[2*eidx]:
                            eliminate_flag.append(1)
                        elif i==eliminate_list_up[2*eidx+1]:
                            eliminate_flag.append(0)
                            eidx=eidx+1
                        else:
                            eliminate_flag.append(0)
                    else:
                        eliminate_flag.append(1)
                # print(eliminate_flag)

                towerListUp_ed=[]
                insert_idx=0
                for i in range(0, len(towerListUp)):
                    if eliminate_flag[i]>0:
                        towerListUp_ed.append(towerListUp[i])
                    if eliminate_flag[i] == 0 and eliminate_flag[i-1]>0:
                        towerListUp_ed.append(insert_list[insert_idx])
                        insert_idx=insert_idx+1
                return  towerListUp_ed
            else:
                return towerListUp

        towerListUp=eliminate_intersection(towerListUp)
        towerListDown=eliminate_intersection(towerListDown)

        # construct polygon
        self.polygon=[]
        for tlu in towerListUp:
            self.polygon.append(tlu.w)
            self.polygon.append(tlu.j)
        towerListDown.reverse()
        for tld in towerListDown:
            self.polygon.append(tld.w)
            self.polygon.append(tld.j)
        print(' ~ Number of Waypoints: {} ~ '.format(int(len(self.polygon)/2)+1))
        for i in range(0,int(len(self.polygon)/2)):
            print('{}, {};'.format(self.polygon[2*i],self.polygon[2*i+1]))
        print('{}, {};'.format(self.polygon[0],self.polygon[1]))
        # ploting
        W=[]
        J=[]
        i=0
        while i<=(len(self.polygon)-1):
            W.append(self.polygon[i])
            J.append(self.polygon[i+1])
            i=i+2
        plt.plot(J,W)

        # construct convexHull
        keypoints=np.array([J,W]).T
        hull = ConvexHull(keypoints)
        # 绘制凸包的顶点（逆时针排列）
        plt.plot(keypoints[hull.vertices, 0], keypoints[hull.vertices, 1], 'r--', lw=2)
        plt.plot(keypoints[hull.vertices[0], 0], keypoints[hull.vertices[0], 1], 'ro')

        # ploting towerList
        tW = []
        tJ = []
        for tl in self.towerList:
            tW.append(tl.w)
            tJ.append(tl.j)
        plt.plot(tJ,tW,'y.')
        print('\n ~ Number of Waypoints: {} ~ '.format(len(self.towerList)))
        for i in range(0,len(self.towerList)):
            print('{}, {};'.format(self.towerList[i].w,self.towerList[i].j))


        # plt.show()


        # construct obstacle
        self.obstacles=[]
        w0 = towerListUp[0].w
        j0 = towerListUp[0].j
        w1 = towerListDown[0].w
        j1 = towerListDown[0].j
        w2 = (towerListUp[0].w-towerListUp[1].w)*1.1+towerListUp[1].w
        j2 = (towerListUp[0].j-towerListUp[1].j)*1.1+towerListUp[1].j
        obstacle=[w0,j0,w1,j1,w2,j2]
        self.obstacles.append(obstacle)

        # cal start fly point
        self.initialPos=[]
        dis_thresh=0
        dis_interval=max(self.towerListDistance)/n_drones
        for i in range(len(self.towerListDistance)-1):
            if self.towerListDistance[i]>=dis_thresh:
                self.initialPos.append(towerListDown[i].w)
                self.initialPos.append(towerListDown[i].j)
                dis_thresh=dis_thresh+dis_interval

        # cal rPortions
        self.rPortions=[]
        r1=(int((1/n_drones)*100))/100
        r2=(round((1-r1*(n_drones-1))*100))/100
        self.rPortions.append(r2)
        for i in range(0,n_drones-1):
            self.rPortions.append(r1)




# main function
# polygon=[30.786407031756323,104.09212699758014,30.784821917869277,104.09508185063873,30.785964677020694,104.09593988419478,30.787084383501835,104.09518910483321]
#obstacles=[[1,2,3,4],[2,3,4,5]]
# obstacles=[[30.785765035788764,104.09427175005911,30.78560375978379,104.09449162115787,30.785751212141786,104.09472758038575,30.785972390254894,104.09447017031898],]
# initialPos=[30.78611,104.09274,30.78558,104.09563,30.78699,104.09487]
# initialPos=[30.75,104.09,30.75,104.09,30.75,104.09]
# rPortions=[0.2,0.5,0.3]

# # reso_cam,f,nw,nl,reso_GSD_requ,f_ol,s_ol,hd,uav_num
# cm=CppModel(0.0001,0.05,720,1280,0.2,0.9,0.8,5,3)
# cm.H=[50,50,50]
# cm.Rd=[5,5,5]
# cm.py2txt0()

# t = geotable.load('../source/towers_test.kml')
# # 取前20个杆塔
# ka = Kml2Area(t.geometries[0:7])
# # d:垂直扩展 l:线性扩展
# ka.kml2area_jwh(30,20,3)
# # cj = CppJson(int(sys.argv[1]), cm.Rd / 2, ka.polygon, ka.obstacles, False, ka.initialPos, ka.rPortions)
# cj=CppJson(3,20,ka.polygon,ka.obstacles,False,ka.initialPos,ka.rPortions)
# cj.py2json()

if len(sys.argv)>1:
    # t = geotable.load('../source/towers_test.kml')
    # 取杆塔序號start_idx到end_idx
    drone_num = int(sys.argv[1])
    start_idx=int(sys.argv[2])
    end_idx=int(sys.argv[3])
    outFileDate = sys.argv[4]
    kmlPath = sys.argv[5]
    scanningDensity=int(sys.argv[6])

    overlayrate=float(sys.argv[8]) #重叠率

    f_list = []
    fov_w_list = []
    fov_l_list = []
    H_list = []
    type_list = []
    coordinates_list = []

    try:
        # 从命令行第7个参数获取JSON字符串并解析
        json_string = sys.argv[7]
        print("json_string:"+json_string)
        uav_configs = json.loads(json_string)
        print("成功解析UAV配置:")
        for config in uav_configs:
            # 1. 先获取selectedUav对象（如果不存在，返回空字典{}）
            selected_uav = config.get('selectedUav', {})
            # 2. 从selectedUav对象中获取label（如果不存在，返回默认值"未指定"）
            uav_name = selected_uav.get('name', '未指定')
            type_list.append(uav_name)
            # 3. 航高（如果不存在，返回默认值0）
            H_list.append(config.get('flightRouteHeight', 0))
            # 4. 起飞点（string 经纬度）
            initial_location_str = config.get('initialLocation', '0.0,0.0') # Retrieve initialLocation
            try:
                lon, lat = map(float, initial_location_str.split(','))
                coordinates_list.append([lat, lon])  # Store as [latitude, longitude] format
            except ValueError:
                # Handle case where split doesn't produce exactly 2 values
                print(f"Warning: Invalid initialLocation format '{initial_location_str}', using default [0.0, 0.0]")
                coordinates_list.append([0.0, 0.0])
            
            print(f"  - 无人机型号: {uav_name}, "
                  f"起飞高度: {config.get('startHeight', '未指定')}, "
                  f"航线高度: {config.get('flightRouteHeight', '未指定')}")

    except json.JSONDecodeError as e:
        print(f"错误:解析JSON参数失败:请检查参数格式。错误信息: {e}")
        sys.exit(1) # 如果参数错误，直接退出程序

    #从type_para中获取参数f_list, fov_w_list, fov_l_list
    for i in range(drone_num):
        # 获取当前无人机的型号
        drone_type = type_list[i]
        
        # 从type_para中查询对应型号的参数
        if drone_type in type_para:
            f, fov_w, fov_l = type_para[drone_type]
            f_list.append(f)
            fov_w_list.append(fov_w)
            fov_l_list.append(fov_l)
        else:
            # 如果找不到对应型号的参数，使用默认参数或抛出异常
            print(f"Warning: Type {drone_type} not found in type_para, using default parameters")
            # reso_cam,f,nw,nl,reso_GSD_requ,f_ol,s_ol,hd,
            # 创建CppModel实例并添加到列表
            # init_from_FOVandH(self,fov_w,fov_l,H,f)
            f_list.append(0.006)
            fov_w_list.append(np.deg2rad(44.59))
            fov_l_list.append(np.deg2rad(63.11))

    # 经典摄影测量初始化（已弃
    # reso_cam,f,nw,nl,reso_GSD_requ,f_ol,s_ol,hd,uav_num
    # cm = CppModel(0.0001, 0.05, 720, 1280, 0.2, overlay_rate, 0.8, 5)
    # fov_w_list,fov_l_list,H_list,f_list,overlay_rate
    cm_list = CppModel.init_from_FOVandH(fov_w_list,fov_l_list,H_list,f_list,overlayrate)

    # 如果需要使用列表中的cm对象进行后续操作，可以这样遍历：
    # for i, cm_instance in enumerate(cm):
    #     cm_instance.py2txt0()  # 或其他操作

    CppModel.py2txt0(cm_list) # 调用静态方法，传入所有CppModel实例的列表

    t = geotable.load(kmlPath)
    ka = Kml2Area(t.geometries[start_idx:end_idx])
    # d:垂直扩展 l:线性扩展
    ka.kml2area_jwh(40,20,drone_num)
    # cj = CppJson(int(sys.argv[1]), cm.Rd / 2, ka.polygon, ka.obstacles, False, ka.initialPos, ka.rPortions)
    # if len(sys.argv)<8:
    #     # 输入scanningDensit
    #     cj=CppJson(drone_num,scanningDensity,ka.polygon,ka.obstacles,False,ka.initialPos,ka.rPortions,outFileDate,coordinates_list,type_list)
    # else:

    # 输入overlay_rate
    cj=CppJson(drone_num,scanningDensity,ka.polygon,ka.obstacles,False,ka.initialPos,ka.rPortions,outFileDate,coordinates_list,type_list,overlayrate)
    cj.py2json(cm_list)
