# Yun Platform README Manual Design

## Goal

Replace the minimal root README with a Chinese-first project manual that helps
new GitHub users run the platform with Docker and helps developers develop,
debug, deploy, maintain, and troubleshoot it.

## Audience

- Users cloning the repository for the first time
- Frontend and backend developers
- Operators deploying the platform on a Linux server

## Document Structure

The root `README.md` will remain a single, navigable manual with these sections:

1. Project introduction and core capabilities
2. Technology stack and service architecture
3. Repository structure and service ports
4. Docker Compose quick start
5. Environment configuration
6. Runtime data and excluded large files
7. Local frontend and backend development
8. Debugging APIs, WebSockets, containers, and dependencies
9. Production deployment, upgrades, backup, and rollback
10. Security guidance and troubleshooting
11. Build and verification commands

## Primary Workflow

Docker Compose is the supported first-run path:

```bash
cp .env.example .env
docker compose up -d --build
```

Local development is documented as a secondary path. Developers start the
infrastructure services with Compose, then run the Vue frontend and Spring Boot
backend on the host with their native toolchains.

## Accuracy Rules

- Commands, paths, ports, service names, and environment variables must match
  the current repository.
- The document must not invent default user credentials or unsupported health
  endpoints.
- It must state that point clouds, captured images, generated reports, model
  weights, and environment-specific fusion datasets are excluded from Git.
- Features requiring external datasets or scripts must be marked as optional
  and unavailable until those assets are supplied.
- Production guidance must distinguish recommendations from behavior already
  implemented by the repository.

## Scope

Only the root `README.md` is changed during implementation. Application code,
Dockerfiles, Compose configuration, and runtime behavior remain unchanged.

## Verification

- Check every documented path and Compose service against the repository.
- Check every documented command against package scripts, Maven configuration,
  Dockerfiles, and `docker-compose.yml`.
- Run Markdown-oriented static checks using repository searches and
  `docker compose config --quiet`.
- Confirm the final Git diff contains documentation changes only.
