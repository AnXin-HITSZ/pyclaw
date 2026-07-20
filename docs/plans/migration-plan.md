# 迁移计划

## 迁移目标

将当前项目的能力逐步迁移到 `claw-saas` 的新目标架构中。

由于当前项目名为 `pyclaw`，因此迁移时需要同步更新为 `claw-saas`。

`ARCHITECTURE.md` 描述最终架构，本文件只记录迁移过程、阶段任务、当前状态和完成标准。

## 迁移原则

```text
1. 每次迁移只处理一个服务或一个明确功能。
2. 先补齐工程骨架，再迁移业务代码。
3. 先迁移领域模型和业务用例，再迁移接口和持久化。
4. Spring Boot Controller 不承载业务逻辑。
5. Spring Boot 业务逻辑进入 service / impl。
6. FastAPI router 不承载业务编排。
7. Runtime 控制面和 Claw Runner 数据面必须隔离。
8. 每次改动后运行对应模块的校验或测试。
```

## 第一阶段：补齐后端骨架

目标：让 `backend/` 成为完整的 Maven 多模块后端工程。

任务：

```text
1. 保持 backend/pom.xml 作为父 POM。
2. 统一所有子模块继承 backend/pom.xml。
3. 补齐 backend/agent-marketplace-service。
4. 确认 backend/pom.xml modules 完整。
5. 为每个 Spring Boot 服务设置 application.name 和 server.port。
6. 保持每个服务可以独立启动。
```

## 第二阶段：跑通最小后端链路

目标：验证请求可以从 Gateway 进入 BFF，并具备继续调用领域服务的基础。

任务：

```text
1. gateway 引入 Spring Cloud Gateway。
2. gateway 配置 /api/** -> backend-for-frontend。
3. backend-for-frontend 提供最小测试接口。
4. conversation-service / runtime-service 提供最小健康或测试接口。
5. BFF 通过 HTTP client 调用一个领域服务。
6. 验证 Gateway -> BFF -> Service 链路。
```

## 第三阶段：迁移核心业务服务

目标：从旧项目 `D:\projects\personal\pyclaw` 迁移核心业务能力到新的服务边界。

建议顺序：

```text
1. conversation-service
2. runtime-service
3. agent-marketplace-service
4. billing-service
```

每个服务迁移顺序：

```text
1. 梳理旧项目相关代码位置。
2. 提取领域对象、枚举、状态。
3. 建立 service / impl 业务用例。
4. 建立 controller 接口层。
5. 建立 repository 持久化层。
6. 建立 client 外部调用层。
7. 补充测试或最小启动验证。
```

## 第四阶段：补齐 Python Runtime

目标：建立 `runtime/pyclaw-runtime-api` 与 `runtime/claw-runner`，将 Runtime 控制面和 Claw 隔离执行环境拆开。

任务：

```text
1. 创建 runtime/pyclaw-runtime-api。
2. 创建 runtime/claw-runner。
3. 建立两个 FastAPI 应用入口。
4. 将 Agent run 编排、审批恢复、Runner 调度迁移到 pyclaw-runtime-api。
5. 将 workspace 操作、工具执行、命令执行迁移到 claw-runner。
6. 定义 runtime-service -> pyclaw-runtime-api 的接口契约。
7. 定义 pyclaw-runtime-api -> claw-runner 的接口契约。
8. 明确审批恢复、执行状态、错误响应协议。
```

调用关系：

```text
backend/runtime-service
  -> runtime/pyclaw-runtime-api
  -> runtime/claw-runner
```

旧项目迁移映射：

```text
openclaw/api.py
  -> runtime/pyclaw-runtime-api/app/api/
  -> runtime/pyclaw-runtime-api/app/runtime/
  -> runtime/pyclaw-runtime-api/app/schemas/

sandbox-runner/app/main.py
  -> runtime/claw-runner/app/api/
  -> runtime/claw-runner/app/workspace/
  -> runtime/claw-runner/app/schemas/
```

## 第五阶段：补齐前端工程

目标：建立 `frontend/`，通过 Gateway 访问后端能力。

任务：

```text
1. 创建 frontend/。
2. 建立前端构建与本地开发脚本。
3. 所有后端请求统一访问 Gateway。
4. 前端不直接访问领域服务。
5. 前端不直接访问 pyclaw-runtime-api。
6. 前端不直接访问 claw-runner。
```

## 第六阶段：补齐部署与工程脚本

目标：让本地开发、联调、部署具备统一入口。

任务：

```text
1. 创建 deploy/。
2. 创建 deploy/docker-compose.yml。
3. 创建 deploy/env/ 环境变量模板。
4. 预留 deploy/k8s/。
5. 创建 scripts/ 存放本地开发与初始化脚本。
```

`docker-compose.yml` 最终应覆盖：

```text
gateway
backend-for-frontend
conversation-service
runtime-service
agent-marketplace-service
billing-service
pyclaw-runtime-api
claw-runner
frontend
postgres
redis
```

## 第七阶段：补齐平台治理能力

目标：在核心链路跑通后补齐生产化能力。

任务：

```text
认证与授权
租户上下文
请求 ID 与链路追踪
统一错误码
限流
服务间鉴权
配置管理
日志规范
数据库迁移
接口文档
CI 校验
Runner 隔离与资源限制
```
