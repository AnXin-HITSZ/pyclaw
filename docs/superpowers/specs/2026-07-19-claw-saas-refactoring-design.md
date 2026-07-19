# Claw SaaS 重构设计文档

**日期：** 2026-07-19
**状态：** 待审核

---

## 1. 背景与动机

### 1.1 当前状态

项目 `pyclaw` 是一个 Claw SaaS 平台，当前为混合状态：

| 组件 | 当前状态 | 问题 |
|------|----------|------|
| `spring-backend/` | 单体 Spring Boot 应用，~25 个 package 混在一起 | 耦合严重，改一处牵全身 |
| `openclaw/` | 单体 FastAPI，`api.py` 37KB | 路由/编排/调度全揉在一起 |
| `sandbox-runner/` | 独立 runner | 相对干净 |
| `pyclaw-web/` | 前端应用 | 相对干净 |

### 1.2 目标

重构为 `claw-saas` 产品级 monorepo，包含 8 个 Spring Boot 微服务、2 个 FastAPI 服务、前端和部署配置。项目名从 `com.anxin.pyclaw` 改为 `com.clawsaas`。

### 1.3 约束

- 可以接受短期停工（不需要边跑边拆）
- 重构后保持 K8s 管理方式
- 引入阿里云 OSS 管理 Agent/Skill 制品包
- 移除 Channel 调度（不需要）
- 删除 token/（当前以 Web 场景为主）

---

## 2. 总体策略：骨架先行

### 2.1 分为两大阶段

```
阶段一：骨架搭建（同步进行）
  Java 后端: 建 Maven 多模块父工程 + 8 个服务空壳，全部可编译通过
  Python Runtime: 建 2 个 FastAPI 服务空壳，全部可启动验证

阶段二：业务迁移（按服务逐个进行）
  逐服务从旧代码迁移业务逻辑，拆一个验证一个
```

### 2.2 目标目录结构

```
claw-saas/
  CLAUDE.md
  README.md

  docs/
    architecture/
      ARCHITECTURE.md
      backend-coding-standards.md
      runtime-coding-standards.md
      database-schema.md
    plans/
      migration-plan.md
    superpowers/
      specs/

  backend/
    pom.xml                         # 父 POM（多模块）
    gateway/
    backend-for-frontend/
    agent-marketplace-service/
    skill-marketplace-service/
    conversation-service/
    runtime-service/
    billing-service/

  frontend/
    package.json
    src/
    public/

  runtime/
    pyclaw-runtime-api/
      pyproject.toml
      app/
        main.py
        api/
        runtime/
        schemas/
        config/
    claw-runner/
      pyproject.toml
      app/
        main.py
        api/
        workspace/
        tools/
        sandbox/
        schemas/
        config/

  deploy/
    docker-compose.yml
    env/
    k8s/

  scripts/
```

---

## 3. Java 后端骨架（8 个服务）

### 3.1 父 POM

```xml
<groupId>com.clawsaas</groupId>
<artifactId>claw-saas-backend</artifactId>
<version>0.1.0</version>
<packaging>pom</packaging>

<modules>
  <module>gateway</module>
  <module>backend-for-frontend</module>
  <module>agent-marketplace-service</module>
  <module>skill-marketplace-service</module>
  <module>conversation-service</module>
  <module>runtime-service</module>
  <module>billing-service</module>
</modules>
```

统一管理 Spring Boot 版本、Java 17、公共依赖版本号。

### 3.2 服务清单

| 服务 | 端口 | 关键依赖 | 职责 |
|------|------|----------|------|
| gateway | 8080 | Spring Cloud Gateway, WebFlux | 统一入口、认证、路由、用户管理 |
| backend-for-frontend | 8081 | Web | 页面聚合 |
| conversation-service | 8082 | Web, JPA, Flyway | 对话、消息、Claw/Agent 实例管理、安装 |
| runtime-service | 8083 | Web, JPA, HTTP Client | 业务编排、审批、调 FastAPI、Provider、Secret |
| agent-marketplace-service | 8084 | Web, JPA, Flyway, OSS SDK | Agent 发布、版本、OSS 制品上传下载 |
| billing-service | 8085 | Web, JPA, Flyway | 用量、计费 |
| skill-marketplace-service | 8086 | Web, JPA, Flyway, OSS SDK | Skill 发布、搜索、独立安装、OSS 制品 |

### 3.3 空壳结构

每个服务遵循 `backend-coding-standards.md` 的标准包结构：

```
<service>/
├── pom.xml
└── src/main/java/com/clawsaas/<service>/
    ├── <Service>Application.java
    ├── config/          # 阶段一放基础配置
    └── exception/       # 阶段一放 GlobalExceptionHandler
    # 以下目录阶段二按需创建：
    # controller/ service/ service/impl/ repository/ domain/ dto/ client/
```

阶段一仅确保 `@SpringBootApplication` + `application.yml`（服务名 + 端口）可编译通过。

### 3.4 关键设计决策

- **gateway 使用 WebFlux**，不依赖 spring-boot-starter-web
- **不建 common 模块**。异常处理、加密等工具类各服务自持
- **各服务独立数据库连接**，独立 Flyway 迁移目录
- **audit 各服务自持**。gateway 做 HTTP 访问审计，领域服务做操作审计

---

## 4. Python Runtime 骨架（2 个服务）

### 4.1 服务清单

| 服务 | 端口 | 职责 |
|------|------|------|
| pyclaw-runtime-api | 8090 | FastAPI 控制面，执行引擎 |
| claw-runner | 8091 | FastAPI 数据面，隔离执行 |

### 4.2 pyclaw-runtime-api（控制面/执行引擎）

```
runtime/pyclaw-runtime-api/
├── pyproject.toml
└── app/
    ├── main.py              # 创建 FastAPI app，include routers
    ├── api/
    │   ├── __init__.py
    │   ├── health.py
    │   ├── runs.py
    │   ├── approvals.py
    │   └── tools.py
    ├── runtime/              # 执行引擎（非业务编排）
    │   ├── __init__.py
    │   ├── run_executor.py
    │   ├── approval_handler.py
    │   ├── runner_client.py
    │   ├── session_factory.py
    │   ├── provider_factory.py
    │   └── policy_factory.py
    ├── schemas/
    │   ├── __init__.py
    │   ├── health.py
    │   ├── runs.py
    │   ├── approvals.py
    │   └── tools.py
    └── config/
        ├── __init__.py
        ├── settings.py
        └── logging.py
```

- 去掉 `scheduler/` 目录（调度由 Spring 侧负责，根据数据库记录路由）
- 执行引擎不做业务编排，只负责"收到 run 请求 → 调 LLM → 工具调用 → 等待审批 → 继续 → 返回结果"

### 4.3 claw-runner（数据面/隔离执行）

```
runtime/claw-runner/
├── pyproject.toml
└── app/
    ├── main.py
    ├── api/
    │   ├── __init__.py
    │   ├── health.py
    │   ├── workspace.py
    │   ├── tools.py
    │   └── commands.py
    ├── workspace/
    │   ├── __init__.py
    │   ├── path_guard.py
    │   └── file_service.py
    ├── tools/
    │   ├── __init__.py
    │   ├── registry.py
    │   ├── executor.py
    │   └── policy.py
    ├── sandbox/
    │   ├── __init__.py
    │   ├── environment.py
    │   ├── limits.py
    │   └── command_runner.py
    ├── schemas/
    │   ├── __init__.py
    │   ├── health.py
    │   ├── workspace.py
    │   ├── tools.py
    │   └── commands.py
    └── config/
        ├── __init__.py
        ├── settings.py
        └── logging.py
```

- `workspace/path_guard.py` 是安全隔离的底线，阶段二迁移时最高优先级

### 4.4 调用关系

```
Spring runtime-service（业务编排）
  "这场对话要用哪个 Agent，审批流程怎么走"
        │
        ▼  传入已选好的 Agent + Claw + Runner 地址
Python pyclaw-runtime-api（执行引擎）
  "收到 run 请求 → 调 LLM → 工具调用 → 等待审批 → 继续 → 返回结果"
        │
        ▼
Python claw-runner（隔离执行）
  "在这个 Claw 的 workspace 里执行具体工具/命令"
```

---

## 5. OSS 制品存储

### 5.1 OSS 存储结构

```
阿里云 OSS Bucket: claw-saas-artifacts/
├── agents/
│   └── <agent-package-id>/
│       ├── v1.0.0/
│       │   ├── agent.tar.gz
│       │   └── agent.tar.gz.sha256
│       └── v2.0.0/
│           └── ...
├── skills/
│   └── <skill-id>/
│       ├── v1.0.0/
│       │   ├── skill.tar.gz
│       │   └── skill.tar.gz.sha256
│       └── v1.1.0/
│           └── ...
```

### 5.2 Agent 包结构

```
agent.tar.gz
├── manifest.json            # name, version, author, description
├── prompt/
│   └── system.md            # System Prompt 模板
├── config.json              # 默认配置、参数 schema
└── skill-refs.json          # 依赖的 Skill 列表
    [
      { "skillId": "xxx", "version": ">=1.0.0" },
      { "skillId": "yyy", "version": "^2.1.0" }
    ]
```

### 5.3 Skill 包结构

```
skill.tar.gz
├── manifest.json            # name, version, author, category, tags
├── README.md                # 使用说明
├── skill.py / skill.js      # Skill 执行逻辑
├── requirements.json        # Skill 依赖（如有）
└── examples/
```

### 5.4 Agent 安装流程

```
用户安装 Agent
  → conversation-service (agentinstall/)
    ① 从 OSS 下载 agent.tar.gz
    ② 解析 skill-refs.json 获取依赖列表
    ③ 从 OSS 下载所有依赖的 skill.tar.gz
    ④ 组装：Agent 包 + Skill 包 → 合并推送至 claw-runner
    ⑤ claw-runner 解压到 ./claw/<agent-role>/skills/
    ⑥ 记录安装元数据到数据库
```

### 5.5 OSS 客户端归属

| 服务 | 用途 |
|------|------|
| agent-marketplace-service | 上传/下载 Agent 制品包 |
| skill-marketplace-service | 上传/下载 Skill 制品包 |
| conversation-service | 安装时下载 Agent + Skill 包 |

各服务自持 OSS 配置，不建共享模块。

---

## 6. 旧代码迁移映射

### 6.1 gateway

| 旧 package | 说明 |
|------|------|
| `user/` | 用户管理 |
| `auth/` | 认证逻辑 |
| `routebinding/` | 路由绑定 |

### 6.2 conversation-service

| 旧 package | 说明 |
|------|------|
| `conversation/` | 对话、消息、会话状态 |
| `session/` | 会话解析 |
| `clawchat/` | Claw 聊天关联 |
| `claw/` | Claw 实例管理 |
| `agentinstall/` | Agent 实例安装、审批 |
| `agentconfig/` | Agent 实例工具策略配置 |

### 6.3 runtime-service

| 旧 package | 说明 |
|------|------|
| `orchestrator/` | 对话编排 |
| `sandbox/` | 沙箱编排、工具调度 |
| `approval/` | 工具审批 |
| `agent/` | Agent 运行入口 |
| `pyclaw/` | 调 FastAPI 的 HTTP client |
| `tool/` | 工具目录、解析 |
| `provider/` | LLM 提供商管理 |
| `secret/` | 加密凭据管理、K8s 同步 |

### 6.4 agent-marketplace-service

| 旧 package | 说明 |
|------|------|
| `agentpackage/` | Agent 发布、版本管理 |

### 6.5 billing-service

| 旧 package | 说明 |
|------|------|
| `usage/` | 用量、额度 |

### 6.6 删除

| 旧 package | 原因 |
|------|------|
| `token/` | 当前以 Web 场景为主，CLI Token 不保留 |
| `channel/` | 不需要 Channel 调度 |

### 6.7 各服务自持

| 旧 package | 说明 |
|------|------|
| `audit/` | 审计日志，每个需要审计的领域服务各持一份 |
| `common/` | 异常处理等工具，每个服务各持一份 |
| `config/SecretEncryptionService` | 加密服务，需要加密的领域服务各持一份 |

---

## 7. 被删除/变更的原 CLAUDE.md 要求

| 原要求 | 处理 |
|------|------|
| "Claw Runner 必须保持执行环境隔离" | 保留，迁移至 claw-runner 的 `sandbox/` 和 `workspace/path_guard.py` |
| 原 migration-plan.md 7 阶段 | 本设计替代原迁移计划。骨架先行，再逐服务迁移 |

---

## 8. 后续阶段预览

阶段二业务迁移建议顺序：

```
1. conversation-service（对话核心，依赖相对清晰）
2. runtime-service（编排引擎，依赖 conversation）
3. agent-marketplace-service（Agent 市场 + OSS）
4. skill-marketplace-service（Skill 市场 + OSS）
5. billing-service（计费）
6. gateway + backend-for-frontend（入口层，最后调整）
7. Python Runtime：pyclaw-runtime-api → claw-runner
```
