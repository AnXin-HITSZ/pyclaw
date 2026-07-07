# pyclaw MySQL Durable Ingress Queue Implementation Log

本文记录本次将 Channel Durable Ingress Queue 从 SQLite-only 扩展为 SQLite / MySQL 双后端的实现过程。

## 1. 需求来源

原频道平台文档中第 6 节写的是：

```text
SQLite Durable Ingress Queue
openclaw/channels/message/ingress_queue.py 实现 SQLiteIngressQueue
```

用户希望确认是否可以选用 MySQL，并要求补充文档和完成实现。

结论：可以使用 MySQL。SQLite 继续作为默认本地实现，MySQL 作为生产部署候选实现。

## 2. 设计决策

### 2.1 不删除 SQLite

SQLite 适合：

```text
本地开发
单机 demo
无外部依赖测试
快速验证微信 / 飞书消息链路
```

因此保留 `SQLiteIngressQueue`。

### 2.2 新增 MySQL 后端

MySQL 适合：

```text
K3s 部署
多 worker 并发 claim
长期运行
审计查询
备份恢复
生产运维
```

新增 `MySQLIngressQueue`，保持与 SQLite 相同的公开方法。

### 2.3 通过协议统一调用方

新增 `IngressQueue` Protocol，约束所有队列后端都实现：

```text
enqueue
claim_next
complete
fail
release
get
```

上层 worker 后续只依赖协议，不关心底层是 SQLite 还是 MySQL。

## 3. 代码实现记录

### 3.1 `openclaw/channels/message/ingress_queue.py`

新增：

```text
IngressQueue
MySQLIngressQueueConfig
MySQLIngressQueue
parse_mysql_dsn
validate_mysql_identifier
create_ingress_queue_from_env
```

`MySQLIngressQueue` 使用可选依赖 `pymysql`。没有安装时，只有实例化真实 MySQL 连接才会报错，不影响 SQLite 用户。

### 3.2 DSN 格式

支持：

```text
mysql://user:pass@host:3306/database
mysql+pymysql://user:pass@host:3306/database?charset=utf8mb4&table=ingress_queue
```

解析结果进入：

```python
MySQLIngressQueueConfig(
    host="...",
    port=3306,
    user="...",
    password="...",
    database="...",
    charset="utf8mb4",
    table_name="ingress_queue",
)
```

### 3.3 表名安全

`table` query 参数只允许：

```text
^[A-Za-z_][A-Za-z0-9_]*$
```

拒绝：

```text
ingress_queue;drop
../x
name-with-dash
```

这是为了避免 DSN 中的表名变成 SQL 注入入口。

### 3.4 MySQL claim 策略

`claim_next()` 使用事务读取 pending 消息：

```sql
select event_id, lane_key, payload_json
from ingress_queue
where status = 'pending'
order by created_at asc
limit 100
for update skip locked
```

然后继续检查：

```text
blocked_lane_keys
同 lane_key 是否已有 claimed 消息
```

claim 成功后写入：

```text
status = claimed
owner_id
claim_token
claimed_at
attempts = attempts + 1
updated_at
```

### 3.5 环境变量工厂

新增：

```python
create_ingress_queue_from_env()
```

支持：

```text
OPENCLAW_INGRESS_QUEUE_BACKEND=sqlite
OPENCLAW_INGRESS_QUEUE_SQLITE_PATH=chatdata/ingress_queue.db

OPENCLAW_INGRESS_QUEUE_BACKEND=mysql
OPENCLAW_INGRESS_QUEUE_DSN=mysql+pymysql://user:pass@mysql:3306/pyclaw?charset=utf8mb4&table=ingress_queue
OPENCLAW_INGRESS_QUEUE_STALE_AFTER_SECONDS=300
```

## 4. 包导出

更新：

```text
openclaw/channels/message/__init__.py
```

导出：

```text
IngressQueue
IngressQueueClaim
IngressQueueRecord
SQLiteIngressQueue
MySQLIngressQueue
MySQLIngressQueueConfig
create_ingress_queue_from_env
parse_mysql_dsn
```

## 5. 依赖配置

更新 `pyproject.toml`：

```toml
mysql = ["pymysql>=1.1.1"]
```

`all` extra 同步加入 `pymysql`。

安装方式：

```powershell
python -m pip install -e ".[mysql]"
```

或：

```powershell
python -m pip install -e ".[all]"
```

## 6. 测试记录

更新：

```text
tests/test_channels.py
```

新增测试：

```text
1. MySQL DSN 解析。
2. MySQL identifier 校验。
3. 环境变量工厂默认创建 SQLiteIngressQueue。
```

执行：

```powershell
py -m unittest tests.test_channels
py -m unittest
py -m compileall openclaw tests
```

结果：

```text
Ran 11 tests OK
Ran 105 tests OK, skipped=4
compileall OK
```

## 7. 后续建议

1. 在 K3s values 中新增 ingressQueue 配置块。
2. 在 pyclaw-api Deployment 中通过 Secret 注入 MySQL DSN。
3. 增加真实 MySQL 集成测试，验证 `FOR UPDATE SKIP LOCKED` 并发 claim。
4. 增加 channel worker，把 `create_ingress_queue_from_env()` 接入实际消息消费流程。
5. 后续如引入正式数据库迁移工具，可把 MySQL DDL 从代码迁移到 migration 文件。
## 8. 2026-07-07 收尾修正

补充实现：

```text
1. MySQL DSN 中的 charset 也走 validate_mysql_identifier() 校验。
2. openclaw.channels.message.__init__ 导出 validate_mysql_identifier。
3. openclaw.channels.__init__ 导出 Channel runtime 新 helper。
4. tests/test_channels.py 增加 Channel runtime 集成测试。
```

补充测试覆盖：

```text
parse_mysql_dsn("...?charset=utf8mb4;drop") 会抛出 ValueError。
ChannelTurnDispatcher 能完成 Agent 回复文本抽取和 send adapter 调用。
IngressQueueWorker 成功路径会把队列记录置为 completed。
```

## 9. 最终验证记录

本轮最终验证：

```powershell
py -m unittest tests.test_channels
py -m compileall openclaw tests
py -m unittest
```

结果：

```text
tests.test_channels: Ran 16 tests OK
compileall: OK
全量 unittest: Ran 110 tests OK, skipped=4
```

说明：本地没有配置真实 MySQL 服务，因此本轮覆盖的是 MySQL DSN / identifier / backend factory / SQLite 默认路径，以及 Channel runtime 和微信 / 飞书 adapter 的 fake HTTP 单元测试。真实 MySQL 的 `FOR UPDATE SKIP LOCKED` 并发行为需要后续在 K3s 或本地 MySQL 容器中补集成测试。
