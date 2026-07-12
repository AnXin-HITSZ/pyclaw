# pyclaw Channel 动态配置实现记录

日期：2026-07-07

## 1. 背景

前端已经提供 Channel 配置页面，并通过 Spring Backend 的接口保存配置：

```text
GET    /api/channels
POST   /api/channels
PUT    /api/channels/{id}
DELETE /api/channels/{id}
```

这些配置会进入 Spring Backend 的数据库表 `channel_configs`。

但此前 pyclaw-api 的 webhook 运行时配置仍然来自环境变量或配置文件，例如：

```text
OPENCLAW_WECHAT_ENABLED
OPENCLAW_WECHAT_TOKEN
OPENCLAW_FEISHU_VERIFICATION_TOKEN
OPENCLAW_FEISHU_SIGN_SECRET
```

这会导致一个问题：前端页面保存配置后，pyclaw-api 并不会自动感知。若继续沿用旧方案，就需要在保存数据库后额外更新 Kubernetes Secret / ConfigMap，并 rollout restart pyclaw-api。

本次改造选择更轻的方案：

```text
前端保存配置
  -> Spring Backend 写入 MySQL
  -> pyclaw-api 收到 webhook 时向 Spring Backend 读取运行时配置
  -> 不更新 Kubernetes Secret/ConfigMap
  -> 不重启 pyclaw-api
```

## 2. 新增运行时配置链路

目标链路：

```text
WeChat / Feishu
  -> https://api.anxin-hitsz.com/api/webhooks/<platform>
  -> spring-backend webhook proxy
  -> http://pyclaw.pyclaw.svc.cluster.local:8000/v1/channels/<platform>/webhook
  -> pyclaw-api load_channel_config(platform)
  -> http://pyclaw-spring-backend.pyclaw.svc.cluster.local:8080/api/internal/channels/<platform>/runtime-config
  -> Spring Backend 查询 channel_configs
  -> pyclaw-api 使用返回配置校验签名、入队、发送回复
```

## 3. Spring Backend 改动

### 3.1 内部运行时配置接口

新增文件：

```text
spring-backend/src/main/java/com/anxin/pyclaw/backend/channel/ChannelRuntimeConfigController.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/channel/ChannelRuntimeConfigResponse.java
```

新增接口：

```text
GET /api/internal/channels/{channelType}/runtime-config?accountId=<account-id>
Authorization: Bearer <internal-token>
```

返回示例：

```json
{
  "channel": "wechat",
  "accountId": "gh_demo",
  "name": "demo-wechat",
  "enabled": true,
  "config": {
    "accountId": "gh_demo",
    "token": "wechat-token",
    "appId": "wx_app"
  }
}
```

若数据库中没有启用配置，返回：

```json
{
  "channel": "wechat",
  "accountId": "gh_demo",
  "name": null,
  "enabled": false,
  "config": {}
}
```

这样 pyclaw-api 可以稳定判断 channel disabled，而不是把“未配置”当作系统异常。

### 3.2 内部 token

新增 Spring 配置项：

```yaml
pyclaw:
  runtime:
    internal-token: ${PYCLAW_INTERNAL_API_TOKEN:${PYCLAW_API_TOKEN:}}
```

含义：

- 优先使用 `PYCLAW_INTERNAL_API_TOKEN`。
- 如果没有单独配置，则复用 `PYCLAW_API_TOKEN`。
- 内部接口必须携带 `Authorization: Bearer <token>`。

`/api/internal/channels/**` 在 Spring Security 中设置为 `permitAll`，但这不是公开放行。它只是绕过普通 JWT / API Token 认证，由 Controller 内部执行共享 token 校验。

这样做的原因是：

- 普通 `BearerAuthenticationFilter` 只识别 JWT 或 `pcat_` API Token。
- pyclaw-api 到 Spring Backend 的服务间调用更适合使用独立内部共享 token。
- Controller 自己校验 token 后，公网请求即使打到该接口，也会因为缺少内部 token 返回 401。

### 3.3 Repository 查询

`ChannelConfigRepository` 新增：

```java
List<ChannelConfigEntity> findByChannelTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc(String channelType);
```

查询策略：

1. 只查询指定平台，例如 `wechat` / `feishu`。
2. 只查询 `enabled=true` 的配置。
3. 按 `updatedAt desc` 排序，让最近更新的配置优先。
4. 如果请求带 `accountId`，先找精确匹配账号的配置。
5. 如果没有精确配置，再回退到未绑定账号的通用配置。

## 4. pyclaw-api 改动

修改文件：

```text
openclaw/channels/config.py
```

`load_channel_config(channel, account_id=...)` 新增配置源：

```text
OPENCLAW_CHANNEL_CONFIG_SOURCE=spring
```

当该变量为 `spring` 时，pyclaw-api 会调用 Spring Backend：

```text
GET ${OPENCLAW_SPRING_BACKEND_BASE_URL}/api/internal/channels/{channel}/runtime-config?accountId=...
Authorization: Bearer ${OPENCLAW_CHANNEL_CONFIG_TOKEN 或 OPENCLAW_INTERNAL_API_TOKEN 或 PYCLAW_API_TOKEN}
```

配置优先级变为：

1. `OPENCLAW_CHANNEL_CONFIG_SOURCE=spring` 时，从 Spring Backend 读取。
2. `OPENCLAW_<CHANNEL>_CONFIG_JSON`
3. `OPENCLAW_<CHANNEL>_CONFIG_FILE`
4. `OPENCLAW_CHANNEL_CONFIG_FILE`
5. 平台专属环境变量。

返回 JSON 会被转换成 `ChannelRuntimeConfig`。

例如 Spring 返回：

```json
{
  "channel": "wechat",
  "accountId": "gh_demo",
  "name": "demo-wechat",
  "enabled": true,
  "config": {
    "token": "wechat-token",
    "appId": "wx_app"
  }
}
```

pyclaw-api 内部得到：

```python
ChannelRuntimeConfig(
    channel="wechat",
    account_id="gh_demo",
    name="demo-wechat",
    enabled=True,
    config={
        "token": "wechat-token",
        "app_id": "wx_app",
        "enabled": True,
        "account_id": "gh_demo",
        "name": "demo-wechat",
    },
)
```

注意：`appId` 会规范化为 `app_id`，保持 Python 端原有 snake_case 使用习惯。

## 5. Helm 改动

修改文件：

```text
helm/pyclaw/values.yaml
spring-backend/helm/values.yaml
```

pyclaw-api 默认新增：

```yaml
OPENCLAW_CHANNEL_CONFIG_SOURCE: spring
OPENCLAW_SPRING_BACKEND_BASE_URL: http://pyclaw-spring-backend.pyclaw.svc.cluster.local:8080
OPENCLAW_CHANNEL_CONFIG_TIMEOUT_SECONDS: "3"
```

Spring Backend Secret 默认新增：

```yaml
PYCLAW_INTERNAL_API_TOKEN: ""
```

生产建议：

- 短期可以让 `PYCLAW_INTERNAL_API_TOKEN` 和 `PYCLAW_API_TOKEN` 相同。
- 后续建议拆分为两个不同 token：
  - `PYCLAW_API_TOKEN`：Spring Backend 调 pyclaw-api。
  - `PYCLAW_INTERNAL_API_TOKEN`：pyclaw-api 调 Spring Backend 内部配置接口。

## 6. 安全边界

当前安全边界如下：

```text
公网用户 / 平台 webhook
  -> 只能访问 api.anxin-hitsz.com
  -> Spring Backend 对 /api/webhooks/wechat 和 /api/webhooks/feishu permitAll
  -> 平台签名仍由 pyclaw-api 使用运行时配置校验

pyclaw-api
  -> 使用集群内 Service 地址访问 Spring Backend
  -> 必须带内部 Bearer token
  -> 只能读取 Channel 运行时配置
```

敏感配置处理：

- 当前 `channel_configs.configJson` 会保存运行时所需配置。
- 如果在前端保存微信 Token、AppSecret、飞书 Verification Token、Sign Secret，这些值会进入数据库。
- 因此数据库本身必须作为敏感资产保护。
- 后续可以继续升级为字段级加密，或让 `secretRef` 引用外部 Secret Manager。

## 7. 为什么不自动更新 Secret/ConfigMap 并 rollout

自动更新 Kubernetes Secret / ConfigMap 需要 Spring Backend 获得 Kubernetes API 权限：

```text
Spring Backend ServiceAccount
  -> RBAC 允许 update secret/configmap/deployment
  -> 保存 DB 后调用 Kubernetes API
  -> patch Secret/ConfigMap
  -> patch Deployment annotation 触发 rollout
```

这个方案的问题：

- Spring Backend 权限会明显变大。
- 每次保存配置都可能重启 pyclaw-api，影响正在处理的请求。
- Secret/ConfigMap 与数据库会出现双写一致性问题。
- 多环境部署时逻辑更复杂。

动态读取方案的优点：

- 前端保存后立即生效。
- 不需要重启 pyclaw-api。
- 不需要让 Spring Backend 管理 Kubernetes 资源。
- 配置单一来源变成 Spring Backend 数据库。

## 8. 验证方式

### 8.1 pyclaw-api 路由侧验证

部署后进入 pyclaw-api Pod：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw exec deployment/pyclaw -- printenv | grep OPENCLAW_CHANNEL_CONFIG
```

期望看到：

```text
OPENCLAW_CHANNEL_CONFIG_SOURCE=spring
OPENCLAW_CHANNEL_CONFIG_TIMEOUT_SECONDS=3
```

### 8.2 Spring 内部配置接口验证

从 pyclaw-api Pod 内访问：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw exec deployment/pyclaw -- sh -c '
python -c "import os,urllib.request; token=os.environ.get(\"PYCLAW_API_TOKEN\",\"\"); req=urllib.request.Request(\"http://pyclaw-spring-backend.pyclaw.svc.cluster.local:8080/api/internal/channels/wechat/runtime-config\", headers={\"Authorization\":\"Bearer \"+token}); print(urllib.request.urlopen(req).read().decode())"
'
```

未启用时可能返回：

```json
{"channel":"wechat","accountId":null,"name":null,"enabled":false,"config":{}}
```

### 8.3 公网 webhook 验证

```bash
curl -k -i "https://api.anxin-hitsz.com/api/webhooks/wechat?signature=test&timestamp=1&nonce=1&echostr=hello"
```

如果 Channel 未启用，应看到 pyclaw-api 返回的 disabled 信息。

如果前端已保存启用配置，且签名参数不正确，应看到签名校验失败相关响应。

## 9. 本次测试覆盖

Python：

```text
tests/test_channels.py
```

新增覆盖：

- `OPENCLAW_CHANNEL_CONFIG_SOURCE=spring` 时会调用 Spring Backend。
- `Authorization` 使用内部 token。
- `appId` 会规范化为 `app_id`。
- disabled 响应会转换为 `ChannelRuntimeConfig(enabled=False)`。

Spring：

```text
spring-backend/src/test/java/com/anxin/pyclaw/backend/SecuritySmokeTest.java
```

新增覆盖：

- 内部配置接口缺少 token 返回 401。
- 写入启用的 Channel 配置后，内部接口可返回运行时配置。