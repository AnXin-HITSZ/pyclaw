# Runtime 代码规约

## 适用范围

本规约适用于 `runtime/` 下所有 Python / FastAPI 服务：

```text
pyclaw-runtime-api
claw-runner
```

## Runtime 分层

Runtime 分为控制面和数据面：

```text
pyclaw-runtime-api
  控制面
  接收 runtime-service 调用
  编排 Agent run
  处理 approval resume
  根据 claw_id 调度对应 claw-runner

claw-runner
  数据面
  每个 Claw 的隔离执行环境
  操作该 Claw 的 workspace
  执行工具和命令
```

调用关系：

```text
backend/runtime-service
  -> runtime/pyclaw-runtime-api
  -> runtime/claw-runner
```

禁止：

```text
gateway -> claw-runner
backend-for-frontend -> claw-runner
domain services -> claw-runner
frontend -> claw-runner
```

## pyclaw-runtime-api 结构

`pyclaw-runtime-api` 负责 Runtime 控制面。

```text
runtime/pyclaw-runtime-api/
  pyproject.toml
  app/
    main.py
    api/
      health.py
      runs.py
      approvals.py
      tools.py
    runtime/
      run_orchestrator.py
      agent_runner.py
      approval_runner.py
      session_factory.py
      provider_factory.py
      policy_factory.py
    scheduler/
      claw_resolver.py
      runner_client.py
      runner_registry.py
    schemas/
      health.py
      runs.py
      approvals.py
      tools.py
    config/
      settings.py
      logging.py
```

`app/main.py` 只负责创建 FastAPI app 和 include routers，不写业务逻辑。

## claw-runner 结构

`claw-runner` 负责单个 Claw 的隔离执行环境。

```text
runtime/claw-runner/
  pyproject.toml
  app/
    main.py
    api/
      health.py
      workspace.py
      tools.py
      commands.py
    workspace/
      path_guard.py
      file_service.py
    tools/
      registry.py
      executor.py
      policy.py
    sandbox/
      environment.py
      limits.py
      command_runner.py
    schemas/
      health.py
      workspace.py
      tools.py
      commands.py
    config/
      settings.py
      logging.py
```

## FastAPI API 规约

Router 文件只做接口适配。

允许：

```text
接收请求
做轻量参数校验
读取鉴权头或内部调用凭证
调用 runtime/workspace/tools/sandbox 层
返回 Pydantic response
```

禁止：

```text
在 router 中写 Agent run 编排
在 router 中直接操作复杂文件逻辑
在 router 中直接执行 shell 命令
在 router 中写跨模块业务流程
```

## Schema 规约

所有 API 入参和出参使用 Pydantic schema。

命名：

```text
RunAgentRequest
RunAgentResponse
ResumeApprovalRequest
ToolCatalogResponse
WorkspaceFileResponse
CommandRunRequest
```

要求：

```text
Request / Response 分开。
内部对象不直接作为接口响应。
跨服务接口字段保持稳定。
错误响应结构保持稳定。
```

## 隔离规约

每个 Claw 必须拥有独立执行环境。

Runner 必须遵守：

```text
1. 只能访问当前 Claw 的 workspace。
2. 所有文件路径必须经过 path_guard 校验。
3. 禁止通过相对路径、软链接或路径穿越逃逸 workspace。
4. 命令执行必须带资源限制。
5. 工具执行必须遵守 tool policy。
6. Runner 不保存平台业务事实。
7. Runner 不处理用户、套餐、计费、Agent 市场等业务。
```

## 旧项目迁移规约

旧项目 `D:\projects\personal\pyclaw` 迁移时：

```text
openclaw/api.py
  不允许原样迁移成 app/main.py。
  必须拆分到 api/、runtime/、scheduler/、schemas/、config/。

sandbox-runner/app/main.py
  不并入 pyclaw-runtime-api。
  迁移到 claw-runner。
  workspace 路径保护迁移到 workspace/path_guard.py。
```

## 测试规约

FastAPI 服务应优先补以下测试：

```text
health endpoint
schema validation
path_guard
runner client
tool policy
approval resume
```

运行方式以各 Runtime 服务的 `pyproject.toml` 为准。
