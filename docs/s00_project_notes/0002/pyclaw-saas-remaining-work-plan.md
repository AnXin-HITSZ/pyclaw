# PyClaw SaaS 仍需完成的工作清单

日期：2026-07-16

本文档用于承接当前已经实现的基础能力，整理“距离真正的 PyClaw SaaS 还缺什么”。目标不是再做一份宏观愿景，而是给后续实施者一份可以直接执行的剩余工作清单。

## 1. 当前已经具备的基础

当前系统已经有了这些底座：

1. 登录、注册、用户管理。
2. Claw 管理、Claw Detail、Claw 对话页、会话历史列表。
3. Provider / Secret / API Token 管理。
4. Claw 多角色 Agent 管理。
5. Python Tool Catalog 作为唯一事实来源。
6. Spring 通过 Tool Resolver Client 动态获取可用工具与提示词片段。
7. sandbox-runner 作为 Claw 专属 workspace 承载层。
8. 每个 Claw 已具备独立 Deployment / Service / PVC 的雏形。
9. Redis 会话存储链路已存在。
10. 基础的审计、统计、权限校验已有部分实现。

这意味着系统已经不是“空白项目”，而是“平台雏形已经成型，缺少 SaaS 化闭环和生产级约束”。

## 2. 真正的 SaaS 还缺什么

下面按优先级拆解。P0 是不补就不能称为 SaaS；P1 是上线后很快会撞到；P2 是增强项。

## 3. P0 必须完成

### 3.1 Claw 级 Web 对话闭环

要做什么：

1. 从 ClawDetail 进入对话页时，URL 必须携带 `clawId`。
2. 后端必须按 `clawId` 找到默认 Agent 或当前角色 Agent。
3. Agent Run 必须绑定 `clawId`、`roleKey`、`agentId`、`sessionId`。
4. 用户消息与 Agent 回复都要写入会话存储。
5. ClawDetail 要能直接看到历史会话。
6. 对话页不能再像通用 Playground 那样脱离 Claw 上下文。

完成判定：

1. 用户在某个 Claw 下发起对话后，系统能稳定回到同一个 Claw 的会话历史。
2. 刷新页面后历史还在。
3. 切换 Claw 不会串会话。
4. 默认 Agent 与角色 Agent 的选择规则稳定可解释。

建议改动位置：

1. `spring-backend/.../clawchat`
2. `spring-backend/.../session`
3. `pyclaw-web/src/views/ClawDetailPage.vue`
4. `pyclaw-web/src/views/ClawChatPage.vue`

### 3.2 租户隔离最后补齐

要做什么：

1. 每个用户固定一个 namespace，命名规则一致且可追踪。
2. 每个 Claw 在自己的 namespace 内拥有独立 sandbox-runner Deployment / Service / PVC。
3. Spring 只能操作当前用户当前 Claw 的资源。
4. 前端不能仅靠隐藏菜单，后端必须校验 owner 归属。
5. 任何按 ID 访问资源的接口都要做归属校验。

完成判定：

1. 用户 A 无法看到或访问用户 B 的 Claw、Session、Workspace、Secret。
2. 同一用户多个 Claw 互不干扰。
3. 删除 Claw 时，能按策略清理它自己的资源。

建议改动位置：

1. `spring-backend/.../claw`
2. `spring-backend/.../sandbox`
3. `spring-backend/.../session`
4. `pyclaw-web/src/router`

### 3.3 Sandbox Orchestrator 生产化

要做什么：

1. 明确一个后端组件负责创建、查询、重启、删除 Claw 的 sandbox 资源。
2. 该组件需要处理 namespace、Deployment、Service、PVC 的生命周期。
3. `active / inactive / archiving / deleting / deleted` 之类的业务状态要和实际健康状态分开。
4. 需要定义 `Claw.status` 与 `Claw.healthStatus` 的职责边界。
5. 对于健康检查失败，要给出可读的失败原因，而不是只显示 Down。

完成判定：

1. 创建 Claw 就能稳定创建对应 sandbox 资源。
2. 删除 Claw 能正确清理或保留 PVC，按策略可配置。
3. 重启 sandbox 能恢复健康。
4. 页面能区分“业务状态”与“运行健康状态”。

建议改动位置：

1. `spring-backend/.../sandbox`
2. `spring-backend/.../claw`
3. `spring-backend/helm`
4. `pyclaw-web/src/views/ClawDetailPage.vue`

### 3.4 Secret 模型彻底统一

要做什么：

1. 统一区分 ConfigMap、Secret、Provider Secret、Claw Secret、Agent Secret。
2. 不允许把敏感值放在 `env` 里误当作普通配置。
3. Helm 私有 values 只保存“引用关系”或“是否创建”，不要把真实值散落到多处。
4. 对外只展示脱敏值，不展示明文。
5. 更新 Secret 时要支持覆盖、补全、旋转和审计。

完成判定：

1. Redis 密码、数据库密码、JWT Secret、API Token 不会被 Helm upgrade 意外清空。
2. 页面只能看到掩码和元数据。
3. 当前用户只能读写自己的 Secret。

建议改动位置：

1. `spring-backend/.../secret`
2. `spring-backend/helm`
3. `pyclaw-web/src/views/SecretPage.vue`
4. `pyclaw-web/src/views/ProviderPage.vue`

### 3.5 权限和路由强校验

要做什么：

1. 前端路由只负责体验，不负责安全边界。
2. 所有资源查询、更新、删除都要做 owner 校验。
3. 管理员与普通用户的能力必须隔离。
4. 角色 Agent、Provider、Secret、Session、Workspace 都要复用同一套归属判断。
5. 对危险接口要保留 `PreAuthorize` + 业务校验双层防线。

完成判定：

1. 通过 ID 猜测资源无法越权。
2. 管理员能看全局，普通用户只能看自己的。
3. 所有 403/404 行为一致且可预期。

建议改动位置：

1. `spring-backend/.../security`
2. `spring-backend/.../claw`
3. `spring-backend/.../agentconfig`
4. `spring-backend/.../provider`
5. `spring-backend/.../secret`

### 3.6 会话历史持久化和检索

要做什么：

1. Agent Run 开始与结束都写入 session。
2. 用户消息、工具结果、Agent 回复要完整落 Redis。
3. session 必须绑定 `userId + clawId + agentId + roleKey`。
4. 删除 Claw 时要清理它的 session 索引。
5. 历史查询要能按 Claw 和角色过滤。

完成判定：

1. 刷新页面后会话不丢。
2. 切换设备或重新登录后仍可继续历史会话。
3. 会话列表按 Claw 维度正确分组。

建议改动位置：

1. `spring-backend/.../session`
2. `spring-backend/.../clawchat`
3. `pyclaw-web/src/views/ClawChatPage.vue`
4. `pyclaw-web/src/views/ClawDetailPage.vue`

## 4. P1 很重要

### 4.1 Claw 内多角色 Agent 的完整管理

要做什么：

1. 新增角色。
2. 编辑角色。
3. 删除角色。
4. 设置默认角色。
5. 设置 Mention Aliases。
6. 设置 Command Prefixes。
7. 自动同步 Route Binding。
8. 角色启用/禁用要影响对话路由。

完成判定：

1. 一个 Claw 可以稳定维护多个角色。
2. 对话路由能按角色正确落到目标 Agent。
3. 角色配置变更后立即生效或可预测生效。

### 4.2 计费、配额和限流

要做什么：

1. 每个用户最大 Claw 数。
2. 每个 Claw 最大 Agent 数。
3. 每个用户每日调用次数。
4. 每个用户 Token 消耗统计。
5. 每个 sandbox CPU / Memory / PVC 限额。
6. 超额后的降级策略。

完成判定：

1. 单个用户不能把 2c4g ECS 打满。
2. 超额时系统能明确拒绝，而不是直接崩。
3. 统计能被前端展示和审计引用。

### 4.3 运行状态和运维视图

要做什么：

1. Claw 状态列表要真实反映运行态。
2. Pod、PVC、Service、Namespace 的状态要可见。
3. 需要展示最近一次启动时间、最近错误、资源占用。
4. 健康状态与业务状态分层展示。

完成判定：

1. 用户能知道自己的 Claw 为何不可用。
2. 运维无需直接进集群查日志就能定位大部分问题。

### 4.4 审计日志

要做什么：

1. 记录谁创建了 Claw。
2. 记录谁修改了 Agent。
3. 记录谁更新了 Provider Secret。
4. 记录谁绑定了飞书群。
5. 记录谁执行了高风险工具。
6. 记录谁重启了 sandbox。

完成判定：

1. 每个关键动作可回溯到人和时间。
2. 审计结果可在前端查询。
3. 运维与安全排查能直接复用。

### 4.5 注册后的初始化流程

要做什么：

1. 新用户注册后自动进入 onboarding。
2. 引导创建第一个 Claw。
3. 引导选择角色模板。
4. 引导填写 Provider API Key。
5. 可选绑定飞书。
6. 引导直接进入 Web 对话。

完成判定：

1. 用户注册后不会卡在空白首页。
2. 首次使用路径清晰。

## 5. P2 增强项

### 5.1 飞书自有应用模式

要做什么：

1. 支持平台托管飞书应用。
2. 支持用户自有飞书应用。
3. 用户可以选择把自己的 Claw 部署到飞书。
4. 平台应明确区分“应用级配置”和“群级绑定”。

### 5.2 多 Claw / 多 Agent 协作

要做什么：

1. 支持 Agent 发现。
2. 支持 Agent 间路由。
3. 支持任务分发与协作。
4. 保留当前 Claw 边界，不让 Agent 直接突破到平台宿主机。

### 5.3 统一首页与产品化体验

要做什么：

1. 登录后展示统一首页。
2. 首页提供“开启我的 Claw”等入口。
3. 从首页进入 Claw 列表、对话页、工具目录、Secret、Provider、审计。
4. 让体验更像 SaaS，而不是一组零散管理页。

## 6. 当前最优先的 5 件事

如果只看“什么时候能算一个可演示、可写简历的 SaaS”，优先级最高的是：

1. Claw 级 Web 对话闭环。
2. 每个 Claw 的独立 workspace / sandbox runner。
3. Sandbox Orchestrator 的创建 / 删除 / 重启 / 查询闭环。
4. Secret 与 Provider 的彻底隔离。
5. 前后端 owner 权限校验补齐。

## 7. 推荐执行顺序

1. 先把 Claw 对话闭环收口，确保用户能在 Web 上真正用自己的 Claw 工作。
2. 再把租户隔离和 sandbox 生命周期做硬，保证资源边界正确。
3. 再把 Secret、Provider、权限、审计补齐，避免泄漏和越权。
4. 再做配额、限流、状态看板和 onboarding。
5. 最后再做飞书自有应用、多 Agent 协作和更完整的 SaaS 首页。

## 8. 交付标准

当且仅当以下条件同时满足时，才可以把 PyClaw 视作“真正的 SaaS 雏形”：

1. 用户能注册、登录、创建自己的 Claw。
2. 每个 Claw 有自己的运行空间和 workspace。
3. Web 对话、会话、Agent、Provider、Secret 都按 Claw / 用户隔离。
4. 所有资源访问都经过后端 owner 校验。
5. 工具与提示词由统一 Resolver 生成。
6. 审计、健康状态和错误原因可见。
7. 部署不会轻易把 Secret 覆盖为空或让旧版本静默回退。

---

本文件是“剩余工作总览”。后续实施建议优先从第 3 章开始逐项拆分执行。