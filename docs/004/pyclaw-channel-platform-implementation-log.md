# pyclaw 微信 / 飞书 Channel 平台适配核心实现记录

本文记录根据 `docs/004/openclaw-channel-platform-implementation-notes.md` 在 Python 版 `pyclaw` 中落地的第一阶段实现。

本阶段范围已经收敛为 **微信 + 飞书**。除通用 Channel 运行时以外，不再保留其他平台的实现说明或平台专属 helper。

当前目标不是一次性接入微信、飞书的真实 SDK，而是先把两个平台后续接入都需要的公共边界落到 Python 代码中：

- ChannelPlugin 根对象与 registry。
- message send / receive adapter contract。
- ack policy 与 ack/nack 幂等状态机。
- 统一 receipt / raw event / prepared message 类型。
- SQLite durable ingress queue。
- plugin-state keyed JSON store。
- 微信 webhook 签名校验 helper。
- 飞书 per-key sequential queue。

这些能力构成后续微信/飞书接入的基础层。真实平台 monitor、webhook、send API 可以继续接到这些接口上，而不需要把可靠性逻辑揉进单个平台模块。

## 1. 新增与保留文件

| 文件 | 作用 |
| --- | --- |
| `openclaw/channels/__init__.py` | 导出 channel 核心类型。 |
| `openclaw/channels/core.py` | `ChannelPlugin`、`ChannelMeta`、`ChannelCapabilities`、`ChannelRegistry` 和配置/生命周期/status 协议。 |
| `openclaw/channels/message/types.py` | ack policy、receive stage、raw inbound event、prepared inbound message、attachment、receipt、send context 等统一类型。 |
| `openclaw/channels/message/receive.py` | `MessageReceiveContext`，实现幂等 `ack()` / `nack()` 和按 stage 自动 ack。 |
| `openclaw/channels/message/adapter.py` | send/receive adapter Protocol、默认 manual receive adapter、`define_channel_message_adapter()`。 |
| `openclaw/channels/message/send.py` | 文本消息发送 helper，串接 send lifecycle hook。 |
| `openclaw/channels/message/ingress_queue.py` | SQLite-backed durable ingress queue，支持幂等 enqueue、claim token、lane 串行、stale claim recovery。 |
| `openclaw/state/plugin_state.py` | JSON keyed state store，使用临时文件 + fsync + atomic replace 写入。 |
| `openclaw/plugins/wechat/signature.py` | 微信公众号/回调场景的 SHA-1 签名构造与校验。 |
| `openclaw/plugins/feishu/sequential_queue.py` | per-key async sequential queue，不同 key 可并发，同 key 串行。 |
| `tests/test_channels.py` | Channel 核心、ACK、ingress queue、state store、微信签名、飞书顺序队列单元测试。 |

## 2. ChannelPlugin 根对象

`openclaw/channels/core.py` 中新增：

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

这个结构刻意没有退化成单一 `Channel.receive()` / `Channel.send()` 抽象，而是保留平台插件根对象思想：

- `meta`：展示名称、描述、homepage。
- `capabilities`：声明平台能力，例如 inbound、outbound text、media、durable final、threaded replies。
- `message`：消息收发 adapter。
- `config`：平台配置解析协议。
- `lifecycle`：平台 monitor 启停协议。
- `status`：健康检查协议。
- `metadata`：给微信/飞书后续扩展保留字段。

后续微信插件可以声明：

```text
id = "wechat"
capabilities.inbound = true
capabilities.outbound_text = true
```

飞书插件可以声明：

```text
id = "feishu"
capabilities.inbound = true
capabilities.outbound_text = true
capabilities.outbound_payload = true
```

## 3. Message Contract

`openclaw/channels/message/types.py` 中新增了 Python 版统一消息类型。

### 3.1 RawInboundEvent

```python
@dataclass(frozen=True)
class RawInboundEvent:
    id: str
    channel: str
    account_id: str | None
    platform_payload: Mapping[str, Any]
    received_at: float = field(default_factory=time.time)
    ack_policy: ChannelMessageReceiveAckPolicy = ChannelMessageReceiveAckPolicy.MANUAL
    lane_key: str | None = None
```

它表示平台原始事件进入 channel runtime 的第一层记录。微信 webhook XML/JSON、飞书 event callback 都可以先转成 `RawInboundEvent`。

`lane_key` 用于后续 durable queue 保证同一会话、同一 open_id、同一 chat_id 的顺序。

### 3.2 PreparedInboundMessage

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

它是平台 payload 归一化后的中间产物。

微信场景中：

```text
conversation_id 可以取公众号/企业微信会话 ID
sender_id 可以取 open_id / external_userid
```

飞书场景中：

```text
conversation_id 可以取 chat_id
sender_id 可以取 sender id / open_id / union_id
```

后续 dispatcher 可以把它转成 `AgentSession.run_prompt()` 的输入。

### 3.3 MessageReceipt

```python
@dataclass(frozen=True)
class MessageReceipt:
    primary_platform_message_id: str | None = None
    platform_message_ids: list[str] = field(default_factory=list)
    parts: list[MessageReceiptPart] = field(default_factory=list)
    thread_id: str | None = None
    reply_to_id: str | None = None
    sent_at: float = field(default_factory=time.time)
    metadata: dict[str, Any] = field(default_factory=dict)
```

出站发送结果不只返回一个 `"ok"`，而是保留平台消息 ID、分段发送结果、thread/reply 信息和 metadata。这样后续可以扩展：

- 微信客服消息发送记录。
- 飞书文本消息 / 卡片消息发送记录。
- 消息重试、审计、诊断。

## 4. ACK Policy 与 Receive Context

`ChannelMessageReceiveAckPolicy` 表达 ack 时机：

```python
AFTER_RECEIVE_RECORD
AFTER_AGENT_DISPATCH
AFTER_DURABLE_SEND
MANUAL
```

`MessageReceiveContext` 提供：

- `ack()`：幂等确认。
- `nack(error)`：幂等拒绝。
- `ack_after_stage(stage)`：根据策略判断当前 stage 是否应 ack。

幂等规则：

```text
ack 后不能 nack
nack 后不能 ack
重复 ack / nack 返回 False
```

微信和飞书 webhook 都需要谨慎处理 ACK：

- ACK 太早：Agent 或发送回复失败时，平台认为消息已处理。
- ACK 太晚：平台可能重试推送，导致重复消息。

因此 ACK 策略必须成为 channel runtime 的一部分。

## 5. Message Adapter 与 Send Lifecycle

`openclaw/channels/message/adapter.py` 定义：

- `ChannelMessageSendAdapter`
- `ChannelMessageReceiveAdapter`
- `ChannelMessageSendLifecycleAdapter`
- `ChannelMessageAdapter`
- `ManualReceiveAdapter`
- `define_channel_message_adapter()`

如果平台没有显式 receive adapter，`define_channel_message_adapter()` 会补一个 `ManualReceiveAdapter`，默认 ack policy 是 `manual`。

`openclaw/channels/message/send.py` 提供 `send_text_message()`，按顺序执行：

```text
before_send_attempt
adapter.text(...)
after_send_success / after_send_failure
after_commit
```

当前只实现 text helper。后续飞书卡片消息、微信图文/客服消息可以扩展 payload/media helper。

## 6. SQLite Durable Ingress Queue

`openclaw/channels/message/ingress_queue.py` 实现 `SQLiteIngressQueue`。

### 6.1 表结构

核心字段：

```text
event_id      入站事件唯一 ID，主键
channel       平台名，wechat 或 feishu
lane_key      同会话/同 sender 串行 key
payload_json  原始 payload JSON
status        pending / claimed / completed / failed
attempts      claim 次数
owner_id      当前 worker
claim_token   claim 所有权 token
error         fail 诊断信息
created_at / updated_at / claimed_at
```

### 6.2 enqueue

```python
enqueue(event_id, channel, payload, lane_key=None) -> bool
```

如果 `event_id` 已存在，返回 `False`，实现入站重复检测。

### 6.3 claim_next

```python
claim_next(owner_id, blocked_lane_keys=()) -> IngressQueueClaim | None
```

行为：

1. 先释放超过 `stale_after_seconds` 的旧 claim。
2. 按 `created_at` 顺序扫描 pending 事件。
3. 跳过外部传入的 `blocked_lane_keys`。
4. 如果同一 `lane_key` 已有 active claim，跳过，保证同一 lane 串行。
5. claim 成功后写入 `owner_id`、`claim_token`、`claimed_at` 并递增 attempts。

### 6.4 微信 / 飞书 lane_key 建议

微信：

```text
公众号私聊: wechat:<account_id>:<openid>
企业微信客户: wechat:<corp_id>:<external_userid>
```

飞书：

```text
单聊/群聊: feishu:<tenant_key>:<chat_id>
```

这样可以保证同一会话内消息顺序，同时允许不同会话并发处理。

## 7. Plugin State Keyed Store

`openclaw/state/plugin_state.py` 新增：

```python
class PluginStateKeyedStore(Protocol[T]):
    async def register(self, key: str, value: T, ttl_ms: int | None = None) -> None: ...
    async def lookup(self, key: str) -> T | None: ...
    async def delete(self, key: str) -> bool: ...
```

当前落地实现是 `JsonPluginStateStore`：

- 文件内以 key -> `{value, expires_at}` 存储。
- 写入时使用临时文件。
- 写入后 `fsync`。
- 最后 `os.replace()` 原子替换。
- `lookup()` 时发现 TTL 过期会删除该 key 并重写文件。

后续微信/飞书可用它保存：

- access token cache。
- webhook challenge 状态。
- message dedupe tombstone。
- 平台账号绑定关系。
- 飞书 tenant 配置缓存。

## 8. 微信签名校验

`openclaw/plugins/wechat/signature.py` 实现：

```python
def build_wechat_signature(token: str, timestamp: str, nonce: str) -> str:
    pieces = sorted([token, timestamp, nonce])
    raw = "".join(pieces).encode("utf-8")
    return hashlib.sha1(raw).hexdigest()


def verify_wechat_signature(token: str, timestamp: str, nonce: str, signature: str) -> bool:
    expected = build_wechat_signature(token, timestamp, nonce)
    return hmac.compare_digest(expected, signature)
```

这是微信公众号回调 URL 校验常用的 SHA-1 签名逻辑：

```text
1. token、timestamp、nonce 字典序排序
2. 拼接成字符串
3. SHA-1
4. 与 signature 比较
```

使用 `hmac.compare_digest()` 是为了避免普通字符串比较带来的时序侧信道风险。

## 9. 飞书 SequentialQueue

`openclaw/plugins/feishu/sequential_queue.py` 实现：

```python
class SequentialQueue:
    async def run(self, key: str, task: Callable[[], Awaitable[T]]) -> T:
        ...
```

行为：

- 相同 key 使用同一个 `asyncio.Lock`，保证串行。
- 不同 key 使用不同 lock，可以并发。
- 可选 `timeout_seconds`，用于限制单个 task 占用队列时间。

飞书同一个 chat 内消息通常需要保持顺序，否则连续消息可能被 Agent 乱序处理。

## 10. 本阶段没有做的事情

本阶段刻意没有直接实现：

1. 微信 webhook 路由。
2. 微信 XML/JSON 消息解析。
3. 微信客服消息或企业微信消息发送 API。
4. 飞书 webhook/ws monitor。
5. 飞书文本/卡片消息发送 API。
6. channel turn dispatcher 到 `AgentSession` 的完整桥接。
7. durable final delivery reconciliation。
8. live preview/finalizer。

原因是这些能力依赖平台配置、鉴权、网络入口和更完整的运行时生命周期。当前先把可测试、无外部依赖的核心可靠性边界落地，后续接微信/飞书时可以直接复用。

## 11. 验证

已执行：

```powershell
py -m compileall openclaw tests
py -m unittest tests.test_channels
```

结果：

```text
compileall 通过
Ran 9 tests OK
```

新增测试覆盖：

- ChannelRegistry 注册与重复注册。
- receive ack policy 和 ack/nack 幂等。
- ingress queue 幂等 enqueue、claim、complete、lane blocking、stale claim recovery。
- JSON plugin state register / lookup / delete。
- 微信签名构造与校验。
- 飞书 SequentialQueue 同 key 串行、不同 key 可并发。

## 12. 后续建议

下一阶段建议优先做：

1. `ChannelTurnDispatcher`：把 `PreparedInboundMessage` 转成 `AgentSession.run_prompt()`。
2. 微信 webhook adapter：签名校验 -> payload parse -> enqueue -> dispatch -> send reply。
3. 飞书 webhook/ws adapter：event verify -> enqueue -> per-chat sequential dispatch -> send text/card。
4. 出站 text adapter：将 agent 回复通过微信/飞书 adapter 发送，并记录 `MessageReceipt`。
5. ingress queue worker：把 SQLite 队列和 channel adapter 连接起来。
6. 平台配置加载：从 env / YAML / Secret 映射到 `ChannelPlugin.config`。

这些补齐后，pyclaw 就可以从 HTTP API / CLI 扩展到微信和飞书消息入口。
