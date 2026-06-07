# Yun Platform

Yun Platform is a Vue 2 frontend and Spring Boot backend packaged with MySQL,
Redis, RocketMQ, and Nginx. The repository intentionally excludes runtime point
clouds, captured images, generated reports, and fusion datasets.

## Requirements

- Docker Engine 24 or newer
- Docker Compose v2
- At least 8 GB RAM and 20 GB free disk space for a full image build

## Start

```bash
cp .env.example .env
docker compose up -d --build
```

Open:

- Web application: `http://localhost`
- Backend API: `http://localhost:8080`

The frontend proxies `/api/` and `/ws/` to the backend, so deployment does not
depend on a fixed server IP.

Check startup status:

```bash
docker compose ps
docker compose logs -f backend-service frontend-service
```

Stop the platform:

```bash
docker compose down
```

Use `docker compose down -v` only when the MySQL data volume should also be
deleted.

## Configuration

Copy `.env.example` to `.env` and change at least:

```dotenv
MYSQL_ROOT_PASSWORD=replace-this-password
APP_JWT_SECRET=replace-with-a-long-random-secret
```

The optional published ports are `FRONTEND_PORT`, `BACKEND_PORT`, and
`GRPC_PORT`. Internal service communication always uses the Compose network.

## Runtime Data

The following host directories are mounted into the containers and ignored by
Git. Their `.gitkeep` files preserve the required directory structure.

| Directory | Content |
| --- | --- |
| `shared_data/images/` | Source images |
| `shared_data/pointclouds/` | Incoming point-cloud frames |
| `shared_data/ply_files/` | PLY conversion workspace |
| `shared_data/dist_data/` | Potree output served at `/dist/data/` |
| `shared_data/annotated/` | Annotated image output |
| `shared_data/reports/` | Generated report images |
| `shared_data/kml/` | Uploaded KML files |
| `shared_data/change_potree_view/` | External fusion datasets and scripts |
| `kml_output/` | Mission-planning output |

Point-cloud fusion is available only after its dataset and algorithm script are
placed under `shared_data/change_potree_view/<dataset>/`. These large,
environment-specific files are not included in the repository.

## Build Separately

Frontend:

```bash
cd frontend
npm ci
npm run build:prod
```

Backend:

```bash
cd backend/yun
mvn clean package
```
