# pyclaw Channel 配置规范化与修复记录

本文记录本次对微信 / 飞书 Channel 接入的修复，以及运行时配置的统一规范。目标是让本地 `.env`、Helm values、K3s ConfigMap/Secret 和 Python 配置加载逻辑保持同一套变量命名。

## 1. 本次修复范围

### 1.1 IngressQueue 按 channel 领取

问题：同步 webhook worker 在同一个队列中调用 `claim_next()` 时，可能领取到其他平台的消息。例如微信 webhook 同步处理时，队列里如果更早存在飞书消息，微信 worker 会先拿到飞书消息并因为没有对应 receive adapter 而失败。

修复：

```text
IngressQueue.claim_next(owner_id, channel=None)
SQLiteIngressQueue.claim_next(..., channel=None)
MySQLIngressQueue.claim_next(..., channel=None)
IngressQueueWorker(channel="wechat" / "feishu")
```

当 worker 带有 `channel` 时，SQLite 查询追加：

```sql
and channel = ?
```

MySQL 查询追加：

```sql
and channel = %s
```

这样微信 webhook 只会同步领取微信消息，飞书 webhook 只会同步领取飞书消息。

### 1.2 worker 缺失 time import

问题：`raw_event_from_claim()` 在历史消息缺少 `received_at` 时会回退到 `time.time()`，但 `worker.py` 没有导入 `time`。

修复：在 `openclaw/channels/worker.py` 增加：

```python
import time
```

### 1.3 飞书 v2 header 字段解析

问题：飞书 v2 callback 的关键字段在 `payload["header"]` 下，例如：

```json
{
  "schema": "2.0",
  "header": {
    "event_id": "...",
    "tenant_key": "...",
    "token": "..."
  },
  "event": {...}
}
```

原实现主要读取顶层 `event_id`、`tenant_key`、`token`，会导致 v2 payload 下事件 ID、租户 ID、verification token 判断不稳定。

修复：新增内部 helper：

```text
_feishu_header(payload)
_feishu_event_id(payload, message)
_feishu_tenant_key(payload, config)
```

读取优先级：

```text
header.event_id -> payload.event_id -> payload.uuid -> message.message_id -> fallback
header.tenant_key -> payload.tenant_key -> payload.tenantKey -> config.account_id
header.token -> payload.token
```

### 1.4 channel enabled 开关生效

问题：`OPENCLAW_WECHAT_ENABLED` / `OPENCLAW_FEISHU_ENABLED` 已经被配置加载器读取，但 webhook route 没有真正拒绝 disabled channel。

修复：在 webhook route 中调用：

```python
_ensure_channel_enabled(config)
```

当 `enabled=False` 时返回 404，避免未启用平台的 webhook 被误调用。

### 1.5 Channel Agent 配置集中加载

问题：`openclaw/api.py` 和 `openclaw/channels/api_routes.py` 分散读取 `OPENCLAW_CHANNEL_*` 环境变量，变量入口不够清晰。

修复：在 `openclaw/channels/config.py` 新增：

```python
@dataclass(frozen=True)
class ChannelAgentConfig:
    provider: str = "openai"
    model: str | None = None
    system: str | None = None
    api_mode: str = "auto"
    chatdata_dir: str | None = None
    tool_profile: str = "messaging"
    shell_approval: str = "deny"
    webhook_sync: bool = False


def load_channel_agent_config() -> ChannelAgentConfig:
    ...
```

后续 Channel Agent 相关运行时配置统一从这个函数读取。

## 2. 配置分层规则

### 2.1 本地 `.env`

本地开发可以把所有配置写入 `.env`。`.env.example` 只保留变量名和示例，不放真实密钥。

### 2.2 K3s ConfigMap

非敏感配置放 ConfigMap，也就是 Helm values 的 `env`：

```yaml
env:
  OPENCLAW_CHATDATA_DIR: /app/chatdata
  OPENAI_MODEL: gpt-4.1-mini
  OPENAI_API_MODE: responses
  OPENCLAW_CHANNEL_PROVIDER: openai
  OPENCLAW_CHANNEL_API_MODE: auto
  OPENCLAW_CHANNEL_TOOL_PROFILE: messaging
  OPENCLAW_CHANNEL_SHELL_APPROVAL: deny
  OPENCLAW_CHANNEL_WEBHOOK_SYNC: "false"
  OPENCLAW_WECHAT_ENABLED: "false"
  OPENCLAW_WECHAT_NAME: wechat
  OPENCLAW_FEISHU_ENABLED: "false"
  OPENCLAW_FEISHU_NAME: feishu
```

### 2.3 K3s Secret

敏感配置放 Secret，也就是 Helm values 的 `secret.values` 或已有 Secret：

```yaml
secret:
  create: true
  values:
    OPENAI_API_KEY: ""
    OPENAI_BASE_URL: ""
    PYCLAW_API_TOKEN: ""
    OPENCLAW_WECHAT_TOKEN: ""
    OPENCLAW_WECHAT_APP_SECRET: ""
    OPENCLAW_FEISHU_APP_SECRET: ""
    OPENCLAW_FEISHU_VERIFICATION_TOKEN: ""
    OPENCLAW_FEISHU_SIGN_SECRET: ""
```

### 2.4 DSN Secret

DSN Secret 是一个 Kubernetes Secret，用来保存数据库连接字符串。当前 MySQL ingress queue 使用：

```text
OPENCLAW_INGRESS_QUEUE_DSN
```

示例值：

```text
mysql+pymysql://pyclaw:<password>@pyclaw-mysql:3306/pyclaw?charset=utf8mb4&table=ingress_queue
```

不建议把 DSN 写进 ConfigMap，因为它包含用户名、密码、数据库地址和表名。

## 3. 完整配置项清单

### 3.1 pyclaw API 基础配置

| 变量 | 类型 | 建议位置 | 说明 |
| --- | --- | --- | --- |
| `OPENCLAW_ENV_FILE` | string | ConfigMap / 本地环境 | 指定额外加载的 env 文件，默认 `.env`。K3s 中通常不需要设置。 |
| `OPENCLAW_CHATDATA_DIR` | path | ConfigMap | transcript、session store 等运行数据目录。 |
| `PYCLAW_API_TOKEN` | secret | Secret | 保护 `/v1/agent/run` 的 Bearer token。 |

### 3.2 LLM 配置

| 变量 | 类型 | 建议位置 | 说明 |
| --- | --- | --- | --- |
| `OPENAI_API_KEY` | secret | Secret | LLM API Key。即使使用 OpenAI-compatible 网关，也沿用此变量。 |
| `OPENAI_BASE_URL` | secret/string | Secret | OpenAI-compatible 网关地址。 |
| `OPENAI_ORGANIZATION` | secret/string | Secret | OpenAI 官方组织 ID，可选。 |
| `OPENAI_PROJECT` | secret/string | Secret | OpenAI 官方 project，可选。 |
| `OPENAI_MODEL` | string | ConfigMap | 默认模型。 |
| `OPENAI_API_MODE` | enum | ConfigMap | `responses` 或 `chat_completions`。OpenAI-compatible 网关通常用 `chat_completions`。 |

### 3.3 Channel Agent 配置

| 变量 | 类型 | 建议位置 | 说明 |
| --- | --- | --- | --- |
| `OPENCLAW_CHANNEL_PROVIDER` | enum | ConfigMap | webhook 触发 Agent 时使用的 provider，通常为 `openai`。 |
| `OPENCLAW_CHANNEL_MODEL` | string | ConfigMap | 渠道 Agent 专用模型；为空时回退到 `OPENAI_MODEL`。 |
| `OPENCLAW_CHANNEL_SYSTEM` | string | ConfigMap/Secret | 渠道 Agent 专用 system prompt。内容敏感时放 Secret。 |
| `OPENCLAW_CHANNEL_API_MODE` | enum | ConfigMap | 渠道 Agent API 模式，默认 `auto`。 |
| `OPENCLAW_CHANNEL_CHATDATA_DIR` | path | ConfigMap | 渠道会话数据目录；为空时使用 `OPENCLAW_CHATDATA_DIR`。 |
| `OPENCLAW_CHANNEL_TOOL_PROFILE` | enum | ConfigMap | 渠道 Agent 工具集，默认 `messaging`。 |
| `OPENCLAW_CHANNEL_SHELL_APPROVAL` | enum | ConfigMap | shell 工具审批模式，默认 `deny`。 |
| `OPENCLAW_CHANNEL_WEBHOOK_SYNC` | bool | ConfigMap | 是否在 webhook 请求内同步处理队列。生产建议为 `false`，由 worker 异步消费。 |

### 3.4 Ingress Queue 配置

| 变量 | 类型 | 建议位置 | 说明 |
| --- | --- | --- | --- |
| `OPENCLAW_INGRESS_QUEUE_BACKEND` | enum | ConfigMap | `sqlite` 或 `mysql`。 |
| `OPENCLAW_INGRESS_QUEUE_SQLITE_PATH` | path | ConfigMap | SQLite 队列文件路径。 |
| `OPENCLAW_INGRESS_QUEUE_DSN` | secret | Secret | MySQL 队列 DSN。 |
| `OPENCLAW_INGRESS_QUEUE_STALE_AFTER_SECONDS` | number | ConfigMap | claimed 消息超时释放时间。 |

### 3.5 微信配置

| 变量 | 类型 | 建议位置 | 说明 |
| --- | --- | --- | --- |
| `OPENCLAW_WECHAT_ENABLED` | bool | ConfigMap | 是否启用微信 webhook。 |
| `OPENCLAW_WECHAT_NAME` | string | ConfigMap | 平台显示名。 |
| `OPENCLAW_WECHAT_ACCOUNT_ID` | string | Secret/ConfigMap | 平台账号标识。 |
| `OPENCLAW_WECHAT_TOKEN` | secret | Secret | 微信 webhook token，用于签名校验。 |
| `OPENCLAW_WECHAT_APP_ID` | secret | Secret | 微信 AppID。 |
| `OPENCLAW_WECHAT_APP_SECRET` | secret | Secret | 微信 AppSecret。 |
| `OPENCLAW_WECHAT_ACCESS_TOKEN` | secret | Secret | 可选，直接配置 access token；未配置时可通过 AppID/AppSecret 获取。 |
| `OPENCLAW_WECHAT_API_BASE_URL` | string | Secret/ConfigMap | 微信 API base URL，默认 `https://api.weixin.qq.com`。 |

### 3.6 飞书配置

| 变量 | 类型 | 建议位置 | 说明 |
| --- | --- | --- | --- |
| `OPENCLAW_FEISHU_ENABLED` | bool | ConfigMap | 是否启用飞书 webhook。 |
| `OPENCLAW_FEISHU_NAME` | string | ConfigMap | 平台显示名。 |
| `OPENCLAW_FEISHU_ACCOUNT_ID` | string | Secret/ConfigMap | 默认 tenant/account 标识。 |
| `OPENCLAW_FEISHU_APP_ID` | secret | Secret | 飞书 App ID。 |
| `OPENCLAW_FEISHU_APP_SECRET` | secret | Secret | 飞书 App Secret。 |
| `OPENCLAW_FEISHU_VERIFICATION_TOKEN` | secret | Secret | 飞书 verification token。 |
| `OPENCLAW_FEISHU_SIGN_SECRET` | secret | Secret | 飞书签名密钥。 |
| `OPENCLAW_FEISHU_ENCRYPT_KEY` | secret | Secret | 预留字段；当前代码会拒绝 encrypted payload，尚未实现解密。 |
| `OPENCLAW_FEISHU_TENANT_ACCESS_TOKEN` | secret | Secret | 可选，直接配置 tenant token；未配置时通过 AppID/AppSecret 获取。 |
| `OPENCLAW_FEISHU_API_BASE_URL` | string | Secret/ConfigMap | 飞书 API base URL，默认 `https://open.feishu.cn`。 |

## 4. webhook 鉴权边界

当前 `/v1/channels/wechat/webhook` 和 `/v1/channels/feishu/webhook` 不使用 `PYCLAW_API_TOKEN`，而是依赖平台签名 / verification token：

```text
微信：signature + timestamp + nonce + token
飞书：sign_secret header 校验 + verification token
```

原因：平台 webhook 通常无法额外携带 pyclaw 自定义 Bearer token，或者配置入口不稳定。因此平台入口应以平台签名为主要鉴权方式。

后续如果需要进一步收紧，可以增加可选配置：

```text
OPENCLAW_CHANNEL_WEBHOOK_REQUIRE_API_TOKEN=true
```

但这需要确认微信 / 飞书配置侧能稳定附加对应请求头，否则会导致平台无法回调。

## 5. K3s 推荐配置方式

生产推荐：

```text
ConfigMap:
  非敏感开关、模型名、模式、profile、路径、超时时间

Secret:
  API Key、平台 token、App Secret、签名密钥、数据库 DSN

独立 DSN Secret:
  OPENCLAW_INGRESS_QUEUE_DSN
```

MySQL queue 的 Helm values 应只引用 Secret 名和 key：

```yaml
ingressQueue:
  enabled: true
  backend: mysql
  mysql:
    dsnSecretName: pyclaw-ingress-queue-secret
    dsnSecretKey: OPENCLAW_INGRESS_QUEUE_DSN
```

## 6. 本次验证

执行：

```powershell
py -m compileall openclaw tests
py -m unittest tests.test_channels tests.test_channel_platforms
```

结果：

```text
compileall OK
Channel 相关测试 OK，1 个 FastAPI 可选依赖测试在未安装 api extra 时跳过
```