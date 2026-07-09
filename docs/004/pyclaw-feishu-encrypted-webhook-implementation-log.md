# pyclaw 飞书加密 Webhook 实现记录

## 背景

2026-07-09 调试飞书事件订阅 URL 校验时，pyclaw-api 日志出现：

```text
failed Feishu webhook: error=encrypted Feishu webhook payloads require decrypt support body_shape={'json': True, 'keys': ['encrypt'], 'has_encrypt': True}
```

这说明飞书开放平台已经启用了事件加密，请求体不再是明文事件 JSON，而是：

```json
{"encrypt":"..."}
```

旧实现遇到 `encrypt` 字段会直接返回 400，因此飞书页面提示 `Challenge code 没有返回`。

## 目标

支持飞书事件配置中的加密模式，使以下场景可用：

1. URL 验证请求使用加密请求体时，pyclaw 能解密并返回 `challenge`。
2. 普通事件消息使用加密请求体时，pyclaw 能解密后继续复用现有入队和 worker 消费流程。
3. 未启用加密的明文模式保持兼容。
4. 缺少 `encrypt_key` 或密文非法时返回明确错误。

## 实现方案

实现位置：

```text
openclaw/plugins/feishu/adapter.py
```

处理顺序：

1. 先对原始请求体执行飞书签名校验。
2. 解析请求体 JSON。
3. 如果 JSON 不包含 `encrypt`，按原明文逻辑处理。
4. 如果 JSON 包含 `encrypt`：
   - 从 Channel 配置读取 `encrypt_key`。
   - 对 `encrypt` 字段做 Base64 decode。
   - 使用 `SHA256(encrypt_key)` 得到 32 字节 AES key。
   - 使用 AES-CBC 解密。优先尝试 IV 为 AES key 的前 16 字节，同时兼容 16 字节零 IV。
   - 使用 PKCS#7 去 padding。
   - 将明文重新解析为事件 JSON。
5. 解密后的 JSON 继续执行 verification token 校验、challenge 提取、事件入队。

## 配置项

飞书 Channel 配置需要包含：

```json
{
  "enabled": true,
  "reply_mode": "async_worker",
  "app_id": "<飞书 App ID>",
  "app_secret": "<飞书 App Secret>",
  "verification_token": "<飞书 Verification Token>",
  "encrypt_key": "<飞书 Encrypt Key>",
  "api_base_url": "https://open.feishu.cn"
}
```

如果同时启用签名校验，可以再配置：

```json
{
  "sign_secret": "<飞书 Sign Secret>"
}
```

注意：`verification_token`、`encrypt_key`、`sign_secret` 是不同字段，不应混填。

## 依赖变更

`pyproject.toml` 中的 `api` 和 `all` extra 新增：

```text
cryptography>=42.0.0
```

Dockerfile 当前安装 `.[all]`，因此 GitHub Actions 构建新 pyclaw-api 镜像时会自动安装解密依赖。

## 测试覆盖

新增测试文件位置：

```text
tests/test_channel_platforms.py
```

覆盖点：

1. `test_feishu_encrypted_url_verification_returns_challenge`
   - 构造飞书加密 payload。
   - 调用 `build_feishu_webhook_event()`。
   - 验证返回 `encrypted-challenge`。

2. `test_feishu_encrypted_payload_requires_encrypt_key`
   - 构造只有 `encrypt` 的请求体。
   - 不配置 `encrypt_key`。
   - 验证抛出 `Feishu encrypted webhook requires encrypt_key`。

当前本地环境如果未安装 `cryptography`，加密测试会自动 skip；容器镜像安装 `.[all]` 后会运行完整能力。

## 验证命令

本地验证：

```powershell
py -m unittest discover -s D:\project\pyclaw\tests
git -C D:\project\pyclaw diff --check
```

本次执行结果：

```text
Ran 128 tests
OK (skipped=8)
```

## 部署后验证

推送到 `main` 后，GitHub Actions 会因以下路径变更触发 pyclaw-api 自动构建部署：

```text
openclaw/**
pyproject.toml
```

ECS 上确认镜像版本：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw get deployment pyclaw \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

查看 API Pod 日志：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw get pods
sudo /usr/local/bin/k3s kubectl -n pyclaw logs pod/pyclaw-xxxxx --since=10m -f
```

飞书开放平台事件配置请求地址保持：

```text
https://api.anxin-hitsz.com/api/webhooks/feishu
```

启用加密策略后，保存事件配置。如果配置正确，飞书 URL 验证应通过，不再出现：

```text
encrypted Feishu webhook payloads require decrypt support
```

如果出现：

```text
Feishu encrypted webhook requires encrypt_key
```

说明 pyclaw Channel 配置中缺少 `encrypt_key` 或配置尚未刷新到 pyclaw-api。

如果出现：

```text
invalid encrypted Feishu webhook payload
```

说明 `encrypt_key` 与飞书开放平台当前 Encrypt Key 不一致，或密文不是当前 key 生成。
## 2026-07-09 追加修正：兼容零 IV

线上使用正确 Encrypt Key 时曾出现：

```text
'utf-8' codec can't decode byte ... body_shape={'keys': ['encrypt'], 'has_encrypt': True}
```

这说明服务已进入解密流程，但解出的首段明文不是合法 UTF-8 JSON。此类现象通常不是 URL 或数据库问题，而是 AES-CBC 的 IV 取值与平台实际请求不一致。

本次修正将飞书加密 payload 解密调整为：

1. Base64 decode `encrypt`。
2. `aes_key = SHA256(encrypt_key)`。
3. 依次尝试：
   - `iv = aes_key[:16]`
   - `iv = bytes(16)`
4. 任一方式能解出合法 JSON 即采用。
5. 全部失败时统一抛出：

```text
invalid encrypted Feishu webhook payload
```

新增测试：

```text
test_feishu_encrypted_url_verification_accepts_zero_iv
test_feishu_encrypted_payload_rejects_wrong_encrypt_key
```