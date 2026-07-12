# OpenClaw Agent Loop Implementation Notes

本文档整理 OpenClaw 中与单文件 `claw0` 原型相对应的生产级 TypeScript 实现，重点覆盖循环位置、消息存储、`stopReason` 分支、错误与重试、上下文保护，以及系统提示词组装。它也可作为后续改写 Python 版本时的技术拆解参考。

## 1. 总览

OpenClaw 的生产实现不是一个简单的单文件 `agent_loop()`。核心职责被拆成了几层：

- `packages/agent-core/src/agent.ts`: 面向调用方的 `Agent` 类，维护内存态、事件订阅、队列、abort 状态，并调用底层 loop。
- `packages/agent-core/src/agent-loop.ts`: 真正的循环、模型流式响应、工具调用分支、`stopReason` 处理。
- `src/agents/sessions/agent-session.ts`: 生产会话层，负责把事件写入 transcript、自动重试、自动压缩、上下文溢出恢复。
- `src/config/sessions/*`: 会话元数据 `sessions.json`、JSONL transcript、路径解析、读写锁、append 事务。
- `src/agents/system-prompt.ts`: OpenClaw 拥有的系统提示词渲染器。

可以把它理解成：

```text
AgentSession
  -> Agent
    -> runAgentLoop / runAgentLoopContinue
      -> streamAssistantResponse
      -> executeToolCalls
      -> emit events
  -> SessionManager / transcript append
  -> retry / compaction / context guards
```

## 2. 对应关系速查

| 方面 | claw0 原型 | OpenClaw 生产实现 |
| --- | --- | --- |
| 循环入口 | 单文件 `agent_loop()` | [`Agent.prompt()`](../packages/agent-core/src/agent.ts) -> `runAgentLoop()` |
| 核心循环 | `while True` | [`runLoop()`](../packages/agent-core/src/agent-loop.ts) |
| 消息存储 | 内存 `list[dict]` | `Agent.mutableState.messages` + JSONL transcript + `sessions.json` 元数据 |
| 模型调用 | 同步或简单请求 | `streamAssistantResponse()` 流式读取 provider event stream |
| 工具调用 | 发现 tool call 后执行 | `stopReason === "toolUse"` 后进入 `executeToolCalls()` |
| stop reason | 字符串分支 | `StopReason = "stop" | "length" | "toolUse" | "error" | "aborted"` |
| 错误处理 | 记录最后一条错误后继续/退出 | 错误消息持久化、自动重试、上下文溢出压缩、tool-result guard |
| 系统提示词 | 硬编码字符串 | `buildAgentSystemPrompt()` 多 section 组装 |

## 3. Agent 类与运行入口

主要文件：

- [`../packages/agent-core/src/agent.ts`](../packages/agent-core/src/agent.ts)
- [`../packages/agent-core/src/agent-loop.ts`](../packages/agent-core/src/agent-loop.ts)

`Agent` 类位于 `packages/agent-core/src/agent.ts`。它不是直接写业务持久化的地方，而是一个核心运行时封装：

- 保存当前 `messages`、`tools`、`model`、`systemPrompt`。
- 提供 `prompt()`、`continue()`、`steer()`、`followUp()`。
- 将 `AgentEvent` 归约到内存状态。
- 调用底层 `runAgentLoop()` 或 `runAgentLoopContinue()`。

关键位置：

- `Agent` 类定义：`packages/agent-core/src/agent.ts:204`
- `prompt()` 入口：`packages/agent-core/src/agent.ts:373`
- `runPromptMessages()` 调用 `runAgentLoop()`：`packages/agent-core/src/agent.ts:438`
- `runContinuation()` 调用 `runAgentLoopContinue()`：`packages/agent-core/src/agent.ts:454`
- `createContextSnapshot()` 复制当前 `systemPrompt/messages/tools`：`packages/agent-core/src/agent.ts:466`
- `createLoopConfig()` 把模型、工具钩子、队列等传入 loop：`packages/agent-core/src/agent.ts:474`
- `processEvents()` 把事件写回内存态：`packages/agent-core/src/agent.ts:568`

`Agent.processEvents()` 对消息状态的处理很重要：

- `message_start`: 设置 `streamingMessage`。
- `message_update`: 更新流式中的 assistant message。
- `message_end`: 将最终 message push 到 `mutableState.messages`。
- `tool_execution_start/end`: 更新 pending tool call 集合。
- `turn_end`: 如果 assistant 带 `errorMessage`，写入内存错误状态。

这相当于单文件原型里不断 append 到 `messages` 的动作，但生产实现通过事件完成。

## 4. 核心循环 runLoop

主要文件：

- [`../packages/agent-core/src/agent-loop.ts`](../packages/agent-core/src/agent-loop.ts)

核心入口：

- `runAgentLoop()`：`packages/agent-core/src/agent-loop.ts:172`
- `runAgentLoopContinue()`：`packages/agent-core/src/agent-loop.ts:199`
- `runLoop()`：`packages/agent-core/src/agent-loop.ts:266`

`runAgentLoop()` 做三件事：

1. 把用户 prompt 加入当前上下文。
2. 发出 `agent_start`、`turn_start`、用户消息的 `message_start/message_end`。
3. 调用 `runLoop()` 进入真正循环。

`runLoop()` 包含两个循环：

- 外层 `while (true)`: 用于处理 follow-up 消息。如果 agent 本来要结束，但 follow-up 队列里还有消息，则继续跑。
- 内层 `while (hasMoreToolCalls || pendingMessages.length > 0)`: 用于处理工具调用链和 steering 消息。

核心流程：

```text
start turn
  -> 注入 pending steering messages
  -> streamAssistantResponse()
  -> 如果 stopReason 是 error/aborted，结束
  -> 如果 stopReason 是 toolUse，执行工具
  -> 写入 toolResult messages
  -> prepareNextTurn()
  -> shouldStopAfterTurn()
  -> drain steering messages
end when no tools, no steering, no follow-up
```

关键位置：

- abort 时生成 assistant failure message：`packages/agent-core/src/agent-loop.ts:281`
- 进入模型流式响应：`packages/agent-core/src/agent-loop.ts:336`
- `error/aborted` 直接结束：`packages/agent-core/src/agent-loop.ts:347`
- `toolUse` 分支：`packages/agent-core/src/agent-loop.ts:358`
- 工具结果加入上下文与新消息：`packages/agent-core/src/agent-loop.ts:369`
- 每轮结束后 `prepareNextTurn()`：`packages/agent-core/src/agent-loop.ts:381`
- `shouldStopAfterTurn()`：`packages/agent-core/src/agent-loop.ts:409`
- follow-up 消息处理：`packages/agent-core/src/agent-loop.ts:427`

## 5. 流式模型响应

主要函数：

- `streamAssistantResponse()`：`packages/agent-core/src/agent-loop.ts:446`

它的职责是把 agent 内部消息转换为 provider 可以理解的 LLM context，并读取流式事件：

```text
AgentMessage[]
  -> transformContext()
  -> convertToLlm()
  -> Context { systemPrompt, messages, tools }
  -> streamFunction(model, context, options)
  -> start / delta / done / error events
  -> final AssistantMessage
```

关键点：

- `transformContext` 可在模型调用前改写上下文，例如截断过大的 tool result。
- `convertToLlm` 将 agent 自己的消息格式转成 provider 消息格式。
- `start` 事件会把 partial assistant message 放入 `context.messages`。
- delta 事件不断更新 partial assistant message。
- `done/error` 时通过 `response.result()` 取得最终 assistant message。

关键位置：

- 应用 `transformContext`：`packages/agent-core/src/agent-loop.ts:454`
- 转换为 LLM messages：`packages/agent-core/src/agent-loop.ts:460`
- 构建 `Context`：`packages/agent-core/src/agent-loop.ts:463`
- 调用 stream function：`packages/agent-core/src/agent-loop.ts:476`
- 处理 `start`：`packages/agent-core/src/agent-loop.ts:487`
- 处理 delta 更新：`packages/agent-core/src/agent-loop.ts:496`
- 处理 `done/error`：`packages/agent-core/src/agent-loop.ts:517`

## 6. stopReason 设计

主要文件：

- [`../packages/llm-core/src/types.ts`](../packages/llm-core/src/types.ts)

OpenClaw 对不同 provider 的终止原因做了归一化：

```ts
export type StopReason = "stop" | "length" | "toolUse" | "error" | "aborted";
```

位置：`packages/llm-core/src/types.ts:276`

`AssistantMessage` 中保存：

- `role: "assistant"`
- `content`
- `api/provider/model`
- `usage`
- `stopReason`
- `errorMessage/errorCode/errorType/errorBody`
- `timestamp`

生产 loop 中对 `stopReason` 的主要分支：

- `error` / `aborted`: 发出 `turn_end` 和 `agent_end`，结束 run。
- `toolUse`: 提取 tool calls，执行工具，并把 tool result 放回上下文继续下一轮。
- `stop`: 正常完成。
- `length`: 模型达到长度上限，通常不会继续工具执行。

错误消息构造函数：

- `createLoopFailureMessage()`：`packages/agent-core/src/agent-loop.ts:232`

它会构造一个 assistant message，并设置：

```ts
stopReason: aborted ? "aborted" : "error"
```

## 7. 工具调用执行

主要文件：

- [`../packages/agent-core/src/agent-loop.ts`](../packages/agent-core/src/agent-loop.ts)

当 assistant message 的 `stopReason === "toolUse"` 且内容中存在 `toolCall` block 时，loop 调用 `executeToolCalls()`。

关键位置：

- 判断 `toolUse`：`packages/agent-core/src/agent-loop.ts:358`
- `executeToolCalls()`：`packages/agent-core/src/agent-loop.ts:548`
- 顺序执行：`packages/agent-core/src/agent-loop.ts:613`
- 并行执行：`packages/agent-core/src/agent-loop.ts:673`
- 查找工具或 deferred tool：`packages/agent-core/src/agent-loop.ts:797`
- 参数准备与校验：`packages/agent-core/src/agent-loop.ts:841`

执行路径：

```text
assistant toolCall
  -> resolveToolCallTool()
  -> prepareToolCall()
  -> executePreparedToolCall()
  -> finalizeExecutedToolCall()
  -> createToolResultMessage()
  -> emit toolResult message
```

如果工具不存在或参数错误，不会直接让整个进程崩掉，而是生成错误 tool result 给模型继续处理。

## 8. 消息存储：内存态、sessions.json、JSONL

OpenClaw 的消息存储不是单一 `list[dict]`，而是三层：

1. `Agent.mutableState.messages`: 当前进程内的运行上下文。
2. `sessions.json`: 会话索引与元数据。
3. `${sessionId}.jsonl`: 持久化 transcript，每行一个 JSON entry。

### 8.1 内存消息

主要位置：

- `Agent.processEvents()`：`packages/agent-core/src/agent.ts:568`
- `message_end` push 到 `mutableState.messages`：`packages/agent-core/src/agent.ts:583`

这层最接近单文件原型中的 `messages: list[dict]`。

### 8.2 sessions.json 元数据

主要文件：

- [`../src/config/sessions/types.ts`](../src/config/sessions/types.ts)
- [`../src/config/sessions/store-load.ts`](../src/config/sessions/store-load.ts)
- [`../src/config/sessions/store.ts`](../src/config/sessions/store.ts)

`SessionEntry` 位于 `src/config/sessions/types.ts:213`，核心字段包括：

- `sessionId`
- `updatedAt`
- `sessionFile`
- `spawnedBy`
- `spawnedWorkspaceDir`
- `spawnedCwd`
- `status`
- `chatType`
- 模型、工具、插件、恢复、quota 等大量运行时状态

读写入口：

- `loadSessionStore()`：`src/config/sessions/store-load.ts:378`
- `readSessionStoreSnapshot()`：`src/config/sessions/store-load.ts:507`
- `saveSessionStore()`：`src/config/sessions/store.ts:1004`
- `updateSessionStore()`：`src/config/sessions/store.ts:1014`
- `updateSessionStoreEntry()`：`src/config/sessions/store.ts:1724`

`loadSessionStore()` 在 Windows 上会对短暂空文件或 JSON parse 失败做最多 3 次重试。这是生产实现相比原型明显更稳的地方。

### 8.3 JSONL transcript

主要文件：

- [`../src/config/sessions/paths.ts`](../src/config/sessions/paths.ts)
- [`../src/config/sessions/transcript.ts`](../src/config/sessions/transcript.ts)
- [`../src/config/sessions/session-accessor.ts`](../src/config/sessions/session-accessor.ts)
- [`../src/config/sessions/transcript-append.ts`](../src/config/sessions/transcript-append.ts)
- [`../src/config/sessions/transcript-jsonl.ts`](../src/config/sessions/transcript-jsonl.ts)

路径解析：

- `resolveSessionTranscriptPathInDir()`：`src/config/sessions/paths.ts:244`

它生成：

```text
<sessionId>.jsonl
<sessionId>-topic-<topicId>.jsonl
```

写入链路：

```text
AgentSession receives message_end
  -> sessionManager.appendMessage()
  -> appendToSessionFile()
  -> persistSessionTranscriptTurn()
  -> appendTranscriptTurnMessages()
  -> appendSessionTranscriptMessageLocked()
  -> appendJsonlEntry()
```

关键位置：

- `AgentSession` 收到 `message_end` 后 append：`src/agents/sessions/agent-session.ts:620`
- `appendToSessionFile()`：`src/config/sessions/transcript.ts:424`
- `persistSessionTranscriptTurn()`：`src/config/sessions/session-accessor.ts:2239`
- `appendTranscriptTurnMessages()`：`src/config/sessions/session-accessor.ts:2273`
- `appendSessionTranscriptMessageLocked()`：`src/config/sessions/transcript-append.ts:637`
- 最终 `appendJsonlEntry()`：`src/config/sessions/transcript-append.ts:701`
- JSONL 序列化与换行保护：`src/config/sessions/transcript-jsonl.ts:11`
- 追加写文件：`src/config/sessions/transcript-jsonl.ts:90`

JSONL entry 大致结构：

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

真实实现还会处理：

- transcript header。
- idempotency key 去重。
- parent-linked transcript。
- secret redaction。
- session write lock。
- transcript update publish。

## 9. AgentSession：生产会话层

主要文件：

- [`../src/agents/sessions/agent-session.ts`](../src/agents/sessions/agent-session.ts)

`AgentSession` 是生产运行时中非常关键的一层。它连接了：

- `Agent` core loop。
- `SessionManager` 持久化。
- 自动重试。
- 自动 compaction。
- tool-result guard。
- 插件 hook。
- UI/runtime 事件。

关键位置：

- 收到 `message_end` 后持久化消息：`src/agents/sessions/agent-session.ts:620`
- 跟踪最后一个 assistant message：`src/agents/sessions/agent-session.ts:644`
- 成功 assistant 响应后重置 retry 计数：`src/agents/sessions/agent-session.ts:653`
- `runPrompt()` 后循环执行 `handlePostAgentRun()`：`src/agents/sessions/agent-session.ts:1090`
- `handlePostAgentRun()`：`src/agents/sessions/agent-session.ts:1100`

`handlePostAgentRun()` 的核心逻辑：

```text
lastAssistantMessage
  -> 如果是 retryable error，prepareRetry() 后 agent.continue()
  -> 如果是最终 error，发出 auto_retry_end
  -> 否则检查 compaction
```

## 10. 错误处理与自动重试

主要文件：

- [`../src/agents/sessions/agent-session.ts`](../src/agents/sessions/agent-session.ts)

底层 `agent-loop.ts` 只负责把异常包装成 assistant error message。生产级“是否重试”由 `AgentSession` 处理。

关键位置：

- `isRetryableError()`：`src/agents/sessions/agent-session.ts:2601`
- `prepareRetry()`：`src/agents/sessions/agent-session.ts:2623`

`isRetryableError()` 只对以下条件返回 true：

- `message.stopReason === "error"`
- 存在 `errorMessage`
- 不是 context overflow
- 错误文本匹配临时故障，例如 overloaded、rate limit、429、5xx、network error、timeout、stream ended 等

`prepareRetry()` 做：

1. 检查 retry 设置是否启用。
2. 增加 `retryCount`。
3. 超过最大次数则放弃。
4. 按指数退避计算 delay：

```ts
const delayMs = settings.baseDelayMs * 2 ** (this.retryCount - 1);
```

5. 发出 `auto_retry_start` 事件。
6. 从 agent 内存态里移除最后一条 assistant error message。
7. 等待 delay。
8. 返回 true，让调用方执行 `agent.continue()`。

这里有一个重要设计：错误消息会保留在 session transcript 里用于历史记录，但会从下一次模型上下文中移除，避免模型把上一次 error 当成真实对话继续读。

## 11. 上下文溢出与 tool result 保护

OpenClaw 有多层上下文保护，主要解决两个问题：

- 工具输出太大，下一次模型调用爆上下文。
- assistant tool call 与 tool result 不匹配，严格 provider 报错。

### 11.1 Tool result 持久化保护

主要文件：

- [`../src/agents/session-tool-result-guard.ts`](../src/agents/session-tool-result-guard.ts)

入口：

- `installSessionToolResultGuard()`：`src/agents/session-tool-result-guard.ts:566`

它会 monkey-patch `sessionManager.appendMessage`，在写入 transcript 前做保护。

关键逻辑：

- tool result 写入前进行 name 归一化、大小上限截断、redaction：`src/agents/session-tool-result-guard.ts:762`
- 对 `stopReason === "error"` 或 `"aborted"` 的 assistant message，不提取 tool calls：`src/agents/session-tool-result-guard.ts:798`
- 对非 toolResult 消息，必要时 flush 或清空 pending tool calls，避免孤儿 tool use：`src/agents/session-tool-result-guard.ts:810`
- 最终替换 `sessionManager.appendMessage`：`src/agents/session-tool-result-guard.ts:900`

这层保护的是“写入 transcript 的内容”。

### 11.2 模型调用前上下文保护

主要文件：

- [`../src/agents/embedded-agent-runner/tool-result-context-guard.ts`](../src/agents/embedded-agent-runner/tool-result-context-guard.ts)

入口：

- `installToolResultContextGuard()`：`src/agents/embedded-agent-runner/tool-result-context-guard.ts:469`

它会包装 `Agent.transformContext`，在每次模型调用前检查上下文：

- 单个 tool result 过大时，克隆消息并截断。
- 如果中途 tool result 导致上下文压力过高，发出 mid-turn precheck signal。
- 如果总上下文仍超过高水位阈值，抛出 preemptive context overflow。

关键位置：

- 包装 `transformContext`：`src/agents/embedded-agent-runner/tool-result-context-guard.ts:492`
- 判断是否需要截断 tool result：`src/agents/embedded-agent-runner/tool-result-context-guard.ts:498`
- 执行截断：`src/agents/embedded-agent-runner/tool-result-context-guard.ts:505`
- mid-turn precheck：`src/agents/embedded-agent-runner/tool-result-context-guard.ts:510`
- 超过 preemptive overflow 阈值时抛错：`src/agents/embedded-agent-runner/tool-result-context-guard.ts:554`

这层保护的是“下一次发给模型的上下文”。

### 11.3 Context overflow 后 compaction

主要文件：

- [`../src/agents/sessions/agent-session.ts`](../src/agents/sessions/agent-session.ts)

关键位置：

- context overflow 处理：`src/agents/sessions/agent-session.ts:2062`

当检测到上下文溢出：

1. 标记 `overflowRecoveryAttempted = true`。
2. 从 agent 内存态移除最后一条 assistant error message。
3. 调用 `runAutoCompaction("overflow", true)`。

这和 retry 的思想一致：错误可以保存在历史里，但不要把它带入修复后的下一次模型上下文。

## 12. 系统提示词组装

主要文件：

- [`../src/agents/system-prompt.ts`](../src/agents/system-prompt.ts)
- [`./concepts/system-prompt.md`](./concepts/system-prompt.md)

入口：

- `buildAgentSystemPrompt()`：`src/agents/system-prompt.ts:682`

OpenClaw 不依赖 provider 默认系统提示词，而是每次 run 组装自己的系统提示词。参数包括：

- `workspaceDir`
- `extraSystemPrompt`
- `toolNames`
- `toolSummaries`
- `userTimezone`
- `contextFiles`
- `skillsPrompt`
- `heartbeatPrompt`
- `docsPath`
- `sourcePath`
- `runtimeInfo`
- sandbox、memory、message、reaction、provider overlay 等

固定 sections 主要包括：

- Tooling
- Tool Call Style
- Execution Bias
- Safety
- OpenClaw Control
- Skills
- Memory
- OpenClaw Self-Update
- Workspace
- Documentation
- Sandbox
- Current Date & Time
- Workspace Files
- Project Context
- Silent Replies
- Messaging
- Voice
- Group Chat Context / Subagent Context
- Reactions
- Heartbeats
- Runtime

关键位置：

- 函数参数定义：`src/agents/system-prompt.ts:682`
- Safety section：`src/agents/system-prompt.ts:959`
- Skills section：`src/agents/system-prompt.ts:967`
- stable prefix cache key：`src/agents/system-prompt.ts:1001`
- stable prefix 开始：`src/agents/system-prompt.ts:1041`
- Tooling section：`src/agents/system-prompt.ts:1045`
- Workspace section：`src/agents/system-prompt.ts:1159`
- Current Date & Time：`src/agents/system-prompt.ts:1229`
- Workspace Files / Project Context 注入：`src/agents/system-prompt.ts:1232`
- Project Context 插入：`src/agents/system-prompt.ts:1243`
- cache boundary：`src/agents/system-prompt.ts:1268`
- 动态会话内容位于 cache boundary 后：`src/agents/system-prompt.ts:1282`
- extra system prompt 作为 Group Chat Context 或 Subagent Context：`src/agents/system-prompt.ts:1318`
- Heartbeats：`src/agents/system-prompt.ts:1351`
- Runtime section：`src/agents/system-prompt.ts:1353`

`docs/concepts/system-prompt.md` 中也描述了这些 sections。对应位置：`docs/concepts/system-prompt.md:46`。

### 12.1 Stable prefix 与动态后缀

系统提示词有一个重要设计：大块稳定内容放在 cache boundary 前，动态内容放在 boundary 后。

稳定内容包括：

- Tooling
- Safety
- Skills
- Workspace
- Documentation
- Project Context

动态内容包括：

- owner identity
- messaging/channel guidance
- group/subagent context
- reactions
- heartbeats
- runtime

这样可以让支持 prefix cache 的 provider 复用稳定 prompt，减少成本与延迟。

## 13. Python 改造建议

如果要把 OpenClaw 的 TypeScript 实现改写成 Python 版本，不建议一开始照搬所有生产复杂度。可以按以下边界拆分：

### 13.1 核心数据结构

建议先定义：

```python
StopReason = Literal["stop", "length", "toolUse", "error", "aborted"]

@dataclass
class AssistantMessage:
    role: Literal["assistant"]
    content: list[dict]
    provider: str
    model: str
    usage: dict
    stop_reason: StopReason
    error_message: str | None = None
    timestamp: int = 0
```

再定义：

- `UserMessage`
- `ToolResultMessage`
- `ToolCall`
- `AgentContext`
- `AgentState`
- `SessionEntry`

### 13.2 第一阶段：实现最小 loop

优先实现：

```text
run_agent_loop()
  -> append user message
  -> stream assistant response
  -> if error/aborted: stop
  -> if toolUse: execute tools, append tool results, continue
  -> if stop/length: stop
```

对应 TypeScript 参考：

- `runAgentLoop()` / `runLoop()`：`packages/agent-core/src/agent-loop.ts`
- `streamAssistantResponse()`：`packages/agent-core/src/agent-loop.ts`
- `executeToolCalls()`：`packages/agent-core/src/agent-loop.ts`

### 13.3 第二阶段：加入 JSONL transcript

建议实现两个文件：

- `sessions.json`: 保存 `SessionEntry`。
- `<session_id>.jsonl`: 保存每条 transcript entry。

最小 JSONL entry：

```json
{"type":"message","id":"...","timestamp":"...","message":{...}}
```

对应 TypeScript 参考：

- `src/config/sessions/types.ts`
- `src/config/sessions/store-load.ts`
- `src/config/sessions/store.ts`
- `src/config/sessions/transcript-jsonl.ts`

### 13.4 第三阶段：加入 retry 与上下文保护

建议按顺序加：

1. `is_retryable_error()`
2. 指数退避 `prepare_retry()`
3. retry 前从内存上下文移除上一条 assistant error
4. tool result 最大字符数限制
5. context overflow 检测
6. compaction 或 summary

对应 TypeScript 参考：

- `AgentSession.isRetryableError()`：`src/agents/sessions/agent-session.ts`
- `AgentSession.prepareRetry()`：`src/agents/sessions/agent-session.ts`
- `installSessionToolResultGuard()`：`src/agents/session-tool-result-guard.ts`
- `installToolResultContextGuard()`：`src/agents/embedded-agent-runner/tool-result-context-guard.ts`

### 13.5 第四阶段：系统提示词模块化

不要把系统提示词硬编码成一个巨大字符串。建议使用 section builder：

```python
def build_system_prompt(params: SystemPromptParams) -> str:
    sections = []
    sections += build_tooling_section(params)
    sections += build_safety_section(params)
    sections += build_skills_section(params)
    sections += build_workspace_section(params)
    sections += build_project_context_section(params)
    sections += [CACHE_BOUNDARY]
    sections += build_messaging_section(params)
    sections += build_runtime_section(params)
    return "\n".join(filter(None, sections))
```

对应 TypeScript 参考：

- `src/agents/system-prompt.ts`
- `docs/concepts/system-prompt.md`

## 14. 推荐阅读顺序

如果后续要继续深入 OpenClaw 源码，建议按这个顺序读：

1. [`../packages/llm-core/src/types.ts`](../packages/llm-core/src/types.ts): 先看 message 和 stop reason 类型。
2. [`../packages/agent-core/src/agent-loop.ts`](../packages/agent-core/src/agent-loop.ts): 看核心循环。
3. [`../packages/agent-core/src/agent.ts`](../packages/agent-core/src/agent.ts): 看 `Agent` 如何封装 loop 和事件。
4. [`../src/agents/sessions/agent-session.ts`](../src/agents/sessions/agent-session.ts): 看生产会话、持久化、重试、compaction。
5. [`../src/config/sessions/transcript-jsonl.ts`](../src/config/sessions/transcript-jsonl.ts): 看最底层 JSONL。
6. [`../src/config/sessions/transcript-append.ts`](../src/config/sessions/transcript-append.ts): 看 message append 事务。
7. [`../src/config/sessions/store.ts`](../src/config/sessions/store.ts): 看 `sessions.json` 更新。
8. [`../src/agents/system-prompt.ts`](../src/agents/system-prompt.ts): 看系统提示词。
9. [`../src/agents/session-tool-result-guard.ts`](../src/agents/session-tool-result-guard.ts): 看 transcript 保护。
10. [`../src/agents/embedded-agent-runner/tool-result-context-guard.ts`](../src/agents/embedded-agent-runner/tool-result-context-guard.ts): 看下一次模型调用前的上下文保护。

## 15. 迁移时的最小闭环

如果目标是快速做一个 Python 版并开源，建议第一版只做这个闭环：

```text
Message types
  -> System prompt builder
  -> Provider stream adapter
  -> Agent loop
  -> Tool registry
  -> JSONL transcript
  -> Retry policy
```

暂时不要一开始就实现完整的：

- 多 channel。
- plugin runtime。
- subagent。
- compaction checkpoint。
- prompt cache boundary。
- gateway config hot reload。
- 所有 provider overlay。

这样更容易得到一个可运行、可测试、可开源维护的 Python 项目。等核心 loop 稳定后，再逐步把 OpenClaw 的生产级保护机制迁移过来。
