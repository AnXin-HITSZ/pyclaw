# PyClaw 前后端重构实现记录

> 日期：2026-07-14
> 概要：删除 pyclaw-web 旧版前端，根据后端接口从零重建，同时补齐后端若干缺口。

---

## 一、已完成：后端改动

### 1. Provider 改造 — 支持用户自配

| 文件 | 路径 | 改动 |
|------|------|------|
| ProviderConfigEntity | `spring-backend/.../provider/ProviderConfigEntity.java` | 新增 `ownerUserId` (String) 和 `shared` (boolean) 字段及 getter/setter |
| ProviderConfigRequest | `spring-backend/.../provider/ProviderConfigRequest.java` | 新增 `shared` 字段 |
| ProviderConfigResponse | `spring-backend/.../provider/ProviderConfigResponse.java` | 新增 `ownerUserId`、`shared` 字段（`apiKeyConfigured` 和 `apiKeyLast4` 已有掩码逻辑） |
| ProviderConfigRepository | `spring-backend/.../provider/ProviderConfigRepository.java` | 新增 `findByOwnerUserIdOrSharedTrueOrderByUpdatedAtDesc(String ownerUserId)` 查询方法 |
| **ProviderConfigService**（新） | `spring-backend/.../provider/ProviderConfigService.java` | 完整业务逻辑：<br>- `list()` / `options()` 按 owner 过滤（非管理员只看到自己的 + shared=true 的）<br>- `create()` 自动设置 `ownerUserId`，管理员可设 `shared`<br>- `update()` / `delete()` 校验 owner（非管理员只能操作自己创建的）<br>- 对齐 ClawService 的 isAdmin/actorId/audit 模式 |
| ProviderConfigController | `spring-backend/.../provider/ProviderConfigController.java` | 改用 Service 层；各端点权限增加 `provider:manage_self`；方法签名增加 `Authentication` 参数 |

**权限模型变更：**

```
provider:manage        — 管理员，管理全部 Provider
provider:manage_self   — 普通用户，管理自己的 Provider（新增）
```

**API 行为变更：**

| 接口 | 管理员 | 普通用户 |
|------|--------|----------|
| `GET /api/providers` | 全量 | 自己的 + shared=true |
| `GET /api/providers/options` | 全量 | 自己的 + shared=true |
| `POST /api/providers` | 可设 shared | 创建即 ownerUserId=自己 |
| `PUT /api/providers/{id}` | 可改全部 | 只改自己的 |
| `DELETE /api/providers/{id}` | 可删全部 | 只删自己的 |

---

### 2. Agent Config 改造 — owner 过滤 + 权限校验

| 文件 | 路径 | 改动 |
|------|------|------|
| AgentConfigRepository | `spring-backend/.../agentconfig/AgentConfigRepository.java` | 新增 `findByCreatedByOrderByUpdatedAtDesc(String createdBy)` |
| AgentConfigService | `spring-backend/.../agentconfig/AgentConfigService.java` | <br>- `list(Authentication)` — 非管理员按 `createdBy` 过滤<br>- `get(id, Authentication)` — 新增 owner 校验<br>- `update()` / `delete()` — 改用 `requireOwned()` 替代 `requireAgent()`<br>- 新增 `isAdmin()` 和 `requireOwned()` 方法 |
| AgentConfigController | `spring-backend/.../agentconfig/AgentConfigController.java` | `list()` 和 `get()` 传递 `Authentication` 参数 |

**注意：** AgentConfigEntity 已有 `createdBy` 字段（创建时写入），但此前 `list()` 不区分用户返回全量。现已对齐 Claw 行为：管理员看全量，普通用户只看自己创建的。

---

### 3. Auth — 注册默认权限补全

| 文件 | 路径 | 改动 |
|------|------|------|
| AuthService | `spring-backend/.../auth/AuthService.java` | `DEFAULT_REGISTER_AUTHORITIES` 从原来的 6 项扩展到 11 项 |

**旧值：**
```
claw:read,claw:create,claw:update,claw:delete,agent:run,agent:read,token:manage_self
```

**新值：**
```
claw:read,claw:create,claw:update,claw:delete,
agent:run,agent:read,agent:create,agent:update,
tool:catalog:read,token:manage_self,provider:manage_self
```

**新增的 4 项权限：** `agent:create`、`agent:update`、`tool:catalog:read`、`provider:manage_self`

**不包含的权限（仅管理员）：** `agent:delete`、`user:manage`、`provider:manage`、`channel:manage`、`audit:read`、`agent:route:manage`

---

### 4. 会话历史 API — Redis

| 文件 | 路径 | 改动 |
|------|------|------|
| pom.xml | `spring-backend/pom.xml` | 新增依赖 `spring-boot-starter-data-redis` + `commons-pool2` |
| **RedisConfig**（新） | `spring-backend/.../config/RedisConfig.java` | StringRedisTemplate Bean 配置 |
| **SessionSummaryResponse**（新） | `spring-backend/.../session/SessionSummaryResponse.java` | 会话摘要 record |
| **SessionMessageResponse**（新） | `spring-backend/.../session/SessionMessageResponse.java` | 单条消息 record |
| **SessionDetailResponse**（新） | `spring-backend/.../session/SessionDetailResponse.java` | 会话详情 record（meta + messages） |
| **SessionService**（新） | `spring-backend/.../session/SessionService.java` | Redis 读写：<br>- `listByClaw(clawId)` — ZREVRANGE 按 Claw 索引查<br>- `listByUser()` — ZREVRANGE 按用户索引查<br>- `getDetail(id)` — HGETALL meta + LRANGE messages<br>- `saveMessage(...)` — 双索引写入 + TTL 设置<br>- `deleteByClaw(clawId)` — Claw 删除时级联清理 |
| **SessionController**（新） | `spring-backend/.../session/SessionController.java` | `GET /api/sessions?clawId=` + `GET /api/sessions/{id}`，权限 `agent:run` |

**Redis 键结构：**

| 键模式 | 类型 | 说明 |
|--------|------|------|
| `sessions:user:{userId}` | Sorted Set | 用户维度索引（score=最后活跃时间戳） |
| `sessions:claw:{clawId}` | Sorted Set | Claw 维度索引 |
| `session:{id}:meta` | Hash | 会话元数据（userId, clawId, agentKey, provider, model, messageCount, createdAt, lastActiveAt） |
| `session:{id}:messages` | List | 消息时间线，每条为 JSON `{role, content, timestamp}` |

**TTL：** 所有 session 键 30 天自动过期。

---

## 二、已完成：前端新建

### 项目结构

```
pyclaw-web/src/
├── main.js                          # Vue 3 入口
├── App.vue                          # 根组件 + 全局 CSS 变量（深色主题）
├── router/index.js                  # 路由表 + 导航守卫（token 预检）
├── api/client.js                    # fetch 封装 HTTP 客户端（自动附带 Bearer token、401/403 处理）
├── composables/useAuth.js           # 登录态管理（login/register/logout/checkAuth/isAdmin/hasAuthority）
└── views/
    ├── WelcomePage.vue              # 欢迎页（Logo + "开启 PyClaw 之旅" 按钮）
    ├── LoginPage.vue                # 登录表单
    ├── RegisterPage.vue             # 注册表单
    ├── WorkspaceLayout.vue          # 工作台布局（侧边栏 + 顶栏，角色感知菜单）
    ├── ClawListPage.vue             # Claw 卡片列表 + 创建弹窗（选默认 Agent、飞书配置）
    ├── ClawDetailPage.vue           # Claw 详情（基本信息 + Agent 角色 + 会话历史 + 编辑弹窗）
    ├── AgentConfigPage.vue          # Agent CRUD（Provider 下拉、System Prompt、Tool Profile、Shell 审批）
    ├── ProviderPage.vue             # Provider CRUD（API Key 掩码展示、shared 共享开关）
    ├── PlaygroundPage.vue           # Agent 即时对话（Provider/Model/ToolProfile 选择 + 聊天界面）
    ├── ToolCatalogPage.vue          # 工具目录（按 Profile 分类筛选，只读表格）
    ├── TokenPage.vue                # API Token 管理（创建弹窗 + 一次性明文展示 + 撤销确认）
    ├── PodStatusPage.vue            # Pod 资源状态（前端静态 mock 数据：CPU/内存/存储进度条）
    └── admin/
        ├── ChannelPage.vue          # 微信/飞书渠道管理（Tab 切换 + 快捷配置 + JSON 编辑器）
        ├── UserManagePage.vue       # 用户管理（创建 + 禁用）
        ├── AuditLogPage.vue         # 审计日志表格
        └── UsagePage.vue            # 用量统计（总调用/成功率/Token/延迟 摘要卡片 + 明细表格）
```

### 页面路由

```
/                              → WelcomePage        （公开）
/login                         → LoginPage           （公开）
/register                      → RegisterPage        （公开）
/workspace/claws               → ClawListPage        （需登录）
/workspace/claws/:id           → ClawDetailPage      （需登录）
/workspace/agents              → AgentConfigPage     （需登录）
/workspace/providers           → ProviderPage        （需登录）
/workspace/playground          → PlaygroundPage      （需登录）
/workspace/tools               → ToolCatalogPage     （需登录）
/workspace/tokens              → TokenPage           （需登录）
/workspace/pods                → PodStatusPage       （需登录，mock 数据）
/workspace/admin/channels      → ChannelPage         （需登录 + 管理员）
/workspace/admin/users         → UserManagePage      （需登录 + 管理员）
/workspace/admin/audit         → AuditLogPage        （需登录 + 管理员）
/workspace/admin/usage         → UsagePage           （需登录 + 管理员）
```

### 技术特点

- **零额外依赖** — 仅用 `vue` + `vue-router` + `vite`（package.json 已有），不引入 Pinia/Axios/组件库
- **深色主题** — 8 个全局 CSS 变量（`--bg-primary/secondary/tertiary`、`--text-primary/secondary/muted`、`--accent`、`--border-color`）
- **角色感知** — 侧边栏菜单根据 `authorities` 中是否包含 `user:manage` 判断管理员，动态显示/隐藏管理后台入口
- **导航守卫** — 进入需登录页面时预检 token 有效性（`GET /api/auth/me`），无效则跳转登录页
- **API 安全** — 用户列表隐藏 `passwordHash`、Token 列表隐藏 `tokenHash`、Provider 列表只展示 `apiKeyLast4`
- **构建验证** — `npm run build` 62 个模块全部编译通过

---

## 三、还需手动完成的事项

### 3.1 数据库迁移

在 MySQL 中执行以下 DDL：

```sql
ALTER TABLE provider_configs ADD COLUMN owner_user_id VARCHAR(64) DEFAULT NULL;
ALTER TABLE provider_configs ADD COLUMN shared BOOLEAN NOT NULL DEFAULT FALSE;
```

**说明：**
- `owner_user_id` 为 NULL 表示由管理员创建（兼容存量数据）
- `shared` 为 TRUE 时，管理员创建的 Provider 对所有用户可见
- 存量 Provider 的这两个字段都会是 NULL/FALSE，不影响现有功能

### 3.2 Redis 部署与配置

#### 3.2.1 K3s 中部署 Redis

推荐使用 Bitnami Redis Helm Chart，或直接部署单节点 StatefulSet。关键要求：

- **持久化必须开启** — RDB + AOF 双写
- **PVC 挂载** — 存储数据文件和日志
- **Service 类型** — ClusterIP，仅集群内访问

Helm 安装示例：

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install redis bitnami/redis \
  -n pyclaw \
  --set architecture=standalone \
  --set auth.enabled=false \
  --set persistence.enabled=true \
  --set persistence.size=8Gi
```

集群内访问地址：`redis-master.pyclaw.svc.cluster.local:6379`

#### 3.2.2 Spring Boot Redis 配置

在 `spring-backend` 的 `application.properties` 或 `application-prod.properties` 中添加：

```properties
# Redis 连接（开发环境）
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Redis 连接（K3s 生产环境）
# spring.data.redis.host=redis-master.pyclaw.svc.cluster.local
# spring.data.redis.port=6379

# 连接池
spring.data.redis.lettuce.pool.max-active=16
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=4
```

如果开发环境无需 Redis（仅测试非会话相关功能），可配置：

```properties
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
```

临时跳过 Redis 自动配置（SessionController 会因缺少 Bean 而不可用，但不影响其他功能）。

### 3.3 Python 侧 Redis 写入

`openclaw/session/` 模块需要增加 Redis 写入逻辑。当前 Python 端只写本地 JSONL 文件，需要新增：

```python
# 伪代码结构
class RedisSessionStore:
    async def save_message(self, session_id, user_id, claw_id, claw_name,
                           agent_key, provider, model, role, content, timestamp):
        # 1. 追加消息到 List
        await redis.rpush(f"session:{session_id}:messages",
                          json.dumps({"role": role, "content": content, "timestamp": timestamp}))

        # 2. 更新元数据 Hash
        await redis.hset(f"session:{session_id}:meta", mapping={...})

        # 3. 更新双索引 Sorted Set
        score = timestamp / 1000.0
        await redis.zadd(f"sessions:user:{user_id}", {session_id: score})
        if claw_id:
            await redis.zadd(f"sessions:claw:{claw_id}", {session_id: score})

        # 4. 设置 TTL（30 天）
        await redis.expire(f"session:{session_id}:messages", 2592000)
        await redis.expire(f"session:{session_id}:meta", 2592000)
```

**建议：** 保留现有 JSONL 文件写入作为冷备份，Redis 作为热数据查询层。

### 3.4 部署验证步骤

1. **后端构建部署：**
   ```bash
   cd spring-backend
   mvn clean package -DskipTests
   # 构建 Docker 镜像 → 推送 ACR → helm upgrade
   ```

2. **数据库迁移：** 在 MySQL Pod 中执行 3.1 的 DDL

3. **Redis 部署：** Helm 安装 + 配置 Spring Boot 连接

4. **前端构建部署：**
   ```bash
   cd pyclaw-web
   npm run build
   # 构建 Docker 镜像 → 推送 ACR → helm upgrade
   ```

5. **验证清单：**
   - [ ] 访问 `pyclaw.anxin-hitsz.com` 显示欢迎页
   - [ ] 注册新用户后 JWT 含 `agent:create,agent:update,tool:catalog:read,provider:manage_self`
   - [ ] 普通用户创建 Provider 后只有自己能查看和编辑
   - [ ] 管理员设置 Provider shared=true 后普通用户可在下拉框看到
   - [ ] 普通用户创建 Agent 后只能看到自己的
   - [ ] 普通用户无法删除其他用户的 Agent
   - [ ] Playground 对话正常工作
   - [ ] Redis 连接正常后，会话记录出现在 ClawDetailPage

---

## 四、未实现（后续迭代）

| 功能 | 状态 | 说明 |
|------|------|------|
| Pod 资源监控 | 前端占位 | PodStatusPage 使用静态 mock 数据，后端 API 尚未实现 |
| 会话历史 Python 侧 Redis 写入 | 待实现 | 见 3.3 节 |
| 用户自助修改密码 | 未实现 | 后端无对应 API |
| 列表分页 | 未实现 | 当前所有列表接口返回全量，数据量大后需改造 |
| Refresh Token | 未实现 | JWT 过期后需重新登录 |
