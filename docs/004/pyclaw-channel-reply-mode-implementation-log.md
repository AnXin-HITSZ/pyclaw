# pyclaw Channel Reply Mode Implementation Log

This document records the implementation of configurable Channel reply modes.
Only the following two modes are supported in this change:

```text
passive_xml
async_worker
```

The `sync_worker` mode is intentionally not added.

## Background

The current WeChat official account may not have permission to use the custom service message API. The independent `pyclaw-channel-worker` can consume MySQL `ingress_queue` events and call the Agent, but WeChat may reject the outbound custom-message call with errors such as `48001 api unauthorized`.

For WeChat, the project therefore needs passive replies: pyclaw-api receives the webhook POST, calls the Agent during the same HTTP request, and returns a WeChat XML response directly. Feishu and other asynchronous platforms continue to use MySQL queue plus the independent worker.

## Modes

### passive_xml

Intended for WeChat, especially test accounts or subscription accounts without custom service message permission.

Flow:

```text
WeChat user message
  -> spring-backend webhook proxy
  -> pyclaw-api /v1/channels/wechat/webhook
  -> verify WeChat signature
  -> parse WeChat XML
  -> call Agent synchronously
  -> return passive WeChat XML in the HTTP response
```

Properties:

```text
Does not write to MySQL ingress_queue
Does not call /cgi-bin/message/custom/send
Does not depend on the independent worker to send the WeChat reply
```

Limits:

```text
Must finish within the WeChat webhook response window
If the Agent times out or fails, pyclaw-api returns fallback text
```

### async_worker

Intended for Feishu, WeChat accounts with custom-message permission, enterprise WeChat, and future asynchronous platforms.

Flow:

```text
Platform webhook
  -> pyclaw-api
  -> write MySQL ingress_queue
  -> return success immediately
  -> pyclaw-channel-worker polls and consumes the queue
  -> call Agent
  -> call the platform send API to reply to the user
```

Properties:

```text
Webhook returns quickly
Slow Agent calls do not block the platform callback
Failures can be inspected in worker logs and ingress_queue state
```

## Backend Changes

### pyclaw-api webhook route

Changed file:

```text
openclaw/channels/api_routes.py
```

New behavior:

```text
wechat + reply_mode=passive_xml:
  Verify signature, parse message, call Agent, and return XML.
  The message is not enqueued into MySQL.

wechat + reply_mode=async_worker:
  Keep the existing queue flow. Write to MySQL ingress_queue and let worker consume it.

feishu:
  Continue to use async_worker. passive_xml is rejected for Feishu.
```

In `passive_xml` mode, `ChannelTurnDispatcher(session_factory=session_factory)` is created without send adapters. It only runs the Agent and does not call the WeChat custom-message API.

### WeChat passive XML builder

Changed file:

```text
openclaw/plugins/wechat/adapter.py
```

Added function:

```text
build_wechat_passive_text_response()
```

It swaps sender and recipient according to WeChat passive-reply rules:

```text
response ToUserName   = inbound FromUserName
response FromUserName = inbound ToUserName
```

### Channel config loading

Changed file:

```text
openclaw/channels/config.py
```

Added environment variables:

```text
OPENCLAW_WECHAT_REPLY_MODE
OPENCLAW_WECHAT_PASSIVE_REPLY_TIMEOUT_SECONDS
OPENCLAW_WECHAT_PASSIVE_REPLY_FALLBACK_TEXT
OPENCLAW_FEISHU_REPLY_MODE
```

When Spring Backend dynamic config is enabled, runtime behavior should use the Channel config JSON returned by Spring Backend first.

## Frontend Changes

Changed file:

```text
pyclaw-web/src/App.vue
```

The Channel form now includes:

```text
Reply Mode
```

Available options:

```text
wechat: Passive XML, Async Worker
feishu: Async Worker
```

When saving a Channel, the frontend writes the selected mode into config JSON:

```json
{
  "reply_mode": "passive_xml"
}
```

When the user switches Channel type, the frontend normalizes invalid combinations. For example, switching from `wechat` to `feishu` changes `passive_xml` to `async_worker`.

## K3s Config Examples

Changed files:

```text
helm/pyclaw/values.yaml
pyclaw-values-k3s.example.yaml
.env.example
```

Recommended values:

```text
OPENCLAW_WECHAT_REPLY_MODE=passive_xml
OPENCLAW_WECHAT_PASSIVE_REPLY_TIMEOUT_SECONDS=4.5
OPENCLAW_FEISHU_REPLY_MODE=async_worker
```

If production uses the frontend Channel page and Spring Backend dynamic config, these environment variables mainly work as defaults or fallback values.

## Verification

Local commands:

```powershell
cd D:\project\pyclaw
py -m compileall openclaw
py -m unittest discover -s tests
cd pyclaw-web
npm run build
cd ..
git diff --check
```

ECS log commands:

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw logs deployment/pyclaw --since=10m -f
sudo /usr/local/bin/k3s kubectl -n pyclaw logs deployment/pyclaw-channel-worker --since=10m -f
```

Expected WeChat behavior in `passive_xml` mode:

```text
pyclaw-api logs show WeChat webhook POST 200
worker logs should not show new WeChat claimed ingress event
```

Expected Feishu behavior in `async_worker` mode:

```text
worker logs show claimed ingress event
worker logs show completed ingress event
```

## Notes

1. `passive_xml` is only for WeChat passive replies.
2. `async_worker` remains the recommended mode for Feishu and other asynchronous platforms.
3. This change does not add `sync_worker`.
4. If the Agent call exceeds `passive_reply_timeout_seconds`, the user receives fallback text.
5. `passive_xml` does not write MySQL queue records, so there will be no matching completed or failed `ingress_queue` row.