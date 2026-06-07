import open3d as o3d

# 读取 .pcd 文件
pcd = o3d.io.read_point_cloud("E:\pointcloud\intensity\\1639558735.034604.pcd")

# 打印点云的基本信息
print("点云基本信息：")
print(pcd)

# 打印点云的点数
print(f"点数: {len(pcd.points)}")

# 可视化点云
o3d.visualization.draw_geometries([pcd])