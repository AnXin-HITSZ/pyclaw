# 项目架构说明

## 项目说明

项目为 Claw SaaS 平台。

用户可以创建自己的 Claw，每个 Claw 是一个独立的执行环境，每个 Claw 可以运行多个 Agent。

## 项目定位

`claw-saas` 是产品级 monorepo，用于承载 Claw SaaS 的前端、Java 后端、Python Runtime、部署配置与工程脚本。

根目录不直接作为某一种技术栈的工程根目录。各技术栈放在各自目录下独立管理：

```text
claw-saas/
  backend/    Java / Spring Boot 后端服务
  frontend/   前端应用
  runtime/    Python / FastAPI Runtime 服务
  deploy/     本地与生产部署配置
  scripts/    工程脚本
  docs/       架构、数据库、接口与运维文档
```

## 目标目录结构

```text
claw-saas/
  README.md
  CLAUDE.md

  docs/
    architecture/
      ARCHITECTURE.md
      backend-coding-standards.md
      runtime-coding-standards.md
      database-schema.md

    plans/
      migration-plan.md

  backend/
    pom.xml

    gateway/
    backend-for-frontend/
    agent-marketplace-service/
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
        scheduler/
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

## 总体架构

```text
Frontend
  -> gateway
  -> backend-for-frontend
  -> domain services
  -> database / pyclaw-runtime-api / external systems

pyclaw-runtime-api
  -> claw-runner
```

模块职责如下：

```text
gateway
  统一入口、认证前置、限流、路由、CORS、请求追踪、健康检查

backend-for-frontend
  面向前端页面的聚合 API，负责页面级数据组装、裁剪和格式转换

agent-marketplace-service
  Agent 发布、版本、发现、搜索、安装申请、用户已安装 Agent

conversation-service
  对话线程、消息、展示结构、会话状态

runtime-service
  Orchestrator、Agent run、call_agent、FastAPI 调用、审批恢复

billing-service
  用量、额度、套餐、账单、计费规则

pyclaw-runtime-api
  Python / FastAPI Runtime 控制面，负责 Agent run 编排、审批恢复、Runner 调度

claw-runner
  Python / FastAPI Runtime 数据面，作为单个 Claw 的隔离执行环境，负责工具执行、workspace 操作、命令执行
```

## 分层边界

### Gateway

Gateway 是系统公网入口，职责是入口治理，不承载业务聚合。

应该放在 Gateway 的能力：

```text
路由转发
认证前置
CORS
限流
请求 ID
访问日志
健康检查
统一入口错误响应
```

不应该放在 Gateway 的能力：

```text
页面数据聚合
Agent 发布规则
对话状态流转
套餐额度扣减
Agent 执行编排
```

### BFF

`backend-for-frontend` 是 Backend For Frontend，面向前端页面提供 API。

BFF 负责把多个领域服务的数据聚合成前端需要的形状。BFF 不负责保存领域事实，也不替代领域服务的业务规则。

### Domain Services

领域服务负责稳定的业务能力和业务事实。

```text
conversation-service
  负责会话与消息事实

runtime-service
  负责运行编排与执行状态

agent-marketplace-service
  负责 Agent 市场与安装事实

billing-service
  负责额度、用量、账单事实
```

领域服务默认不直接暴露给前端，由 BFF 调用。

### Runtime API 与 Claw Runner

`runtime/pyclaw-runtime-api` 是 Python / FastAPI Runtime 控制面。

Java 后端通过 `runtime-service` 调用它，不建议由 Gateway 或 BFF 直接调用。

`runtime/claw-runner` 是 Python / FastAPI Runtime 数据面。每个 Claw 对应一个隔离执行环境，Runner 只负责该 Claw 内的 workspace 操作、工具执行和命令执行。

推荐调用关系：

```text
runtime-service
  -> pyclaw-runtime-api
  -> claw-runner
```

不建议：

```text
gateway -> claw-runner
backend-for-frontend -> claw-runner
domain services -> claw-runner
```

隔离原则：

```text
1. 每个 Claw 拥有独立 workspace。
2. 每个 Claw 的 Runner 必须限制文件访问范围。
3. Runner 不保存平台业务事实。
4. Runner 不直接处理用户、计费、Agent 市场等平台业务。
5. pyclaw-runtime-api 根据 claw_id 选择或调度对应 Runner。
```

## 后端工程结构

`backend/` 是 Maven 多模块父工程，不是 Spring Boot 应用。

后端子模块均是独立 Spring Boot 应用：

```text
backend/gateway
backend/backend-for-frontend
backend/agent-marketplace-service
backend/conversation-service
backend/runtime-service
backend/billing-service
```

推荐端口：

```text
gateway                    8080
backend-for-frontend       8081
conversation-service       8082
runtime-service            8083
agent-marketplace-service  8084
billing-service            8085
pyclaw-runtime-api         8090
claw-runner                8091
```

## 服务内部结构

Spring Boot 服务内部统一采用以下包结构，详细规约见 `docs/architecture/backend-coding-standards.md`。

```text
controller/
service/
service/impl/
repository/
domain/
dto/
client/
config/
exception/
```

FastAPI Runtime 服务内部统一采用控制面 / 数据面的不同结构，详细规约见 `docs/architecture/runtime-coding-standards.md`。

`pyclaw-runtime-api` 是控制面：

```text
app/
  main.py
  api/
  runtime/
  scheduler/
  schemas/
  config/
```

`claw-runner` 是数据面：

```text
app/
  main.py
  api/
  workspace/
  tools/
  sandbox/
  schemas/
  config/
```

## 文档分工

```text
docs/architecture/ARCHITECTURE.md
  最终目标架构，描述系统最终应该长什么样。

docs/plans/migration-plan.md
  迁移计划，记录从旧项目 pyclaw 迁移到 claw-saas 的阶段、任务、状态和完成标准。

docs/architecture/backend-coding-standards.md
  后端代码规约，约束 Spring Boot 服务的包结构、分层、命名、异常、事务和测试方式。

docs/architecture/runtime-coding-standards.md
  Runtime 代码规约，约束 FastAPI 控制面与 Claw Runner 数据面的包结构、边界、隔离和测试方式。
```
