# SaaS PyClaw 架构设计

## 文档作用

本文档指明 SaaS PyClaw 的架构设计，执行时需要严格按照该架构设计进行。

## 平台功能

期望 SaaS PyClaw 平台的功能如下。

用户可以创建自己的 Claw，每个 Claw 是当前用户独立的工作空间。

每个 Claw 中可以引入多个 Agent。

在一个 Claw 中的多个 Agent 之间，可以互相发现、互相对话，但是要保持各自记忆的独立性。

用户可以发布自己的 Agent，发布的 Agent 可以被其他用户显式引入，也可以由其他用户的 Agent 在运行时自动发现并审批引入。

工具系统提供 Profile 策略，用户在创建 Agent 时可以选择不同的 Profile 策略。

Agent 在执行中高风险工具时，需要进行审批。

## 当前缺乏

### Agent 对话编排

当前没有真正的多 Agent 对话编排。

现在是一轮请求选一个 Agent 回答。还没有：
```text
用户 @agent
Agent A 调用 Agent B
多个 Agent 在同一对话中接力
Agent 自动发现其他 Agent
```

### Agent 发现 / 发布 / 引入模型

当前没有 Agent 发现 / 发布 / 引入模型。

目前 Agent 是用户自己创建的配置。缺少：
```text
Agent Marketplace
Agent Package
Agent Version
Published Agent Manifest
Claw 内安装实例
自动发现候选 Agent
审批引入流程
```

### Agent 记忆独立性

当前 Agent 记忆独立性还不够。

当前 session 是 Claw Chat 的 session。消息会记录 agentKey/roleKey/agentId 元数据，但如果多个 Agent 共用一个 sessionId，OpenClaw Runtime 侧的 transcript/history 很容易变成共享上下文。

目标是“多个 Agent 可互相对话，但保持各自记忆独立”。这需要显式建模：
```text
Conversation Thread：用户看到的一条对话流
Agent Memory Session：每个 Agent 自己的私有记忆
Shared Claw Context：多个 Agent 都能看到的公共上下文
```

不能只靠一个 sessionId。

## 当前实现对照

当前项目已经具备 SaaS PyClaw 的部分基础能力。

### 已具备

```text
Claw
  Spring Backend 已有 Claw 模型，并通过 K3s 为每个 Claw 准备独立 sandbox runner。

Claw Agent
  Spring Backend 已有 claw_agents 关系表。
  一个 Claw 可以挂载多个 Agent，并通过 roleKey 区分该 Agent 在当前 Claw 中的角色。

Agent Runtime
  Spring Backend 负责读取 Agent、Provider、Tool Policy、Sandbox 地址。
  OpenClaw FastAPI 负责运行一个已经确定的 Agent。

Tool Profile
  OpenClaw FastAPI 已支持 minimal / readonly / messaging / coding / full 等 profile。

Tool Approval
  OpenClaw FastAPI 可以在中高风险工具调用时挂起执行。
  Spring Backend 负责持久化审批记录、校验用户归属、恢复或拒绝执行。
```

### 当前主链路

```text
前端
  -> Spring Backend ClawChatService
  -> 根据 clawId + roleKey 解析当前轮使用的 Agent
  -> 读取 Agent 配置、Provider 配置、Tool Policy
  -> 调用 OpenClaw FastAPI /v1/agent/run
  -> OpenClaw Runtime 执行单个 Agent
  -> Spring 保存用户消息和 Agent 回复
```

当前链路的本质是：

```text
Spring Backend 决定本轮运行哪个 Agent。
OpenClaw FastAPI 只负责运行这个已经确定的 Agent。
```

这个边界长期需要保留。

## 长期架构分层

SaaS PyClaw 长期分为四层。

```text
Frontend
  负责 Claw、Agent、Conversation、审批、发布市场的用户界面。

Spring Backend
  负责 SaaS 业务状态、用户权限、Claw、Agent Instance、Conversation、Agent 发布与引入、审批和审计。

Conversation Orchestrator
  逻辑上属于 Spring Backend。
  负责决定一轮对话由哪个 Agent 回答，以及 Agent 之间如何互相调用。

OpenClaw FastAPI Runtime
  负责执行一个确定的 Agent turn。
  不负责 SaaS 业务路由、市场、引入、权限归属。
```

一句话原则：

```text
谁回答由 Spring / Orchestrator 决定。
怎么回答由 OpenClaw FastAPI Runtime 执行。
```

## 核心领域模型

长期核心模型如下。

```text
User
  平台用户。

Claw
  用户拥有的独立工作空间。
  是文件沙箱、共享上下文、权限边界和协作边界。

Agent Package
  可发布的 Agent 模板。
  包含 Agent 的人格、Skills、能力描述、默认 Profile、版本和发布元数据。

Agent Instance
  某个 Claw 中引入的 Agent 实例。
  引用一个 Agent Package 版本，或引用用户本地创建的 Agent 配置。
  在当前 Claw 中拥有 roleKey、displayName、本地覆盖配置和启用状态。

Conversation Thread
  用户看到的一条 Claw 对话流。
  可以出现多个 Agent 的发言。

Agent Memory Session
  某个 Agent Instance 在某条 Conversation Thread 下的私有记忆。
  不同 Agent Instance 的记忆必须隔离。

Shared Claw Context
  当前 Claw 下可以被多个 Agent 共享的上下文。
  例如工作区文件、用户明确共享的摘要、任务目标和对话公开消息。
```

建议关系：

```text
User 1 - N Claw
Claw 1 - N AgentInstance
AgentInstance N - 1 AgentPackageVersion
Claw 1 - N ConversationThread
ConversationThread 1 - N Message
ConversationThread 1 - N AgentMemorySession
AgentMemorySession N - 1 AgentInstance
```

## 多 Agent 对话编排

多 Agent 对话不能只是在现有 session 中切换 roleKey。长期需要显式引入 Conversation Orchestrator。

### 本轮 Agent 选择

本轮由哪个 Agent 回答，按以下优先级决定：

```text
1. 用户显式指定：例如 @frontend 或在 UI 中选择 Agent。
2. 当前对话状态：延续上一轮正在处理任务的 Agent。
3. Agent 调用请求：某个 Agent 通过 call_agent 请求另一个 Agent。
4. 默认 Agent：Claw 中 defaultRole=true 的 Agent。
5. 自动路由：基于 Agent 能力描述、mentionAliases、commandPrefixes 和任务意图选择。
```

自动路由规则可以复用当前 Channel 路由的思想，但不建议复用 FastAPI Channel 路由作为 SaaS 主路径。

当前 FastAPI Channel 路由主要解决：

```text
外部飞书/微信/Channel 消息进来时，应该由哪个 Agent 处理。
```

SaaS 多 Agent 对话要解决：

```text
当前 Claw 内，哪个 Agent Instance 有权、适合、且被允许回答这一轮。
```

这个判断依赖用户权限、Claw 内已引入 Agent、发布市场状态、审批状态、会话消息和审计，因此应放在 Spring / Orchestrator 侧。

### Agent 互相调用

长期通过工具形式支持 Agent 互相调用：

```text
call_agent(agent_key | role_key, message, context_policy)
```

调用链路：

```text
Agent A
  -> OpenClaw Runtime 工具调用 call_agent
  -> Spring Orchestrator API
  -> 校验 Agent B 是否已引入当前 Claw
  -> 校验 Agent A 是否允许调用 Agent B
  -> 如需引入或越权，创建审批
  -> 调用 OpenClaw Runtime 执行 Agent B
  -> 将 Agent B 回复返回给 Agent A，并写入 Conversation Thread
```

FastAPI 不得绕过 Spring 直接查询市场或安装 Agent。

## Agent 记忆隔离

长期必须区分“用户可见对话流”和“Agent 私有记忆”。

```text
conversation_id
  用户看到的一条对话。

agent_memory_session_id
  某个 Agent Instance 在某条 conversation_id 下的私有运行记忆。
```

建议格式：

```text
conversation:{conversation_id}
agent-memory:{conversation_id}:{agent_instance_id}
```

Redis 存储建议使用不同 key 明确隔离：

```text
用户可见对话消息：
conversation:{conversation_id}:messages

用户可见对话元信息：
conversation:{conversation_id}:meta

某个 Agent Instance 的私有记忆消息：
agent-memory:{conversation_id}:{agent_instance_id}:messages

某个 Agent Instance 的私有记忆元信息：
agent-memory:{conversation_id}:{agent_instance_id}:meta
```

必须使用稳定的 `agent_instance_id`。不得使用 `role_key` 作为私有记忆 key 的组成部分，因为 `role_key` 是当前 Claw 内的可读标识，未来可能被用户改名。

Spring / Conversation Orchestrator 调用 OpenClaw FastAPI Runtime 时，必须把 Runtime `session_id` 设置为目标 Agent 的私有记忆 session，而不是用户可见的 `conversation_id`：

```text
session_id = agent-memory:{conversation_id}:{agent_instance_id}
```

这样可以保证：

```text
同一条 Conversation Thread 中可以出现多个 Agent 的发言。
Agent A 的 OpenClaw transcript/history 只来自 Agent A 的私有 key。
Agent B 的 OpenClaw transcript/history 只来自 Agent B 的私有 key。
审批恢复时也能回到对应 Agent 的私有上下文。
```

当用户在同一条对话中切换 Agent 时：

```text
Conversation Thread 保存完整公开消息流。
Agent A 只加载 Agent A 的私有记忆，加上允许共享的 Claw 上下文。
Agent B 只加载 Agent B 的私有记忆，加上允许共享的 Claw 上下文。
```

Agent 不应默认读取其他 Agent 的私有记忆。若需要共享，应通过显式消息、摘要、handoff 或共享上下文完成。

不要让多个 Agent 共用一个 Runtime `session_id` 后再依赖消息里的 `agentKey` 或 `roleKey` 过滤上下文。这样会让上下文压缩、工具结果回放、审批恢复和错误恢复都变得脆弱。

## Agent 发布、发现与引入

发布的 Agent 不应只是数据库中的 systemPrompt。长期应发布为 Agent Package。

Agent 发布、发现和引入必须一步到位实现为“发布包 + 安装实例 + 运行时审批”的完整模型。

核心原则：

```text
Agent Package
  可发布、可搜索、可版本化的 Agent 模板。

Agent Package Version
  某次发布生成的不可变快照。

Agent Instance
  某个 Claw 中安装后的 Agent 实例。
  运行时只运行 Agent Instance，不直接运行市场中的 Agent Package。
```

建议包结构：

```text
agent.yaml
persona/
  identity.md
  style.md
  boundaries.md
skills/
  skill-a.md
  skill-b.md
examples/
  example-a.md
README.md
```

`agent.yaml` 至少声明：

```text
agent_key
name
summary
description
version
publisher_user_id
default_profile
required_profile
persona_files
skill_files
capabilities
input_contract
output_contract
visibility
```

Agent 引入到 Claw 时，应生成 Agent Instance，而不是直接运行发布者的原始 Agent。

```text
Published Agent Package
  -> install into Claw
  -> Agent Instance
  -> roleKey / displayName / local overrides / pinned version
```

### Agent Package 数据模型

Spring Backend 需要新增 Agent 发布包模型。

```text
agent_packages
  id
  package_key
  owner_user_id
  name
  summary
  description
  visibility
  latest_version_id
  install_count
  created_at
  updated_at
```

字段说明：

```text
package_key
  发布市场中的稳定标识，同一 owner 下唯一。

visibility
  private / unlisted / public。

latest_version_id
  指向最新可安装版本。
```

### Agent Package Version 数据模型

每次发布生成不可变版本。

```text
agent_package_versions
  id
  package_id
  version
  status
  manifest_json
  system_prompt_snapshot
  persona_files_json
  skill_files_json
  default_profile
  required_profile
  capabilities_json
  input_contract_json
  output_contract_json
  changelog
  created_at
```

字段说明：

```text
status
  draft / published / revoked。

manifest_json
  发布包完整 manifest 快照。

system_prompt_snapshot
  发布时 Agent 的 System Prompt 快照。

persona_files_json / skill_files_json
  发布时人格文件和 Skills 文件快照。
  当前实现直接在数据库中保存文件路径、文件名、内容、hash 和顺序。

default_profile
  安装时默认使用的工具 Profile。

required_profile
  运行该 Agent 至少需要的工具 Profile。
```

已发布版本不得原地修改。更新 Agent 必须发布新版本。

### Agent Instance 数据模型

当前 `claw_agents` 应演进为 Agent Instance，而不是只表示简单的 Claw-Agent 关联。

建议保留表名 `claw_agents`，扩展为以下字段：

```text
claw_agents
  id
  claw_id
  source_type
  source_agent_id
  package_id
  package_version_id
  role_key
  display_name
  local_system_prompt_override
  local_profile
  default_role
  enabled
  sort_order
  installed_by
  installed_at
  created_at
  updated_at
```

字段说明：

```text
id
  Agent Instance ID。
  必须作为 Agent 私有记忆 key 的组成部分。

source_type
  local / package。

source_agent_id
  本地创建 Agent 的原始 AgentConfig ID。

package_version_id
  市场安装 Agent 的固定版本。

role_key
  当前 Claw 内用户可读、可 @ 的角色标识。
  可以修改，但不得作为记忆隔离 ID。

local_profile
  当前 Claw 对该 Agent Instance 的本地 Profile 覆盖。
  为空时使用 package version 的 default_profile。
```

### 发布 Agent 流程

用户发布 Agent 时，Spring Backend 执行：

```text
1. 校验当前用户拥有该 Agent。
2. 校验 Agent 有 name、description、systemPrompt、default_profile。
3. 生成或更新 agent_packages。
4. 生成新的 agent_package_versions。
5. 快照 systemPrompt、persona、skills、capabilities、contracts、profile。
6. 将版本状态置为 published。
7. 更新 agent_packages.latest_version_id。
8. 写入 audit log。
```

发布接口建议：

```text
POST /api/agents/{agentId}/publish
GET  /api/agent-packages
GET  /api/agent-packages/{packageId}
GET  /api/agent-packages/{packageId}/versions
```

`POST /api/agents/{agentId}/publish` 请求体：

```json
{
  "packageKey": "k3s-troubleshooter",
  "version": "1.0.0",
  "visibility": "public",
  "summary": "排查 K3s、Helm、Pod 和部署问题",
  "changelog": "首次发布"
}
```

### 显式安装 Agent 流程

用户从市场显式安装 Agent 到某个 Claw 时，Spring Backend 执行：

```text
1. 校验用户拥有目标 Claw。
2. 校验 package visibility 和 version status。
3. 校验目标 Claw 尚未安装同一 package version，或按产品规则允许多实例安装。
4. 生成 claw_agents.id 作为 Agent Instance ID。
5. 生成 role_key，默认来自 package_key，冲突时追加短后缀。
6. 写入 claw_agents，source_type=package，package_version_id=固定版本。
7. 设置 local_profile，默认使用 package version default_profile。
8. 初始化 Agent Memory Namespace。
9. 写入 audit log。
10. 返回 Agent Instance。
```

安装接口建议：

```text
POST /api/claws/{clawId}/agents/install
GET  /api/claws/{clawId}/agents
PATCH /api/claws/{clawId}/agents/{agentInstanceId}
DELETE /api/claws/{clawId}/agents/{agentInstanceId}
```

`POST /api/claws/{clawId}/agents/install` 请求体：

```json
{
  "packageVersionId": "pkgver-1",
  "roleKey": "k3s",
  "displayName": "K3s 排障助手",
  "localProfile": "readonly"
}
```

### 运行时发现 Agent

运行时发现必须通过 Spring Orchestrator，不允许 FastAPI 直接访问市场或修改 Claw。

OpenClaw Runtime 可以暴露工具给 Agent：

```text
discover_agents(query, capabilities, required_profile)
request_agent_install(package_version_id, reason)
call_agent(agent_instance_id | role_key, message, context_policy)
```

`call_agent` 可以接受 `role_key` 作为用户可读引用，但 Spring Orchestrator 必须在执行前将其解析为稳定的 `agent_instance_id`。所有记忆、审批、审计和 Runtime 调用只使用 `agent_instance_id` 作为主标识。

这些工具的 executor 必须回调 Spring Orchestrator API。

Spring Orchestrator API 建议：

```text
POST /api/orchestrator/agents/discover
POST /api/orchestrator/agents/install-requests
POST /api/orchestrator/agents/call
```

运行时发现流程：

```text
1. Agent A 调用 discover_agents。
2. FastAPI 工具 executor 调 Spring /api/orchestrator/agents/discover。
3. Spring 根据当前 Claw、用户权限、query、capabilities 搜索可见 Agent Package Version。
4. Spring 返回候选项，只包含 manifest 摘要，不返回私有实现细节。
5. Agent A 可以向用户建议安装某个候选 Agent。
6. Agent A 调用 request_agent_install。
7. Spring 创建 AgentInstallApproval。
8. 用户审批后，Spring 执行安装流程。
9. 安装完成后，Agent A 或用户可以通过 call_agent 调用新 Agent Instance。
```

### Agent 引入审批

自动发现后的引入必须审批。该审批不同于工具执行审批，但可以复用统一的审批 UI 和审计机制。

新增审批类型：

```text
approval_type = tool_execution | agent_install | agent_call
```

AgentInstallApproval 至少保存：

```text
id
approval_type=agent_install
claw_id
owner_user_id
requesting_agent_instance_id
package_id
package_version_id
reason
status
expires_at
created_at
resolved_at
```

审批通过后：

```text
Spring 创建 Agent Instance。
Spring 将审批状态标记为 approved/consumed。
Spring 写入 audit log。
Spring 可以向 Conversation Thread 写入一条系统消息：
“已引入 K3s 排障助手。”
```

审批拒绝后：

```text
Spring 标记 rejected。
Spring 将拒绝结果返回给发起请求的 Agent。
不得创建 Agent Instance。
```

### 安装后运行配置组装

Spring 调用 OpenClaw FastAPI Runtime 前，必须从 Agent Instance 组装运行配置：

```text
1. 读取 claw_agents。
2. 如果 source_type=package，读取 agent_package_versions。
3. 如果 source_type=local，读取 AgentConfig。
4. 合并 system prompt：
   平台安全提示
   + package/persona/skills 快照
   + local_system_prompt_override
   + Claw 共享上下文摘要
5. 解析 tool_profile：
   local_profile
   或 package default_profile
   或本地 Agent policy profile
6. 生成 Runtime session_id：
   agent-memory:{conversation_id}:{agent_instance_id}
7. 调用 OpenClaw FastAPI /v1/agent/run。
```

FastAPI 请求中必须携带：

```text
claw_id
owner_user_id
agent_key
role_key
agent_instance_id
conversation_id
session_id
sandbox_base_url
tool_profile
system
```

如果 FastAPI 当前请求模型没有 `agent_instance_id` 和 `conversation_id`，需要补充字段。旧字段 `agent_key` 和 `role_key` 仍保留用于展示、日志和兼容，但不得作为记忆隔离主键。

运行时自动发现 Agent 时，不允许自动静默引入。正确流程是：

```text
Agent A 发现候选 Agent Package
  -> 向 Spring 提交引入建议
  -> Spring 创建 pending import approval
  -> 用户审批
  -> Spring 在当前 Claw 创建 Agent Instance
  -> 后续对话才允许调用该 Agent Instance
```

### 数据库约束与索引

实现时必须补充唯一约束和查询索引，避免市场、安装和多 Agent 会话出现重复或歧义。

```text
agent_packages
  unique(owner_user_id, package_key)
  index(visibility, updated_at)
  index(owner_user_id, updated_at)

agent_package_versions
  unique(package_id, version)
  index(package_id, status, created_at)
  index(status, created_at)

claw_agents
  unique(claw_id, role_key)
  index(claw_id, enabled, sort_order)
  index(claw_id, package_version_id)
  index(claw_id, source_agent_id)

conversations
  index(owner_user_id, updated_at)
  index(claw_id, updated_at)

conversation_messages
  index(conversation_id, created_at)
  index(conversation_id, agent_instance_id, created_at)

agent_install_approvals
  index(claw_id, status, created_at)
  index(owner_user_id, status, created_at)
  index(requesting_agent_instance_id, created_at)
```

### API 契约要求

Spring Backend 对外 API 使用 `agentInstanceId` 作为 Agent Instance 主标识。`roleKey` 只作为用户可读选择器。

对话运行 API 请求至少包含：

```json
{
  "conversationId": "conv-1",
  "agentInstanceId": "claw-agent-1",
  "roleKey": "k3s",
  "prompt": "请排查这个 Pod 日志"
}
```

如果用户只传 `roleKey`，Spring 必须在进入 Orchestrator 前解析为 `agentInstanceId`。

对话消息响应至少包含：

```json
{
  "conversationId": "conv-1",
  "messageId": "msg-1",
  "role": "assistant",
  "agentInstanceId": "claw-agent-1",
  "agentKey": "k3s-troubleshooter",
  "roleKey": "k3s",
  "content": "诊断结果...",
  "createdAt": "2026-07-18T08:00:00Z"
}
```

OpenClaw FastAPI Runtime 请求至少包含：

```json
{
  "prompt": "请排查这个 Pod 日志",
  "session_id": "agent-memory:conv-1:claw-agent-1",
  "conversation_id": "conv-1",
  "agent_instance_id": "claw-agent-1",
  "claw_id": "claw-1",
  "owner_user_id": "user-1",
  "agent_key": "k3s-troubleshooter",
  "role_key": "k3s",
  "tool_profile": "readonly",
  "system": "...",
  "sandbox_base_url": "http://..."
}
```

FastAPI 返回 `PENDING_APPROVAL` 时，Spring 保存审批记录时也必须保存 `conversation_id` 和 `agent_instance_id`，以便审批恢复回到正确的 Agent 私有记忆。

## Claude Code 实现边界

本架构文件可作为实现入口，但 Claude Code 开始实现前还需要遵守以下落地约束。

### 必须优先实现的基础能力

```text
1. Agent Instance ID
   claw_agents.id 必须被明确用作 agent_instance_id。

2. Conversation Thread
   新增独立 conversation_id，不再把 Runtime session_id 等同为用户可见会话 ID。

3. Agent 私有记忆 session
   Spring 调 FastAPI 时使用：
   session_id = agent-memory:{conversation_id}:{agent_instance_id}

4. Agent Package / Version
   发布 Agent 必须生成不可变版本快照。

5. 显式安装
   市场安装必须创建 claw_agents Agent Instance。

6. Orchestrator
   本轮 Agent 选择、call_agent、discover_agents、request_agent_install 均由 Spring 侧负责。

7. 审批
   工具执行审批和 Agent 引入审批需要在 UI 上统一呈现，但数据类型必须可区分。
```

### 不得实现的捷径

```text
不得让多个 Agent 共用一个 OpenClaw Runtime session_id。
不得让 FastAPI 直接修改 Claw 内 Agent 安装关系。
不得让 Agent 自动静默安装其他 Agent。
不得把 role_key 当作长期记忆隔离 ID。
不得把发布 Agent 简化为共享原始 AgentConfig。
不得让已发布版本原地修改。
```

### 建议实现顺序

```text
1. 数据模型迁移
   agent_packages
   agent_package_versions
   claw_agents 扩展为 Agent Instance
   conversations
   conversation_messages
   agent_install_approvals

2. Spring 服务层
   AgentPackageService
   AgentInstallService
   ConversationOrchestratorService
   AgentMemorySessionResolver

3. FastAPI 请求模型
   AgentRunRequest 增加 agent_instance_id、conversation_id。
   日志增加 agent_instance_id、conversation_id。

4. 运行链路改造
   ClawChatService 改为通过 Orchestrator 解析 Agent Instance。
   调用 FastAPI 时传 Agent 私有 session_id。

5. 显式发布和安装 UI/API
   先完成用户可见的发布、市场列表、安装到 Claw。

6. 运行时 discover_agents / request_agent_install / call_agent
   作为 OpenClaw 工具接入，但所有业务动作回调 Spring。

7. 审批 UI 整合
   同一入口展示 tool_execution 与 agent_install。
```

### 一步到位验收标准

实现完成后必须满足：

```text
1. 用户可以将自己的 Agent 发布为 Agent Package Version。
2. 已发布版本不可变，重新发布必须生成新版本。
3. 用户可以从市场将 Agent Package Version 安装到任意自己拥有的 Claw。
4. 每次安装都会生成独立 Agent Instance ID。
5. 同一个 Claw 中可以安装多个 Agent Instance，并通过 role_key 或 UI 选择本轮回答者。
6. 同一个 Conversation Thread 中不同 Agent 的 Runtime session_id 不同。
7. OpenClaw 日志能看到 conversation_id、agent_instance_id、agent_key、role_key 和实际 resolved_tools。
8. Agent A 可以通过 discover_agents 发现候选 Agent。
9. Agent A 请求安装新 Agent 时必须生成 agent_install 审批。
10. 用户审批通过前，不得创建 Agent Instance，不得调用候选 Agent。
11. 用户审批通过后，Spring 创建 Agent Instance，并允许后续 call_agent。
12. Agent A 调用 Agent B 时，Spring 必须校验 Agent B 属于同一 Claw 且 enabled=true。
13. Conversation Thread 保存用户可见消息流。
14. Agent Memory Session 只保存对应 Agent Instance 的私有上下文。
15. 工具执行审批和 Agent 安装审批都写入审计日志。
```

## 工具 Profile 与审批边界

工具系统长期保留 Profile 策略，但用户侧只需要理解 Profile，不应暴露 allow / also_allow 这类高级白名单配置。

```text
Profile
  决定 Agent 默认能看到哪些工具。

Approval
  决定某一次中高风险工具调用是否允许执行。

System Deny
  平台内部默认禁用的能力，例如 shell、exec、host 相关工具。
```

长期产品语义：

```text
用户配置 tool_profile。
系统内部维护 tools_deny。
tools_allow 和 tools_also_allow 仅作为兼容字段或未来高级能力，不作为普通 SaaS 用户配置项。
```

## FastAPI Runtime 边界

OpenClaw FastAPI Runtime 长期保留，但职责必须收敛。

应该保留：

```text
/v1/agent/run
/v1/agent/resume
/v1/tools/catalog
/v1/tools/resolve
/healthz
```

长期可以新增别名，使语义更清晰：

```text
/v1/runtime/agent/run
/v1/runtime/agent/resume
/v1/runtime/tools/catalog
/v1/runtime/tools/resolve
```

不应该放在 FastAPI 主路径：

```text
SaaS 用户权限判断
Agent 发布市场
Agent 引入安装
多 Agent 对话编排
Claw 内 Agent 选择
自动发现审批
Conversation Thread 持久化
```

Channel 路由和 Channel Worker 可以作为可选扩展或插件存在，不应成为 SaaS 主路径的架构基础。
