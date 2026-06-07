# Yun Platform 无人机与点云数据管理平台

Yun Platform 是一个面向无人机任务规划、机器人状态监控、轨迹展示、图像与点云数据管理、Potree 点云浏览和多机点云融合的前后端一体化平台。

项目采用 Vue 2 + Spring Boot 架构，并通过 Docker Compose 编排 Nginx、后端、MySQL、Redis 和 RocketMQ。仓库已经移除运行时产生的大型点云、图片、报告及环境相关融合数据，便于上传 GitHub、克隆和重新部署。

> 本文档以 Linux 服务器和 Docker Compose 部署为主要场景。Windows 和 macOS 可以使用 Docker Desktop，但涉及点云转换、Python 算法及宿主机文件权限时，建议优先使用 Linux。

## 目录

- [主要功能](#主要功能)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [项目结构](#项目结构)
- [端口与服务](#端口与服务)
- [环境要求](#环境要求)
- [Docker 快速运行](#docker-快速运行)
- [配置说明](#配置说明)
- [运行数据与大文件](#运行数据与大文件)
- [本地开发](#本地开发)
- [调试指南](#调试指南)
- [生产部署](#生产部署)
- [升级与回滚](#升级与回滚)
- [数据备份与恢复](#数据备份与恢复)
- [安全建议](#安全建议)
- [常见问题](#常见问题)
- [构建与验证](#构建与验证)
- [GitHub 发布](#github-发布)

## 主要功能

平台当前包含以下主要功能模块：

- **无人机任务规划**：在地图中绘制任务区域、障碍区域和起点，生成多无人机航线，支持 KML 文件处理。
- **卫星地图轨迹查看**：通过 WebSocket 接收机器人状态和轨迹数据，并在地图中实时展示。
- **机器人设备管理**：查看机器人状态、任务会话、轨迹、图像和点云数据。
- **点云数据查看**：使用 Potree 和 Three.js 加载、浏览和交互查看点云。
- **多视角数据查看**：从不同视角查看机器人采集的数据。
- **多机点云融合**：调用后端 Python 融合流程，生成可由 Potree 访问的结果。
- **图像与点云查询**：按机器人、任务会话、分类和时间范围查询历史数据。
- **数据流控制**：控制数据接收状态，并通过 Redis、RocketMQ 和 MySQL 协同处理数据。
- **变化检测与报告数据**：提供相关接口和页面；完整算法、模型权重及业务数据需要由部署者另外提供。

部分算法功能依赖未纳入 Git 的模型、脚本或数据集。缺少这些外部资源时，平台基础页面和数据管理功能仍可运行，但对应算法功能不可用。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 2.6、Vue Router、Vuex、Element UI、Axios |
| 地图与三维 | Leaflet、Three.js、Potree |
| 后端 | Java 8、Spring Boot 2.7、Spring Security、MyBatis-Plus |
| 实时通信 | WebSocket、gRPC |
| 消息队列 | Apache RocketMQ 4.9.4 |
| 数据库与缓存 | MySQL 8.0、Redis 6.2 |
| 算法与处理 | Python 3、PyTorch CPU、PDAL、PotreeConverter |
| 网关与静态资源 | Nginx 1.25 |
| 构建与编排 | Maven、npm、Docker、Docker Compose |

## 系统架构

```text
浏览器
  |
  | HTTP / WebSocket
  v
Nginx (frontend-service)
  |-- /                 -> Vue 静态页面
  |-- /api/             -> backend-service:8080
  |-- /ws/              -> backend-service:8080
  |-- /dist/data/       -> Potree 运行数据
  `-- /data/            -> 图片与点云文件
                          |
                          v
                Spring Boot (backend-service)
                  |-- MySQL
                  |-- Redis
                  |-- RocketMQ
                  |-- gRPC :50052
                  |-- Python / PDAL / PotreeConverter
                  `-- 宿主机共享数据目录
```

前端生产环境使用同源路径 `/api` 和 `/ws`，由 Nginx 转发到后端。因此从其他服务器或电脑访问时，不需要在前端代码中写死部署服务器 IP。

## 项目结构

```text
yun-platform/
├── backend/
│   └── yun/                         # Spring Boot 后端
│       ├── src/main/java/           # Java 源码
│       ├── src/main/resources/      # 配置、Mapper 和静态资源
│       ├── scripts/                 # 容器内调用的 Python 脚本
│       ├── py2json/                 # 航线/KML 转换工具
│       ├── PotreeConverter/         # Potree 转换程序
│       ├── lib/                     # 本地 JAR 依赖
│       └── pom.xml
├── frontend/
│   ├── src/                         # Vue 页面、组件、路由和状态管理
│   ├── public/Potree/               # Potree 前端运行库
│   ├── tests/                       # 前端测试
│   └── package.json
├── deploy/
│   ├── Dockerfile.frontend          # 前端多阶段构建
│   ├── Dockerfile.backend           # 后端及算法运行环境构建
│   ├── nginx.conf                   # 前端和反向代理配置
│   ├── requirements.txt             # Python 依赖
│   └── sql/init.sql                 # MySQL 首次初始化脚本
├── shared_data/                     # 容器与宿主机共享的运行数据
├── kml_output/                      # 航线规划输出
├── docker-compose.yml
├── .env.example
└── README.md
```

## 端口与服务

| Compose 服务 | 容器端口 | 默认宿主机端口 | 用途 |
| --- | ---: | ---: | --- |
| `frontend-service` | 80 | 80 | Web 页面、API/WebSocket 反向代理 |
| `backend-service` | 8080 | 8080 | Spring Boot API |
| `backend-service` | 50052 | 50052 | gRPC 服务 |
| `mysql` | 3306 | 3306 | MySQL 数据库 |
| `redis` | 6379 | 6379 | Redis 缓存 |
| `namesrv` | 9876 | 9876 | RocketMQ NameServer |
| `broker` | 10909、10911 | 10909、10911 | RocketMQ Broker |

`FRONTEND_PORT`、`BACKEND_PORT` 和 `GRPC_PORT` 可以在 `.env` 中修改。MySQL、Redis 和 RocketMQ 的宿主机端口目前直接定义在 `docker-compose.yml` 中。

## 环境要求

### Docker 运行

推荐配置：

- 64 位 Linux
- Docker Engine 24 或更高版本
- Docker Compose v2
- 至少 8 GB 内存
- 建议 4 核 CPU
- 至少 20 GB 可用磁盘空间

后端镜像包含 PyTorch、PDAL 和 Python 科学计算依赖，首次构建下载量较大。执行完整点云或融合任务时，建议使用 16 GB 或更多内存。

检查环境：

```bash
docker --version
docker compose version
docker info
```

### 本地开发

除 Docker 外，还需要：

- Node.js 14.x 和 npm
- JDK 8
- Maven 3.8+
- 可选：Python 3、PDAL 和项目算法依赖

前端 Dockerfile 固定使用 Node.js 14，开发环境也建议保持相同版本，避免旧版 Vue CLI 与新版本 Node.js 的兼容问题。

## Docker 快速运行

### 1. 克隆项目

```bash
git clone <你的 GitHub 仓库地址>
cd yun-platform
```

### 2. 创建环境配置

```bash
cp .env.example .env
```

至少修改以下两项：

```dotenv
MYSQL_ROOT_PASSWORD=请替换为数据库强密码
APP_JWT_SECRET=请替换为足够长的随机字符串
```

可以生成随机 JWT 密钥：

```bash
openssl rand -base64 48
```

不要把 `.env` 提交到 Git。根目录 `.gitignore` 已默认忽略该文件。

### 3. 构建并启动

```bash
docker compose up -d --build
```

首次构建可能需要较长时间。后端镜像会下载 Maven、APT、PyTorch 和 Python 依赖，前端镜像会安装 npm 依赖并生成生产构建。

### 4. 查看启动状态

```bash
docker compose ps
docker compose logs --tail=100 mysql redis namesrv broker
docker compose logs --tail=100 backend-service frontend-service
```

持续查看后端日志：

```bash
docker compose logs -f backend-service
```

`depends_on` 只控制容器启动顺序，不代表 MySQL、Redis 或 RocketMQ 已完全就绪。首次启动时后端可能短暂连接失败；由于服务设置了自动重启，基础设施就绪后会再次启动。若持续失败，请按[常见问题](#常见问题)检查。

### 5. 访问平台

- Web 页面：`http://localhost`
- 后端 API：`http://localhost:8080`
- 从局域网其他电脑访问：`http://<服务器IP>`

若修改了 `FRONTEND_PORT`，例如：

```dotenv
FRONTEND_PORT=8088
```

则访问地址为 `http://localhost:8088`。

平台未在文档中提供通用默认账号。请根据初始化数据库中的实际用户数据登录，或通过注册页面/API 创建用户。

### 6. 停止或重启

停止并保留数据：

```bash
docker compose down
```

重启：

```bash
docker compose restart
```

仅重建前端：

```bash
docker compose up -d --build frontend-service
```

仅重建后端：

```bash
docker compose up -d --build backend-service
```

删除 MySQL 数据卷并重新初始化：

```bash
docker compose down -v
docker compose up -d --build
```

> `docker compose down -v` 会永久删除 Compose 管理的 MySQL 数据卷。执行前必须备份数据库。

## 配置说明

### `.env` 配置

| 变量 | 默认示例 | 说明 |
| --- | --- | --- |
| `TZ` | `Asia/Shanghai` | 容器时区 |
| `MYSQL_ROOT_PASSWORD` | 无安全默认值 | MySQL root 密码，生产环境必须修改 |
| `MYSQL_DATABASE` | `mydatabase` | 首次创建的数据库名 |
| `FRONTEND_PORT` | `80` | Web 页面宿主机端口 |
| `BACKEND_PORT` | `8080` | 后端 API 宿主机端口 |
| `GRPC_PORT` | `50052` | gRPC 宿主机端口 |
| `JAVA_TOOL_OPTIONS` | `-Xms2g -Xmx4g -XX:+UseG1GC` | JVM 内存及 GC 参数 |
| `APP_JWT_SECRET` | 无安全默认值 | JWT 签名密钥，生产环境必须修改 |
| `FUSION_PUBLIC_PREFIX` | `/dist/data` | 后端返回给前端的融合结果 URL 前缀 |

`.env.example` 中的 `MYSQL_USER` 和 `MYSQL_PASSWORD` 当前不参与 Compose 服务配置；后端和 MySQL 使用 root 用户及 `MYSQL_ROOT_PASSWORD`。修改数据库用户模型时，需要同步修改 `docker-compose.yml`。

### 后端容器环境

`backend-service` 使用 `prod1` Spring Profile，并由 Compose 覆盖服务地址：

```text
MySQL       mysql:3306
Redis       redis:6379
RocketMQ    namesrv:9876
```

容器内部服务之间通过 `yun-net` 网络和 Compose 服务名通信，不使用宿主机 IP。

### 前端运行时地址

生产构建中的 `VUE_APP_BASE_API` 为 `/api`。相关页面通过当前浏览器访问的 host 生成 API 和 WebSocket 地址：

```text
HTTP API    当前页面 host + /api
WebSocket   ws(s)://当前页面 host/ws/...
```

部署到新的 IP 或域名后，不需要重新写死后端 IP。若使用 HTTPS，WebSocket 会自动使用 `wss://`。

## 运行数据与大文件

仓库有意排除点云、采集图片、生成报告、模型权重和环境相关融合数据。Docker 启动后，下列宿主机目录会挂载到容器：

| 宿主机目录 | 用途 | 容器主要路径 |
| --- | --- | --- |
| `shared_data/images/` | 原始图片 | `/app/data/images` |
| `shared_data/pointclouds/` | 原始点云帧 | `/app/data/pointclouds` |
| `shared_data/ply_files/` | PLY 临时处理目录 | `/app/ply_files` |
| `shared_data/dist_data/` | Potree 转换结果 | `/app/dist/data` |
| `shared_data/annotated/` | 标注图片 | `/app/data/annotated_images` |
| `shared_data/reports/` | 报告图片 | `/app/data/report_images` |
| `shared_data/kml/` | 上传的 KML | `/app/data/kml` |
| `shared_data/change_potree_view/` | 融合数据集和外部算法 | `/app/change_potree_view` |
| `kml_output/` | 航线规划输出 | `/app/py2json/cppFiles` |

这些目录只提交 `.gitkeep`，实际内容被 `.gitignore` 和 `.dockerignore` 排除。

### 点云融合数据

多机点云融合依赖部署环境提供的数据集和算法脚本。建议目录形式：

```text
shared_data/change_potree_view/
└── <数据集名称>/
    ├── fuse_cloud_instances_3d_multi_uav_scattered_pcd.py
    ├── 输入点云或中间数据
    └── 算法所需的其他资源
```

后端默认配置中包含一个数据集名称和脚本路径示例。部署自己的数据集时，应同步修改：

```text
backend/yun/src/main/resources/application-prod1.yml
```

不要把大型数据集、`.pcd`、`.ply`、`.las`、`.laz`、模型权重或生产图片直接提交到 GitHub。需要分发样例数据时，可以使用独立对象存储、发布附件或 Git LFS。

### 文件权限

容器需要写入共享目录。若日志出现 `Permission denied`，检查：

```bash
ls -ld shared_data shared_data/* kml_output
```

开发环境可以先将目录所有者改为当前用户：

```bash
sudo chown -R "$(id -u):$(id -g)" shared_data kml_output
```

生产环境应根据实际容器用户和权限策略设置更严格的权限，不建议长期使用 `chmod -R 777`。

## 本地开发

推荐采用“基础设施运行在 Docker，前后端运行在宿主机”的方式。这样既保留 MySQL、Redis 和 RocketMQ 的一致环境，又能使用前端热更新和 Java IDE 调试。

### 1. 启动基础设施

```bash
docker compose up -d mysql redis namesrv broker
docker compose ps
```

默认服务地址：

```text
MySQL       127.0.0.1:3306
Redis       127.0.0.1:6379
RocketMQ    127.0.0.1:9876
```

### 2. 启动后端

```bash
cd backend/yun

export SPRING_PROFILES_ACTIVE=prod1
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/mydatabase?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true'
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD='你的 MYSQL_ROOT_PASSWORD'
export SPRING_REDIS_HOST=127.0.0.1
export SPRING_REDIS_PORT=6379
export ROCKETMQ_NAME_SERVER=127.0.0.1:9876
export ROCKETMQ_NAMESRV_ADDR=127.0.0.1:9876
export APP_JWT_SECRET='本地开发使用的长随机字符串'

mvn spring-boot:run
```

后端启动后监听：

- HTTP：`http://localhost:8080`
- gRPC：`localhost:50052`

本地直接运行后端时，`application-prod1.yml` 中部分算法路径以 `/app` 为基础，这是容器内路径。需要调试点云转换、融合和 Python 脚本时，建议：

1. 使用后端容器调试；或
2. 在 IDE 中覆盖 `project.path`、融合工作目录和脚本路径，使其指向本地绝对路径。

仅调试普通 API、认证、数据库和消息处理时，不需要修改这些算法路径。

### 3. 启动前端

打开另一个终端：

```bash
cd frontend
npm ci
VUE_APP_BASE_API=http://localhost:8080 npm run dev
```

Vue CLI 默认开发地址通常为：

```text
http://localhost:9528
```

以终端实际输出为准。

`frontend/.env.development` 可能包含特定开发网络的地址。使用命令行设置 `VUE_APP_BASE_API` 可以覆盖该值，避免把本机开发请求发送到旧服务器。

若需要让局域网其他设备访问开发服务器：

```bash
VUE_APP_BASE_API=http://<开发机IP>:8080 npm run dev -- --host 0.0.0.0
```

同时确认开发机防火墙允许前端和后端端口。

### 4. 前端常用命令

```bash
cd frontend

npm run dev
npm run lint
npm run test:unit
npm run build:prod
npm run preview
```

当前测试依赖来自较旧的 Vue CLI/Jest 生态。在部分 Node/npm 组合中，单元测试可能因 `jsdom` 或历史依赖冲突而在测试收集阶段失败。优先使用 Node.js 14 和锁定的 `package-lock.json`，不要随意删除锁文件升级全部依赖。

### 5. 后端常用命令

```bash
cd backend/yun

mvn test
mvn clean package
mvn clean package -DskipTests
mvn spring-boot:run
```

Maven 构建成功后会生成：

```text
backend/yun/target/yun-0.0.1-SNAPSHOT.jar
```

## 调试指南

### 查看容器状态

```bash
docker compose ps
docker compose top
docker stats
```

检查单个容器：

```bash
docker inspect yun-backend
docker inspect yun-frontend
```

### 查看日志

```bash
docker compose logs --tail=200 backend-service
docker compose logs --tail=200 frontend-service
docker compose logs --tail=200 mysql
docker compose logs --tail=200 redis
docker compose logs --tail=200 namesrv broker
```

同时跟踪多个服务：

```bash
docker compose logs -f backend-service frontend-service
```

### 进入容器

```bash
docker compose exec backend-service bash
docker compose exec frontend-service sh
docker compose exec mysql bash
docker compose exec redis sh
```

后端容器内常用检查：

```bash
java -version
python3 --version
pdal --version
ls -la /app/data /app/dist/data /app/change_potree_view
```

### API 调试

确认 Nginx 到后端的代理链：

```bash
curl -i http://localhost/api/robots
```

直接访问后端：

```bash
curl -i http://localhost:8080/api/robots
```

部分接口受 Spring Security 和 JWT 保护。收到 `401` 或 `403` 时，先确认用户已登录并在请求中携带：

```text
Authorization: Bearer <token>
```

浏览器调试时重点检查：

1. 开发者工具 `Network` 中请求 URL 是否使用当前部署 host。
2. `/api/...` 请求是否返回 `401`、`403`、`404` 或 `5xx`。
3. WebSocket 是否连接到 `/ws/...`。
4. 控制台是否出现跨域、Mixed Content 或资源路径错误。

### WebSocket 调试

生产部署应通过前端 Nginx 访问 WebSocket：

```text
ws://<服务器>/ws/status
ws://<服务器>/ws/data/<机器人或传感器ID>
```

HTTPS 部署必须使用 `wss://`。若连接后立即断开，检查：

```bash
docker compose logs -f frontend-service backend-service
```

并确认外层反向代理保留以下头部：

```nginx
proxy_http_version 1.1;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

### 数据库调试

进入 MySQL：

```bash
docker compose exec mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"'
```

查看数据库和表：

```sql
SHOW DATABASES;
USE mydatabase;
SHOW TABLES;
```

`deploy/sql/init.sql` 只在 MySQL 数据卷首次创建时执行。修改初始化 SQL 后，仅重启容器不会重新执行。

### Redis 调试

```bash
docker compose exec redis redis-cli ping
docker compose exec redis redis-cli INFO server
```

正常情况下 `PING` 返回：

```text
PONG
```

### RocketMQ 调试

```bash
docker compose logs --tail=200 namesrv broker
docker compose exec namesrv sh -c 'echo "$NAMESRV_ADDR"'
```

后端日志出现 NameServer 连接失败时，确认 Compose 中使用的是 `namesrv:9876`，本地开发时使用的是 `127.0.0.1:9876`。

### 点云与静态文件调试

检查文件是否进入共享目录：

```bash
find shared_data/pointclouds -maxdepth 2 -type f | head
find shared_data/dist_data -maxdepth 3 -type f | head
```

检查 Nginx 是否可以访问 Potree 输出：

```bash
curl -I http://localhost/dist/data/<数据集>/metadata.json
```

实际文件名可能是 `metadata.json` 或 `meta.json`，以转换结果为准。

若后端返回了数据但页面不显示，依次检查：

1. 返回 URL 是否以 `/dist/data` 开头。
2. 对应文件是否存在于 `shared_data/dist_data/`。
3. 浏览器是否成功请求 metadata、octree 和层级文件。
4. Nginx 容器是否挂载了相同目录。
5. 浏览器控制台是否出现 CORS、404 或 WebGL 错误。

### Java 远程调试

仓库默认未开放 JDWP 端口。需要远程调试后端容器时，可以在本地临时修改 Compose：

```yaml
backend-service:
  ports:
    - "5005:5005"
  environment:
    JAVA_TOOL_OPTIONS: >-
      -Xms2g -Xmx4g -XX:+UseG1GC
      -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

然后重建后端：

```bash
docker compose up -d --build backend-service
```

生产环境不要向公网开放调试端口。

## 生产部署

### 1. 服务器准备

建议：

- 使用受支持的 Linux 发行版。
- 为项目准备独立目录和受限系统用户。
- 配置 Docker 开机启动。
- 确保点云和数据库备份位于独立磁盘或远程存储。
- 使用域名和 HTTPS 对外提供服务。

示例：

```bash
sudo systemctl enable --now docker
git clone <你的 GitHub 仓库地址> /opt/yun-platform
cd /opt/yun-platform
cp .env.example .env
```

编辑 `.env`，设置强密码、随机 JWT 密钥和合适的 JVM 内存。

### 2. 调整生产端口

若使用宿主机 Nginx、Caddy 或云负载均衡器，建议让内部前端只监听回环地址。当前 Compose 端口格式会监听所有网卡，可以将前端端口临时改为：

```yaml
ports:
  - "127.0.0.1:${FRONTEND_PORT:-8088}:80"
```

同样建议限制 MySQL、Redis、RocketMQ、后端 API 和 gRPC 的宿主机暴露范围。若外部系统不需要直接访问这些服务，可以删除对应 `ports`，或绑定到 `127.0.0.1`。

### 3. 启动服务

```bash
docker compose pull
docker compose build --pull
docker compose up -d
docker compose ps
```

当前前后端镜像由本地源码构建，`docker compose pull` 主要更新 MySQL、Redis、RocketMQ 及构建基础镜像。

### 4. 配置外层 HTTPS 反向代理

若将 Compose 的 `FRONTEND_PORT` 设置为 `8088`，宿主机 Nginx 可以统一代理到前端容器：

```nginx
server {
    listen 80;
    server_name example.com;

    location / {
        proxy_pass http://127.0.0.1:8088;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://127.0.0.1:8088;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
```

配置 HTTPS 证书后，将 80 重定向到 443。前端运行时会根据页面协议自动选择 `ws://` 或 `wss://`。

### 5. 防火墙

公网通常只需要开放：

- `80/tcp`
- `443/tcp`

以下端口不应直接暴露到公网，除非有明确业务需求和访问控制：

- `3306`
- `6379`
- `9876`
- `10909`
- `10911`
- `50052`
- `8080`

### 6. 资源监控

```bash
docker stats
df -h
du -sh shared_data/* kml_output
docker system df
```

不要直接执行 `docker system prune --volumes`，该命令可能删除未使用的数据卷。清理前应确认 MySQL 数据卷和业务文件已有备份。

## 升级与回滚

### 升级

升级前先备份数据库和共享数据：

```bash
git status
git pull --ff-only
docker compose build --pull
docker compose up -d
docker compose ps
docker compose logs --tail=100 backend-service frontend-service
```

`docker compose up -d` 会替换发生变化的容器，但保留 MySQL 命名卷和宿主机共享目录。

### 回滚

记录升级前提交：

```bash
git rev-parse HEAD
```

需要回滚时切换到经过验证的提交或标签，并重新构建：

```bash
git switch --detach <提交或标签>
docker compose build
docker compose up -d
```

若升级包含数据库结构变更，仅回滚代码可能不够。应同时准备与该版本匹配的数据库备份和迁移方案。

## 数据备份与恢复

### 备份 MySQL

```bash
mkdir -p backups
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot \
  --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
  > "backups/mysql-$(date +%Y%m%d-%H%M%S).sql"
```

### 恢复 MySQL

```bash
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' \
  < backups/<备份文件>.sql
```

恢复会修改当前数据库。生产环境执行前应停止写入流量并再次确认备份文件。

### 备份运行数据

```bash
mkdir -p backups
tar -czf "backups/runtime-$(date +%Y%m%d-%H%M%S).tar.gz" \
  shared_data kml_output
```

点云目录可能非常大。生产环境建议使用支持增量、校验和保留策略的备份工具，并将备份复制到另一台机器或对象存储。

### 恢复运行数据

停止会写入共享目录的服务后再恢复：

```bash
docker compose stop backend-service frontend-service
tar -xzf backups/<运行数据备份>.tar.gz
docker compose up -d
```

恢复后检查目录权限和容器日志。

## 安全建议

部署前至少完成以下事项：

1. 修改 `MYSQL_ROOT_PASSWORD`。
2. 使用 `openssl rand -base64 48` 生成新的 `APP_JWT_SECRET`。
3. 不提交 `.env`、数据库备份、点云、图片、私钥和模型权重。
4. 仅向公网开放 80/443，限制数据库、Redis、RocketMQ、gRPC 和调试端口。
5. 使用 HTTPS，避免 JWT 和业务数据明文传输。
6. 为服务器、Docker、基础镜像和系统软件安装安全更新。
7. 定期备份 MySQL、`shared_data/` 和 `kml_output/`，并验证恢复流程。
8. 对上传文件大小、来源和格式实施业务侧限制。
9. 根据实际并发和文件大小设置反向代理超时及请求体大小。
10. 生产环境不要继续使用 `.env.example` 中的示例密码或 JWT 密钥。

## 常见问题

### 1. `docker compose up` 后页面无法访问

检查：

```bash
docker compose ps
docker compose logs --tail=200 frontend-service
ss -lntp | grep -E ':80|:8088'
```

常见原因：

- 80 端口已被宿主机 Nginx、Apache 或其他服务占用。
- `FRONTEND_PORT` 已修改，但仍访问旧端口。
- 防火墙未放行对应端口。
- Nginx 因无法解析 `backend-service` 而退出，通常意味着未通过 Compose 网络启动。

### 2. 后端持续重启

```bash
docker compose logs --tail=300 backend-service
docker compose ps mysql redis namesrv broker
```

重点检查：

- MySQL 密码或数据库名是否一致。
- Redis 是否返回 `PONG`。
- RocketMQ NameServer 和 Broker 是否已启动。
- JVM 内存是否超过机器可用内存。
- 共享目录和算法文件是否有权限或路径错误。

### 3. MySQL 登录失败

如果已经创建过 MySQL 数据卷，修改 `.env` 中的密码不会自动修改现有数据库 root 密码。

可以：

1. 使用旧密码登录后在数据库中修改密码；或
2. 备份数据后执行 `docker compose down -v` 重新初始化。

不要在有生产数据时直接删除数据卷。

### 4. 修改 `init.sql` 后没有生效

`deploy/sql/init.sql` 只在空 MySQL 数据目录初始化时执行。已有 `mysql_data` 卷时不会重复执行。

开发环境可在确认不需要旧数据后执行：

```bash
docker compose down -v
docker compose up -d
```

生产环境应使用正式数据库迁移脚本，不应通过删除数据卷更新表结构。

### 5. 前端请求旧 IP

生产环境应通过 Dockerfile 使用 `.env.production` 构建，并由 Nginx 代理 `/api`。

本地开发时显式覆盖：

```bash
VUE_APP_BASE_API=http://localhost:8080 npm run dev
```

修改环境变量后需要重新启动 Vue 开发服务器或重新构建前端镜像。

### 6. 前端收到数据但页面不显示

检查浏览器开发者工具：

- API 返回结构是否符合页面预期。
- 点云或图片 URL 是否指向当前部署 host。
- `/dist/data/` 文件是否返回 200。
- WebSocket 是否持续连接。
- 是否存在 CORS、Mixed Content、404 或 JavaScript 异常。

再检查服务器：

```bash
find shared_data/dist_data -maxdepth 3 -type f | head
docker compose logs --tail=200 backend-service frontend-service
```

### 7. Docker 构建停在 npm、Maven 或镜像元数据下载

先确认宿主机网络和 DNS：

```bash
curl -I https://registry.npmjs.org/
curl -I https://repo.maven.apache.org/maven2/
docker pull node:14-bullseye
```

若宿主机可以访问但 Docker 构建无法解析域名，检查 Docker daemon DNS 配置和企业代理设置。修改 Docker 配置后重启 Docker，再执行：

```bash
docker compose build --no-cache frontend-service
```

不要在不可信环境中使用来源不明的 npm 或 Maven 镜像。

### 8. 后端镜像构建时间过长

后端镜像包含：

- Maven 依赖
- Java 应用
- APT 系统包
- PyTorch CPU
- Python 科学计算与点云依赖

首次构建耗时较长属于预期。保留 Docker BuildKit 缓存，日常不要频繁使用 `--no-cache`。

### 9. 机器内存不足或容器被杀死

检查：

```bash
docker stats
dmesg | grep -i -E 'out of memory|killed process'
```

降低 `.env` 中 JVM 参数，例如开发机可使用：

```dotenv
JAVA_TOOL_OPTIONS=-Xms512m -Xmx2g -XX:+UseG1GC
```

大型点云转换和融合任务仍可能需要更多内存。

### 10. 融合功能提示数据集或脚本不存在

确认：

```bash
find shared_data/change_potree_view -maxdepth 3 -type f | head
docker compose exec backend-service \
  ls -la /app/change_potree_view /app/scripts
```

仓库不包含生产融合数据集。需要把对应数据和算法脚本放入共享目录，并使 `application-prod1.yml` 中的配置与目录名称一致。

### 11. Potree 页面显示空白

确认：

- metadata 文件路径正确。
- metadata 引用的 octree 文件完整。
- Nginx 能访问 `/dist/data/`。
- 浏览器支持 WebGL。
- 点云坐标、包围盒和点预算合理。

使用浏览器 Network 面板查看第一个失败的资源请求，通常比只看页面报错更容易定位问题。

## 构建与验证

### 验证 Compose

```bash
docker compose config --quiet
docker compose config --services
```

预期服务：

```text
mysql
namesrv
redis
broker
backend-service
frontend-service
```

### 单独构建前端

本地构建：

```bash
cd frontend
npm ci
npm run build:prod
```

Docker 构建：

```bash
docker build \
  -f deploy/Dockerfile.frontend \
  -t yun-platform-frontend:local .
```

### 单独构建后端

本地构建：

```bash
cd backend/yun
mvn clean package
```

Docker 构建：

```bash
docker build \
  -f deploy/Dockerfile.backend \
  -t yun-platform-backend:local .
```

### 完整验证

```bash
docker compose build
docker compose up -d
docker compose ps
docker compose logs --tail=100 backend-service frontend-service
```

验证结束后：

```bash
docker compose down
```

## GitHub 发布

提交前检查是否混入大文件或运行数据：

```bash
git status
git ls-files | grep -E '\.(pcd|ply|las|laz|e57|bag|tif|tiff)$' || true
git ls-files -z | xargs -0 -r du -b | sort -nr | head -30
```

配置远程仓库并推送：

```bash
git remote add origin https://github.com/<用户名>/<仓库名>.git
git branch -M main
git push -u origin main
```

如果已经存在 `origin`：

```bash
git remote set-url origin https://github.com/<用户名>/<仓库名>.git
git push -u origin main
```

克隆后的标准启动流程：

```bash
git clone https://github.com/<用户名>/<仓库名>.git
cd <仓库名>
cp .env.example .env
# 编辑 .env
docker compose up -d --build
```

## 许可证与第三方组件

仓库包含 Vue Admin Template、Potree、Three.js、Element UI 等第三方组件。发布或分发项目前，请检查根项目及各第三方目录中的许可证文件，并确保实际使用方式满足对应许可证要求。

当前根目录未声明统一的项目许可证时，不应默认认为全部业务代码均可自由再分发。若计划公开协作，建议项目维护者补充明确的根级 `LICENSE` 和贡献说明。
