# Claw 级 Web 对话闭环实施方案

> 日期：2026-07-14  
> 面向执行者：Claude Code  
> 目标：实现“用户创建自己的 Claw 后，可以在 Web 端进入该 Claw，与 Claw 内的默认 Agent / 多角色 Agent 对话，并将会话历史按 userId + clawId 持久化”的 SaaS 核心闭环。

## 0. 前置约定：Claw 状态模型

本方案先记录一个后续必须修改的设计点：

```text
Claw.status        = 用户意图 / 业务状态
Claw.healthStatus  = 系统探测到的运行状态
```

`Claw.status` 不应该单独承担“是否真的可用”的判断。

建议后续状态：

```text
active      用户希望 Claw 正常运行
inactive    用户暂停，runner 可缩容到 0
archived    归档，只保留数据
deleting    删除中
deleted     已软删除
```

实际健康状态由 Kubernetes / runner / Provider / Secret / Agent 配置共同探测：

```text
healthy
starting
stopped
degraded
error
unknown
```

本次 Web 对话闭环实现中，至少要做到：

```text
只有 status=active 的 Claw 允许发起对话。
inactive / archived / deleting / deleted 返回 409 Conflict。
runner 健康探测失败时返回 409 或 503，并给出明确 healthReason。
```

## 1. 当前状态

当前已有能力：

```text
注册 / 登录
用户自己的 Claw
Claw 多角色 Agent 配置
Provider 管理
Secret 管理雏形
每用户 namespace
每 Claw sandbox-runner Deployment / Service / PVC
ClawDetail 能展示会话列表入口
SessionService 已有 Redis 读写能力
PlaygroundPage 可调用 /api/agent/run
```

当前缺口：

```text
Playground 不是某个 Claw 的对话页
/api/agent/run 不携带 clawId
AgentService.run 不按 Claw 解析默认 Agent / 角色 Agent
Session 写入没有稳定绑定 clawId + roleKey + agentId
Python pyclaw-api 运行时没有收到 Claw 上下文
Agent 文件工具默认操作 pyclaw-api Pod 本地目录，而不是 Claw 的 sandbox workspace
ClawDetail 的历史会话只是读取结果，没有完整“开始对话 -> 写入历史 -> 回到详情查看”的闭环
```

本次目标是补齐这条链路：

```text
ClawDetail
  -> 点击开始对话
  -> /workspace/claws/{clawId}/chat
  -> POST /api/claws/{clawId}/chat/runs
  -> Spring 校验 owner + status + runner health
  -> Spring 解析 role/default Agent/Provider/Tool Policy
  -> Spring 调 pyclaw-api /v1/agent/run
  -> pyclaw-api 带 Claw 上下文运行 Agent
  -> Spring 写入 Redis Session
  -> ClawDetail 能看到历史会话
```

## 2. 设计原则

### 2.1 不让前端直接访问 pyclaw-api

前端只访问 Spring Backend：

```text
POST /api/claws/{clawId}/chat/runs
GET  /api/claws/{clawId}/chat/sessions
GET  /api/sessions/{sessionId}
```

Spring Backend 负责：

```text
认证
owner 校验
Claw 状态校验
Agent / Provider / Secret 解析
Session 写入
调用 pyclaw-api
审计和用量记录
```

### 2.2 不把用户输入的 agentId / providerId 当成可信来源

前端可以传：

```text
roleKey
sessionId
prompt
```

但最终使用哪个 Agent / Provider 必须由后端根据 Claw 归属和角色配置解析。

禁止前端直接指定任意别人的：

```text
agentId
providerId
ownerUserId
namespace
sandbox service URL
```

### 2.3 Agent 暂时仍在 pyclaw-api Pod 中运行

当前 sandbox-runner 是最小 FastAPI workspace 服务，不是完整 Agent runtime。

因此本次不把 Agent 直接搬到 sandbox-runner 中运行，而是采用：

```text
pyclaw-api 运行 Agent
Agent 的 workspace 文件工具通过 sandbox-runner HTTP API 操作该 Claw 的 /workspace
```

这能先闭合 SaaS demo 的核心体验：

```text
用户有自己的 Claw
Claw 有自己的 Agent
Claw 有自己的 session
Claw 有自己的 workspace
Web 可以对话并保存历史
```

后续如果要做到“命令也在 runner 里执行”，再给 sandbox-runner 增加安全的 command API。

## 3. 后端接口设计

### 3.1 新增 ClawChatController

新增文件：

```text
spring-backend/src/main/java/com/anxin/pyclaw/backend/clawchat/ClawChatController.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/clawchat/ClawChatService.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/clawchat/ClawChatRunRequest.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/clawchat/ClawChatRunResponse.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/clawchat/ClawChatSessionResponse.java
```

接口：

```text
POST /api/claws/{clawId}/chat/runs
GET  /api/claws/{clawId}/chat/sessions
GET  /api/claws/{clawId}/chat/sessions/{sessionId}
```

也可以复用已有：

```text
GET /api/sessions?clawId={clawId}
GET /api/sessions/{sessionId}
```

但建议新增 Claw 语义接口，让前端更清楚。

### 3.2 POST /api/claws/{clawId}/chat/runs

请求：

```json
{
  "prompt": "请帮我创建一个 README",
  "roleKey": "frontend",
  "sessionId": "optional-existing-session-id"
}
```

字段说明：

```text
prompt      必填，用户消息
roleKey     可选，指定 Claw 内角色；为空时使用默认角色
sessionId   可选，继续历史会话；为空时创建新会话
```

响应：

```json
{
  "sessionId": "claw-fa951a96-...",
  "clawId": "fa951a96-3a18-4ce6-bafe-516bf2a5503a",
  "roleKey": "frontend",
  "agentId": "...",
  "agentKey": "frontend-agent",
  "text": "已创建 README 草稿。",
  "message": {},
  "latencyMs": 1234
}
```

错误：

```text
401 未登录
403 无 agent:run 权限
404 Claw 不存在或不属于当前用户
404 roleKey 不存在或不属于该 Claw
409 Claw 非 active
409 Claw 没有可用 Agent
409 Provider / API Key 未配置
503 sandbox runner 不可用
502 pyclaw-api 调用失败
```

### 3.3 GET /api/claws/{clawId}/chat/sessions

返回该 Claw 的会话摘要：

```json
[
  {
    "sessionId": "...",
    "clawId": "...",
    "clawName": "Agent",
    "agentKey": "frontend-agent",
    "roleKey": "frontend",
    "provider": "DeepSeek",
    "model": "deepseek-chat",
    "messageCount": 8,
    "createdAt": "...",
    "lastActiveAt": "..."
  }
]
```

必须校验：

```text
clawId 属于当前用户，或当前用户是管理员。
```

## 4. ClawChatService 解析规则

### 4.1 requireOwnedClaw

实现：

```text
ClawEntity claw = claws.findById(clawId)
  - 不存在：404
  - 普通用户且 ownerUserId != currentUserId：404
```

不要返回 403 暴露资源存在性。

### 4.2 status 校验

```text
active:
  允许继续

inactive:
  409 "Claw is inactive"

archived:
  409 "Claw is archived"

deleting/deleted:
  404 或 409

其他未知状态:
  409 "Unsupported Claw status"
```

### 4.3 runner 健康校验

在执行 Agent 前调用：

```text
SandboxClient.healthz(ownerUserId, clawId)
```

如果失败：

```text
返回 503 "Sandbox runner is not ready"
```

注意：

```text
如果本地开发 pyclaw.sandbox.enabled=false，可以通过配置跳过 runner health。
生产开启 sandbox 时必须检查。
```

### 4.4 role 解析

Claw 内角色来源：

```text
ClawAgentEntity
```

规则：

```text
如果 request.roleKey 非空:
  查找 clawId + roleKey + enabled=true
  找不到返回 404

如果 request.roleKey 为空:
  优先使用 defaultRole=true 且 enabled=true
  否则使用 sortOrder 最小的 enabled=true 角色
  否则使用 claw.defaultAgentId
  如果仍为空，返回 409 "No agent configured for Claw"
```

解析结果：

```text
agentId
agentKey
roleKey
displayName
tool policy
provider
model
system prompt
```

### 4.5 AgentConfig 归属校验

当前 AgentConfig 是否已有 owner 字段需要检查。

本次必须保证：

```text
普通用户只能使用自己拥有的 AgentConfig 或 shared/template AgentConfig。
Claw 角色绑定的 agentId 必须可被 Claw owner 访问。
```

如果 AgentConfig 目前没有 ownerUserId/shared：

```text
短期：只允许使用当前用户创建的 AgentConfig，新增 ownerUserId/shared 字段。
如果历史数据没有 ownerUserId，管理员 bootstrap 数据可标记 shared=true。
```

### 4.6 Provider 解析

优先级：

```text
1. AgentConfig.providerId
2. AgentConfig.provider/name/type
3. 用户自己的默认 ProviderConfig
4. shared=true 的默认 ProviderConfig
```

禁止：

```text
直接 findFirstByProviderTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc(...)
```

因为这可能拿到其他用户的 Provider。

推荐新增 ProviderConfigService 方法：

```java
ProviderConfigEntity resolveForAgentAndUser(AgentConfigEntity agent, String ownerUserId)
```

规则：

```text
如果 agent.providerId 非空:
  provider 必须 ownerUserId == userId 或 shared=true

如果按 name/type 查:
  优先 ownerUserId == userId
  再 fallback shared=true

如果找不到:
  返回 null 或抛 409
```

## 5. Session 写入规则

### 5.1 当前 SessionService

已有能力：

```text
saveMessage(sessionId, userId, clawId, clawName, agentKey, provider, model, role, content, timestamp)
listByClaw(clawId, authentication)
listByUser(authentication)
getDetail(sessionId, authentication)
deleteByClaw(clawId)
```

需要补充：

```text
roleKey
agentId
runStatus
error
```

可以通过扩展 meta 实现，先不一定改 response。

### 5.2 sessionId 生成

如果前端没有传 sessionId：

```text
sessionId = "claw-" + shortClawId + "-" + UUID.randomUUID()
```

如果前端传了 sessionId：

```text
SessionService.requireOwned(sessionId, principal)
再校验 session meta.clawId == 当前 clawId
不匹配返回 404
```

需要新增：

```java
SessionService.requireOwnedByClaw(String sessionId, String clawId, Authentication authentication)
```

### 5.3 保存消息

执行前保存用户消息：

```text
role=user
content=request.prompt
```

执行成功后保存 Agent 回复：

```text
role=assistant
content=response.text
```

执行失败时建议保存错误消息：

```text
role=assistant
content="[Error] " + safeErrorMessage
```

但错误细节不要泄漏 API Key、内部 URL、堆栈。

### 5.4 Redis key

继续沿用：

```text
sessions:user:{userId}
sessions:claw:{clawId}
session:{sessionId}:meta
session:{sessionId}:messages
```

meta 至少包含：

```text
userId
clawId
clawName
roleKey
agentId
agentKey
provider
model
messageCount
createdAt
lastActiveAt
```

## 6. Spring -> pyclaw-api 请求扩展

### 6.1 扩展 PyclawAgentRunRequest

修改：

```text
spring-backend/src/main/java/com/anxin/pyclaw/backend/pyclaw/PyclawAgentRunRequest.java
```

新增字段：

```java
@JsonProperty("system") String system,
@JsonProperty("tools_allow") List<String> toolsAllow,
@JsonProperty("tools_deny") List<String> toolsDeny,
@JsonProperty("tools_also_allow") List<String> toolsAlsoAllow,
@JsonProperty("shell_approval") String shellApproval,
@JsonProperty("claw_id") String clawId,
@JsonProperty("owner_user_id") String ownerUserId,
@JsonProperty("claw_name") String clawName,
@JsonProperty("role_key") String roleKey,
@JsonProperty("agent_key") String agentKey,
@JsonProperty("sandbox_base_url") String sandboxBaseUrl,
@JsonProperty("workspace_mode") String workspaceMode
```

`workspaceMode` 推荐值：

```text
local
sandbox_runner
```

Web Claw 对话使用：

```text
workspaceMode=sandbox_runner
```

### 6.2 Spring 计算 sandboxBaseUrl

不要让前端传 sandbox URL。

由后端根据 ownerUserId + clawId 计算：

```text
http://sandbox-runner-{clawId}.pyclaw-user-{ownerUserId}.svc.cluster.local:8000
```

注意：

```text
Service 名和 namespace 名必须复用 SandboxOrchestratorService 的命名逻辑。
如果当前命名方法是 private，应提取 SandboxNamingService。
```

新增：

```text
spring-backend/src/main/java/com/anxin/pyclaw/backend/sandbox/SandboxNamingService.java
```

提供：

```java
String namespaceForUser(String userId)
String runnerName(String clawId)
String serviceBaseUrl(String userId, String clawId)
String workspacePvcName(String clawId)
String clawSecretName(String clawId)
```

`SandboxOrchestratorService`、`SandboxClient`、`ClawChatService` 都使用它。

## 7. pyclaw-api 扩展

### 7.1 扩展 AgentRunRequest

修改：

```text
openclaw/api.py
```

`AgentRunRequest` 新增：

```python
claw_id: str | None = None
owner_user_id: str | None = None
claw_name: str | None = None
role_key: str | None = None
agent_key: str | None = None
sandbox_base_url: str | None = None
workspace_mode: Literal["local", "sandbox_runner"] = "local"
```

### 7.2 tool_metadata

`build_tool_metadata(request)` 应包含：

```python
{
  "claw_id": request.claw_id,
  "owner_user_id": request.owner_user_id,
  "claw_name": request.claw_name,
  "role_key": request.role_key,
  "agent_key": request.agent_key,
  "workspace_mode": request.workspace_mode,
  "sandbox_base_url": request.sandbox_base_url,
}
```

如果 `workspace_mode=sandbox_runner` 且 `sandbox_base_url` 为空：

```text
返回 400
```

### 7.3 chatdata_dir

当前 pyclaw-api 仍会在本地存 transcript/session。

Web SaaS 权威会话历史应以 Spring Redis 为准。

建议：

```text
Spring 调 pyclaw-api 时继续传 sessionId。
pyclaw-api 本地 chatdata 只作为运行时上下文存储。
后续可把 chatdata_dir 指向按 clawId/sessionId 分隔的目录。
```

短期：

```text
chatdata_dir=/data/chatdata/web/{ownerUserId}/{clawId}
```

通过 `PyclawAgentRunRequest.chatdata_dir` 传入。

如 Java record 暂未包含，也一起补上：

```java
@JsonProperty("chatdata_dir") String chatdataDir
```

## 8. sandbox workspace 工具

### 8.1 问题

现有 `read/list_dir/write/edit/apply_patch` 工具操作的是 pyclaw-api Pod 的本地 workspace。

Claw Web 对话必须操作该 Claw 的 sandbox-runner workspace。

### 8.2 推荐实现

新增工具文件：

```text
openclaw/tools/sandbox_workspace.py
```

新增工具：

```text
sandbox_workspace_info
sandbox_list_files
sandbox_read_file
sandbox_write_file
```

这些工具通过 `sandbox_base_url` 调用：

```text
GET /v1/workspace
GET /v1/workspace/files?path=.
GET /v1/workspace/files/{file_path}
PUT /v1/workspace/files/{file_path}
```

### 8.3 工具注入方式

修改：

```text
openclaw/tools/builder.py
openclaw/tools/catalog.py
```

目标：

```text
当 request.workspace_mode == "sandbox_runner":
  注册 sandbox_* 工具
  默认不要注册本地 fs 写工具，避免误改 pyclaw-api Pod 文件
```

更简单可执行方案：

```text
1. build_tool_registry 增加 optional runtime_metadata 参数。
2. ToolFactory 可以接收 runtime_metadata，或新增 build_runtime_tools(metadata)。
3. 在 openclaw/api.py 中，当 workspace_mode=sandbox_runner 时，把 sandbox tools 追加到 registry。
4. 对 coding/full profile，保留 web/host 等非 fs 工具，但本地 fs 工具需要移除或 deny。
```

最小实现：

```text
如果 workspace_mode=sandbox_runner:
  tools_deny 自动追加:
    read,list_dir,ls,grep,find,write,edit,apply_patch,shell,exec
  tools_also_allow 自动追加:
    sandbox_workspace_info,sandbox_list_files,sandbox_read_file,sandbox_write_file
```

后续再加：

```text
sandbox_apply_patch
sandbox_search_files
sandbox_command
```

### 8.4 系统提示词补充

当 `workspace_mode=sandbox_runner` 时，系统提示词中加入：

```text
You are working inside a Claw sandbox workspace.
Use sandbox_* tools for file operations.
Do not assume local filesystem paths are the user's project workspace.
```

中文可写：

```text
当前工作区是 Claw 专属 sandbox workspace。文件读写必须使用 sandbox_* 工具。
```

## 9. ClawChatService 调用流程

伪代码：

```java
public ClawChatRunResponse run(String clawId, ClawChatRunRequest request, Authentication auth) {
    AuthenticatedPrincipal principal = requirePrincipal(auth);
    ClawEntity claw = requireOwnedClaw(clawId, auth);
    requireClawActive(claw);
    requireAgentRunAuthority(principal);

    SandboxHealth health = sandboxHealthChecker.check(claw);
    if (!health.ready()) throw 503/409;

    ResolvedClawRole role = resolveRole(claw, request.roleKey());
    AgentConfigEntity agent = requireAgentAccessible(role.agentId(), claw.ownerUserId());
    AgentToolPolicyEntity policy = agents.requirePolicy(agent.getId());
    ProviderConfigEntity provider = providerConfigService.resolveForAgentAndUser(agent, claw.getOwnerUserId());

    String sessionId = resolveSessionId(request.sessionId(), claw, principal);
    sessionService.saveMessage(sessionId, principal.userId(), claw.id, claw.name,
        agent.agentKey, provider.name, model, "user", request.prompt, now);

    PyclawAgentRunResponse pyResponse = pyclawClient.runAgent(new PyclawAgentRunRequest(...full config...));

    sessionService.saveMessage(sessionId, principal.userId(), claw.id, claw.name,
        agent.agentKey, provider.name, model, "assistant", pyResponse.text, now);

    usageRecords.save(... userId, clawId, sessionId, provider, model ...);
    auditLogService.record(... "claw.chat.run", "claw", clawId ...);

    return response;
}
```

## 10. 前端实现

### 10.1 新增 ClawChatPage

新增：

```text
pyclaw-web/src/views/ClawChatPage.vue
```

路由：

```text
/workspace/claws/:id/chat
```

页面结构：

```text
顶部：
  返回 Claw 详情
  Claw 名称
  状态 badge
  当前 role selector

左侧：
  会话列表
  新建会话按钮

主区域：
  消息流
  输入框
  发送按钮
```

### 10.2 ClawDetail 增加入口

修改：

```text
pyclaw-web/src/views/ClawDetailPage.vue
```

增加按钮：

```text
开始对话
查看工作区
Secret 管理
```

`开始对话` 跳转：

```js
router.push(`/workspace/claws/${claw.id}/chat`)
```

### 10.3 role selector

从 Claw 详情接口返回的 roles 中生成：

```text
默认角色
frontend
backend
ops
product
algorithm
```

发送请求时：

```json
{
  "prompt": "...",
  "roleKey": "frontend",
  "sessionId": "..."
}
```

### 10.4 会话加载

进入页面：

```text
GET /api/claws/{clawId}
GET /api/claws/{clawId}/chat/sessions
```

点击会话：

```text
GET /api/claws/{clawId}/chat/sessions/{sessionId}
```

或复用：

```text
GET /api/sessions/{sessionId}
```

但必须确认后端已经校验 session 属于 clawId。

### 10.5 UI 状态

发送期间：

```text
禁用发送按钮
显示“思考中...”
保留用户消息 optimistic update
失败时显示错误消息
```

Claw 不可用：

```text
显示状态提示：
  Claw 已暂停
  Sandbox 启动中
  Provider 未配置
  Runner 不可用
```

## 11. 权限要求

### 11.1 前端

路由 meta：

```js
{
  path: "/workspace/claws/:id/chat",
  component: ClawChatPage,
  meta: { authority: "agent:run" }
}
```

### 11.2 后端

Controller 方法需要：

```text
@PreAuthorize("hasAuthority('agent:run')")
```

并且不能只靠 authority，必须 owner 校验：

```text
claw.ownerUserId == principal.userId
```

管理员：

```text
user:manage 可以查看/运行所有 Claw 吗？
```

建议：

```text
管理员可以查看，但默认不要替用户运行，除非带 explicit admin action。
```

个人 demo 可先允许管理员运行，必须写 audit。

## 12. 用量与审计

### 12.1 UsageRecord

当前 UsageRecord 可能只有 userId/sessionId/provider/model。

建议补：

```text
clawId
agentId
agentKey
roleKey
```

如暂不改表，至少 AuditLog 记录这些字段到 comment/detail。

### 12.2 AuditLog

每次对话记录：

```text
action: claw.chat.run
resourceType: claw
resourceId: clawId
success: true/false
```

错误时：

```text
不要记录 prompt 全文
不要记录 API Key
记录安全错误摘要即可
```

## 13. 与飞书多角色路由的关系

飞书路由：

```text
Feishu event -> RouteBinding -> role Agent -> pyclaw runtime
```

Web 路由：

```text
Web ClawChatPage -> roleKey/default role -> role Agent -> pyclaw runtime
```

二者应共用：

```text
ClawAgentEntity
AgentConfigEntity
ProviderConfigEntity
AgentToolPolicyEntity
```

区别：

```text
飞书通过 mentionAliases / commandPrefixes 命中角色。
Web 通过 role selector 显式选择角色。
```

## 14. 后端测试

新增测试：

```text
spring-backend/src/test/java/com/anxin/pyclaw/backend/clawchat/ClawChatServiceTest.java
spring-backend/src/test/java/com/anxin/pyclaw/backend/clawchat/ClawChatSecurityTest.java
```

覆盖：

```text
用户 A 不能对用户 B 的 Claw 发起对话
inactive Claw 不能发起对话
无 roleKey 时使用默认角色
指定 roleKey 时使用对应角色
不存在 roleKey 返回 404
sessionId 属于其他用户时返回 404
sessionId 属于同用户但不同 clawId 时返回 404
Provider 不属于当前用户且非 shared 时拒绝
成功运行时写入 user 和 assistant 两条消息
失败运行时写 audit
```

## 15. Python 测试

新增测试：

```text
tests/test_api_claw_context.py
tests/test_sandbox_workspace_tools.py
```

覆盖：

```text
AgentRunRequest 接收 claw_id / sandbox_base_url
workspace_mode=sandbox_runner 且 sandbox_base_url 为空时返回 400
sandbox_list_files 调用 /v1/workspace/files
sandbox_read_file 防止路径逃逸由 runner 处理
workspace_mode=sandbox_runner 时本地 fs 工具不应被默认暴露
```

## 16. 前端验收

手工验收：

```text
1. 注册用户 A。
2. 创建 Provider。
3. 创建 Agent。
4. 创建 Claw，设置默认 Agent。
5. 进入 ClawDetail，点击开始对话。
6. 发送“你好”。
7. 页面收到回复。
8. 刷新页面，会话仍在左侧列表。
9. 回到 ClawDetail，会话记录数量增加。
10. 用户 B 登录，直接访问用户 A 的 /workspace/claws/{id}/chat，应失败。
```

## 17. ECS 验收命令

查看核心服务：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw get pods
```

查看 runner：

```bash
sudo /usr/local/bin/k3s kubectl get deploy,svc,pvc,pod -A \
  -l pyclaw.io/claw-id=<clawId>
```

验证 runner health：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw-user-<userId> exec deploy/sandbox-runner-<clawId> -- \
  python -c 'import urllib.request; print(urllib.request.urlopen("http://127.0.0.1:8000/healthz").read().decode())'
```

验证 Spring API：

```bash
curl -i https://api.anxin-hitsz.com/healthz
```

验证会话接口：

```bash
curl -i https://api.anxin-hitsz.com/api/claws/<clawId>/chat/sessions \
  -H "Authorization: Bearer <USER_TOKEN>"
```

## 18. Definition of Done

完成标准：

```text
1. ClawDetail 有“开始对话”入口。
2. /workspace/claws/{id}/chat 可以进入 Claw 专属对话页。
3. Web 对话请求必须携带 clawId。
4. 后端按 clawId 校验 ownerUserId。
5. 后端按 roleKey/defaultRole 解析 Agent。
6. 后端按用户归属解析 Provider。
7. pyclaw-api 接收 Claw 上下文。
8. Session 写入 userId + clawId + agentId + agentKey + roleKey。
9. ClawDetail 能看到该 Claw 的历史会话。
10. 用户不能访问其他用户 Claw 的 chat/session。
11. status 非 active 的 Claw 不能发起对话。
12. sandbox runner 不可用时，前端显示明确错误。
13. Python 本地 fs 工具不会误操作 pyclaw-api Pod 本地目录。
14. 关键后端安全逻辑有测试覆盖。
```

## 19. 推荐 commit 拆分

```text
feat: 增加 Claw 级 Web 对话后端接口
feat: 按 Claw 角色解析 Agent 与 Provider
feat: 为 pyclaw-api 注入 Claw 上下文
feat: 增加 sandbox workspace 工具
feat: 前端新增 Claw 对话页面
test: 增加 Claw 对话租户隔离测试
docs: 记录 Claw 级 Web 对话闭环方案
```

## 20. 注意事项

```text
不要让前端传 ownerUserId / namespace / sandbox URL。
不要用全局最新 Provider 作为普通用户默认 Provider。
不要让普通用户通过 sessionId 读取别人的会话。
不要把 prompt、API Key、Secret 明文写入 AuditLog。
不要默认把 shell/exec 暴露给 Web Claw 对话。
不要让 pyclaw-api 的本地 /app 目录成为用户 workspace。
```

