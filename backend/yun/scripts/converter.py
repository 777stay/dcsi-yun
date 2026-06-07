import argparse
import json
import subprocess
import sys
import os
import shutil

def log(message):
    """将日志信息打印到标准错误流，方便 Java 捕获和记录"""
    print(f"PYTHON SCRIPT LOG: {message}", file=sys.stderr)

def main():
    """主执行函数"""
    parser = argparse.ArgumentParser(
        description="将 PLY 文件转换为 LAS，然后使用 PotreeConverter 处理。"
    )
    parser.add_argument("--input", required=True, help="输入的 .ply 文件路径")
    parser.add_argument("--potree_converter", required=True, help="PotreeConverter 的可执行文件路径")
    # 接收 Java 传来的输出目录参数
    parser.add_argument("--output", required=True, help="Potree 数据输出的基础目录")
    
    args = parser.parse_args()

    ply_file = args.input
    potree_converter_exe = args.potree_converter
    base_output_dir = args.output

    if not os.path.exists(ply_file):
        log(f"错误：输入文件不存在！路径: {ply_file}")
        sys.exit(1)

    # --- 步骤 1: 定义路径 ---
    base_name = os.path.splitext(os.path.basename(ply_file))[0]
    # 临时文件都放在同一目录下
    las_file = os.path.splitext(ply_file)[0] + ".las"
    pipeline_json_file = os.path.splitext(ply_file)[0] + ".json"
    
    # 最终输出目录
    potree_output_dir = os.path.join(base_output_dir, base_name)

    log(f"输入 PLY: {ply_file}")
    log(f"临时 LAS: {las_file}")
    log(f"Potree 输出目录: {potree_output_dir}")

    if os.path.exists(potree_output_dir):
        shutil.rmtree(potree_output_dir)

    # --- 步骤 2: 使用 PDAL CLI 将 PLY 转换为 LAS ---
    try:
        log("正在生成 PDAL Pipeline 配置文件...")
        pipeline_definition = {
            "pipeline": [
                {
                    "type": "readers.ply",
                    "filename": ply_file
                },
                {
                    "type": "writers.las",
                    "filename": las_file,
                    "minor_version": 2,
                    "dataformat_id": 3  # 支持 RGB
                }
            ]
        }
        
        # 将 pipeline 写入临时 JSON 文件
        with open(pipeline_json_file, 'w') as f:
            json.dump(pipeline_definition, f, indent=4)

        log("开始执行 PDAL 命令行工具进行转换...")
        # 调用系统安装的 pdal 命令
        pdal_cmd = ["pdal", "pipeline", pipeline_json_file]
        
        subprocess.run(
            pdal_cmd, 
            check=True, 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE,
            text=True
        )
        log("PDAL 转换成功。")

    except subprocess.CalledProcessError as e:
        log(f"PDAL 执行失败，退出码: {e.returncode}")
        log(f"PDAL 错误信息:\n{e.stderr}")
        sys.exit(1)
    except Exception as e:
        log(f"发生未知错误: {e}")
        sys.exit(1)

    # --- 步骤 3: 调用 PotreeConverter ---
    try:
        if os.path.exists(potree_converter_exe) and not os.access(potree_converter_exe, os.X_OK):
            os.chmod(potree_converter_exe, 0o755)

        log(f"开始调用 PotreeConverter...")
        cmd = [
            potree_converter_exe,
            las_file,
            "-o",
            potree_output_dir,
            "--generate-page", base_name
        ]
        
        result = subprocess.run(
            cmd, capture_output=True, text=True, check=True, encoding='utf-8'
        )
        
        log("PotreeConverter 执行成功。")
        if result.stderr:
            log(f"输出信息:\n{result.stderr}")

    except Exception as e:
        log(f"PotreeConverter 错误: {e}")
        sys.exit(1)
    finally:
        # 清理临时文件
        for f in [las_file, pipeline_json_file]:
            if os.path.exists(f):
                try:
                    os.remove(f)
                except:
                    pass

if __name__ == "__main__":
    main()
