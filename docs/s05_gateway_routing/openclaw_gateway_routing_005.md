# OpenClaw 网关路由与 BindingTable 对应实现技术文档

本文基于 `claw0/sessions/zh/s05_gateway_routing.md` 的教学版设计，结合当前目录 `openclaw` 的 TypeScript 源码，整理 OpenClaw 中“路由规则、会话 key、多 Agent、网关协议、并发控制”的生产实现思路与执行流程。

目标是回答三个问题：

1. `claw0` 中的 `BindingTable` 思想在 OpenClaw 中对应哪些源码。
2. OpenClaw 的生产版路由流程如何运行。
3. 路由规则如何修改，路由层是否接入 LLM。

---

## 1. 总体结论

`claw0` 的教学版把路由压缩成一个简单模型：

```text
Inbound message
  -> BindingTable.resolve()
  -> agent_id
  -> build_session_key()
  -> run_agent()
```

OpenClaw 的生产版保留了相同的核心思想，但拆成了更多模块：

```text
channel / gateway inbound
  -> resolveAgentRoute()
  -> configured binding / runtime conversation binding
  -> buildAgentPeerSessionKey()
  -> agent session / CLI runner / embedded runner
  -> gateway event / channel delivery
```

对应关系如下：

| 方面 | claw0 教学版 | OpenClaw 生产版 |
| --- | --- | --- |
| 路由解析 | `BindingTable.resolve()` 5 层线性扫描 | `resolveAgentRoute()` 分层匹配 + 索引 + 缓存 |
| 路由规则 | 内存 `Binding[]` | 配置文件 `bindings[]`，由 Zod schema 校验 |
| 会话 key | `build_session_key()` + `dm_scope` | `buildAgentPeerSessionKey()` + `session.dmScope` / binding 级覆盖 |
| 多 Agent | 内存 `AgentManager` | `agents.list` 配置 + agent scope / workspace / runtime |
| 运行时绑定 | 无或简单内存绑定 | `SessionBindingService` + channel adapter + generic store |
| 网关协议 | WebSocket + JSON-RPC 2.0 | WebSocket request/response frame + HTTP API + method registry |
| 并发控制 | `asyncio.Semaphore(4)` | per-session / per-backend queue、session lock、channel event queue |
| 规则修改 | 代码中 `add/remove` | CLI `openclaw agents bind/unbind`、配置文件、Gateway `config.patch/apply` |
| LLM 路由 | 未接入 | 基础路由不接入 LLM，LLM 位于 Agent 执行层 |

---

## 2. 核心源码索引

### 2.1 路由解析

- [openclaw/src/routing/resolve-route.ts](D:/learn/openclaw/src/routing/resolve-route.ts)
  - `resolveAgentRoute()`：生产版路由入口。
  - `buildAgentSessionKey()`：根据 agent、channel、account、peer、dmScope 生成 session key。
  - `deriveLastRoutePolicy()`：决定 last route 写入主会话还是当前会话。

### 2.2 会话 key 与 dmScope

- [openclaw/src/routing/session-key.ts](D:/learn/openclaw/src/routing/session-key.ts)
  - `buildAgentMainSessionKey()`：构建 `agent:<id>:main`。
  - `buildAgentPeerSessionKey()`：构建 direct/group/channel 会话 key。
  - `resolveAgentIdFromSessionKey()`：从 session key 反解 agentId。

### 2.3 配置型 binding

- [openclaw/src/config/types.agents.ts](D:/learn/openclaw/src/config/types.agents.ts)
  - `AgentBindingMatch`
  - `AgentRouteBinding`
  - `AgentAcpBinding`
  - `AgentConfig`

- [openclaw/src/config/zod-schema.agents.ts](D:/learn/openclaw/src/config/zod-schema.agents.ts)
  - `BindingMatchSchema`
  - `RouteBindingSchema`
  - `AcpBindingSchema`
  - `BindingsSchema`

- [openclaw/src/config/bindings.ts](D:/learn/openclaw/src/config/bindings.ts)
  - `listConfiguredBindings()`
  - `listRouteBindings()`
  - `listAcpBindings()`

### 2.4 binding 作用域匹配

- [openclaw/src/routing/binding-scope.ts](D:/learn/openclaw/src/routing/binding-scope.ts)
  - `normalizeRouteBindingId()`
  - `normalizeRouteBindingRoles()`
  - `routeBindingScopeMatches()`

### 2.5 运行时 conversation binding

- [openclaw/src/infra/outbound/session-binding-service.ts](D:/learn/openclaw/src/infra/outbound/session-binding-service.ts)
  - `SessionBindingService`
  - `registerSessionBindingAdapter()`
  - `getSessionBindingService()`
  - `bind()`
  - `resolveByConversation()`
  - `listBySession()`
  - `touch()`
  - `unbind()`

- [openclaw/src/channels/plugins/binding-routing.ts](D:/learn/openclaw/src/channels/plugins/binding-routing.ts)
  - `resolveConfiguredBindingRoute()`
  - `resolveRuntimeConversationBindingRoute()`
  - `ensureConfiguredBindingRouteReady()`

### 2.6 绑定规则修改入口

- [openclaw/src/commands/agents.commands.bind.ts](D:/learn/openclaw/src/commands/agents.commands.bind.ts)
  - `agentsBindingsCommand()`
  - `agentsBindCommand()`
  - `agentsUnbindCommand()`

- [openclaw/src/commands/agents.bindings.ts](D:/learn/openclaw/src/commands/agents.bindings.ts)
  - `parseBindingSpecs()`
  - `applyAgentBindings()`
  - `removeAgentBindings()`

### 2.7 Gateway 协议与方法注册

- [openclaw/src/gateway/server/ws-connection/message-handler.ts](D:/learn/openclaw/src/gateway/server/ws-connection/message-handler.ts)
  - WebSocket 握手后接收 request frame，并按 `method` 调度。

- [openclaw/src/gateway/methods/core-descriptors.ts](D:/learn/openclaw/src/gateway/methods/core-descriptors.ts)
  - Gateway 核心方法声明。
  - 包括 `send`、`chat.send`、`sessions.list`、`sessions.send`、`config.patch`、`config.apply`。

- [openclaw/packages/gateway-protocol/src/index.ts](D:/learn/openclaw/packages/gateway-protocol/src/index.ts)
  - Gateway protocol schema、frame、validator 的公共入口。

### 2.8 并发与队列

- [openclaw/src/agents/cli-runner/helpers.ts](D:/learn/openclaw/src/agents/cli-runner/helpers.ts)
  - `KeyedAsyncQueue`
  - `enqueueCliRun()`
  - `resolveCliRunQueueKey()`

- [openclaw/src/agents/sessions/agent-session.ts](D:/learn/openclaw/src/agents/sessions/agent-session.ts)
  - Agent session 内部的 steering / follow-up queue。

- [openclaw/src/agents/session-write-lock.ts](D:/learn/openclaw/src/agents/session-write-lock.ts)
  - session 写入锁，避免同一会话并发写 transcript。

---

## 3. claw0 的 BindingTable 设计回顾

`claw0` 中 `BindingTable` 的设计非常直接：

```python
class BindingTable:
    def add(self, binding):
        self._bindings.append(binding)
        self._bindings.sort(key=lambda b: (b.tier, -b.priority))

    def resolve(self, channel="", account_id="", guild_id="", peer_id=""):
        for b in self._bindings:
            ...
        return None, None
```

它的核心思想是：

```text
越具体的规则越优先。
同层规则用 priority 排序。
第一个匹配项胜出。
```

教学版 5 层规则：

```text
T1 peer_id
T2 guild_id
T3 account_id
T4 channel
T5 default
```

这套模型的价值不是复杂度，而是把路由语义讲清楚：

```text
入口身份信息
  -> 路由规则
  -> agent_id
```

路由层只做确定性选择，不调用 LLM。

---

## 4. OpenClaw 中的生产版路由模型

OpenClaw 的生产版入口是 `resolveAgentRoute()`。

它接收的输入不是简单字符串，而是结构化路由上下文：

```ts
export type ResolveAgentRouteInput = {
  cfg: OpenClawConfig;
  channel: string;
  accountId?: string | null;
  peer?: RoutePeer | null;
  parentPeer?: RoutePeer | null;
  guildId?: string | null;
  teamId?: string | null;
  memberRoleIds?: string[];
};
```

这里的字段可以对应到真实消息平台：

| 字段 | 含义 |
| --- | --- |
| `channel` | 平台或通道，如 telegram、discord、slack |
| `accountId` | 多账号场景下的机器人账号 |
| `peer` | 当前会话对象，可能是 direct、group、channel |
| `parentPeer` | 线程消息的父会话，用于继承父通道绑定 |
| `guildId` | Discord guild / server 级别标识 |
| `teamId` | Slack/企业协作平台中的团队或 workspace |
| `memberRoleIds` | 成员角色，用于 Discord role-based routing |

OpenClaw 的路由层仍然是规则路由，但已经比 `claw0` 多了几个生产需求：

1. 多账号隔离。
2. 群组、频道、私聊统一抽象。
3. Discord role 级路由。
4. 线程父会话继承。
5. binding 级 `dmScope` 覆盖。
6. 缓存和索引，避免每次全量扫描。

---

## 5. OpenClaw 的路由规则结构

OpenClaw 的路由规则来自配置文件中的 `bindings[]`。

类型定义在 `AgentRouteBinding`：

```ts
export type AgentRouteBinding = {
  type?: "route";
  agentId: string;
  comment?: string;
  match: AgentBindingMatch;
  session?: {
    dmScope?: DmScope;
  };
};
```

匹配条件定义在 `AgentBindingMatch`：

```ts
export type AgentBindingMatch = {
  channel: string;
  accountId?: string;
  peer?: { kind: ChatType; id: string };
  guildId?: string;
  teamId?: string;
  roles?: string[];
};
```

一个典型 route binding 可以写成：

```yaml
bindings:
  - type: route
    agentId: sage
    match:
      channel: telegram
      accountId: personal
      peer:
        kind: direct
        id: "12345"
    session:
      dmScope: per-account-channel-peer
```

含义是：

```text
telegram/personal 账号收到 direct peer=12345 的消息时，
路由给 sage，
并且会话 key 按 account + channel + peer 隔离。
```

这与 `claw0` 的 `Binding(agent_id, tier, match_key, match_value)` 思路一致，只是 OpenClaw 把匹配条件改成了结构化对象。

---

## 6. OpenClaw 的分层匹配流程

`resolveAgentRoute()` 内部先做归一化：

```text
channel -> lowercase / channel id normalize
accountId -> normalizeAccountId
peer.kind -> normalizeChatType
peer.id -> normalizeRouteBindingId
guildId/teamId -> normalizeRouteBindingId
dmScope -> cfg.session.dmScope ?? "main"
```

然后从配置中取 route binding：

```ts
const bindings = getEvaluatedBindingsForChannelAccount(input.cfg, channel, accountId);
const bindingsIndex = getEvaluatedBindingIndexForChannelAccount(input.cfg, channel, accountId);
```

这里和 `claw0` 最大的区别是：OpenClaw 不再每次全量线性扫描所有 binding，而是先按 `channel + accountId` 过滤，并构建索引。

索引大致分成：

```text
byPeer
byPeerWildcard
byGuildWithRoles
byGuild
byTeam
byAccount
byChannel
```

然后按固定层级尝试匹配：

```text
1. binding.peer
2. binding.peer.parent
3. binding.peer.wildcard
4. binding.guild+roles
5. binding.guild
6. binding.team
7. binding.account
8. binding.channel
9. default
```

这就是 `claw0` 五层路由的生产扩展版。

对应关系可以这样看：

| claw0 tier | OpenClaw matchedBy | 说明 |
| --- | --- | --- |
| `peer_id` | `binding.peer` | 当前 peer 精确匹配 |
| `peer_id` 扩展 | `binding.peer.parent` | 线程/子会话继承父 peer |
| `peer_id` 扩展 | `binding.peer.wildcard` | 某类 peer 通配，如 direct:* |
| `guild_id` | `binding.guild+roles` / `binding.guild` | guild 级或 guild + role |
| 额外生产层 | `binding.team` | team/workspace 级 |
| `account_id` | `binding.account` | 账号级绑定 |
| `channel` | `binding.channel` | 通道级绑定 |
| `default` | `default` | 默认 agent |

最终命中后调用内部 `choose()`：

```ts
const route = {
  agentId: resolvedAgentId,
  channel,
  accountId,
  sessionKey,
  mainSessionKey,
  lastRoutePolicy,
  matchedBy,
};
```

也就是说，OpenClaw 的路由结果不只是 `agentId`，而是一个完整的 `ResolvedAgentRoute`。

---

## 7. session key 与 dmScope 的实现

`claw0` 中，路由和会话 key 是分离的：

```text
BindingTable.resolve()
  -> 选择 agent

build_session_key()
  -> 决定会话隔离
```

OpenClaw 也保留了这个边界。

核心函数是：

```ts
buildAgentPeerSessionKey()
```

它支持这些 `dmScope`：

```text
main
per-peer
per-channel-peer
per-account-channel-peer
```

direct 私聊场景下，生成规则大致是：

```text
main:
  agent:<agentId>:main

per-peer:
  agent:<agentId>:direct:<peerId>

per-channel-peer:
  agent:<agentId>:<channel>:direct:<peerId>

per-account-channel-peer:
  agent:<agentId>:<channel>:<accountId>:direct:<peerId>
```

非 direct 的 group/channel 场景则使用：

```text
agent:<agentId>:<channel>:<peerKind>:<peerId>
```

这个设计说明：

```text
路由规则决定哪个 agent 回答。
dmScope 决定这条消息进入哪个会话记忆。
```

二者不能混在一起。

OpenClaw 还支持 binding 级覆盖：

```ts
session?: {
  dmScope?: DmScope;
};
```

这意味着可以全局使用：

```yaml
session:
  dmScope: main
```

但对某些高风险/多账号通道单独设置：

```yaml
bindings:
  - type: route
    agentId: work
    match:
      channel: telegram
      accountId: work
    session:
      dmScope: per-account-channel-peer
```

这是生产系统里很重要的能力，因为不同平台对“同一个用户”的定义并不总是一致。

---

## 8. configured binding 与 runtime conversation binding

OpenClaw 中除了普通 route binding，还有更复杂的 conversation binding。

主要分两类：

```text
configured binding:
  写在配置文件里的绑定。

runtime conversation binding:
  运行时创建并持久化的会话绑定。
```

### 8.1 configured binding

相关代码：

- `resolveConfiguredBindingRoute()`
- `resolveConfiguredBinding()`
- `ensureConfiguredBindingRouteReady()`

当当前 conversation 命中某个 configured binding 时，OpenClaw 会重写 route：

```ts
route: {
  ...params.route,
  sessionKey: boundSessionKey,
  agentId: boundAgentId,
  matchedBy: "binding.channel",
}
```

这说明 configured binding 的优先级不只是“选 agent”，还可以直接指定一个 stateful target session。

### 8.2 runtime conversation binding

相关代码：

- `getSessionBindingService().resolveByConversation()`
- `resolveRuntimeConversationBindingRoute()`

运行时 binding 的典型用途是：

```text
某个 Discord/Slack/Telegram 线程已经绑定到某个 session。
后续这个线程的消息应继续进入同一个 session。
```

这解决的是持久会话归属问题，而不只是路由默认选择问题。

需要注意的是，OpenClaw 对 cron run session 做了保护：

```text
如果 runtime binding 指向 isolated cron run session，则忽略。
```

原因是 cron run session 是短生命周期隔离任务，不能让实时通道消息误入其中。

---

## 9. 路由规则如何修改

### 9.1 直接修改配置文件

OpenClaw 的规则来源是顶层：

```yaml
bindings:
  - type: route
    agentId: main
    match:
      channel: telegram
      accountId: default
```

配置由 `BindingsSchema` 校验：

```ts
export const BindingsSchema = z.array(z.union([RouteBindingSchema, AcpBindingSchema])).optional();
```

因此，生产环境推荐把 route binding 看成配置资产，而不是运行时随意插入的内存对象。

### 9.2 使用 CLI 修改

OpenClaw 提供了 agent binding 命令。

主要实现：

```text
agentsBindingsCommand()  查看绑定
agentsBindCommand()      新增绑定
agentsUnbindCommand()    删除绑定
```

底层逻辑：

```text
parseBindingSpecs()
  -> 把 <channel> 或 <channel>:<account> 解析成 AgentRouteBinding

applyAgentBindings()
  -> 合并到 cfg.bindings
  -> 检测冲突
  -> 支持同 agent 的 account-scope upgrade

removeAgentBindings()
  -> 从 cfg.bindings 删除匹配项
  -> 保留非 route binding
```

CLI 修改不是直接改内存，而是写回配置文件：

```ts
await replaceConfigFile({ nextConfig: result.config, baseHash });
```

这样做有几个好处：

1. 配置可审计。
2. 重启后不会丢失。
3. 可以检测并发修改。
4. 可以和 UI / Gateway config API 共用同一配置源。

### 9.3 通过 Gateway 修改配置

Gateway 核心方法里有：

```text
config.patch
config.apply
```

它们在 `core-descriptors.ts` 中被标记为：

```ts
controlPlaneWrite: true
```

这说明通过 Gateway 修改配置属于控制面写操作，需要更高权限。

与 `claw0` 的 `bindings.set` 相比，OpenClaw 没有把“路由绑定修改”做成一个孤立的 `bindings.set` 小接口，而是纳入统一配置写入系统。

这是更生产化的设计：

```text
路由规则是 config 的一部分。
修改路由规则就是 config patch/apply。
```

---

## 10. 路由层是否接入 LLM

结论：OpenClaw 的基础路由层不接入 LLM。

从 `resolveAgentRoute()` 可以看出，它只使用：

```text
channel
accountId
peer
parentPeer
guildId
teamId
memberRoleIds
cfg.bindings
cfg.session.dmScope
```

它不读取用户消息正文，也不调用模型。

这说明 OpenClaw 的基础路由是确定性规则系统：

```text
身份路由，不是语义路由。
```

这样设计的原因：

1. 可预测：同样的 channel/account/peer 总是得到同样 route。
2. 可审计：可以解释为什么命中某条 binding。
3. 低成本：不需要为每条消息额外调用一次 LLM。
4. 低延迟：路由阶段只做本地匹配。
5. 权限安全：账号、通道、角色这类访问控制不能交给 LLM 猜。

LLM 位于后续 Agent 执行层，例如 embedded runner、CLI runner、ACP runtime 等，而不是基础路由层。

如果未来要做“语义路由”，更合理的方式是：

```text
第一层：确定性 identity route
  channel/account/peer/guild/role -> agent or router-agent

第二层：可选 semantic router
  只有命中特定 router-agent 时才调用 LLM 判断意图

第三层：目标 agent 执行任务
```

也就是说，不建议让基础路由完全依赖 LLM。LLM 语义路由应该作为后置增强层，而不是替代权限和身份路由。

---

## 11. Gateway 协议流程

`claw0` 的教学版使用 WebSocket + JSON-RPC 2.0。

OpenClaw 生产版同样使用 WebSocket 作为主要控制通道，但协议更完整：

```text
client connect
  -> handshake / hello
  -> auth / pairing / role check
  -> request frame
  -> method registry dispatch
  -> response frame
  -> event frame broadcast
```

从 `message-handler.ts` 可以看到，握手完成后只接受 request frame：

```text
After handshake, accept only req frames
```

请求 frame 结构大致是：

```text
{
  type: "req",
  id: "...",
  method: "...",
  params: {...}
}
```

响应 frame：

```text
{
  type: "res",
  id: "...",
  ok: true,
  payload: {...}
}
```

如果失败：

```text
{
  type: "res",
  id: "...",
  ok: false,
  error: {...}
}
```

Gateway 方法声明在 `core-descriptors.ts`，典型方法包括：

```text
send
chat.send
sessions.list
sessions.send
agents.list
config.patch
config.apply
gateway.restart.request
```

这比 `claw0` 的 demo 协议更完整：

```text
claw0:
  send / bindings.set / bindings.list / sessions.list / agents.list / status

OpenClaw:
  send/chat/sessions/config/agents/channels/cron/tools/update 等完整控制面
```

---

## 12. 多 Agent 的实现思路

`claw0` 用 `AgentManager` 在内存里保存多个 `AgentConfig`。

OpenClaw 中，多 Agent 是配置驱动的：

```ts
export type AgentsConfig = {
  defaults?: AgentDefaultsConfig;
  list?: AgentConfig[];
};
```

每个 `AgentConfig` 可以定义：

```text
id
name
description
workspace
agentDir
model
models
skills
identity
groupChat
subagents
runtime
sandbox
tools
```

路由命中某个 `agentId` 后，系统会进一步解析：

```text
agent workspace
agent model
agent runtime
agent tools
agent system prompt
agent session store
```

所以 OpenClaw 的 agent 不是一个简单对象，而是一组配置、目录、运行时和会话状态的组合。

---

## 13. 并发控制与队列模型

`claw0` 中用：

```python
asyncio.Semaphore(4)
```

限制 LLM 并发。

OpenClaw 没有用单一 semaphore 覆盖所有场景，而是按资源边界拆成多种队列和锁：

### 13.1 CLI backend 串行化

`cli-runner/helpers.ts` 中：

```ts
const CLI_RUN_QUEUE = new KeyedAsyncQueue();

export function enqueueCliRun<T>(key: string, task: () => Promise<T>): Promise<T> {
  return CLI_RUN_QUEUE.enqueue(key, task);
}
```

队列 key 由 `resolveCliRunQueueKey()` 决定。

它会按 backend、session、owner、workspace 等维度串行化，避免同一 CLI 会话被并发写入。

### 13.2 session 写锁

`session-write-lock.ts` 负责保护 session transcript 写入。

这是生产系统里非常关键的点：多个异步事件可能同时尝试写同一个会话，如果没有锁，transcript 很容易损坏或乱序。

### 13.3 Agent session 内部队列

`agent-session.ts` 中存在：

```text
steering queue
follow-up queue
asides queue
```

当 Agent 正在 streaming 时，新消息不能总是立即插入执行流。系统需要判断：

```text
是打断当前运行？
还是排到当前回复之后？
还是作为旁路上下文？
```

这比教学版单 semaphore 更复杂，但目标相同：

```text
避免无边界并发导致状态错乱。
```

---

## 14. OpenClaw 路由执行流程

综合源码，生产流程可以概括为：

```text
1. 外部消息进入 channel adapter 或 Gateway。

2. 提取结构化身份：
   channel
   accountId
   peer.kind
   peer.id
   parentPeer
   guildId
   teamId
   memberRoleIds

3. 调用 resolveAgentRoute(input)。

4. resolveAgentRoute 归一化输入。

5. 读取 cfg.bindings，并筛选 route binding。

6. 按 channel/account 建索引。

7. 按层级匹配：
   peer
   parent peer
   peer wildcard
   guild + roles
   guild
   team
   account
   channel
   default

8. 选出 agentId。

9. 根据 cfg.session.dmScope 或 binding.session.dmScope 生成 sessionKey。

10. 得到 ResolvedAgentRoute：
    agentId
    channel
    accountId
    sessionKey
    mainSessionKey
    lastRoutePolicy
    matchedBy

11. 应用 configured binding / runtime conversation binding。

12. 进入 agent runtime：
    embedded agent / CLI backend / ACP runtime

13. Agent 执行 LLM 调用与工具调用。

14. Gateway / channel adapter 将结果投递回原通道。
```

---

## 15. 对 Python 版 PyClaw 的设计建议

如果你要把 OpenClaw 改造成 Python 版本，可以保留 `claw0` 的简洁结构，但吸收 OpenClaw 的生产边界。

建议拆成这些模块：

```text
pyclaw/routing/models.py
  AgentRouteBinding
  AgentBindingMatch
  ResolvedAgentRoute

pyclaw/routing/session_key.py
  build_agent_main_session_key()
  build_agent_peer_session_key()
  parse_agent_session_key()

pyclaw/routing/resolve_route.py
  resolve_agent_route()
  build_evaluated_binding_index()

pyclaw/config/schema.py
  bindings[] schema
  agents.list schema
  session.dm_scope schema

pyclaw/bindings/service.py
  SessionBindingService
  register_adapter()
  resolve_by_conversation()

pyclaw/gateway/server.py
  WebSocket request/response frame
  method registry

pyclaw/agents/manager.py
  Agent registry
  workspace / runtime / model resolution

pyclaw/runtime/queue.py
  keyed async queue
  per-session locks
```

Python 版可以先实现一个简化流程：

```text
config bindings[]
  -> resolve_agent_route()
  -> build_session_key()
  -> agent_manager.run()
```

之后再逐步加入：

```text
runtime conversation binding
thread binding
Gateway config.patch
role-based routing
per-session queue
plugin channel adapter
```

最重要的是保持三个边界：

```text
1. 路由层
   只做身份规则匹配，不调用 LLM。

2. 会话层
   只负责 session key 和记忆隔离。

3. Agent 层
   负责 LLM、工具、人格、workspace 和执行策略。
```

---

## 16. 最后总结

OpenClaw 中的 `BindingTable` 对应实现不是一个单独类，而是一组生产模块：

```text
resolveAgentRoute()
  是 BindingTable.resolve() 的生产版。

cfg.bindings[]
  是 Binding[] 的持久化配置版。

buildAgentPeerSessionKey()
  是 build_session_key() 的生产版。

SessionBindingService
  是运行时会话绑定的生产扩展。

Gateway method registry
  是 demo JSON-RPC 方法表的生产扩展。

KeyedAsyncQueue + session locks
  是 asyncio.Semaphore(4) 的生产扩展。
```

路由规则的修改方式也从教学版的 `bindings.add/remove` 变成了：

```text
配置文件 bindings[]
CLI: openclaw agents bind/unbind
Gateway: config.patch/config.apply
```

基础路由层不接入 LLM。它是稳定、可审计、低成本的身份路由系统。LLM 应位于 Agent 执行层；如果未来要做语义路由，也建议作为确定性路由之后的可选增强，而不是替代基础 binding 规则。
