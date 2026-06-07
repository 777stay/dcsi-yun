# Yun Platform README Manual Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the minimal root README with an accurate Chinese manual for running, developing, debugging, deploying, and maintaining Yun Platform.

**Architecture:** Keep Docker Compose as the primary onboarding path and describe native frontend/backend development as a secondary workflow. Derive all commands, service names, paths, ports, and limitations from the repository rather than adding unsupported behavior.

**Tech Stack:** Markdown, Docker Compose, Vue 2, Node.js 14, Spring Boot 2.7, Java 8, Maven, MySQL 8, Redis 6.2, RocketMQ 4.9.4, Nginx, Potree.

---

### Task 1: Write The Complete Manual

**Files:**
- Modify: `README.md`

- [x] **Step 1: Replace the minimal README**

Write a Chinese-first manual covering introduction, features, architecture,
directory layout, prerequisites, quick start, configuration, runtime data,
native development, debugging, deployment, backup, upgrades, security,
troubleshooting, and verification.

- [x] **Step 2: Keep claims within repository capabilities**

Do not document default login credentials, health endpoints, bundled datasets,
or automated production features that do not exist in the repository.

### Task 2: Verify Documentation Accuracy

**Files:**
- Verify: `README.md`
- Verify: `docker-compose.yml`
- Verify: `.env.example`
- Verify: `frontend/package.json`
- Verify: `backend/yun/pom.xml`

- [x] **Step 1: Validate Markdown structure and references**

Run:

```bash
rg -n '^##? ' README.md
rg -o '`[^`]+/[^`]*`' README.md
```

Expected: all major sections appear in the table of contents and referenced
repository paths exist or are explicitly described as user-provided runtime
paths.

- [x] **Step 2: Validate Compose configuration**

Run:

```bash
docker compose config --quiet
docker compose config --services
```

Expected: configuration exits successfully and lists `mysql`, `redis`,
`namesrv`, `broker`, `backend-service`, and `frontend-service`.

- [x] **Step 3: Validate the documentation-only diff**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; only `README.md` and this implementation plan
are modified or untracked after the previously committed design document.
