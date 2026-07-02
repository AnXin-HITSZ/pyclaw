# OpenClaw Python CLI Entry Technical Design

本文档记录 Python 版 pyclaw 新增命令行入口层的技术细节。该入口层对应原生 OpenClaw 的外层启动结构，但实现方式完全使用 Python 语义说明。

## 1. 本次新增内容

新增文件：

```text
openclaw/cli.py
openclaw/__main__.py
```

更新文件：

```text
pyproject.toml
```

新增测试：

```text
tests/test_cli.py
```

入口层的目标是让项目不再只能通过临时脚本调用 `Agent`，而是可以通过命令行启动：

```cmd
python -m openclaw "你好"
```

或者在 editable install 后启动：

```cmd
pyclaw "你好"
openclaw "你好"
```

## 2. Python 入口层分工

### 2.1 `openclaw/__main__.py`

`__main__.py` 是 Python 包运行入口。

当用户执行：

```cmd
python -m openclaw
```

Python 会查找并执行：

```text
openclaw/__main__.py
```

当前实现保持很薄：

```python
from openclaw.cli import main

if __name__ == "__main__":
    raise SystemExit(main())
```

含义：

- `openclaw.__main__` 不承载复杂业务逻辑。
- 真正 CLI 逻辑放在 `openclaw.cli`。
- `raise SystemExit(main())` 会把 `main()` 返回值变成进程退出码。

### 2.2 `openclaw/cli.py`

`cli.py` 是当前 Python 版 CLI 的主实现文件，职责包括：

- 构建命令行参数解析器。
- 加载 `.env`。
- 根据参数创建 provider。
- 创建 `Agent`。
- 执行一次 prompt。
- 输出 assistant 结果。
- 注册但暂不实现 `gateway run`。

当前主要函数：

```text
main(argv=None)
  -> build_parser()
  -> asyncio.run(run(args, parser))

run(args, parser)
  -> load_env_file()
  -> 识别 gateway run 占位命令
  -> 拼接 prompt
  -> run_prompt(args, prompt)
  -> 打印纯文本或 JSON

run_prompt(args, prompt)
  -> build_provider()
  -> Agent(...)
  -> await agent.prompt(prompt)
```

## 3. `pyproject.toml` 脚本入口

新增：

```toml
[project.scripts]
pyclaw = "openclaw.__main__:main"
openclaw = "openclaw.__main__:main"
```

含义：

- `pyclaw` 是 Python 版推荐命令名。
- `openclaw` 是为了模拟原生 OpenClaw 的命令名。
- 两个命令都会调用同一个函数：`openclaw.__main__.main`。

安装 editable 包后：

```cmd
python -m pip install -e ".[openai]"
```

虚拟环境会生成命令脚本，因此可以执行：

```cmd
pyclaw "你好"
```

它等价于：

```python
from openclaw.__main__ import main
main()
```

## 4. 当前命令行为

### 4.1 一次性 prompt

```cmd
pyclaw --provider mock "你好"
```

流程：

```text
CLI 参数
  -> MockProvider
  -> Agent.prompt("你好")
  -> AssistantMessage
  -> 打印 text content
```

真实 OpenAI 调用：

```cmd
pyclaw "你好"
```

默认 provider 是 `openai`。CLI 会先加载当前目录 `.env`，然后读取：

```text
OPENAI_API_KEY
OPENAI_MODEL
OPENAI_BASE_URL
OPENAI_ORGANIZATION
OPENAI_PROJECT
```

其中 `OPENAI_API_KEY` 必须存在，否则 CLI 会返回清晰错误。

### 4.2 JSON 输出

```cmd
pyclaw --provider mock --json "你好"
```

输出完整 `AssistantMessage` 的 JSON 结构，便于调试 content block、usage、stop reason 等字段。

### 4.3 推理参数透传

```cmd
pyclaw --reasoning-effort low --max-output-tokens 1024 "分析这个问题"
```

CLI 会生成：

```python
{
    "reasoning": {"effort": "low"},
    "max_output_tokens": 1024,
}
```

并传给：

```text
Agent.model_options
  -> LoopConfig.options
  -> provider.stream(..., options=...)
  -> OpenAIProvider request.update(options)
```

## 5. `gateway run` 当前状态

当前 CLI 已经识别：

```cmd
pyclaw gateway run
```

但它只是保留命令，返回：

```text
gateway run is registered but not implemented yet.
```

原因：

- 一次性 prompt 是短生命周期命令。
- `gateway run` 是长生命周期服务。
- 真正实现需要 HTTP/WebSocket 服务层、路由、请求协议、生命周期管理、关闭信号处理等能力。

因此当前只先固定命令语义，避免未来补 gateway 时改变用户入口。

未来完整 `gateway run` 可能的 Python 结构：

```text
openclaw/gateway/
  __init__.py
  app.py
  server.py
  protocol.py
  routes.py
```

未来流程可能是：

```text
pyclaw gateway run
  -> cli.py 识别 gateway run
  -> openclaw.gateway.server.run_gateway_server()
  -> 启动 HTTP/WebSocket 服务
  -> 接收外部请求
  -> 转给 Agent / Provider / ToolRegistry
  -> 返回响应
```

## 6. 错误处理策略

CLI 使用 `CliError` 表示用户可理解的错误，例如：

- 没有设置 `OPENAI_API_KEY`。
- 使用了尚未实现的 `gateway run`。
- 使用未知 gateway 子命令。

`main()` 捕获 `CliError` 后：

- 将错误写到 stderr。
- 返回退出码 `2`。

`KeyboardInterrupt` 对应 Ctrl+C：

- 打印 `Interrupted.`。
- 返回退出码 `130`。

正常执行返回 `0`。

## 7. 测试覆盖

`tests/test_cli.py` 覆盖：

- mock provider 的一次性 prompt 输出。
- `gateway run` 占位命令。
- 无参数时打印 help。
- `assistant_text()` 对多个 text block 的合并。
- `build_model_options()` 对 reasoning/max tokens 的构造。

这些测试不触发真实网络调用。

## 8. 当前边界

当前 CLI 仍然不是完整 OpenClaw CLI：

- 没有真正的 gateway server。
- 没有 sessions 子命令。
- 没有 tools 注册命令。
- 没有 transcript 检索命令。
- 没有多 provider CLI 选择逻辑，除 `openai` 和测试用 `mock` 外尚未扩展。

但入口层已经完成，后续可以在不改变用户启动方式的前提下继续扩展子命令。

## 9. OpenAI-compatible Provider 模式

CLI 默认使用 `OpenAIProvider`。该 provider 现在支持两种 API 模式：

```text
responses          OpenAI Responses API
chat_completions   OpenAI-compatible Chat Completions API
```

`.env` 可配置：

```env
OPENAI_API_MODE=chat_completions
```

如果 `OPENAI_API_MODE=auto` 或未设置：

- `OPENAI_BASE_URL` 为空，或包含 `api.openai.com`：默认使用 `responses`。
- `OPENAI_BASE_URL` 指向第三方兼容服务，例如 `https://api.deepseek.com`：默认使用 `chat_completions`。

这解决了一个常见问题：第三方 OpenAI-compatible 服务通常支持 `/chat/completions`，但不一定支持 OpenAI Responses API 的 `/responses`。如果用 Responses API 请求这些服务，可能返回：

```text
Error code: 404
```

对于 DeepSeek 这类服务，推荐配置：

```env
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-v4-flash
OPENAI_API_MODE=chat_completions
```

`chat_completions` 模式会将内部消息转换为 Chat Completions 格式：

```text
system_prompt -> {"role": "system", "content": ...}
user text     -> {"role": "user", "content": ...}
assistant     -> {"role": "assistant", "content": ...}
tool result   -> {"role": "tool", "tool_call_id": ..., "content": ...}
```

同时会把 `max_output_tokens` 映射为 `max_tokens`。当前项目里的 `reasoning` 参数是 Responses API 风格参数，在 `chat_completions` 模式下会被移除，避免兼容接口拒绝未知参数。
## 10. chatdata Transcript 存储

CLI 正常执行一次 prompt 时，会默认把 session 与 transcript 写入项目当前工作目录下的：

```text
chatdata/
```

默认文件结构：

```text
chatdata/
  .gitkeep
  sessions.json
  <session-id>.jsonl
```

其中：

- `sessions.json` 是 session 索引。
- `<session-id>.jsonl` 是一条会话的 transcript。
- `.gitkeep` 只是为了让 Git 可以保留空目录。

`.gitignore` 配置为：

```gitignore
chatdata/*
!chatdata/.gitkeep
```

因此上传代码时只保留 `chatdata/.gitkeep`，不会提交真实对话数据。

CLI 参数：

```cmd
pyclaw --chatdata-dir chatdata --session-id demo "你好"
```

如果不传 `--session-id`，CLI 会自动生成一个 session id。

如果需要把数据放到别的目录，也可以设置环境变量：

```env
OPENCLAW_CHATDATA_DIR=D:\project\pyclaw\chatdata
```

## 11. CLI 展示粒度

“CLI 展示粒度”指的是：当用户在命令行查看 transcript 时，命令应该展示多少细节。

可以有几种层级：

```text
简略粒度
  只显示 role 与文本，例如 user/assistant 的问答内容。

中等粒度
  显示 role、时间、stop_reason、模型、provider、工具调用名称。

完整粒度
  直接显示 JSONL 原始结构，包括 content blocks、usage、error_body、toolResult 等。
```

例如同一条 assistant 消息：

简略展示：

```text
assistant: 你好！有什么可以帮你？
```

中等展示：

```text
[2026-07-02T10:00:00Z] assistant model=deepseek-v4-flash stop=stop
你好！有什么可以帮你？
```

完整展示：

```json
{
  "type": "message",
  "id": "...",
  "timestamp": "...",
  "message": {
    "role": "assistant",
    "content": [{"type": "text", "text": "你好！有什么可以帮你？"}],
    "model": "deepseek-v4-flash",
    "stopReason": "stop"
  }
}
```

当前已经实现：

```cmd
pyclaw transcripts show <session-id>
```

当前可通过参数切换展示粒度：

```cmd
pyclaw transcripts show demo --format text
pyclaw transcripts show demo --format detail
pyclaw transcripts show demo --format json
```
## 12. Transcript 查看命令

CLI 已实现：

```cmd
pyclaw transcripts show <session-id> --format text
pyclaw transcripts show <session-id> --format detail
pyclaw transcripts show <session-id> --format json
```

示例：

```cmd
pyclaw --session-id demo "你好"
pyclaw transcripts show demo --format text
```

三种格式的含义：

```text
text
  面向日常阅读，只显示 role 与文本内容。

detail
  面向调试阅读，显示 timestamp、role、provider、model、stop_reason、usage 与正文。

json
  面向程序处理或精确排查，输出 JSONL 解析后的完整 JSON 数组。
```

`--session-id` 的效果：

```cmd
pyclaw --session-id demo "你好"
```

会把本次对话写到：

```text
chatdata/demo.jsonl
```

并在：

```text
chatdata/sessions.json
```

记录该 session 的索引信息。

`--chatdata-dir` 的效果：

```cmd
pyclaw --chatdata-dir D:\tmp\my-chatdata --session-id demo "你好"
```

会把 `sessions.json` 和 `demo.jsonl` 写到指定目录，而不是默认的 `./chatdata`。

`.gitkeep` 是一个普通空文件。Git 不会提交空目录，因此在 `chatdata/` 下放 `.gitkeep`，可以让仓库保留这个目录结构。真实对话数据通过 `.gitignore` 忽略：

```gitignore
chatdata/*
!chatdata/.gitkeep
```