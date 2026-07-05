# OpenClaw Channel 平台适配技术笔记：微信 / 飞书

本文档用于指导 `pyclaw` 以 Python 方式落地 Channel 平台适配。当前范围明确收敛为：

- 微信：公众号 / 企业微信等回调入口、签名校验、会话归一化、后续客服消息发送。
- 飞书：事件回调 / 长连接事件、同会话顺序处理、文本与卡片消息发送。

除微信、飞书和通用 Channel 运行时以外，本文档不再保留其他平台的迁移内容。

## 1. 设计目标

`pyclaw` 不应把平台接入写成两个孤立的 HTTP handler。微信和飞书虽然接入协议不同，但都需要同一套可靠消息边界：

- 平台原始事件进入统一 `RawInboundEvent`。
- 按平台规则校验签名、解密或验签。
- 将平台 payload 归一化为 `PreparedInboundMessage`。
- 入站事件进入 durable ingress queue，支持幂等、重试、重复检测。
- 同一个会话使用 lane / sequential key 保持顺序。
- Agent 回复通过平台出站 adapter 发送，并返回 `MessageReceipt`。
- 平台 token、租户信息、去重记录等进入 keyed state store。

因此第一阶段优先迁移 OpenClaw 的通用 Channel 边界，再分别补微信、飞书平台 adapter。

## 2. ChannelPlugin 根对象

OpenClaw 的 Channel 不是一个简单的 `receive()` / `send()` 基类，而是插件根对象。Python 版本应保留这个边界：

```python
@dataclass
class ChannelPlugin:
    id: str
    meta: ChannelMeta
    capabilities: ChannelCapabilities
    message: ChannelMessageAdapter | None = None
    config: ChannelConfigAdapter | None = None
    lifecycle: ChannelLifecycleAdapter | None = None
    status: ChannelStatusAdapter | None = None
    metadata: dict[str, Any] = field(default_factory=dict)
```

微信插件建议：

```text
id = "wechat"
capabilities.inbound = true
capabilities.outbound_text = true
```

飞书插件建议：

```text
id = "feishu"
capabilities.inbound = true
capabilities.outbound_text = true
capabilities.outbound_payload = true
```

`message` 负责收发契约，`config` 负责平台配置解析，`lifecycle` 负责 webhook worker、token refresh、长连接等后台任务。

## 3. 统一消息模型

### 3.1 RawInboundEvent

平台 webhook 或事件流进入系统后的第一层对象：

```python
@dataclass(frozen=True)
class RawInboundEvent:
    id: str
    channel: str
    account_id: str | None
    platform_payload: Mapping[str, Any]
    received_at: float
    ack_policy: ChannelMessageReceiveAckPolicy
    lane_key: str | None = None
```

微信可以把回调 XML / JSON 原文解析后放入 `platform_payload`。飞书可以把 event callback 或长连接事件放入 `platform_payload`。

### 3.2 PreparedInboundMessage

平台 payload 归一化后再派发给 Agent：

```python
@dataclass(frozen=True)
class PreparedInboundMessage:
    id: str
    channel: str
    account_id: str | None
    conversation_id: str
    sender_id: str
    text: str
    thread_id: str | None = None
    reply_to_id: str | None = None
    attachments: list[Attachment] = field(default_factory=list)
    raw: Mapping[str, Any] = field(default_factory=dict)
```

微信字段建议：

```text
conversation_id = openid / external_userid / 群会话 ID
sender_id = openid / userid / external_userid
channel = "wechat"
```

飞书字段建议：

```text
conversation_id = chat_id
sender_id = open_id / union_id / user_id
channel = "feishu"
```

### 3.3 MessageReceipt

出站发送结果需要可追踪：

```python
@dataclass(frozen=True)
class MessageReceipt:
    primary_platform_message_id: str | None
    platform_message_ids: list[str]
    parts: list[MessageReceiptPart]
    thread_id: str | None = None
    reply_to_id: str | None = None
    sent_at: float = field(default_factory=time.time)
    metadata: dict[str, Any] = field(default_factory=dict)
```

微信客服消息、企业微信消息、飞书文本消息、飞书卡片消息都应返回 receipt，便于审计、重试、诊断和后续编辑/撤回扩展。

## 4. ACK Policy

入站消息不能只靠 HTTP 200 简单处理。需要明确 ack 时机：

```text
AFTER_RECEIVE_RECORD   入站记录成功后 ack
AFTER_AGENT_DISPATCH   派发给 Agent 成功后 ack
AFTER_DURABLE_SEND     可靠回复发送完成后 ack
MANUAL                 平台 adapter 自行 ack
```

微信 webhook 常见做法是尽快返回平台要求的响应，但业务处理仍需进入队列，避免超时导致平台重试。飞书也需要区分事件接收确认和业务处理完成。

`MessageReceiveContext` 应保证：

- `ack()` 幂等。
- `nack(error)` 幂等。
- `ack` 后不能 `nack`。
- `nack` 后不能 `ack`。
- `ack_after_stage(stage)` 根据策略自动判断。

## 5. Durable Ingress Queue

微信、飞书都需要可靠入站队列。建议使用 SQLite 作为第一阶段实现：

```text
event_id      平台事件唯一 ID，主键
channel       wechat / feishu
lane_key      同会话顺序 key
payload_json  平台 payload
status        pending / claimed / completed / failed
attempts      claim 次数
owner_id      当前 worker
claim_token   claim 所有权 token
error         失败诊断
created_at / updated_at / claimed_at
```

核心行为：

- `enqueue()` 对 `event_id` 幂等，重复事件返回 `False`。
- `claim_next()` 使用 claim token，避免多个 worker 同时完成同一任务。
- 同一 `lane_key` 同时只允许一个 active claim。
- stale claim 超时后可释放，避免 worker 崩溃导致永久阻塞。
- `complete()`、`release()`、`fail()` 必须校验 claim token。

推荐 lane key：

```text
wechat:<account_id>:<openid_or_conversation_id>
feishu:<tenant_key>:<chat_id>
```

这样同一会话内顺序稳定，不同会话仍可并发。

## 6. Plugin State Keyed Store

微信和飞书都会产生平台状态，不能散落为临时文件。建议统一使用 keyed state store：

```python
class PluginStateKeyedStore(Protocol[T]):
    async def register(self, key: str, value: T, ttl_ms: int | None = None) -> None: ...
    async def lookup(self, key: str) -> T | None: ...
    async def delete(self, key: str) -> bool: ...
```

可存储内容：

- 微信 access token / jsapi ticket 缓存。
- 微信 webhook challenge 或去重 tombstone。
- 飞书 tenant access token 缓存。
- 飞书 app ticket / tenant_key 映射。
- 平台账号绑定关系。
- 已处理事件 ID 的短期去重记录。

第一阶段可以使用 JSON 文件实现，但写入必须使用临时文件 + `fsync` + 原子替换。后续可迁移到 SQLite。

## 7. 微信适配要点

### 7.1 回调验签

微信回调 URL 校验常见签名规则：

```text
1. token、timestamp、nonce 字典序排序
2. 拼接为字符串
3. SHA-1
4. 与 signature 常量时间比较
```

Python helper：

```python
def build_wechat_signature(token: str, timestamp: str, nonce: str) -> str:
    pieces = sorted([token, timestamp, nonce])
    raw = "".join(pieces).encode("utf-8")
    return hashlib.sha1(raw).hexdigest()


def verify_wechat_signature(token: str, timestamp: str, nonce: str, signature: str) -> bool:
    expected = build_wechat_signature(token, timestamp, nonce)
    return hmac.compare_digest(expected, signature)
```

### 7.2 入站流程

建议流程：

```text
HTTP callback
  -> verify signature
  -> parse XML / JSON
  -> build RawInboundEvent
  -> enqueue durable ingress queue
  -> platform ack
  -> worker claim
  -> build PreparedInboundMessage
  -> dispatch Agent
  -> send reply through WeChat adapter
  -> complete queue record
```

### 7.3 出站流程

后续可分两类 adapter：

- 公众号客服消息 / 模板消息 / 订阅消息。
- 企业微信应用消息 / 客户联系消息。

无论具体 API 如何，最终都应返回 `MessageReceipt`。

## 8. 飞书适配要点

### 8.1 入站传输

飞书可通过 webhook event callback 或长连接事件接入。两者都应归一到：

```text
RawInboundEvent(channel="feishu", ...)
```

事件处理时应注意：

- URL verification / challenge 响应。
- 事件 token 或签名校验。
- tenant_key / app_id / chat_id 解析。
- event_id 幂等入队。

### 8.2 同会话顺序队列

飞书同一个 chat 内连续消息需要保持顺序。建议保留 per-key sequential queue：

```python
class SequentialQueue:
    async def run(self, key: str, task: Callable[[], Awaitable[T]]) -> T:
        ...
```

行为：

- 相同 key 串行。
- 不同 key 并发。
- 可配置单任务 timeout，避免某个 chat 永久阻塞。

推荐 key：

```text
feishu:<tenant_key>:<chat_id>
```

### 8.3 出站能力

飞书建议优先支持：

- 发送文本消息。
- 发送卡片消息。
- 发送结构化 payload。

出站 adapter 应把平台 API 结果归一为 `MessageReceipt`，并保留飞书 message_id。

## 9. 推荐 Python 包结构

```text
openclaw/
  channels/
    core.py
    message/
      adapter.py
      ingress_queue.py
      receive.py
      send.py
      types.py
  plugins/
    wechat/
      signature.py
      webhook.py          # 后续补充
      send.py             # 后续补充
    feishu/
      sequential_queue.py
      webhook.py          # 后续补充
      send.py             # 后续补充
  state/
    plugin_state.py
```

## 10. 最小落地顺序

1. 落地通用 Channel 类型、message contract、receipt、receive context。
2. 落地 SQLite durable ingress queue。
3. 落地 plugin-state keyed store。
4. 落地微信签名校验 helper。
5. 落地飞书 per-key sequential queue。
6. 补微信 webhook adapter：验签、解析、入队、派发、回复。
7. 补飞书 webhook / 长连接 adapter：验签、入队、顺序处理、回复。
8. 补出站 text / payload adapter，并记录 `MessageReceipt`。

## 11. 当前 pyclaw 实现状态

当前已经落地的第一阶段能力：

- `openclaw/channels/core.py`
- `openclaw/channels/message/types.py`
- `openclaw/channels/message/receive.py`
- `openclaw/channels/message/adapter.py`
- `openclaw/channels/message/send.py`
- `openclaw/channels/message/ingress_queue.py`
- `openclaw/state/plugin_state.py`
- `openclaw/plugins/wechat/signature.py`
- `openclaw/plugins/feishu/sequential_queue.py`
- `tests/test_channels.py`

当前尚未落地：

- 微信 webhook 路由与 XML / JSON 解析。
- 微信出站消息 API。
- 飞书 webhook / 长连接 monitor。
- 飞书文本 / 卡片发送 API。
- Channel turn dispatcher 到 `AgentSession` 的完整桥接。

## 12. 结论

微信和飞书接入的核心难点不在于调用某个 HTTP API，而在于消息可靠性：验签、幂等、ACK 时机、同会话顺序、失败重试、状态缓存、发送回执。

`pyclaw` 当前应保留 OpenClaw 的插件化 Channel 思路，但平台范围只聚焦微信和飞书。这样可以先把国内常用协作入口跑通，同时避免在早期维护过多平台适配带来的复杂度。
