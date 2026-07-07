# pyclaw Ingress Queue MySQL-only 迁移记录

本文记录本轮将 Channel Durable Ingress Queue 从 SQLite / MySQL 双后端收敛为 MySQL-only 的实现细节。

## 1. 变更目标

当前 Channel webhook 需要可靠的入站消息队列。此前实现中，本地默认使用 SQLite，生产可以切换 MySQL；本轮改造后不再提供队列后端选择，MySQL 是唯一持久化 ingress queue 后端。

这样做的直接效果：

- 运行时代码不再导入或实例化 SQLite 队列。
- `OPENCLAW_INGRESS_QUEUE_BACKEND` 被删除。
- `OPENCLAW_INGRESS_QUEUE_SQLITE_PATH` 被删除。
- `OPENCLAW_INGRESS_QUEUE_DSN` 成为生产队列必填配置。
- 单元测试不依赖真实 MySQL，改为注入内存 fake queue 验证队列语义。

## 2. 运行时代码调整

### 2.1 删除 SQLiteIngressQueue

文件：`openclaw/channels/message/ingress_queue.py`

删除内容：

- `sqlite3` 导入。
- `Path` 导入。
- `SQLiteIngressQueue` 类。
- 基于本地文件路径自动创建队列的逻辑。

保留内容：

- `IngressQueue` 协议。
- `IngressQueueRecord` / `IngressQueueClaim` 数据结构。
- `MySQLIngressQueueConfig`。
- `MySQLIngressQueue`。
- `parse_mysql_dsn()`。
- `validate_mysql_identifier()`。
- `create_ingress_queue_from_env()`。

### 2.2 create_ingress_queue_from_env 改为 MySQL-only

现在工厂函数只读取：

```text
OPENCLAW_INGRESS_QUEUE_DSN
OPENCLAW_INGRESS_QUEUE_STALE_AFTER_SECONDS
```

如果没有配置 `OPENCLAW_INGRESS_QUEUE_DSN`，会直接抛出 `ValueError`：

```text
OPENCLAW_INGRESS_QUEUE_DSN is required; pyclaw ingress queue uses MySQL only
```

这表示：只要某条运行路径需要真实 Channel ingress queue，就必须提供 MySQL DSN。

## 3. 配置调整

### 3.1 .env.example

删除旧变量：

```text
OPENCLAW_INGRESS_QUEUE_BACKEND
OPENCLAW_INGRESS_QUEUE_SQLITE_PATH
```

新增 MySQL-only 示例：

```text
OPENCLAW_INGRESS_QUEUE_DSN=mysql+pymysql://pyclaw:CHANGE_ME@pyclaw-mysql:3306/pyclaw?charset=utf8mb4&table=ingress_queue
OPENCLAW_INGRESS_QUEUE_STALE_AFTER_SECONDS=300
```

本地如果要测试微信 / 飞书 webhook，也需要准备一个 MySQL 服务，并在 `.env` 中填入真实 DSN。

### 3.2 Helm values

文件：`helm/pyclaw/values.yaml`

`ingressQueue` 不再包含 `backend` 或本地文件路径：

```yaml
ingressQueue:
  enabled: true
  staleAfterSeconds: 300
  mysql:
    dsnSecretName: ""
    dsnSecretKey: OPENCLAW_INGRESS_QUEUE_DSN
```

### 3.3 Helm ConfigMap

文件：`helm/pyclaw/templates/configmap.yaml`

ConfigMap 只注入非敏感的超时配置：

```text
OPENCLAW_INGRESS_QUEUE_STALE_AFTER_SECONDS
```

DSN 不进入 ConfigMap，因为 DSN 通常包含用户名、密码、数据库地址和表名。

### 3.4 Helm Deployment

文件：`helm/pyclaw/templates/deployment.yaml`

当配置了：

```yaml
ingressQueue:
  mysql:
    dsnSecretName: pyclaw-ingress-queue-secret
    dsnSecretKey: OPENCLAW_INGRESS_QUEUE_DSN
```

Deployment 会从指定 Kubernetes Secret 注入：

```text
OPENCLAW_INGRESS_QUEUE_DSN
```

根目录 override 文件 `pyclaw-api-ingressqueue-mysql-values-k3s.yaml` 也同步删除了 `backend: mysql`，因为 backend 字段已经没有意义。

## 4. 测试调整

SQLite 删除后，测试不应该为了验证队列行为而依赖真实 MySQL。当前测试采用 `MemoryIngressQueue`：

- 验证幂等 enqueue。
- 验证 claim / complete / fail / release。
- 验证 lane 串行处理。
- 验证 stale claim 释放。
- 验证 `claim_next(channel=...)` 只领取指定平台消息。
- 验证 worker 可以从队列领取 raw event、prepare、dispatch 并 complete。

MySQL 相关测试只覆盖不需要真实数据库连接的部分：

- DSN 解析。
- MySQL identifier 校验。
- `create_ingress_queue_from_env(init_schema=False)` 能创建 `MySQLIngressQueue`。
- 缺少 `OPENCLAW_INGRESS_QUEUE_DSN` 时会报错。

真实 MySQL 的并发锁语义，例如 `FOR UPDATE SKIP LOCKED`，仍建议放到 K3s 或 Docker MySQL 集成测试中验证。

## 5. 部署影响

K3s 中 pyclaw-api 必须能拿到 MySQL DSN Secret。示例：

```bash
kubectl -n pyclaw create secret generic pyclaw-ingress-queue-secret \
  --from-literal=OPENCLAW_INGRESS_QUEUE_DSN='mysql+pymysql://pyclaw:<password>@pyclaw-mysql:3306/pyclaw?charset=utf8mb4&table=ingress_queue'
```

然后 Helm values 引用该 Secret：

```yaml
ingressQueue:
  enabled: true
  staleAfterSeconds: 300
  mysql:
    dsnSecretName: pyclaw-ingress-queue-secret
    dsnSecretKey: OPENCLAW_INGRESS_QUEUE_DSN
```

如果没有该 Secret，普通 `/v1/agent/run` 不一定受影响；但 Channel webhook 或 worker 一旦调用 `create_ingress_queue_from_env()`，就会因为缺少 DSN 失败。

## 6. 历史文档说明

`docs/004` 中早期 Channel 实现记录可能仍会描述 SQLite 作为当时的阶段性方案。以本文为当前版本准则：从本轮开始，pyclaw Channel durable ingress queue 只采用 MySQL。

## 7. 本轮验证

本轮执行的验证命令：

```bash
py -m compileall openclaw tests
py -m unittest tests.test_channels tests.test_channel_platforms
```

全量测试已执行通过：

```bash
py -m unittest
# Ran 120 tests in 1.347s
# OK (skipped=5)
```