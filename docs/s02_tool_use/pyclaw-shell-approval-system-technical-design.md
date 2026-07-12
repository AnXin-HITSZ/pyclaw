# pyclaw Shell 审批系统全量实现技术方案

## 1. 背景

pyclaw 当前已经有 shell 工具安全层：

```text
shell/exec.py
  -> validate_shell_command()
  -> classify_shell_command()
  -> resolve_shell_approval()
  -> subprocess.run()
```

其中 `openclaw/tools/shell/approval.py` 已经支持 `auto` / `require` / `deny` 三种审批模式，但它仍然是一个同步决策函数：

```text
readonly 命令 -> 直接允许
dangerous 命令 -> 审批前直接拒绝
unknown + auto -> 要求显式审批
deny -> 拒绝所有非 readonly 命令
require -> 必须预批准或 callback 批准
approved_shell_commands 命中 -> 允许
shell_approval_callback 返回 True -> 允许
```

当前缺口是：当 Agent 在执行过程中需要审批时，系统只能返回一个 `blocked_result(approval_required)`，并不能真正做到：

```text
Agent 发起高权限工具调用
  -> 系统创建审批单
  -> 当前 turn 挂起
  -> 前端或飞书展示审批
  -> 用户允许或拒绝
  -> 系统恢复原来的 pending tool call
  -> Agent 继续本轮对话
```

本文档给出完整产品化实现方案，目标是把 shell 审批从“同步拦截”升级为“可审计、可挂起、可恢复、可跨端处理”的审批系统。

## 2. 目标

本方案需要实现以下能力：

```text
1. shell 命令需要审批时，自动创建审批记录。
2. Agent 当前 turn 可以进入 waiting_approval 状态，而不是直接失败结束。
3. 系统保存 pending tool call 状态，确保后续可以精确恢复。
4. 前端可以查看、筛选、允许、拒绝审批单。
5. 飞书群可以收到审批卡片，并通过交互按钮审批。
6. 审批通过后，运行时只允许执行原始命令，不允许借审批结果执行变更后的命令。
7. 审批拒绝、过期、重复点击、并发恢复都必须安全处理。
8. 所有审批动作写入审计日志。
```

非目标：

```text
1. 不允许用审批绕过 dangerous 命令硬拦截。
2. 不要求第一期实现全自动飞书卡片恢复，可以先实现前端审批和手动恢复。
3. 不把审批系统绑定到 shell 一个工具，数据模型要能扩展到 fs.write、http.post、k8s.exec 等高风险工具。
```

## 3. 术语

### 3.1 Approval

Approval 是一条审批记录，表示某个 Agent 试图执行一个需要人类确认的高风险动作。

典型字段：

```text
approvalId
sessionId
agentKey
toolName
toolCallId
command
safety
reasons
status
requestedBy
resolvedBy
createdAt
resolvedAt
expiresAt
```

### 3.2 Turn

Turn 是一次用户输入触发的 Agent 运行过程。

例如飞书群里用户发送：

```text
请帮我执行 git status，然后如果有改动就提交
```

这一条消息触发的 Agent 推理、工具调用、继续推理、最终回复，就是一个 turn。

### 3.3 Pending Tool Call

Pending tool call 是已经由模型生成、但因为等待审批而尚未执行的工具调用。

例如模型生成：

```json
{
  "id": "call_abc",
  "name": "shell",
  "input": {
    "command": "git add . && git commit -m \"update docs\"",
    "workdir": "."
  }
}
```

如果该命令需要审批，系统必须保存这段原始 tool call。审批通过后，只能恢复执行这条原始调用。

### 3.4 Turn 挂起

Turn 挂起是指 Agent loop 暂停在某个工具调用之前，把状态保存下来，并向调用方返回：

```json
{
  "status": "waiting_approval",
  "approvalId": "apr_123",
  "sessionId": "..."
}
```

此时模型不继续推理，工具也不执行，直到审批结果产生。

### 3.5 Resume

Resume 是审批通过或拒绝后，系统根据保存的 pending 状态恢复 turn。

审批通过时：

```text
加载 pending turn
  -> 校验审批状态
  -> 将原始命令加入 approved_shell_commands
  -> 执行原始 pending tool call
  -> 写入 ToolResultMessage
  -> 继续 Agent loop
```

审批拒绝时：

```text
加载 pending turn
  -> 生成一个拒绝类 ToolResultMessage
  -> 继续 Agent loop 或直接结束并回复用户
```

### 3.6 Session Runner

Session Runner 是对 `AgentSession + Agent loop + Transcript + Store` 的运行时封装。

当前 pyclaw 中最接近 Session Runner 的结构是：

```text
openclaw/session/agent_session.py
openclaw/agent/agent.py
openclaw/agent/loop.py
openclaw/session/transcript.py
openclaw/session/store.py
```

未来建议新增一个明确的 `SessionRunner`，统一负责：

```text
创建或加载 session
执行用户 prompt
处理 waiting_approval
保存 pending turn
恢复 pending turn
写入 transcript
发布 gateway/channel event
```

## 4. 当前实现分析

### 4.1 Shell 审批入口

文件：

```text
openclaw/tools/shell/approval.py
```

核心函数：

```python
def resolve_shell_approval(...):
    ...
```

当前决策表：

| 场景 | 当前行为 |
| --- | --- |
| readonly 命令 | 直接允许 |
| dangerous 命令 | 审批前直接拒绝 |
| unknown + auto | 拒绝，reason 为需要显式审批 |
| deny | 拒绝所有非 readonly |
| require + 无预批准 | 拒绝，reason 为没有审批 |
| approved_shell_commands 命中 | 允许 |
| shell_approval_callback 返回 True | 允许 |
| auto + mutation | 允许 |

### 4.2 Shell 执行入口

文件：

```text
openclaw/tools/shell/exec.py
```

当前流程：

```text
execute_shell()
  -> 解析 command
  -> classify_shell_command()
  -> validate_shell_command()
  -> readonly 检查
  -> resolve_shell_approval()
  -> 如果不批准，返回 blocked_result(... approval_required ...)
  -> resolve_workspace_path()
  -> resolve_shell_sandbox()
  -> subprocess.run()
```

关键问题：审批不通过时返回的是普通工具结果，Agent loop 不知道这是“应该挂起”的状态。

### 4.3 工具执行器

文件：

```text
openclaw/tools/executor.py
```

当前 `execute_tool_call_batch()` 会把每个 tool call 执行成 `ToolExecutionOutcome`，然后交给 Agent loop。

问题：

```text
1. 不存在 ApprovalRequired 异常。
2. 不存在 WaitingApprovalOutcome。
3. 并行工具调用时，如果其中一个需要审批，没有统一的挂起策略。
4. blocked_result 会被当作普通 tool result 写回模型。
```

### 4.4 Agent Loop

文件：

```text
openclaw/agent/loop.py
```

当前核心循环：

```text
stream assistant
  -> 如果 stop_reason == toolUse
  -> execute_tool_calls()
  -> append tool results
  -> continue
```

问题：

```text
1. run_agent_loop() 只返回 AssistantMessage。
2. 无法返回 TurnResult(status="waiting_approval")。
3. 无法在工具调用前保存 pending 状态。
4. 无法从某个 pending tool call 之后继续。
```

### 4.5 AgentSession

文件：

```text
openclaw/session/agent_session.py
```

当前 `AgentSession` 已经负责：

```text
加载历史消息
运行 prompt
处理重试
处理上下文溢出
写 transcript
更新 session store
```

它可以作为 Session Runner 的基础，但需要增加：

```text
run_prompt() 返回 waiting_approval 的能力
resume_pending_turn() 能力
pending 状态持久化能力
```

## 5. 目标架构

完整审批系统建议拆为以下模块：

```text
Agent Runtime / FastAPI
  |
  | tool call requires approval
  v
Approval Gate
  |
  | create approval + pending turn
  v
SpringBoot Approval Service
  |
  +--> MySQL: agent_approvals
  +--> MySQL: agent_pending_turns
  +--> Audit Log
  |
  +--> Frontend Approvals Page
  +--> Feishu Interactive Card
  |
  | approve / reject
  v
Resume Runner
  |
  | load pending turn
  | verify approval
  | execute or reject original tool call
  v
Agent Loop Continuation
  |
  v
Gateway / Channel Adapter reply
```

更细的调用链：

```text
1. 用户在飞书群发送消息。
2. SpringBoot channel adapter 接收事件。
3. SpringBoot 调用 FastAPI agent run。
4. Agent 生成 shell tool call。
5. shell approval gate 判断需要审批。
6. FastAPI 创建 approval 和 pending turn。
7. Agent run 返回 waiting_approval。
8. SpringBoot 给飞书发送“等待审批”提示或卡片。
9. 用户在前端或飞书点击允许/拒绝。
10. SpringBoot 更新 approval 状态并写审计。
11. SpringBoot 调用 FastAPI resume。
12. FastAPI 恢复原始 pending tool call。
13. Agent 继续推理并生成最终回复。
14. SpringBoot 将回复发送回飞书群。
```

## 6. 数据模型

### 6.1 agent_approvals

审批主表。

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | varchar(64) | 审批 ID，例如 `apr_...` |
| tenant_id | varchar(64) | 租户，单租户可为 `default` |
| workspace_id | varchar(128) | 工作区 |
| session_id | varchar(256) | Agent session ID |
| turn_id | varchar(64) | 当前 turn ID |
| agent_key | varchar(128) | Agent 标识 |
| channel | varchar(64) | feishu / web / api |
| conversation_id | varchar(256) | 群、私聊或会话 ID |
| requester_user_id | varchar(128) | 触发 Agent 的用户 |
| tool_name | varchar(128) | shell / exec / fs.write |
| tool_call_id | varchar(128) | LLM tool call ID |
| action_kind | varchar(64) | shell_command / file_write / http_request |
| action_summary | varchar(1024) | 展示用摘要 |
| risk_level | varchar(32) | low / medium / high / critical |
| safety | varchar(32) | readonly / mutation / unknown / dangerous |
| reasons_json | json | 风险原因 |
| arguments_json | json | 工具参数快照 |
| command_text | longtext | shell 命令，非 shell 可为空 |
| status | varchar(32) | pending / approved / rejected / expired / cancelled / consumed |
| resolved_by | varchar(128) | 审批人 |
| resolution_comment | varchar(1024) | 审批备注 |
| created_at | datetime | 创建时间 |
| resolved_at | datetime | 处理时间 |
| expires_at | datetime | 过期时间 |
| consumed_at | datetime | 恢复执行消费时间 |
| version | bigint | 乐观锁 |

状态流转：

```text
pending -> approved -> consumed
pending -> rejected
pending -> expired
pending -> cancelled
```

约束建议：

```text
1. id 唯一。
2. pending 状态下同一个 sessionId + turnId + toolCallId 唯一。
3. approve/reject 必须使用乐观锁或条件更新。
4. consumed 只能从 approved 转换。
```

### 6.2 agent_pending_turns

挂起状态表。

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | varchar(64) | pending turn ID |
| approval_id | varchar(64) | 对应审批 ID |
| session_id | varchar(256) | session ID |
| turn_id | varchar(64) | turn ID |
| agent_key | varchar(128) | Agent 标识 |
| status | varchar(32) | waiting_approval / resuming / completed / failed / cancelled |
| assistant_message_json | json | 产生 tool call 的 assistant message |
| pending_tool_call_json | json | 原始 tool call |
| messages_snapshot_json | json | 可选，恢复所需消息快照 |
| transcript_cursor | varchar(128) | 可选，transcript 位置 |
| runtime_config_json | json | 模型、provider、cwd、workspace、tool policy 等 |
| route_context_json | json | channel、accountId、peer 等路由上下文 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| expires_at | datetime | 过期时间 |
| version | bigint | 乐观锁 |

示例：

```json
{
  "approvalId": "apr_123",
  "sessionId": "agent:ops:feishu:default:group:oc_xxx",
  "agentKey": "ops",
  "turnId": "turn_456",
  "status": "waiting_approval",
  "pendingToolCall": {
    "toolCallId": "call_abc",
    "toolName": "shell",
    "arguments": {
      "command": "git add .",
      "workdir": "."
    }
  },
  "runtime": {
    "model": "gpt-4.1",
    "provider": "openai-compatible",
    "cwd": "/workspace/pyclaw",
    "workspaceDir": "/workspace/pyclaw",
    "shellApproval": "require"
  }
}
```

### 6.3 audit_events

如果当前后端已有 audit 模块，应复用既有审计表。审批系统至少记录：

```text
approval.created
approval.approved
approval.rejected
approval.expired
approval.consumed
approval.resume.started
approval.resume.succeeded
approval.resume.failed
```

每条审计事件应包含：

```text
actor
actorType: user / token / system / feishu
approvalId
sessionId
agentKey
toolName
commandHash
ip
userAgent
requestId
createdAt
```

## 7. SpringBoot 实现方案

SpringBoot 在 pyclaw 中适合承担 Gateway 和控制面职责，因此审批系统的记录、权限、前端 API、飞书交互都建议放在 SpringBoot。

### 7.1 包结构建议

```text
spring-backend/src/main/java/com/anxin/pyclaw/backend/approval
  ApprovalEntity.java
  PendingTurnEntity.java
  ApprovalStatus.java
  PendingTurnStatus.java
  ApprovalRepository.java
  PendingTurnRepository.java
  ApprovalService.java
  ApprovalController.java
  InternalApprovalController.java
  ApprovalAuditService.java
  ApprovalPermissionService.java
```

### 7.2 ApprovalEntity

核心字段映射 `agent_approvals`。

注意：

```text
1. commandText 使用 @Lob / LONGTEXT。
2. reasonsJson、argumentsJson 建议使用 LONGTEXT 存 JSON 字符串，避免数据库 JSON 类型兼容问题。
3. status 使用 enum string。
4. version 使用 @Version。
```

### 7.3 PendingTurnEntity

核心字段映射 `agent_pending_turns`。

注意：

```text
1. assistantMessageJson、pendingToolCallJson、messagesSnapshotJson、runtimeConfigJson 使用 LONGTEXT。
2. pending 状态数据不要放在前端可编辑表单中。
3. 只允许 runtime internal token 读取完整 pending 状态。
```

### 7.4 前端 API

供控制台使用：

```text
GET    /api/approvals
GET    /api/approvals/{id}
POST   /api/approvals/{id}/approve
POST   /api/approvals/{id}/reject
POST   /api/approvals/{id}/cancel
```

列表查询参数：

```text
status
agentKey
workspaceId
channel
toolName
riskLevel
createdFrom
createdTo
page
size
```

approve 请求：

```json
{
  "comment": "确认执行本次命令",
  "resume": true
}
```

reject 请求：

```json
{
  "comment": "不允许执行 git commit"
}
```

响应：

```json
{
  "id": "apr_123",
  "status": "approved",
  "resolvedBy": "admin",
  "resolvedAt": "2026-07-12T10:30:00+08:00"
}
```

### 7.5 Runtime Internal API

供 FastAPI/OpenClaw Runtime 调用：

```text
POST   /api/internal/approvals
GET    /api/internal/approvals/{id}
POST   /api/internal/approvals/{id}/consume
POST   /api/internal/pending-turns
GET    /api/internal/pending-turns/{approvalId}
POST   /api/internal/pending-turns/{approvalId}/complete
POST   /api/internal/pending-turns/{approvalId}/fail
```

`POST /api/internal/approvals` 请求示例：

```json
{
  "tenantId": "default",
  "workspaceId": "main",
  "sessionId": "agent:ops:feishu:default:group:oc_xxx",
  "turnId": "turn_456",
  "agentKey": "ops",
  "channel": "feishu",
  "conversationId": "oc_xxx",
  "requesterUserId": "ou_xxx",
  "toolName": "shell",
  "toolCallId": "call_abc",
  "actionKind": "shell_command",
  "actionSummary": "git add .",
  "riskLevel": "high",
  "safety": "mutation",
  "reasons": ["writes repository state"],
  "arguments": {
    "command": "git add .",
    "workdir": "."
  },
  "commandText": "git add .",
  "expiresInSeconds": 900
}
```

### 7.6 权限点

建议新增 authorities/scopes：

| 权限 | 含义 |
| --- | --- |
| `approval:read` | 查看审批列表和详情 |
| `approval:resolve` | 允许或拒绝审批 |
| `approval:cancel` | 取消审批 |
| `approval:audit:read` | 查看审批审计 |
| `approval:internal` | Runtime 内部创建和消费审批 |

前端用户使用 JWT authorities 校验：

```text
Users 页 authorities -> 保存到用户表 -> 登录时写入 JWT -> Spring Security 鉴权
```

外部 runtime 使用 API token scopes 校验：

```text
pcat_ token -> Authorization: Bearer ... -> approval:internal
```

### 7.7 审批权限矩阵

| 操作 | 普通用户 | Agent Owner | Workspace Admin | System Admin | Runtime Token |
| --- | --- | --- | --- | --- | --- |
| 查看自己的审批 | 允许 | 允许 | 允许 | 允许 | 拒绝 |
| 查看 workspace 审批 | 拒绝 | 可选 | 允许 | 允许 | 拒绝 |
| 审批自己的请求 | 默认拒绝 | 可配置 | 允许 | 允许 | 拒绝 |
| 审批高风险 shell | 拒绝 | 可配置 | 允许 | 允许 | 拒绝 |
| 取消审批 | 仅自己请求 | 允许 | 允许 | 允许 | 拒绝 |
| 创建审批 | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 允许 |
| 消费审批 | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 允许 |

建议默认策略：

```text
1. 用户不能审批自己触发的高风险命令，除非显式打开 selfApproval。
2. Agent Owner 可以审批自己 Agent 的 medium 风险动作。
3. Workspace Admin 可以审批 workspace 内 high 风险动作。
4. dangerous 动作永远不能审批通过。
```

## 8. FastAPI / OpenClaw Runtime 实现方案

### 8.1 新增 ApprovalRequired

新增文件建议：

```text
openclaw/tools/approval_required.py
```

定义：

```python
from dataclasses import dataclass
from typing import Any

@dataclass
class ApprovalRequired(Exception):
    tool_name: str
    tool_call_id: str
    action_kind: str
    action_summary: str
    safety: str
    reasons: tuple[str, ...]
    arguments: dict[str, Any]
```

用途：

```text
工具层不再把“需要审批”伪装成普通 blocked_result。
工具层抛出 ApprovalRequired，让 Agent loop 有机会挂起 turn。
```

### 8.2 修改 shell approval gate

当前 `execute_shell()` 中：

```python
if not approval.approved:
    return blocked_result(... approval_required ...)
```

目标改为：

```python
if not approval.approved and approval.reason indicates approval_required:
    raise ApprovalRequired(...)
```

但 dangerous、readonly、sandbox 等仍应保留原策略：

```text
dangerous -> blocked_result(dangerous_command)
readonly violation -> blocked_result(readonly)
sandbox unavailable -> blocked_result(sandbox_unavailable)
approval_required -> ApprovalRequired
```

### 8.3 Tool Executor 支持挂起

新增 outcome：

```python
@dataclass
class WaitingApprovalOutcome:
    call: ToolCallBlock
    approval: ApprovalRequired
```

`execute_tool_call()` 捕获 `ApprovalRequired`：

```python
try:
    raw = await tool.execute(context, arguments)
except ApprovalRequired as exc:
    return WaitingApprovalOutcome(call=call, approval=exc, tool=tool)
```

并行策略建议：

```text
1. 只要 batch 中任一 tool call 需要审批，整个 turn 挂起。
2. 已执行完成的前置 tool result 可以保留。
3. 未执行的后续 tool call 不执行。
4. 高风险工具建议 execution_mode="sequential"。
```

第一期可简化：

```text
shell / exec 均为 sequential。
遇到 ApprovalRequired 立即停止 batch。
```

### 8.4 Agent Loop 返回 TurnResult

当前 `run_agent_loop()` 返回 `AssistantMessage`。建议新增：

```python
@dataclass
class TurnResult:
    status: Literal["completed", "waiting_approval", "error"]
    assistant: AssistantMessage | None = None
    approval_id: str | None = None
    pending_turn_id: str | None = None
    session_id: str | None = None
```

第一阶段可新增 `run_agent_turn()`，保留 `run_agent_loop()` 兼容旧调用。

流程：

```text
stream assistant
  -> assistant stop_reason == toolUse
  -> execute_tool_calls()
  -> 如果 WaitingApprovalOutcome
       -> create approval
       -> persist pending turn
       -> return TurnResult(waiting_approval)
  -> 否则 append tool results
  -> continue
```

### 8.5 Approval Client

FastAPI/OpenClaw Runtime 需要调用 SpringBoot internal API。

建议新增：

```text
openclaw/runtime/approval_client.py
```

职责：

```text
create_approval()
create_pending_turn()
get_approval()
get_pending_turn()
consume_approval()
complete_pending_turn()
fail_pending_turn()
```

配置：

```text
PYCLAW_CONTROL_BASE_URL=http://spring-backend:8080
PYCLAW_RUNTIME_TOKEN=pcat_xxx
```

### 8.6 Resume Endpoint

FastAPI 建议新增：

```text
POST /v1/sessions/{session_id}/approvals/{approval_id}/resume
```

请求：

```json
{
  "decision": "approved",
  "resolvedBy": "admin"
}
```

恢复流程：

```text
1. 查询 approval。
2. 查询 pending turn。
3. 校验 approval.status == approved。
4. 使用条件更新把 pending turn 从 waiting_approval 改为 resuming。
5. 重建 AgentSession。
6. 加载 transcript 或 messages snapshot。
7. 注入 approved_shell_commands = {原始 command}。
8. 执行原始 pending tool call。
9. 把 ToolResultMessage 写入 messages 和 transcript。
10. 继续 Agent loop。
11. 成功后 mark approval consumed，pending completed。
12. 失败后 pending failed，并写审计。
```

审批拒绝恢复流程：

```text
1. 查询 approval.status == rejected。
2. 生成 ToolResultMessage：
   "user rejected this tool call"
3. 写入 transcript。
4. 继续 Agent loop，让模型自然回复“已取消执行”。
```

## 9. pending tool call 状态如何保存

### 9.1 必须保存的内容

完整恢复至少需要：

```text
1. sessionId
2. turnId
3. agentKey
4. assistant message
5. toolCallId
6. toolName
7. tool arguments
8. 当前 messages 快照或 transcript cursor
9. model/provider/options
10. tools/tool policy 快照
11. cwd/workspaceDir/chatdataDir
12. readonly/workspaceOnly/shellApprovalMode
13. route context
14. channel reply context
```

其中最关键的是：

```text
assistant message + pending tool call + runtime config
```

因为恢复时不能让模型重新生成 tool call，否则会出现审批 A 命令、执行 B 命令的问题。

### 9.2 messages snapshot 与 transcript cursor

有两种实现方式：

#### 方式 A：保存 messages snapshot

优点：

```text
恢复最简单。
审批通过后直接用 snapshot 重建上下文。
```

缺点：

```text
JSON 可能较大。
如果 transcript 后续被压缩，需要处理一致性。
```

#### 方式 B：保存 transcript cursor

优点：

```text
数据小。
更适合长会话。
```

缺点：

```text
恢复逻辑复杂。
需要 transcript 支持按 entry id 读取精确上下文。
```

建议第一期使用方式 A，第二期再优化为 transcript cursor。

### 9.3 command 精确匹配

审批通过后，只能批准原始命令：

```python
metadata["approved_shell_commands"] = {original_command}
```

恢复执行前必须校验：

```text
pendingToolCall.arguments.command == approval.commandText
```

如果不一致：

```text
拒绝恢复
标记 pending failed
写 audit: approval.resume.failed
```

## 10. 前端实现方案

### 10.1 新增 Approvals 页面

建议导航中新增：

```text
Approvals
```

页面包含：

```text
1. 待审批列表
2. 已处理审批列表
3. 审批详情抽屉或详情页
4. Approve / Reject 操作
5. 审计时间线
```

列表字段：

```text
Status
Risk
Agent
Tool
Action
Requester
Channel
Created At
Expires At
```

筛选：

```text
status
risk level
agent
tool
workspace
channel
date range
```

### 10.2 审批详情

详情页展示：

```text
基础信息
  approvalId
  sessionId
  agentKey
  requester
  channel

风险信息
  riskLevel
  safety
  reasons

工具调用
  toolName
  toolCallId
  arguments

shell 命令
  commandText
  workdir
  cwd

审计记录
  created
  approved/rejected
  consumed
  resume result
```

对于 shell 命令，建议用只读代码块展示，不允许在审批页面编辑。

### 10.3 操作状态

按钮规则：

| 状态 | Approve | Reject |
| --- | --- | --- |
| pending | 可用 | 可用 |
| approved | 禁用 | 禁用 |
| rejected | 禁用 | 禁用 |
| expired | 禁用 | 禁用 |
| consumed | 禁用 | 禁用 |

Approve 后如果选择自动恢复：

```text
前端 -> SpringBoot approve
SpringBoot -> FastAPI resume
前端展示 resume 状态
```

如果 resume 失败：

```text
approval 仍为 approved
pending turn 为 failed
页面显示失败原因
允许管理员重试 resume
```

## 11. 飞书审批卡片

### 11.1 卡片触发

当 SpringBoot 收到 `waiting_approval` 结果后：

```text
1. 在群里回复“需要审批”。
2. 发送交互式卡片。
3. 卡片包含命令摘要、Agent、风险等级、申请人、按钮。
```

卡片内容：

```text
Agent: ops
Tool: shell
Risk: high
Command: git add .
Requester: 张三
Expires: 15 分钟后

[允许执行] [拒绝]
```

### 11.2 飞书回调处理

SpringBoot channel adapter 处理飞书卡片按钮回调：

```text
1. 验证飞书签名。
2. 获取 feishu user id。
3. 映射到 pyclaw user。
4. 校验 approval:resolve。
5. 更新 approval。
6. 调用 FastAPI resume。
7. 更新卡片状态。
```

### 11.3 用户映射

需要维护：

```text
feishu_user_id -> pyclaw_user_id
```

可以放在用户表扩展字段，或新增 `user_external_identities`：

```text
id
user_id
provider: feishu
external_user_id
tenant_key
created_at
```

### 11.4 飞书回复

审批通过：

```text
审批已通过，正在继续执行。
```

审批拒绝：

```text
审批已拒绝，本次命令不会执行。
```

恢复成功后：

```text
Agent 的最终回复仍走原 channel adapter 发回群里。
```

## 12. 安全设计

### 12.1 Fail Closed

所有异常默认拒绝：

```text
审批服务不可用 -> 不执行工具
pending turn 读取失败 -> 不执行工具
approval 状态异常 -> 不执行工具
command 不一致 -> 不执行工具
权限不足 -> 不执行工具
```

### 12.2 dangerous 不能审批绕过

当前 `execute_shell()` 已经在审批前执行：

```text
validate_shell_command()
classification.safety == dangerous
DANGEROUS_COMMAND_PATTERNS
```

目标系统必须继续保持：

```text
dangerous -> 直接 blocked_result
```

即使管理员点击允许，也不能执行被系统判定为 dangerous 的命令。

### 12.3 精确审批

审批对象必须是精确 tool call：

```text
approvalId + sessionId + turnId + toolCallId + commandHash
```

审批结果不能泛化为：

```text
允许这个 Agent 以后执行所有 git 命令
```

如果需要长期授权，应通过 ToolPolicy / Agent Policy 配置，而不是通过单次 approval。

### 12.4 过期机制

建议默认过期：

```text
medium: 30 分钟
high: 15 分钟
critical: 5 分钟
```

过期任务：

```text
每分钟扫描 pending approvals
pending + expiresAt < now -> expired
pending turn -> cancelled
写 audit
```

### 12.5 重放防护

审批通过后恢复执行时：

```text
1. approval 从 approved 条件更新为 consumed。
2. 如果更新影响行数为 0，说明已经被消费，拒绝再次执行。
3. pending turn 从 waiting_approval 条件更新为 resuming。
4. 恢复完成后改为 completed。
```

### 12.6 敏感信息脱敏

审批页面和飞书卡片不能直接展示 secrets：

```text
API key
password
token
authorization header
```

命令展示前应做脱敏：

```text
--password=***
Authorization: Bearer ***
OPENAI_API_KEY=***
```

但数据库可以保存原始 arguments 吗，需要谨慎。建议：

```text
1. approval 展示字段保存脱敏版。
2. pending turn 恢复字段可以加密保存原始版。
3. 审计日志只保存 commandHash 和脱敏摘要。
```

## 13. 并发与幂等

### 13.1 多人同时审批

使用条件更新：

```sql
UPDATE agent_approvals
SET status = 'approved', resolved_by = ?, resolved_at = ?, version = version + 1
WHERE id = ? AND status = 'pending'
```

如果影响行数为 0：

```text
说明已被其他人处理，返回当前状态。
```

### 13.2 多个 resume worker

恢复执行前：

```sql
UPDATE agent_pending_turns
SET status = 'resuming', version = version + 1
WHERE approval_id = ? AND status = 'waiting_approval'
```

只有一个 worker 能成功。

### 13.3 approve 成功但 resume 失败

允许状态：

```text
approval.status = approved
pending.status = failed
```

管理员可以点击：

```text
Retry Resume
```

Retry Resume 仍必须重新走：

```text
approval approved?
approval not consumed?
pending failed or waiting_approval?
command still same?
```

## 14. ToolPolicy 与审批的关系

ToolPolicy 是静态或半动态授权，决定 Agent 能不能看到或调用某类工具。

Approval 是运行时单次授权，决定某一次具体高风险动作能不能执行。

两者关系：

```text
ToolPolicy 先过滤工具集
  -> Agent 只能看到允许的工具
  -> Agent 发起某个工具调用
  -> Approval Gate 再判断这一次调用是否需要人审
```

示例：

```yaml
agents:
  - key: ops
    tools:
      allow:
        - shell
        - fs.read
      deny:
        - fs.write
    approvals:
      shell:
        mode: require
        risk: high
```

含义：

```text
ops Agent 可以调用 shell。
但 shell 的非 readonly 命令需要审批。
ops Agent 不能调用 fs.write。
```

## 15. 配置建议

### 15.1 Agent 级配置

```yaml
agents:
  - key: ops
    name: Ops Agent
    workspace: main
    toolPolicy:
      allow:
        - fs.read
        - shell
        - git.status
      deny:
        - k8s.delete
    approvalPolicy:
      shell:
        mode: require
        requireFor:
          - mutation
          - unknown
        denyFor:
          - dangerous
        expiresInSeconds: 900
```

### 15.2 Workspace 级配置

```yaml
workspaces:
  - key: main
    approvalPolicy:
      selfApproval: false
      defaultExpiresInSeconds: 900
      highRiskRequires:
        - ROLE_WORKSPACE_ADMIN
```

### 15.3 Runtime metadata

传给工具执行上下文：

```python
metadata = {
    "shell_approval_mode": "require",
    "approval_client": approval_client,
    "route_context": route_context,
    "turn_id": turn_id,
    "agent_key": agent_key,
}
```

恢复执行时：

```python
metadata = {
    "shell_approval_mode": "require",
    "approved_shell_commands": {original_command},
    "approval_id": approval_id,
}
```

## 16. 分阶段落地计划

### Phase 1：审批记录 + 前端处理 + 手动重试

目标：

```text
1. SpringBoot 增加 agent_approvals。
2. shell 需要审批时创建 approval。
3. Agent 返回 blocked_result(approval_required)。
4. 前端 Approvals 页面可以审批。
5. 审批通过后用户手动重新发起任务。
```

优点：

```text
实现快，风险低。
先把审批审计、权限、前端流程打通。
```

缺点：

```text
不能恢复当前 turn。
用户体验不完整。
```

### Phase 2：Pending Turn + 显式 Resume

目标：

```text
1. 引入 ApprovalRequired。
2. Agent loop 返回 waiting_approval。
3. 保存 agent_pending_turns。
4. 增加 FastAPI resume endpoint。
5. 前端 approve 后可触发 resume。
```

这是完整审批系统的核心阶段。

### Phase 3：飞书卡片 + 自动 Resume

目标：

```text
1. waiting_approval 时发送飞书交互卡片。
2. 飞书按钮回调审批。
3. 审批通过自动 resume。
4. 最终结果自动回发原群。
```

### Phase 4：审批策略产品化

目标：

```text
1. 支持按工具、风险、Agent、Workspace 配置审批策略。
2. 支持审批委派。
3. 支持多级审批。
4. 支持审批模板。
5. 支持长期授权与单次审批分离。
```

## 17. 测试计划

### 17.1 Python 单元测试

覆盖：

```text
resolve_shell_approval()
execute_shell()
ApprovalRequired
execute_tool_call_batch()
run_agent_turn()
resume_pending_turn()
```

关键用例：

```text
readonly 命令不需要审批
dangerous 命令不能审批绕过
require 模式无审批时抛 ApprovalRequired
approved_shell_commands 精确命中后允许执行
approved_shell_commands 命令不一致时拒绝
pending turn 恢复成功
pending turn 重复恢复被拒绝
```

### 17.2 SpringBoot 测试

覆盖：

```text
ApprovalService 状态流转
ApprovalController 权限校验
InternalApprovalController token scope 校验
过期任务
审计写入
乐观锁并发处理
```

### 17.3 前端测试

覆盖：

```text
审批列表加载
筛选
详情展示
Approve / Reject 按钮状态
重复点击处理
resume failed 展示
```

### 17.4 飞书回调测试

覆盖：

```text
签名校验
用户映射
权限不足
审批成功
审批已处理
resume 成功
resume 失败
卡片状态更新
```

## 18. 迁移与兼容

### 18.1 兼容现有 blocked_result

第一期可以保留：

```text
approval_required -> blocked_result
```

第二期新增 `ApprovalRequired` 后，可以通过配置控制：

```text
approvalSuspendEnabled=false -> 返回 blocked_result
approvalSuspendEnabled=true -> 挂起 turn
```

### 18.2 兼容现有 AgentSession

不要一次性重写 `AgentSession`。建议：

```text
1. 保留 run_prompt() 返回 AssistantMessage。
2. 新增 run_turn() 返回 TurnResult。
3. 新增 resume_approval()。
4. 等调用方迁移完成后，再统一 Session Runner。
```

### 18.3 数据库迁移

新增表：

```sql
CREATE TABLE agent_approvals (...);
CREATE TABLE agent_pending_turns (...);
```

如果已有 audit 表，仅新增 event type。

如果使用 Flyway/Liquibase，建议版本：

```text
V00xx__create_agent_approvals.sql
V00xy__create_agent_pending_turns.sql
```

## 19. 推荐实现顺序

建议按以下顺序施工：

```text
1. SpringBoot agent_approvals 表、Entity、Repository、Service。
2. SpringBoot Approvals 前端 API 和权限点。
3. 前端 Approvals 页面。
4. Runtime ApprovalClient 创建审批。
5. shell approval_required 创建审批记录。
6. agent_pending_turns 表和 PendingTurnService。
7. Python ApprovalRequired + TurnResult。
8. Agent loop 支持 waiting_approval。
9. FastAPI resume endpoint。
10. SpringBoot approve 后调用 resume。
11. 飞书审批卡片。
12. 审计、过期、并发、脱敏补强。
```

这样做的好处是每一步都能独立验证，不会一上来就把 Agent loop、前端、飞书、数据库全部耦合在一起。

## 20. 最终效果

完成后，用户在飞书群中触发高风险命令时，系统行为应为：

```text
用户：请帮我提交当前改动

Agent 生成：
  shell("git add . && git commit -m \"update\"")

系统：
  检测为 mutation / high risk
  创建 approval apr_123
  保存 pending turn
  群里发送审批卡片

管理员：
  点击允许执行

系统：
  校验权限
  标记 approval approved
  resume pending turn
  只执行原始命令
  写 tool result
  Agent 继续推理
  将最终结果回复飞书群
```

这就是完整的审批产品闭环：工具调用不会丢失，用户不用重新发消息，管理员可以审计每一次高风险动作，系统也不会因为审批通过而放弃危险命令硬拦截。
