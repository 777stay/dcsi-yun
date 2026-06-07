import argparse
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

import numpy as np
import open3d as o3d


DEFAULT_CAMERA_PARAMS = """{name}
  Rcl: [-0.0269079,   -0.999596,  0.00915031,
        -0.0159427, -0.00872334,   -0.999835,
          0.999511,  -0.0270493,  -0.0157015  ]
  Pcl: [-0.00398029, 0.0977342, 0.0260574]
img_time_offset: 0.2
cam_model: Pinhole
cam_width: 2048
cam_height: 1536
scale: 0.5
cam_fx: 1808.8
cam_fy: 1809.2
cam_cx: 1019.9
cam_cy: 773.4445
cam_d0: -0.0832
cam_d1: 0.2144
cam_d2: 0.00
cam_d3: 0.000
"""


def parse_args():
    parser = argparse.ArgumentParser(description="Prepare raw robot data, run fusion, and convert fused PLY to Potree.")
    parser.add_argument("--workspace-root", required=True)
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--fusion-script", required=True)
    parser.add_argument("--converter-script", required=True)
    parser.add_argument("--potree-converter", required=True)
    parser.add_argument("--potree-output", required=True)
    parser.add_argument("--public-prefix", default="dist/data")
    parser.add_argument("--image-stride", type=int, default=1)
    parser.add_argument("--max-images", type=int, default=None)
    return parser.parse_args()


def log(message):
    print(message, flush=True)


def parse_detection_boxes(raw):
    try:
        boxes = json.loads(raw)
    except Exception:
        return []

    rows = []
    for box in boxes:
        bbox = box.get("bbox") or []
        if len(bbox) != 4:
            continue
        label = box.get("class") or str(box.get("id", "0"))
        rows.append("{} {} {} {} {}".format(label, float(bbox[0]), float(bbox[1]), float(bbox[2]), float(bbox[3])))
    return rows


def write_label_files(detection_file, label_dir):
    label_dir.mkdir(parents=True, exist_ok=True)
    count = 0
    with detection_file.open("r", encoding="utf-8") as handle:
        next(handle, None)
        for line in handle:
            line = line.strip()
            if not line:
                continue
            match = re.match(r"^(\S+)\s+\S+\s+\S+\s+\S+\s+\S+\s+\S+\s+(\[.*\])$", line)
            if not match:
                continue
            rows = parse_detection_boxes(match.group(2))
            if not rows:
                continue
            (label_dir / "{}.txt".format(match.group(1))).write_text("\n".join(rows) + "\n", encoding="utf-8")
            count += 1
    return count


def copy_odom_without_header(source, target):
    with source.open("r", encoding="utf-8") as src, target.open("w", encoding="utf-8") as dst:
        for line in src:
            if line.strip().startswith("stamp"):
                continue
            dst.write(line)


def merge_pcd_to_ply(pcd_files, target_ply):
    point_parts = []
    color_parts = []
    for pcd_file in pcd_files:
        cloud = o3d.io.read_point_cloud(str(pcd_file))
        points = np.asarray(cloud.points)
        if points.size == 0:
            continue
        point_parts.append(points)
        colors = np.asarray(cloud.colors)
        if colors.shape[0] == points.shape[0]:
            color_parts.append(colors)

    if not point_parts:
        return 0

    merged = o3d.geometry.PointCloud()
    merged.points = o3d.utility.Vector3dVector(np.vstack(point_parts))
    if len(color_parts) == len(point_parts):
        merged.colors = o3d.utility.Vector3dVector(np.vstack(color_parts))
    o3d.io.write_point_cloud(str(target_ply), merged, write_ascii=False)
    return int(np.vstack(point_parts).shape[0])


def prepare_staging(dataset_root):
    stage_root = dataset_root / "_fusion_staging"
    if stage_root.exists():
        shutil.rmtree(stage_root)
    stage_root.mkdir(parents=True)

    robots = []
    robot_summaries = []
    for robot_dir in sorted(dataset_root.glob("robot_*")):
        if not robot_dir.is_dir():
            continue
        robot_name = robot_dir.name.lower()
        image_dir = robot_dir / "image"
        pcd_dir = robot_dir / "pcd"
        detection_file = robot_dir / "detection.txt"
        odom_file = robot_dir / "odom.txt"
        pcd_files = sorted(pcd_dir.glob("*.pcd")) if pcd_dir.exists() else []
        image_files = sorted(image_dir.glob("*.jpg")) if image_dir.exists() else []

        summary = {
            "robot": robot_name,
            "pcdCount": len(pcd_files),
            "imageCount": len(image_files),
            "usable": False,
            "labelCount": 0,
            "pointCount": 0,
        }
        if not pcd_files or not image_files or not detection_file.exists() or not odom_file.exists():
            robot_summaries.append(summary)
            continue

        staged_robot = stage_root / robot_name
        staged_robot.mkdir()
        os.symlink(str(image_dir), str(staged_robot / "{}_images".format(robot_name)))

        label_count = write_label_files(detection_file, staged_robot / "{}_labels".format(robot_name))
        copy_odom_without_header(odom_file, staged_robot / "{}_odom.txt".format(robot_name))
        (staged_robot / "{}_para.txt".format(robot_name)).write_text(
            DEFAULT_CAMERA_PARAMS.format(name=robot_name),
            encoding="utf-8",
        )
        point_count = merge_pcd_to_ply(pcd_files, staged_robot / "{}.ply".format(robot_name))
        if label_count > 0 and point_count > 0:
            robots.append(robot_name)
            summary["usable"] = True
            summary["labelCount"] = label_count
            summary["pointCount"] = point_count
        robot_summaries.append(summary)

    return stage_root, robots, robot_summaries


def run_command(command):
    log("RUN " + " ".join(command))
    completed = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if completed.stdout:
        print(completed.stdout, end="", flush=True)
    if completed.returncode != 0:
        raise subprocess.CalledProcessError(completed.returncode, command, output=completed.stdout)


def read_summary_robots(summary_path):
    if not summary_path.exists():
        return None
    try:
        summary = json.loads(summary_path.read_text(encoding="utf-8"))
    except Exception:
        return None
    robots = summary.get("robots")
    return robots if isinstance(robots, list) else None


def main():
    args = parse_args()
    workspace_root = Path(args.workspace_root)
    dataset_root = workspace_root / args.dataset
    output_dir = dataset_root / "multi_uav_instance_fusion_results"
    if not dataset_root.exists():
        raise RuntimeError("Dataset directory not found: {}".format(dataset_root))

    fusion_command = [
        "python3",
        args.fusion_script,
        "--root",
        str(dataset_root),
        "--output-dir",
        str(output_dir),
        "--image-stride",
        str(max(1, args.image_stride)),
    ]
    if args.max_images:
        fusion_command += ["--max-images", str(args.max_images)]
    run_command(fusion_command)

    fused_ply = output_dir / "multi_uav_overlay_downsampled.ply"
    if not fused_ply.exists():
        raise RuntimeError("Fused PLY was not generated: {}".format(fused_ply))

    convert_input = output_dir / "{}_fused.ply".format(args.dataset)
    shutil.copyfile(str(fused_ply), str(convert_input))
    run_command(
        [
            "python3",
            args.converter_script,
            "--input",
            str(convert_input),
            "--potree_converter",
            args.potree_converter,
            "--output",
            args.potree_output,
        ]
    )

    metadata_url = "{}/{}/pointclouds/{}/metadata.json".format(
        args.public_prefix.rstrip("/"),
        "{}_fused".format(args.dataset),
        "{}_fused".format(args.dataset),
    )
    robot_summaries = read_summary_robots(output_dir / "multi_uav_summary.json")
    result = {
        "dataset": args.dataset,
        "name": "{} fused".format(args.dataset),
        "url": metadata_url,
        "robots": robot_summaries,
        "fusedPly": str(fused_ply),
    }
    print("FUSION_RESULT_JSON=" + json.dumps(result, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
