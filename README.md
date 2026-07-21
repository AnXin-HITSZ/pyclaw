# SaaS Claw

SaaS Claw 是一个以 SaaS 形态交付的平台，核心产品单元叫 **Claw**——一个隔离的独立执行环境，每个 Claw 可运行多个 Agent。

- 域名：`saas.claw.anxin-hitsz.com`
- K8s namespace：`saas-claw`
- 部署方式：GitHub Actions → ACR → ECS K3s

## 架构

9 个微服务，详见 [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md)。

| 层 | 服务 | 端口 | 技术 |
|------|------|------|------|
| 入口 | gateway | 8080 | Spring Cloud Gateway |
| 聚合 | backend-for-frontend | 8081 | Spring Web MVC |
| 领域 | claw-service | 8082 | Spring + JPA |
| 领域 | runtime-service | 8083 | Spring + JPA |
| 领域 | agent-marketplace-service | 8084 | Spring + JPA + OSS |
| 领域 | billing-service | 8085 | Spring + JPA |
| 领域 | skill-marketplace-service | 8086 | Spring + JPA + OSS |
| 运行时 | control-plane | 8090 | FastAPI |
| 运行时 | claw-runner | 8091 | FastAPI（每 Claw 一个） |

## 快速开始

### Java 后端

```bash
cd backend

# 编译全部 7 个模块
mvn -q compile

# 运行单个服务测试
mvn -pl claw-service test

# 本地启动某个服务（需要 MySQL + Redis）
mvn -pl gateway spring-boot:run
```

### Python Runtime

```bash
cd runtime/control-plane
pip install -e ".[dev]"
uvicorn app.main:app --port 8090

cd runtime/claw-runner
pip install -e ".[dev]"
uvicorn app.main:app --port 8091
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

## 部署

`git push origin main` → GitHub Actions 自动：
1. 构建 8 个 Docker 镜像 → 推送 ACR
2. SSH 到 ECS → Helm 部署全部服务

详见 [`docs/plans/migration-plan.md`](docs/plans/migration-plan.md)。

## 文档

| 文档 | 内容 |
|------|------|
| `CLAUDE.md` | 工作规约、服务速查 |
| `docs/architecture/ARCHITECTURE.md` | 架构、调用关系、端口 |
| `docs/plans/migration-plan.md` | 当前进度、剩余待办、配置速查 |
| `docs/architecture/backend-coding-standards.md` | Java 编码规约 |
| `docs/architecture/runtime-coding-standards.md` | Python 编码规约 |
