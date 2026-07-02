# OpenClaw 工具系统实现技术文档

本文档整理 `D:\learn\openclaw` 源码中与「工具定义、工具分发、安全边界、工具数量扩展、结构化工具结果」相关的实现细节，并给出将 TypeScript 版本迁移/改造为 Python 版本时可复用的架构建议。

## 1. 总体结论

OpenClaw 的工具系统不是简单的 `dict[str, Callable]` 分发表，而是由以下几层组成：

1. **工具契约层**：`AgentTool` 用 TypeBox/JSON Schema 描述参数，用 `execute()` 执行工具，返回 `AgentToolResult`。
2. **运行时分发层**：`agent-loop` 根据模型返回的 `toolCall.name` 查找工具，执行参数预处理、schema 校验、before/after 钩子，再按顺序或并行模式执行。
3. **工具装配层**：`createOpenClawCodingTools()` 根据 profile、provider、sandbox、allowlist/denylist、插件、子代理继承策略等动态生成最终工具列表。
4. **安全策略层**：文件工具通过 sandbox path guard、workspaceOnly、沙箱文件桥、apply_patch workspace 限制等约束路径；网络工具通过 SSRF guard、代理策略、超时、响应大小限制等约束网络访问。
5. **结果结构层**：工具结果既给模型返回 `content`，也给日志/UI/诊断返回 `details`、`progress`、`terminate` 等元数据。

对 Python 版 `pyclaw` 来说，建议不要只移植 4 个基础工具，而是保留这些边界：`Tool` 数据模型、schema 校验器、工具注册表、policy pipeline、sandbox/path guard、structured result、hook/middleware。

## 2. 源码定位总表

| 主题 | 源码位置 | 作用 |
| --- | --- | --- |
| 工具基础类型 | [packages/agent-core/src/types.ts:441](D:/learn/openclaw/packages/agent-core/src/types.ts:441) | `AgentToolResult`：content/details/progress/terminate |
| 工具定义接口 | [packages/agent-core/src/types.ts:459](D:/learn/openclaw/packages/agent-core/src/types.ts:459) | `AgentTool`：label、parameters、prepareArguments、execute、executionMode |
| 工具执行入口 | [packages/agent-core/src/agent-loop.ts:548](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:548) | `executeToolCalls()`：按 sequential/parallel 调度 |
| 工具查找 | [packages/agent-core/src/agent-loop.ts:797](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:797) | `resolveToolCallTool()`：按名称查找，支持 deferred tool resolver |
| 参数校验与 before hook | [packages/agent-core/src/agent-loop.ts:841](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:841) | `prepareToolCall()`：prepareArguments、validateToolArguments、beforeToolCall |
| after hook 与结果消息 | [packages/agent-core/src/agent-loop.ts:966](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:966) | `finalizeExecutedToolCall()`、`createToolResultMessage()` |
| TypeBox schema 示例 | [src/agents/sessions/tools/read.ts:38](D:/learn/openclaw/src/agents/sessions/tools/read.ts:38) | `readSchema = Type.Object(...)` |
| shell schema 示例 | [src/agents/bash-tools.schemas.ts:13](D:/learn/openclaw/src/agents/bash-tools.schemas.ts:13) | `execSchema = Type.Object(...)` |
| schema 归一化 | [src/agents/agent-tools.schema.ts:67](D:/learn/openclaw/src/agents/agent-tools.schema.ts:67) | `normalizeToolParameters()`：按 provider/model 清理 schema |
| 工具目录 | [src/agents/tool-catalog.ts:60](D:/learn/openclaw/src/agents/tool-catalog.ts:60) | `CORE_TOOL_DEFINITIONS`：内置工具、分组、profile |
| 工具装配入口 | [src/agents/agent-tools.ts:394](D:/learn/openclaw/src/agents/agent-tools.ts:394) | `createOpenClawCodingTools()`：组装最终工具集 |
| shell 工具懒加载 | [src/agents/agent-tools.ts:193](D:/learn/openclaw/src/agents/agent-tools.ts:193) | `createLazyExecTool()`：延迟加载 exec 实现 |
| 文件工具沙箱包装 | [src/agents/agent-tools.ts:747](D:/learn/openclaw/src/agents/agent-tools.ts:747) | read/write/edit 根据 sandbox/workspaceOnly 包装 |
| policy pipeline | [src/agents/agent-tools.ts:1133](D:/learn/openclaw/src/agents/agent-tools.ts:1133) | `applyToolPolicyPipeline()`：profile/provider/global/group/sender/sandbox/subagent/inherited |
| 工具 hook 包装 | [src/agents/agent-tools.ts:1220](D:/learn/openclaw/src/agents/agent-tools.ts:1220) | `wrapToolWithBeforeToolCallHook()` |
| sandbox path guard | [src/agents/sandbox-paths.ts:88](D:/learn/openclaw/src/agents/sandbox-paths.ts:88) | `assertSandboxPath()`：阻止路径逃逸和 alias/symlink 逃逸 |
| host workspace 写入约束 | [src/agents/agent-tools.read.ts:1019](D:/learn/openclaw/src/agents/agent-tools.read.ts:1019) | `createHostWriteOperations()`：workspaceOnly 时强制相对 workspace |
| blocked result | [src/agents/agent-tools.before-tool-call.ts:915](D:/learn/openclaw/src/agents/agent-tools.before-tool-call.ts:915) | `buildBlockedToolResult()`：阻断工具时返回结构化结果 |
| before hook pipeline | [src/agents/agent-tools.before-tool-call.ts:1042](D:/learn/openclaw/src/agents/agent-tools.before-tool-call.ts:1042) | `runBeforeToolCallHook()`：loop 检测、审批、插件 policy |
| 网络安全 guard | [src/agents/tools/web-guarded-fetch.ts:53](D:/learn/openclaw/src/agents/tools/web-guarded-fetch.ts:53) | `fetchWithWebToolsNetworkGuard()`：SSRF、timeout、trusted proxy |
| web_search 工具 | [src/agents/tools/web-search.ts:78](D:/learn/openclaw/src/agents/tools/web-search.ts:78) | `createWebSearchTool()`：返回 `jsonResult` |
| web_fetch 工具 | [src/agents/tools/web-fetch.ts:702](D:/learn/openclaw/src/agents/tools/web-fetch.ts:702) | `createWebFetchTool()`：URL 清洗、fetch 限制、progress |
| 结果工具函数 | [src/agents/tools/common.ts:394](D:/learn/openclaw/src/agents/tools/common.ts:394) | `textResult()`、`payloadTextResult()`、`jsonResult()` |
| 图片结果结构 | [src/agents/tools/common.ts:481](D:/learn/openclaw/src/agents/tools/common.ts:481) | `imageResult()`：content 中含 image，details 中含 media metadata |

## 3. 工具定义：从 Python 字典列表到 TypeBox schema

简单 Python agent 常见写法是：

```python
tools = [
    {"name": "read", "description": "...", "parameters": {...}, "callable": read_file},
]
```

OpenClaw 的 TypeScript 版更严格。核心接口在 `AgentTool`：

- `name` 来自底层 `Tool<TParameters>`。
- `label` 用于 UI 展示。
- `parameters` 是 TypeBox/JSON Schema，用于模型工具描述和运行时参数校验。
- `prepareArguments(args)` 是兼容层，可在 schema 校验前修正模型传入的原始参数。
- `execute(toolCallId, params, signal, onUpdate)` 是执行函数。
- `executionMode` 可声明工具必须顺序执行或允许并行执行。

代表性源码：

- [packages/agent-core/src/types.ts:459](D:/learn/openclaw/packages/agent-core/src/types.ts:459)
- [src/agents/sessions/tools/read.ts:38](D:/learn/openclaw/src/agents/sessions/tools/read.ts:38)
- [src/agents/sessions/tools/write.ts:31](D:/learn/openclaw/src/agents/sessions/tools/write.ts:31)
- [src/agents/bash-tools.schemas.ts:13](D:/learn/openclaw/src/agents/bash-tools.schemas.ts:13)

以 `read` 为例，参数 schema 是：

```ts
const readSchema = Type.Object({
  path: Type.String({ description: "Path to the file to read (relative or absolute)" }),
  offset: Type.Optional(Type.Number({ description: "Line number to start reading from (1-indexed)" })),
  limit: Type.Optional(Type.Number({ description: "Maximum number of lines to read" })),
});
```

迁移到 Python 时，建议用 Pydantic 或 `jsonschema` 表达同样的契约，例如：

```python
from pydantic import BaseModel, Field

class ReadParams(BaseModel):
    path: str = Field(description="Path to the file to read")
    offset: int | None = Field(default=None, description="1-indexed start line")
    limit: int | None = Field(default=None, description="Maximum number of lines")
```

关键点不是 TypeBox 本身，而是**工具定义和参数校验绑定在一起**。Python 版应避免工具 callable 直接接收未校验的 `dict`。

## 4. Schema 归一化：面向不同模型 provider

OpenClaw 在最终把工具交给模型 runtime 前，会统一调用 `normalizeToolParameters()`：

- 读取 `tool.parameters`。
- 调用 `normalizeToolParameterSchema(schema, options)`。
- 保留插件元数据、channel 元数据、before hook 标记、terminal presentation。
- 对没有 required 参数的 object schema 增加 `prepareArguments`，把 `null/undefined` 归一化为 `{}`。

源码位置：

- [src/agents/agent-tools.schema.ts:54](D:/learn/openclaw/src/agents/agent-tools.schema.ts:54)
- [src/agents/agent-tools.schema.ts:67](D:/learn/openclaw/src/agents/agent-tools.schema.ts:67)
- [src/agents/agent-tools.ts:1184](D:/learn/openclaw/src/agents/agent-tools.ts:1184)

这说明 OpenClaw 并不是「定义 schema 后直接使用」，而是在 provider/model 维度做兼容处理。Python 版可以抽象为：

```python
def normalize_tool_schema(tool: Tool, provider: str | None, model: str | None) -> Tool:
    schema = export_json_schema(tool.params_model)
    schema = clean_schema_for_provider(schema, provider, model)
    return tool.model_copy(update={"json_schema": schema})
```

## 5. 工具分发：同名查表 + 运行时管线

OpenClaw 的核心分发在 `agent-loop.ts`。

执行流程：

1. 从 assistant message 中筛选 `type === "toolCall"` 的块。
2. 若全局允许并行，则先检查是否存在 `executionMode === "sequential"` 的工具。
3. 根据配置选择 `executeToolCallsSequential()` 或 `executeToolCallsParallel()`。
4. 每个 tool call 都会进入 `prepareToolCall()`。
5. `prepareToolCall()` 里先 `resolveToolCallTool()`，按 `toolCall.name` 从 `currentContext.tools` 查找工具。
6. 查找失败时可调用 `config.resolveDeferredTool()` 延迟恢复工具。
7. 执行 `prepareArguments()`。
8. 执行 `validateToolArguments()`。
9. 执行 `config.beforeToolCall()`，可阻断。
10. 调用工具 `execute()`。
11. 执行 `config.afterToolCall()`，可覆盖 content/details/terminate/isError。
12. 生成 `ToolResultMessage` 写回上下文。

关键源码：

- [packages/agent-core/src/agent-loop.ts:548](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:548)
- [packages/agent-core/src/agent-loop.ts:608](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:608)
- [packages/agent-core/src/agent-loop.ts:673](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:673)
- [packages/agent-core/src/agent-loop.ts:797](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:797)
- [packages/agent-core/src/agent-loop.ts:841](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:841)
- [packages/agent-core/src/agent-loop.ts:929](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:929)
- [packages/agent-core/src/agent-loop.ts:966](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:966)
- [packages/agent-core/src/agent-loop.ts:1033](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:1033)

Python 版可以按这个接口拆分：

```python
async def execute_tool_calls(context, assistant_message, config):
    tool_calls = [c for c in assistant_message.content if c.type == "tool_call"]
    registry = {tool.name: tool for tool in context.tools}

    async def prepare(call):
        tool = registry.get(call.name) or await config.resolve_deferred_tool(call)
        if tool is None:
            return ImmediateResult(error=f"Tool {call.name} not found")
        args = tool.prepare_arguments(call.arguments)
        params = tool.validate(args)
        before = await config.before_tool_call(call, params)
        if before.block:
            return ImmediateResult(error=before.reason)
        return PreparedToolCall(call=call, tool=tool, params=params)
```

## 6. 工具目录：从 4 个基础工具扩展到 20+ 工具

OpenClaw 内置工具目录在 `CORE_TOOL_DEFINITIONS`。它不只是罗列工具名，还记录：

- `id`
- `label`
- `description`
- `sectionId`
- `profiles`
- `includeInOpenClawGroup`

源码位置：[src/agents/tool-catalog.ts:60](D:/learn/openclaw/src/agents/tool-catalog.ts:60)

当前目录中可见的核心工具包括：

- 文件系统：`read`、`write`、`edit`、`apply_patch`
- Runtime：`exec`、`process`、`code_execution`
- Web：`web_search`、`web_fetch`、`x_search`
- Memory：`memory_search`、`memory_get`
- Sessions：`sessions_list`、`sessions_history`、`sessions_send`、`sessions_spawn`、`sessions_yield`、`subagents`、`session_status`
- UI：`browser`、`canvas`
- Messaging：`message`
- Automation：`heartbeat_respond`、`cron`、`gateway`
- Nodes：`nodes`
- Agents：`agents_list`、`get_goal`、`create_goal`、`update_goal`、`update_plan`、`skill_workshop`
- Media：`image`、`image_generate`、`music_generate`、`video_generate`、`tts`

对应 profile 逻辑：

- `minimal`：只允许 minimal profile 工具。
- `coding`：允许 coding 工具和 `bundle-mcp`。
- `messaging`：允许 messaging 工具和 `bundle-mcp`。
- `full`：允许 `*`。

相关源码：

- [src/agents/tool-catalog.ts:357](D:/learn/openclaw/src/agents/tool-catalog.ts:357)
- [src/agents/tool-catalog.ts:363](D:/learn/openclaw/src/agents/tool-catalog.ts:363)
- [src/agents/tool-catalog.ts:378](D:/learn/openclaw/src/agents/tool-catalog.ts:378)
- [src/agents/tool-catalog.ts:407](D:/learn/openclaw/src/agents/tool-catalog.ts:407)

Python 版建议把工具目录和工具实现解耦：

```python
class ToolCatalogEntry(BaseModel):
    id: str
    label: str
    description: str
    section_id: str
    profiles: list[str] = []
    include_in_openclaw_group: bool = False

CORE_TOOL_DEFINITIONS: list[ToolCatalogEntry] = [...]
```

这样可以在不实例化所有工具的情况下生成 UI、profile、allowlist、文档和诊断信息。

## 7. 工具装配：createOpenClawCodingTools

`createOpenClawCodingTools()` 是 OpenClaw 工具系统最关键的装配入口。

源码位置：[src/agents/agent-tools.ts:394](D:/learn/openclaw/src/agents/agent-tools.ts:394)

它接受大量上下文参数：

- agent/session/run 信息：`agentId`、`sessionKey`、`sessionId`、`runId`
- 文件系统上下文：`cwd`、`workspaceDir`、`spawnWorkspaceDir`
- 沙箱上下文：`sandbox`、`sandboxFsBridge`、`sandboxRoot`
- 消息上下文：`messageProvider`、`currentChannelId`、`messageThreadId`
- 模型上下文：`modelProvider`、`modelId`、`modelApi`
- 配置和策略：`config`、`runtimeToolAllowlist`、profile policy、provider profile policy
- hook/diagnostic：`trace`、`recordToolPrepStage`、`onToolOutcome`

重要阶段：

1. 解析 profile/provider policy。
2. 解析 sandbox、workspace、runtime root。
3. 创建基础 coding tools：`read/write/edit`。
4. 如果存在 sandbox，就替换为 sandbox-backed 文件工具。
5. 创建 shell tools：`exec/process/apply_patch`。
6. 创建 channel tools、OpenClaw tools、plugin tools、tool search tools。
7. 应用 message provider policy。
8. 应用 model provider policy。
9. 应用统一 policy pipeline。
10. 归一化 tool schema。
11. 包装 before-tool-call hook。
12. 包装 abort signal。
13. 添加 deferred followup 描述。

关键源码：

- [src/agents/agent-tools.ts:600](D:/learn/openclaw/src/agents/agent-tools.ts:600)
- [src/agents/agent-tools.ts:747](D:/learn/openclaw/src/agents/agent-tools.ts:747)
- [src/agents/agent-tools.ts:807](D:/learn/openclaw/src/agents/agent-tools.ts:807)
- [src/agents/agent-tools.ts:989](D:/learn/openclaw/src/agents/agent-tools.ts:989)
- [src/agents/agent-tools.ts:1112](D:/learn/openclaw/src/agents/agent-tools.ts:1112)
- [src/agents/agent-tools.ts:1133](D:/learn/openclaw/src/agents/agent-tools.ts:1133)
- [src/agents/agent-tools.ts:1184](D:/learn/openclaw/src/agents/agent-tools.ts:1184)
- [src/agents/agent-tools.ts:1220](D:/learn/openclaw/src/agents/agent-tools.ts:1220)

Python 版建议拆成独立 builder：

```python
class ToolBuilder:
    def build(self, options: ToolBuildOptions) -> list[Tool]:
        tools = []
        tools += self.build_base_coding_tools(options)
        tools += self.build_shell_tools(options)
        tools += self.build_openclaw_tools(options)
        tools += self.build_plugin_tools(options)
        tools = self.apply_provider_policy(tools, options)
        tools = self.apply_policy_pipeline(tools, options)
        tools = [normalize_tool_schema(t, options.provider, options.model) for t in tools]
        tools = [wrap_before_tool_call(t, options.hook_context) for t in tools]
        return tools
```

## 8. 安全边界：路径、安全策略、沙箱、SSRF

### 8.1 路径逃逸防护

核心函数是 `assertSandboxPath()`：

- 先把用户输入路径解析为绝对路径。
- 计算它相对 sandbox root 的 `relative`。
- 如果 relative 是 `..`、以 `../` 或 `..\\` 开头、是绝对路径、或是 Windows drive path，则抛错。
- 再调用 `assertNoPathAliasEscape()` 防止 symlink/hardlink/alias 逃逸。

源码位置：

- [src/agents/sandbox-paths.ts:70](D:/learn/openclaw/src/agents/sandbox-paths.ts:70)
- [src/agents/sandbox-paths.ts:88](D:/learn/openclaw/src/agents/sandbox-paths.ts:88)

这比简单的 `safe_path()` 更完整，因为它不仅处理 `..`，也处理符号链接/硬链接/Windows drive path 等边界。

Python 版建议：

```python
from pathlib import Path

def assert_sandbox_path(file_path: str, cwd: Path, root: Path) -> Path:
    resolved = (cwd / file_path).resolve() if not Path(file_path).is_absolute() else Path(file_path).resolve()
    root_resolved = root.resolve()
    try:
        resolved.relative_to(root_resolved)
    except ValueError as exc:
        raise PermissionError(f"Path escapes sandbox root: {file_path}") from exc
    return resolved
```

如果要达到 OpenClaw 级别，还需要补充 symlink/hardlink alias 检测。

### 8.2 workspaceOnly 写入约束

OpenClaw 对 host 写入分两种模式：

- `workspaceOnly = false`：允许写 host 任意位置。
- `workspaceOnly = true`：所有 mkdir/write/read/stat 都必须转成 workspace 相对路径，并经过 `assertSandboxPath()`。

源码位置：

- [src/agents/agent-tools.read.ts:1019](D:/learn/openclaw/src/agents/agent-tools.read.ts:1019)
- [src/agents/agent-tools.read.ts:1037](D:/learn/openclaw/src/agents/agent-tools.read.ts:1037)
- [src/agents/agent-tools.read.ts:1063](D:/learn/openclaw/src/agents/agent-tools.read.ts:1063)

### 8.3 sandbox-backed 文件工具

OpenClaw 不只是限制路径，还可以把 read/write/edit 切换为 sandbox-backed operations：

- `createSandboxedReadTool()`
- `createSandboxedWriteTool()`
- `createSandboxedEditTool()`

源码位置：[src/agents/agent-tools.read.ts:837](D:/learn/openclaw/src/agents/agent-tools.read.ts:837)

装配时，如果有 `sandboxRoot`，`createOpenClawCodingTools()` 会替换 read/write/edit 的实现：

- read 使用 `createSandboxedReadTool(...)`
- write/edit 只有 `allowWorkspaceWrites` 时才暴露
- apply_patch 在 sandbox 且只读时禁用

源码位置：

- [src/agents/agent-tools.ts:741](D:/learn/openclaw/src/agents/agent-tools.ts:741)
- [src/agents/agent-tools.ts:747](D:/learn/openclaw/src/agents/agent-tools.ts:747)
- [src/agents/agent-tools.ts:876](D:/learn/openclaw/src/agents/agent-tools.ts:876)
- [src/agents/agent-tools.ts:989](D:/learn/openclaw/src/agents/agent-tools.ts:989)

### 8.4 policy pipeline 和 before-tool-call

OpenClaw 的安全策略还包括工具级 pipeline：

- profile policy
- provider profile policy
- global policy
- agent policy
- group policy
- sender policy
- sandbox policy
- subagent policy
- inherited policy

最终通过 `applyToolPolicyPipeline()` 过滤工具列表。

源码位置：[src/agents/agent-tools.ts:1133](D:/learn/openclaw/src/agents/agent-tools.ts:1133)

执行前还会统一包上 before hook：

- `runBeforeToolCallHook()` 执行 loop 检测、审批、插件 before_tool_call policy。
- `wrapToolWithBeforeToolCallHook()` 在每个工具的 `execute()` 外包一层。
- 被阻断时返回 `buildBlockedToolResult()`，其 `details.status = "blocked"`。

源码位置：

- [src/agents/agent-tools.before-tool-call.ts:915](D:/learn/openclaw/src/agents/agent-tools.before-tool-call.ts:915)
- [src/agents/agent-tools.before-tool-call.ts:1042](D:/learn/openclaw/src/agents/agent-tools.before-tool-call.ts:1042)
- [src/agents/agent-tools.before-tool-call.ts:1360](D:/learn/openclaw/src/agents/agent-tools.before-tool-call.ts:1360)

Python 版可抽象成中间件：

```python
async def before_tool_call_middleware(tool, params, ctx):
    loop = detect_tool_loop(tool.name, params, ctx)
    if loop.blocked:
        return ToolResult.blocked(loop.reason, denied_reason="tool-loop")

    policy = await run_policy_hooks(tool.name, params, ctx)
    if policy.blocked:
        return ToolResult.blocked(policy.reason, denied_reason=policy.denied_reason)

    return None
```

### 8.5 Web/网络工具 SSRF 防护

OpenClaw 的 web fetch 不是裸 `fetch(url)`。`web-guarded-fetch.ts` 提供：

- `fetchWithWebToolsNetworkGuard()`：严格 SSRF 或 trusted env proxy 模式。
- `withTrustedWebToolsEndpoint()`：可信 endpoint，基于 pinned host policy。
- `withSelfHostedWebToolsEndpoint()`：自托管 endpoint，可允许私网。
- `withStrictWebToolsEndpoint()`：严格 SSRF，默认不信任 env proxy。

源码位置：

- [src/agents/tools/web-guarded-fetch.ts:53](D:/learn/openclaw/src/agents/tools/web-guarded-fetch.ts:53)
- [src/agents/tools/web-guarded-fetch.ts:81](D:/learn/openclaw/src/agents/tools/web-guarded-fetch.ts:81)
- [src/agents/tools/web-guarded-fetch.ts:97](D:/learn/openclaw/src/agents/tools/web-guarded-fetch.ts:97)
- [src/agents/tools/web-guarded-fetch.ts:112](D:/learn/openclaw/src/agents/tools/web-guarded-fetch.ts:112)

`web_fetch` 本身还做了：

- URL sanitization
- extract mode 选择
- max chars / max response bytes
- max redirects
- timeout seconds
- cache TTL
- progress update

源码位置：[src/agents/tools/web-fetch.ts:702](D:/learn/openclaw/src/agents/tools/web-fetch.ts:702)

Python 版建议用 `httpx.AsyncClient` 加自定义 DNS/IP 校验，不要只依赖 URL 字符串判断。

## 9. 工具结果：从纯字符串到结构化结果

OpenClaw 的工具返回 `AgentToolResult<T>`：

```ts
export interface AgentToolResult<T> {
  content: (TextContent | ImageContent)[];
  details: T;
  progress?: AgentToolProgress;
  terminate?: boolean;
}
```

源码位置：[packages/agent-core/src/types.ts:441](D:/learn/openclaw/packages/agent-core/src/types.ts:441)

这意味着：

- 给模型看的内容放在 `content`。
- 给 UI、日志、诊断、后续处理看的结构化信息放在 `details`。
- 长任务中间状态通过 `progress`。
- 特殊工具可通过 `terminate` 提示当前工具 batch 后停止。

通用 helper：

- `textResult(text, details)`：文本 content + details。
- `payloadTextResult(payload)`：把 payload 格式化为文本，同时 details 保留 payload。
- `jsonResult(payload)`：把 payload JSON stringify 给模型，同时 details 保留原对象。
- `imageResult(...)`：content 中返回 image，details 中返回 path/media metadata。

源码位置：

- [src/agents/tools/common.ts:394](D:/learn/openclaw/src/agents/tools/common.ts:394)
- [src/agents/tools/common.ts:413](D:/learn/openclaw/src/agents/tools/common.ts:413)
- [src/agents/tools/common.ts:417](D:/learn/openclaw/src/agents/tools/common.ts:417)
- [src/agents/tools/common.ts:481](D:/learn/openclaw/src/agents/tools/common.ts:481)

`agent-loop` 最终会把工具结果转成 `ToolResultMessage`：

```ts
{
  role: "toolResult",
  toolCallId,
  toolName,
  content,
  details,
  isError,
  timestamp,
}
```

源码位置：[packages/agent-core/src/agent-loop.ts:1033](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts:1033)

Python 版建议定义：

```python
class TextContent(BaseModel):
    type: Literal["text"] = "text"
    text: str

class ImageContent(BaseModel):
    type: Literal["image"] = "image"
    data: str
    mime_type: str

class ToolResult(BaseModel):
    content: list[TextContent | ImageContent]
    details: dict[str, Any] = {}
    progress: dict[str, Any] | None = None
    terminate: bool = False
```

## 10. Web 和媒体工具示例

### web_search

`createWebSearchTool()`：

- 根据 config 判断是否禁用。
- 构造 `name = "web_search"`、`parameters = WebSearchSchema`。
- 执行时调用 `runWebSearch()`。
- 返回 `jsonResult({ ...result.result, provider })`。

源码位置：[src/agents/tools/web-search.ts:78](D:/learn/openclaw/src/agents/tools/web-search.ts:78)

### web_fetch

`createWebFetchTool()`：

- 根据 config/sandbox 判断是否可用。
- URL 经过 `sanitizeWebFetchUrl()`。
- 支持 markdown/text extract mode。
- 限制 max chars、response bytes、redirects、timeout。
- 调用 `runWebFetch()`。
- 返回 `jsonResult(result)`。

源码位置：[src/agents/tools/web-fetch.ts:702](D:/learn/openclaw/src/agents/tools/web-fetch.ts:702)

### media

工具目录中媒体类工具包括：

- `image`
- `image_generate`
- `music_generate`
- `video_generate`
- `tts`

相关实现可继续阅读：

- [src/agents/tools/image-tool.ts](D:/learn/openclaw/src/agents/tools/image-tool.ts)
- [src/agents/tools/image-generate-tool.ts](D:/learn/openclaw/src/agents/tools/image-generate-tool.ts)
- [src/agents/tools/music-generate-tool.ts](D:/learn/openclaw/src/agents/tools/music-generate-tool.ts)
- [src/agents/tools/video-generate-tool.ts](D:/learn/openclaw/src/agents/tools/video-generate-tool.ts)
- [src/agents/tools/tts-tool.ts](D:/learn/openclaw/src/agents/tools/tts-tool.ts)

## 11. Python 版 pyclaw 的推荐模块划分

建议目录：

```text
pyclaw/
  core/
    tool.py              # Tool, ToolResult, ToolCall, ToolResultMessage
    loop.py              # execute_tool_calls, prepare_tool_call, finalize_tool_call
    schema.py            # pydantic/jsonschema export + provider normalization
    registry.py          # ToolRegistry, deferred resolver
  tools/
    fs/
      read.py
      write.py
      edit.py
      path_guard.py
    shell/
      exec.py
      process.py
    web/
      search.py
      fetch.py
      ssrf_guard.py
    media/
      image.py
      image_generate.py
      video_generate.py
      music_generate.py
      tts.py
  runtime/
    builder.py           # create_pyclaw_coding_tools
    policy.py            # allow/deny/profile/sandbox/subagent policy
    hooks.py             # before/after tool hooks
    sandbox.py           # sandbox context and fs bridge
  catalog.py             # CORE_TOOL_DEFINITIONS
```

对应 OpenClaw 映射：

| Python 模块 | OpenClaw 对应 |
| --- | --- |
| `core/tool.py` | `packages/agent-core/src/types.ts` |
| `core/loop.py` | `packages/agent-core/src/agent-loop.ts` |
| `core/schema.py` | `src/agents/agent-tools.schema.ts`、`agent-tools-parameter-schema.ts` |
| `runtime/builder.py` | `src/agents/agent-tools.ts` |
| `runtime/policy.py` | `src/agents/tool-policy-pipeline.ts`、`src/agents/tool-catalog.ts` |
| `runtime/hooks.py` | `src/agents/agent-tools.before-tool-call.ts` |
| `tools/fs/path_guard.py` | `src/agents/sandbox-paths.ts`、`src/agents/agent-tools.read.ts` |
| `tools/web/ssrf_guard.py` | `src/agents/tools/web-guarded-fetch.ts` |

## 12. 迁移优先级

建议按以下顺序迁移：

1. **先迁移工具契约**：`Tool`、`ToolResult`、`ToolCall`、`ToolResultMessage`。
2. **再迁移运行时 loop**：查表、参数校验、before/after hook、顺序/并行执行、错误结果。
3. **迁移基础文件工具**：read/write/edit/apply_patch，并先实现 workspaceOnly。
4. **补安全边界**：path guard、symlink 检测、sandbox context、allowlist/denylist。
5. **补 shell 工具**：exec/process，重点是超时、后台任务、审批、safe bins。
6. **补 web 工具**：web_search/web_fetch，重点是 SSRF guard、timeout、redirect、cache。
7. **补高级工具**：session、message、browser、media、cron、subagent。
8. **补工具目录和 profile**：用于配置 UI、文档、工具启用策略。
9. **补 provider schema normalization**：保证不同模型后端都能接受工具 schema。
10. **补诊断和审计**：工具开始/结束/阻断/进度事件。

## 13. 最小 Python 骨架示例

```python
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Awaitable, Callable, Literal

from pydantic import BaseModel


class TextContent(BaseModel):
    type: Literal["text"] = "text"
    text: str


class ToolResult(BaseModel):
    content: list[TextContent]
    details: dict[str, Any] = {}
    terminate: bool = False


ToolExecute = Callable[[str, BaseModel], Awaitable[ToolResult]]


@dataclass(frozen=True)
class Tool:
    name: str
    label: str
    description: str
    params_model: type[BaseModel]
    execute: ToolExecute
    execution_mode: Literal["sequential", "parallel"] = "parallel"

    def validate(self, args: dict[str, Any]) -> BaseModel:
        return self.params_model.model_validate(args)


async def execute_one(tool_call_id: str, name: str, args: dict[str, Any], tools: list[Tool]) -> ToolResult:
    registry = {tool.name: tool for tool in tools}
    tool = registry.get(name)
    if tool is None:
        return ToolResult(content=[TextContent(text=f"Tool {name} not found")], details={})
    params = tool.validate(args)
    return await tool.execute(tool_call_id, params)
```

这个骨架只覆盖最小路径。要接近 OpenClaw，需要继续加入：

- `prepare_arguments`
- before/after hook
- structured details
- progress update callback
- abort signal
- tool policy pipeline
- sandbox path guard
- provider schema normalization
- deferred tool resolver

## 14. 阅读顺序建议

如果要继续深入 OpenClaw 源码，建议按这个顺序看：

1. [packages/agent-core/src/types.ts](D:/learn/openclaw/packages/agent-core/src/types.ts)
2. [packages/agent-core/src/agent-loop.ts](D:/learn/openclaw/packages/agent-core/src/agent-loop.ts)
3. [src/agents/sessions/tools/read.ts](D:/learn/openclaw/src/agents/sessions/tools/read.ts)
4. [src/agents/sessions/tools/write.ts](D:/learn/openclaw/src/agents/sessions/tools/write.ts)
5. [src/agents/bash-tools.schemas.ts](D:/learn/openclaw/src/agents/bash-tools.schemas.ts)
6. [src/agents/tool-catalog.ts](D:/learn/openclaw/src/agents/tool-catalog.ts)
7. [src/agents/agent-tools.ts](D:/learn/openclaw/src/agents/agent-tools.ts)
8. [src/agents/sandbox-paths.ts](D:/learn/openclaw/src/agents/sandbox-paths.ts)
9. [src/agents/agent-tools.read.ts](D:/learn/openclaw/src/agents/agent-tools.read.ts)
10. [src/agents/agent-tools.before-tool-call.ts](D:/learn/openclaw/src/agents/agent-tools.before-tool-call.ts)
11. [src/agents/tools/common.ts](D:/learn/openclaw/src/agents/tools/common.ts)
12. [src/agents/tools/web-guarded-fetch.ts](D:/learn/openclaw/src/agents/tools/web-guarded-fetch.ts)
13. [src/agents/tools/web-search.ts](D:/learn/openclaw/src/agents/tools/web-search.ts)
14. [src/agents/tools/web-fetch.ts](D:/learn/openclaw/src/agents/tools/web-fetch.ts)

