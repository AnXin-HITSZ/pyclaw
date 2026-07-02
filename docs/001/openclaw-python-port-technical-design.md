# OpenClaw Python Port Technical Design

本文档基于 `openclaw-agent-loop-implementation-notes.md`，将 TypeScript 版 OpenClaw 的基础 agent loop 实现转换为 Python 版技术方案。目标不是逐行翻译 TS 代码，而是保留核心运行语义，并把实现边界整理成适合 Python 项目落地的模块、类型、流程和扩展点。

## 1. 目标与边界

Python 版第一阶段应优先实现一个可运行、可测试、可扩展的最小闭环：

```text
Message types
  -> System prompt builder
  -> Provider stream adapter
  -> Agent loop
  -> Tool registry
  -> JSONL transcript
  -> Retry policy
  -> Context guard
```

暂不在第一版完整迁移：

- plugin runtime。
- subagent / group chat runtime。
- 复杂 quota、gateway hot reload、provider overlay。
- 完整 prompt cache boundary 优化。
- 多 topic transcript 与 parent-linked transcript 的全部细节。
- UI/runtime 特定事件。

但第一版的接口设计需要为这些能力预留扩展点，避免后续重写核心循环。

## 2. TypeScript 到 Python 的核心映射

| TypeScript 生产实现 | Python 版建议模块 | 职责 |
| --- | --- | --- |
| `packages/agent-core/src/agent.ts` | `openclaw/agent/agent.py` | 面向调用方的 `Agent` 封装、内存态、事件处理、队列 |
| `packages/agent-core/src/agent-loop.ts` | `openclaw/agent/loop.py` | 核心 run loop、模型流、工具调用、stop reason 分支 |
| `packages/llm-core/src/types.ts` | `openclaw/llm/types.py` | 消息、内容块、stop reason、usage 等类型 |
| `src/agents/sessions/agent-session.ts` | `openclaw/session/agent_session.py` | 生产会话层、持久化、retry、compaction 入口 |
| `src/config/sessions/*` | `openclaw/session/store.py`、`transcript.py`、`paths.py` | `sessions.json` 与 JSONL transcript |
| `src/agents/system-prompt.ts` | `openclaw/prompt/system_prompt.py` | 多 section 系统提示词构建 |
| `session-tool-result-guard.ts` | `openclaw/guards/transcript_tool_result_guard.py` | 写入 transcript 前保护 tool result |
| `tool-result-context-guard.ts` | `openclaw/guards/context_guard.py` | 模型调用前保护上下文 |

推荐目录结构：

```text
openclaw/
  __init__.py
  agent/
    __init__.py
    agent.py
    events.py
    loop.py
    state.py
  llm/
    __init__.py
    provider.py
    types.py
  prompt/
    __init__.py
    system_prompt.py
  session/
    __init__.py
    agent_session.py
    paths.py
    store.py
    transcript.py
  tools/
    __init__.py
    registry.py
    types.py
  guards/
    __init__.py
    context_guard.py
    transcript_tool_result_guard.py
  custom/
    __init__.py
    hooks.py
```

`custom/hooks.py` 用于承载项目自定义逻辑，避免把私有业务规则直接散落在 loop 内部。

## 3. 核心数据类型

Python 版建议使用 `dataclass` + `typing.Literal` 起步。若后续需要更强的输入校验，可在边界层引入 Pydantic，但核心 loop 不应强依赖外部框架。

### 3.1 StopReason

```python
from typing import Literal

StopReason = Literal["stop", "length", "toolUse", "error", "aborted"]
```

语义保持与 TS 一致：

- `stop`: 模型正常结束。
- `length`: 模型达到长度上限。
- `toolUse`: assistant 请求调用工具。
- `error`: provider 或运行时错误。
- `aborted`: 用户或系统取消。

### 3.2 内容块

```python
from dataclasses import dataclass, field
from typing import Any, Literal

@dataclass
class TextBlock:
    type: Literal["text"]
    text: str

@dataclass
class ToolCallBlock:
    type: Literal["toolCall"]
    id: str
    name: str
    input: dict[str, Any]

@dataclass
class ToolResultBlock:
    type: Literal["toolResult"]
    tool_call_id: str
    name: str
    output: Any
    is_error: bool = False
```

第一版可以把 `content` 定义为 `list[dict[str, Any]]`，便于序列化和兼容不同 provider；但内部辅助函数应尽量使用显式类型。

### 3.3 消息类型

```python
from dataclasses import dataclass, field
from time import time
from typing import Any, Literal

@dataclass
class BaseMessage:
    role: str
    content: list[dict[str, Any]]
    timestamp: float = field(default_factory=time)

@dataclass
class UserMessage(BaseMessage):
    role: Literal["user"] = "user"

@dataclass
class AssistantMessage(BaseMessage):
    role: Literal["assistant"] = "assistant"
    provider: str | None = None
    model: str | None = None
    usage: dict[str, Any] = field(default_factory=dict)
    stop_reason: StopReason = "stop"
    error_message: str | None = None
    error_code: str | None = None
    error_type: str | None = None
    error_body: Any | None = None

@dataclass
class ToolResultMessage(BaseMessage):
    role: Literal["tool"] = "tool"
```

注意：TS 中字段使用 `stopReason`，Python 内部建议使用 `stop_reason`，JSON 序列化时再统一转换。

### 3.4 AgentState

```python
@dataclass
class AgentState:
    messages: list[BaseMessage] = field(default_factory=list)
    streaming_message: AssistantMessage | None = None
    pending_tool_call_ids: set[str] = field(default_factory=set)
    last_error: str | None = None
    aborted: bool = False
```

这对应 TS 的 `Agent.mutableState`。所有 provider response、tool result、retry 修正，最终都应体现在 `AgentState.messages` 中。

## 4. 事件模型

TS 版通过 `AgentEvent` 把 loop 中的变化归约到 `Agent.mutableState`。Python 版也建议保留事件层，不要让 session、UI、transcript 直接侵入 loop。

```python
from dataclasses import dataclass
from typing import Any, Literal

AgentEventType = Literal[
    "agent_start",
    "agent_end",
    "turn_start",
    "turn_end",
    "message_start",
    "message_update",
    "message_end",
    "tool_execution_start",
    "tool_execution_end",
]

@dataclass
class AgentEvent:
    type: AgentEventType
    payload: dict[str, Any]
```

`Agent` 对事件的处理规则：

- `message_start`: 设置 `state.streaming_message`。
- `message_update`: 更新流式中的 assistant message。
- `message_end`: 将最终 message append 到 `state.messages`。
- `tool_execution_start`: 记录 pending tool call。
- `tool_execution_end`: 移除 pending tool call。
- `turn_end`: 如 assistant 带 error，更新 `state.last_error`。

事件层的好处是：

- `AgentSession` 可以订阅 `message_end` 并持久化。
- 自定义逻辑可以订阅事件，而不需要改 loop。
- 后续 UI、日志、metrics 可自然接入。

## 5. Agent 封装

`Agent` 是面向调用方的核心运行时对象，不负责磁盘持久化。

建议接口：

```python
class Agent:
    def __init__(
        self,
        *,
        model: str,
        provider: "LlmProvider",
        system_prompt: str,
        tools: "ToolRegistry",
        transform_context: "ContextTransform | None" = None,
    ) -> None:
        self.model = model
        self.provider = provider
        self.system_prompt = system_prompt
        self.tools = tools
        self.transform_context = transform_context
        self.state = AgentState()
        self._subscribers: list[Callable[[AgentEvent], None]] = []

    async def prompt(self, text: str) -> AssistantMessage:
        ...

    async def continue_(self) -> AssistantMessage:
        ...

    def subscribe(self, callback: Callable[[AgentEvent], None]) -> None:
        ...

    def emit(self, event: AgentEvent) -> None:
        ...
```

`continue` 是 Python 关键字，方法名建议使用 `continue_()`。

`Agent.prompt()` 对应 TS 的 `Agent.prompt()`，职责：

1. 构造用户消息。
2. 发出 `agent_start`、`turn_start`、用户消息事件。
3. 调用 `run_agent_loop()`。
4. 返回最后一条 assistant message。

`Agent.continue_()` 对应 TS 的 `runAgentLoopContinue()`，用于 retry、compaction 后继续运行。

## 6. LLM Provider 抽象

Python 版需要把 provider 流式响应封成统一协议，使 loop 不关心 OpenAI、Anthropic 或其他 provider 的差异。

```python
from typing import AsyncIterator, Protocol

class LlmProvider(Protocol):
    async def stream(
        self,
        *,
        model: str,
        system_prompt: str,
        messages: list[dict],
        tools: list[dict],
        options: dict | None = None,
    ) -> AsyncIterator["ProviderEvent"]:
        ...
```

Provider event 建议归一为：

```python
@dataclass
class ProviderEvent:
    type: Literal["start", "delta", "done", "error"]
    data: dict[str, Any]
```

`stream_assistant_response()` 负责把 provider event 聚合成最终 `AssistantMessage`：

```text
AgentMessage[]
  -> transform_context()
  -> convert_to_llm()
  -> provider.stream()
  -> start / delta / done / error
  -> AssistantMessage
```

关键规则：

- `transform_context()` 必须在每次模型调用前执行。
- provider 抛异常时，不直接向外抛出到会话层，而是构造 `stop_reason="error"` 的 assistant message。
- abort 时构造 `stop_reason="aborted"` 的 assistant message。
- 流式中间态通过事件发出，但只有最终 message 进入 `state.messages`。

## 7. 核心循环

Python 版 `run_agent_loop()` 应保持 TS 版 `runLoop()` 的语义，但第一版可以简化 follow-up 与 steering。

### 7.1 最小循环

```python
async def run_agent_loop(config: LoopConfig) -> AssistantMessage:
    has_more_tool_calls = True
    last_assistant: AssistantMessage | None = None

    while has_more_tool_calls:
        has_more_tool_calls = False

        assistant = await stream_assistant_response(config)
        last_assistant = assistant

        if assistant.stop_reason in ("error", "aborted"):
            config.emit(turn_end_event(assistant))
            return assistant

        if assistant.stop_reason == "toolUse":
            tool_results = await execute_tool_calls(config, assistant)
            if tool_results:
                for message in tool_results:
                    config.messages.append(message)
                    config.emit(message_end_event(message))
                has_more_tool_calls = True
                continue

        config.emit(turn_end_event(assistant))
        return assistant

    assert last_assistant is not None
    return last_assistant
```

### 7.2 后续增强循环

第二阶段再补齐 TS 版的两个循环语义：

- 外层 follow-up loop: agent 本要结束，但 follow-up 队列还有消息时继续。
- 内层 tool/steering loop: 处理工具调用链与运行时插入消息。

增强后流程：

```text
start turn
  -> drain pending steering messages
  -> stream_assistant_response()
  -> if error/aborted: end
  -> if toolUse: execute tools
  -> append tool result messages
  -> prepare_next_turn()
  -> should_stop_after_turn()
  -> drain follow-up messages
end when no tools, no steering, no follow-up
```

## 8. 工具系统

### 8.1 Tool 定义

```python
from typing import Awaitable, Callable, Protocol

class Tool(Protocol):
    name: str
    description: str
    input_schema: dict[str, Any]
    parallel: bool

    async def __call__(self, **kwargs: Any) -> Any:
        ...
```

### 8.2 ToolRegistry

```python
@dataclass
class ToolRegistry:
    tools: dict[str, Tool] = field(default_factory=dict)

    def register(self, tool: Tool) -> None:
        self.tools[tool.name] = tool

    def resolve(self, name: str) -> Tool | None:
        return self.tools.get(name)

    def to_llm_tools(self) -> list[dict[str, Any]]:
        ...
```

### 8.3 execute_tool_calls

执行路径保持与 TS 一致：

```text
assistant toolCall
  -> resolve_tool()
  -> validate_input()
  -> execute_tool()
  -> create_tool_result_message()
  -> emit tool_execution_start/end
```

错误处理规则：

- 工具不存在：生成 `is_error=True` 的 tool result。
- 参数校验失败：生成 `is_error=True` 的 tool result。
- 工具执行异常：捕获异常，生成 `is_error=True` 的 tool result。
- 默认顺序执行；仅当 tool 明确声明 `parallel=True` 时才并行。

这样可以避免单个工具异常导致整个 agent run 崩溃，也能让模型看到错误并自我修正。

## 9. Session 与持久化

TS 版有三层消息存储：内存态、`sessions.json`、JSONL transcript。Python 版沿用这一设计。

### 9.1 SessionEntry

```python
@dataclass
class SessionEntry:
    session_id: str
    updated_at: str
    session_file: str
    status: Literal["active", "archived", "error"] = "active"
    cwd: str | None = None
    workspace_dir: str | None = None
    model: str | None = None
    provider: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)
```

### 9.2 SessionStore

`sessions.json` 保存会话索引与元数据。

建议能力：

- `load()`: 读取并解析 `sessions.json`。
- `save()`: 原子写入。
- `update_entry()`: 更新单个 `SessionEntry`。
- Windows 上读取空文件或 JSON parse 失败时短暂重试，最多 3 次。

原子写入建议：

```text
write sessions.json.tmp
fsync if available
replace sessions.json
```

### 9.3 JSONL Transcript

最小 entry：

```json
{
  "type": "message",
  "id": "...",
  "parentId": null,
  "timestamp": "...",
  "message": {
    "role": "assistant",
    "content": [],
    "stopReason": "stop"
  }
}
```

Python 写入接口：

```python
class Transcript:
    def append_message(self, message: BaseMessage) -> None:
        entry = build_transcript_entry(message)
        append_jsonl_entry(self.path, entry)
```

`append_jsonl_entry()` 必须保证：

- 每次写入一行合法 JSON。
- 末尾带换行。
- 消息中的换行不会破坏 JSONL。
- 可选实现文件锁，避免多进程同时写入交错。

第一版可以先做进程内锁；如果需要多进程安全，再加入平台相关 file lock。

## 10. AgentSession 生产会话层

`AgentSession` 负责把 `Agent`、`SessionStore`、`Transcript`、retry、context guard 连接起来。

建议接口：

```python
class AgentSession:
    def __init__(
        self,
        *,
        agent: Agent,
        store: SessionStore,
        transcript: Transcript,
        retry_policy: RetryPolicy,
        hooks: SessionHooks | None = None,
    ) -> None:
        ...

    async def run_prompt(self, text: str) -> AssistantMessage:
        ...

    async def handle_post_agent_run(self, message: AssistantMessage) -> AssistantMessage:
        ...
```

事件订阅：

```text
Agent emits message_end
  -> AgentSession persists message to JSONL
  -> AgentSession updates sessions.json updatedAt/status
  -> hooks.after_message_persisted()
```

`handle_post_agent_run()` 逻辑：

```text
last assistant message
  -> if retryable error: prepare_retry(), then agent.continue_()
  -> if context overflow: remove last assistant error, run compaction, then continue
  -> otherwise return message
```

## 11. Retry 策略

### 11.1 RetryPolicy

```python
@dataclass
class RetryPolicy:
    enabled: bool = True
    max_attempts: int = 3
    base_delay_seconds: float = 1.0
    max_delay_seconds: float = 30.0
```

### 11.2 is_retryable_error

条件与 TS 版保持一致：

- `message.stop_reason == "error"`。
- 存在 `message.error_message`。
- 不是 context overflow。
- 错误文本匹配临时故障，例如 `overloaded`、`rate limit`、`429`、`5xx`、`network error`、`timeout`、`stream ended`。

### 11.3 prepare_retry

```text
check retry enabled
  -> retry_count += 1
  -> if retry_count > max_attempts: stop retry
  -> calculate exponential backoff
  -> emit auto_retry_start
  -> remove last assistant error from AgentState.messages
  -> sleep delay
  -> return true
```

关键语义：

- 错误 message 保留在 JSONL transcript 中。
- 错误 message 从下一次模型上下文中移除。
- 这样既保留历史，又避免模型误读上一次 provider error。

## 12. 上下文保护

Python 版至少需要两层保护。

### 12.1 写入 transcript 前保护

模块：`openclaw/guards/transcript_tool_result_guard.py`

职责：

- tool result name 归一化。
- tool result 输出大小上限截断。
- secret redaction。
- 避免 error/aborted assistant message 被误解析为有效 tool call。
- 对孤儿 tool call / tool result 做兜底处理。

第一版可以实现为 `Transcript.append_message()` 前的 filter，而不是 monkey patch。

```python
class TranscriptToolResultGuard:
    def before_append(self, message: BaseMessage) -> BaseMessage:
        ...
```

### 12.2 模型调用前上下文保护

模块：`openclaw/guards/context_guard.py`

职责：

- 在 `stream_assistant_response()` 调 provider 前检查上下文。
- 截断过大的单个 tool result。
- 估算总上下文长度。
- 超过高水位时抛出可识别的 context overflow error。

建议接口：

```python
class ContextGuard:
    def transform(self, messages: list[BaseMessage]) -> list[BaseMessage]:
        ...
```

第一版可以使用字符数估算，后续再接 provider tokenizer。

### 12.3 Context overflow 后恢复

当 provider 或 `ContextGuard` 返回 context overflow：

```text
mark overflow_recovery_attempted
  -> remove last assistant error from memory context
  -> run_auto_compaction("overflow")
  -> agent.continue_()
```

第一版的 compaction 可以先实现为 summary message：

```text
old messages
  -> summarize earlier turns
  -> keep recent N messages
  -> insert system/user summary message
```

## 13. 系统提示词构建

不要把系统提示词写成单个巨大的硬编码字符串。Python 版应拆成 section builder。

```python
@dataclass
class SystemPromptParams:
    workspace_dir: str
    extra_system_prompt: str | None = None
    tool_names: list[str] = field(default_factory=list)
    tool_summaries: list[str] = field(default_factory=list)
    user_timezone: str | None = None
    context_files: list[str] = field(default_factory=list)
    skills_prompt: str | None = None
    docs_path: str | None = None
    source_path: str | None = None
    runtime_info: dict[str, Any] = field(default_factory=dict)
```

```python
def build_system_prompt(params: SystemPromptParams) -> str:
    sections: list[str] = []
    sections.append(build_tooling_section(params))
    sections.append(build_tool_call_style_section(params))
    sections.append(build_execution_bias_section(params))
    sections.append(build_safety_section(params))
    sections.append(build_workspace_section(params))
    sections.append(build_documentation_section(params))
    sections.append(build_project_context_section(params))
    sections.append(CACHE_BOUNDARY)
    sections.append(build_messaging_section(params))
    sections.append(build_runtime_section(params))
    return "\n\n".join(section for section in sections if section)
```

建议保留 `CACHE_BOUNDARY` 常量，即使第一版不真正使用 provider prefix cache。这样后续可以无痛接入。

## 14. 自定义逻辑扩展点

用户自己的逻辑不建议直接写进 `run_agent_loop()`。建议统一放到 hooks。

```python
class SessionHooks(Protocol):
    async def before_model_call(self, context: list[BaseMessage]) -> list[BaseMessage]:
        ...

    async def after_model_message(self, message: AssistantMessage) -> AssistantMessage:
        ...

    async def before_tool_call(self, call: ToolCallBlock) -> ToolCallBlock:
        ...

    async def after_tool_result(self, result: ToolResultMessage) -> ToolResultMessage:
        ...

    async def after_message_persisted(self, message: BaseMessage) -> None:
        ...
```

推荐插入点：

```text
before_model_call
  -> custom context rewrite / policy injection
after_model_message
  -> custom stop condition / audit
before_tool_call
  -> custom permission check / argument rewrite
after_tool_result
  -> custom result normalization / redaction
after_message_persisted
  -> custom indexing / analytics / external sync
```

原则：

- hook 可以修改消息，但必须返回合法消息。
- hook 抛错时应转成 assistant error 或 tool error，而不是让主进程崩溃。
- hook 的执行结果应可测试、可关闭。

## 15. 第一版实现顺序

建议按以下顺序实现：

1. `llm/types.py`: 消息、content block、stop reason。
2. `tools/registry.py`: 工具注册、查找、序列化为 LLM tools。
3. `llm/provider.py`: provider stream 协议和一个 mock provider。
4. `agent/events.py`: 事件类型。
5. `agent/state.py`: `AgentState`。
6. `agent/loop.py`: `stream_assistant_response()`、`execute_tool_calls()`、`run_agent_loop()`。
7. `agent/agent.py`: `Agent.prompt()`、`Agent.continue_()`、事件订阅。
8. `session/transcript.py`: JSONL append。
9. `session/store.py`: `sessions.json` load/save/update。
10. `session/agent_session.py`: 持久化、retry、post-run 处理。
11. `guards/context_guard.py`: 模型调用前截断和 overflow 检测。
12. `prompt/system_prompt.py`: section builder。
13. `custom/hooks.py`: 自定义逻辑扩展点。

每一步都应配套单元测试，尤其是 loop 和 transcript。

## 16. 最小测试清单

### 16.1 Agent loop

- 普通 user prompt 返回 `stop`。
- assistant 返回 `toolUse` 后执行工具并继续下一轮。
- 工具不存在时生成 error tool result。
- provider error 生成 `stop_reason="error"` 的 assistant message。
- abort 生成 `stop_reason="aborted"` 的 assistant message。

### 16.2 Session

- `message_end` 后写入 JSONL。
- `sessions.json` 更新 `updated_at`。
- JSONL 每行都是合法 JSON。
- Windows 短暂空文件 / parse error 会重试。

### 16.3 Retry

- 429 / timeout / network error 会 retry。
- context overflow 不走普通 retry。
- retry 前会从内存态移除最后一条 assistant error。
- transcript 仍保留错误历史。

### 16.4 Context guard

- 单个超大 tool result 被截断。
- 总上下文超过阈值时抛出 context overflow。
- 截断不修改原始 transcript，只影响下一次模型上下文。

### 16.5 System prompt

- sections 顺序稳定。
- dynamic sections 位于 cache boundary 后。
- 空 section 不产生多余内容。

## 17. 关键设计决策

1. Python 版使用 async loop。模型流式响应和工具执行天然适合 `asyncio`，后续也方便并行工具调用。
2. 内存态与 transcript 分离。内存态服务下一次模型调用，transcript 服务历史记录和审计。
3. 错误 message 可以持久化，但 retry / overflow 恢复前必须从模型上下文移除。
4. tool error 不应崩溃主 loop，而应作为 tool result 返回给模型。
5. 自定义逻辑通过 hooks 接入，不直接污染核心 loop。
6. 第一版保留 prompt cache boundary 的结构，但不强制实现 provider cache。
7. 上下文长度第一版可用字符数估算，provider tokenizer 后续替换。

## 18. 后续演进路线

第一阶段完成最小闭环后，再按收益逐步迁移生产级能力：

```text
phase 1: minimal async agent loop + tools + JSONL + retry
phase 2: context guard + summary compaction + system prompt sections
phase 3: follow-up / steering queues + richer AgentEvent
phase 4: multi-provider adapters + tokenizer-aware context budget
phase 5: plugin runtime / subagent / group chat
phase 6: prompt cache optimization + complete transcript transaction model
```

这样可以先得到一个能跑、能测、能扩展的 Python OpenClaw 核心，再逐步吸收 TS 版的生产级复杂度。

## 19. OpenAI Responses Adapter 实现记录

当前 Python 版已经开始接入真实 provider adapter，第一版实现为：

```text
openclaw/llm/openai_provider.py
```

该 adapter 基于 OpenAI Responses API 的概念边界实现：

```text
pyclaw message/tools
  -> OpenAI Responses input/tools
  -> client.responses.create(..., stream=True)
  -> OpenAI stream events
  -> ProviderEvent start/delta/done/error
  -> AssistantMessage
```

### 19.1 依赖方式

OpenAI SDK 作为可选依赖声明在 `pyproject.toml`：

```toml
[project.optional-dependencies]
openai = ["openai>=1.68.0"]
```

安装方式：

```powershell
py -m pip install -e ".[openai]"
```

如果未安装 `openai` 包，实例化 `OpenAIProvider()` 时会给出明确错误；这不会影响 `MockProvider` 和现有单元测试。

### 19.2 消息转换

内部消息转换为 Responses input 的规则：

- `user` 的 `text` block 转为 `{ "role": "user", "content": "..." }`。
- `assistant` 的 `text` block 转为 `{ "role": "assistant", "content": "..." }`。
- `assistant` 的 `toolCall` block 转为 `function_call` input item。
- `tool` 的 `toolResult` block 转为 `function_call_output` input item。

工具 schema 转换规则：

```text
ToolRegistry.to_llm_tools()
  -> { name, description, input_schema }
  -> OpenAI function tool { type: "function", name, description, parameters }
```

### 19.3 流式事件转换

adapter 将 OpenAI stream event 归一成 pyclaw provider event：

- `response.output_text.delta` -> `ProviderEvent("delta", {"text": ...})`
- `response.output_item.done` 中的 `function_call` -> pyclaw `toolCall` block
- `response.completed` -> 最终 `AssistantMessage`
- `response.failed` / `response.incomplete` / `error` -> `ProviderEvent("error", ...)`

最终 `AssistantMessage.stop_reason` 的最小映射：

- 包含 function call -> `toolUse`
- completed text -> `stop`
- incomplete -> `length`
- failed -> `error`

### 19.4 测试策略

当前测试不依赖真实网络或 API key，而是用 fake client 验证：

- tools 转 OpenAI function tools。
- user / assistant / tool result 转 Responses input。
- Responses output 中的 text 和 function_call 转回 `AssistantMessage`。
- fake stream 能产出最终 `toolUse` message。

对应测试文件：

```text
tests/test_openai_provider.py
```

后续如果要做真实集成测试，应单独放在需要 API key 的测试组里，不应阻塞默认单元测试。


## 20. .env 配置加载

真实 OpenAI adapter 可以从环境变量读取认证信息。为了避免把密钥写进代码，项目提供：

```text
.env.example
openclaw/config/env.py
```

使用方式：

```powershell
Copy-Item .env.example .env
```

然后在 `.env` 中填写：

```env
OPENAI_API_KEY=your_api_key_here
# OPENAI_BASE_URL=
# OPENAI_ORGANIZATION=
# OPENAI_PROJECT=
# OPENAI_MODEL=gpt-4.1-mini
```

`.env` 已加入 `.gitignore`，不要提交真实密钥。

Python 代码中需要显式加载：

```python
from openclaw.config import load_env_file
from openclaw import Agent, OpenAIProvider

load_env_file()

provider = OpenAIProvider()
agent = Agent(
    model="gpt-4.1-mini",
    provider=provider,
    system_prompt="You are helpful.",
)
```

`load_env_file()` 只使用标准库，不依赖 `python-dotenv`。它支持：

- 空行和 `#` 注释。
- `KEY=value`。
- `export KEY=value`。
- 单引号或双引号包裹的值。
- 默认不覆盖已有环境变量；如需覆盖，使用 `load_env_file(override=True)`。

## 21. Reasoning 模型与 model_options 透传

对于带 reasoning / 思考能力的模型，pyclaw 不应把 reasoning 配置硬编码在 `OpenAIProvider` 内部，而应让调用方通过 `Agent` 传入模型级选项。

当前实现新增：

```python
agent = Agent(
    model="gpt-4.1-mini",
    provider=OpenAIProvider(),
    system_prompt="You are helpful.",
    model_options={
        "reasoning": {"effort": "low", "summary": "auto"},
        "max_output_tokens": 4096,
    },
)
```

传递路径：

```text
Agent.model_options
  -> LoopConfig.options
  -> provider.stream(..., options=...)
  -> OpenAIProvider request.update(options)
  -> client.responses.create(...)
```

这样 OpenAI Responses API 支持的模型参数可以从外层透传，例如：

- `reasoning`
- `max_output_tokens`
- `temperature`，如果目标模型支持
- 其他 Responses API 兼容参数

当前边界：

- reasoning 模型可以通过 `OpenAIProvider` 使用。
- reasoning 配置可以通过 `model_options` 透传。
- 默认 transcript 仍只保存最终 assistant message、tool call 和 tool result。
- reasoning token 本身通常不会作为普通文本暴露；后续如果需要保存 reasoning summary，需要在 adapter 中显式解析相关 stream/output item。
- context guard 当前基于字符数估算，后续应为 reasoning 模型预留输出 token / reasoning token 预算。

测试覆盖：

```text
tests/test_model_options.py
```

该测试验证：

- `Agent.model_options` 会传给 provider。
- `OpenAIProvider` 会把 `reasoning` 和 `max_output_tokens` 合并进 Responses request。

## 22. Python CLI 入口层

当前 Python 版已新增最小 CLI 入口层：

```text
openclaw/cli.py
openclaw/__main__.py
pyproject.toml [project.scripts]
```

入口层支持：

- `python -m openclaw "prompt"`
- `pyclaw "prompt"`
- `openclaw "prompt"`
- `--provider mock` 本地无网络测试
- `--json` 输出完整 assistant message
- `--reasoning-effort` 与 `--max-output-tokens` 透传到 `model_options`

`gateway run` 当前仅作为保留命令注册，尚未实现长期运行的 gateway server。

详细设计见同目录：`openclaw-python-cli-entry-technical-design.md`。
## 23. OpenAI-compatible Chat Completions 模式

`OpenAIProvider` 已支持 `OPENAI_API_MODE`：

```text
responses
chat_completions
auto
```

当 `OPENAI_BASE_URL` 指向第三方兼容服务，例如 `https://api.deepseek.com` 时，`auto` 会选择 `chat_completions`，避免使用 Responses API 造成 404。

DeepSeek 示例：

```env
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-v4-flash
OPENAI_API_MODE=chat_completions
```

详细说明见 `openclaw-python-cli-entry-technical-design.md`。
## 24. chatdata Transcript 存储

CLI 已默认把 session 与 transcript 写入当前项目目录下的 `chatdata/`。

Git 策略：

```gitignore
chatdata/*
!chatdata/.gitkeep
```

因此真实聊天记录不会提交，只保留目录占位文件。

详细说明见 `openclaw-python-cli-entry-technical-design.md`。
## 25. Transcript 查看命令

CLI 已支持：

```cmd
pyclaw transcripts show <session-id> --format text
pyclaw transcripts show <session-id> --format detail
pyclaw transcripts show <session-id> --format json
```

默认从 `chatdata/<session-id>.jsonl` 读取 transcript。

详细说明见 `openclaw-python-cli-entry-technical-design.md`。