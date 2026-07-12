# pyclaw 微信 / 飞书 Channel 全链路实现日志

本文记录本轮在 `docs/004` 建议项基础上补齐微信 / 飞书接入链路的实现细节。

## 2026-07-07 目标范围

1. `ChannelTurnDispatcher`：把 `PreparedInboundMessage` 转成稳定会话的 Agent 调用。
2. 微信 webhook adapter：签名校验、XML/JSON 解析、入队、分发、文本回复。
3. 飞书 webhook adapter：challenge / token / 签名校验、入队、同会话顺序处理、文本 / 卡片发送。
4. 出站 text adapter：Agent 回复通过微信 / 飞书发送，并返回 `MessageReceipt`。
5. ingress queue worker：连接 SQLite/MySQL ingress queue 与 channel adapter。
6. 平台配置加载：从 env / JSON / YAML / Secret 文件映射到 channel 插件配置。

## 2026-07-07 通用运行时补齐

新增文件：

- `openclaw/channels/config.py`：`ChannelRuntimeConfig` 与配置加载器，支持 `OPENCLAW_<CHANNEL>_CONFIG_JSON`、`OPENCLAW_<CHANNEL>_CONFIG_FILE`、`OPENCLAW_CHANNEL_CONFIG_FILE` 和平台专属 env。
- `openclaw/channels/dispatcher.py`：`ChannelTurnDispatcher`，把 `PreparedInboundMessage.text` 派发给 `AgentSession.run_prompt()`，并用平台 send adapter 发送 assistant 文本。
- `openclaw/channels/worker.py`：`IngressQueueWorker`，从 ingress queue claim 事件，调用 receive adapter 归一化，再调用 dispatcher，成功后 complete，失败后 fail。
- `openclaw/channels/http.py`：标准库 async HTTP client，供微信 / 飞书出站 API 使用，测试可注入 fake client。
- `openclaw/channels/api_routes.py`：FastAPI webhook 路由，提供 `/v1/channels/wechat/webhook` 与 `/v1/channels/feishu/webhook`。

`openclaw/api.py` 已挂载 channel router，并新增 `build_channel_agent_session()`，按 `channel + account_id + conversation_id` 生成稳定 session id。Channel 默认使用 `OPENCLAW_CHANNEL_PROVIDER` / `OPENCLAW_CHANNEL_MODEL` / `OPENCLAW_CHANNEL_TOOL_PROFILE` 等 env 控制 Agent 运行参数。

## 2026-07-07 微信 adapter

新增 `openclaw/plugins/wechat/adapter.py`：

- `build_wechat_webhook_event()`：校验 `signature/timestamp/nonce`，支持 GET URL 验证的 `echostr`，支持 POST XML/JSON 消息。
- `parse_wechat_payload()`：解析微信公众号常见 XML 与 JSON payload。
- `WeChatReceiveAdapter`：把平台 payload 转成 `PreparedInboundMessage`，默认 `conversation_id = FromUserName`。
- `WeChatTextSendAdapter`：调用微信客服文本消息 API `/cgi-bin/message/custom/send`，返回 `MessageReceipt`。支持直接配置 `access_token`，或通过 `app_id/app_secret` 获取 token。

微信 lane key：`wechat:<account_id>:<openid>`。

## 2026-07-07 飞书 adapter

新增 `openclaw/plugins/feishu/signature.py` 与 `openclaw/plugins/feishu/adapter.py`：

- `build_feishu_signature()` / `verify_feishu_signature()`：支持飞书/Lark webhook header 签名校验。
- `build_feishu_webhook_event()`：支持 challenge 响应、verification token 校验、签名校验、事件入队封装。
- `FeishuReceiveAdapter`：支持 v2 消息事件常见结构，抽取 `chat_id`、sender open_id、文本内容。
- `FeishuTextSendAdapter.text()`：调用 `/open-apis/im/v1/messages?receive_id_type=chat_id` 发送文本消息。
- `FeishuTextSendAdapter.card()`：发送 `interactive` 卡片 payload，并记录 `MessageReceipt`。

飞书 lane key：`feishu:<tenant_key>:<chat_id>`。

当前限制：飞书加密 webhook payload 会显式返回错误 `encrypted Feishu webhook payloads require decrypt support`。未加密事件、challenge、token/sign 校验、文本/卡片发送已实现。

## 2026-07-07 配置方式

微信最小 env：

```env
OPENCLAW_WECHAT_TOKEN=wechat-callback-token
OPENCLAW_WECHAT_ACCOUNT_ID=gh_xxx
OPENCLAW_WECHAT_ACCESS_TOKEN=wechat-access-token
# 或使用 OPENCLAW_WECHAT_APP_ID / OPENCLAW_WECHAT_APP_SECRET 自动换 token
```

飞书最小 env：

```env
OPENCLAW_FEISHU_VERIFICATION_TOKEN=event-token
OPENCLAW_FEISHU_SIGN_SECRET=webhook-sign-secret
OPENCLAW_FEISHU_TENANT_ACCESS_TOKEN=tenant-access-token
# 或使用 OPENCLAW_FEISHU_APP_ID / OPENCLAW_FEISHU_APP_SECRET 自动换 tenant token
```

共享 JSON 配置示例：

```json
{
  "wechat": {
    "name": "wechat-main",
    "account_id": "gh_xxx",
    "token": "wechat-callback-token",
    "access_token": "wechat-access-token"
  },
  "feishu": {
    "name": "feishu-main",
    "verification_token": "event-token",
    "sign_secret": "webhook-sign-secret",
    "tenant_access_token": "tenant-access-token"
  }
}
```

通过 `OPENCLAW_CHANNEL_CONFIG_FILE=/path/to/channels.json` 加载。

Webhook 默认仅入队并快速 ACK；设置 `OPENCLAW_CHANNEL_WEBHOOK_SYNC=true` 时，HTTP 请求内会同步 claim 一条队列消息、调用 Agent 并发送回复。生产建议运行独立 worker 处理队列。

## 2026-07-07 验证

新增测试文件：`tests/test_channel_platforms.py`。

覆盖内容：

- 微信 XML webhook 验签、解析、归一化。
- 微信文本发送 receipt。
- 飞书 webhook 签名校验、归一化。
- 飞书文本与卡片发送 receipt。
- ingress queue worker + dispatcher + send adapter 完整闭环。

已执行：

```powershell
py -m compileall openclaw tests
py -m unittest tests.test_channel_platforms
py -m unittest discover -s tests
```

结果：编译通过；`tests.test_channel_platforms` 5 个测试通过；全量 unittest `Ran 115 tests OK (skipped=4)`。