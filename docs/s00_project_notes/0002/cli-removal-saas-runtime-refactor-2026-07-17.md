# 删除 CLI 入口并收口 SaaS Runtime 默认值

日期：2026-07-17

## 背景

PyClaw 当前目标是 SaaS 控制面 + Python FastAPI runtime，不再把本地命令行 CLI 作为正式运行入口。

原 `openclaw/cli.py` 同时承担了三类职责：

1. 本地命令行运行 Agent。
2. 提供 `DEFAULT_MODEL` / `DEFAULT_SYSTEM_PROMPT` 默认值。
3. 提供 `sanitize_session_id` 这类会话工具函数。

其中 1 不属于 SaaS 正式链路；2 会让生产运行时在 Agent / Provider 配置缺失时静默退回代码硬编码默认值；3 是 SaaS 仍然需要的安全工具函数。

## 实现目标

1. 删除 `openclaw/cli.py`。
2. 删除 console script 中的 `pyclaw` / `openclaw` CLI 入口。
3. 不再保留 `DEFAULT_MODEL` 和 `DEFAULT_SYSTEM_PROMPT`。
4. 将 SaaS 仍需要的 `sanitize_session_id` 移到核心会话模块。
5. Python API 对模型配置缺失显式报错，而不是使用代码默认模型。
6. 系统提示词允许为空，只由 Agent 配置和动态工具 resolver prompt fragments 组成。

## 修改内容

### 1. 会话 ID 清洗迁移

新增：

```text
openclaw/session/ids.py
```

提供：

```python
sanitize_session_id(value: str) -> str
```

该函数只负责安全清洗 session id，不依赖 CLI 错误类型。

### 2. Python API 不再依赖 CLI

修改：

```text
openclaw/api.py
```

变更点：

- 删除 `from openclaw.cli import DEFAULT_MODEL, DEFAULT_SYSTEM_PROMPT, sanitize_session_id`。
- 改为 `from openclaw.session.ids import sanitize_session_id`。
- `AgentRunRequest.system` 从硬编码默认值改为 `str | None = None`。
- `runtime_config.system or DEFAULT_SYSTEM_PROMPT` 改为 `runtime_config.system`。
- 模型解析改为：

```text
request.model
→ OPENAI_MODEL
→ 缺失则 400 报错
```

也就是说生产 SaaS 必须由 Spring Boot 的 Agent / Provider 配置传入模型，或者显式设置 `OPENAI_MODEL` 环境变量。

### 3. 删除 CLI 文件和测试

删除：

```text
openclaw/cli.py
openclaw/__main__.py
tests/test_cli.py
```

新增：

```text
tests/test_session_ids.py
```

用于覆盖迁移后的 session id 清洗逻辑。

### 4. 删除 CLI console scripts

修改：

```text
pyproject.toml
```

删除：

```toml
pyclaw = "openclaw.__main__:main"
openclaw = "openclaw.__main__:main"
```

保留：

```toml
pyclaw-channel-worker = "openclaw.channels.worker_app:main"
```

## 新的 SaaS 行为

### 模型

模型不再有代码硬编码兜底。

推荐链路：

```text
Agent.model
→ Provider.model
→ Spring Boot 传给 Python API request.model
→ Python API 使用 request.model
```

如果 Spring 未传模型，Python API 只接受显式环境变量 `OPENAI_MODEL` 作为部署级兜底。

### 系统提示词

系统提示词不再有 `You are a helpful assistant.` 这种代码默认值。

实际最终 prompt 来源：

```text
Agent.systemPrompt
+ Tool Resolver 生成的动态 prompt_fragments
```

如果 Agent 没有配置 systemPrompt，最终只剩平台根据实际可用工具生成的动态提示词，或者为空。

## 注意事项

1. 如果后续还需要开发者本地调试，不应恢复 `cli.py`，而应单独设计 dev-only script，避免它进入 SaaS runtime 依赖链。
2. Spring Boot 创建 Agent 时，建议提供平台默认 Agent 模板，而不是让 Python runtime 内部硬编码默认 system prompt。
3. Provider / Agent 配置缺失模型时，现在应在 Spring Boot 配置校验或 Python API 400 错误中暴露，不能静默运行到默认模型。