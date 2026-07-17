# Claw 工具用户审批实现复盘：发现的不合理之处

日期：2026-07-17
关联方案：[claw-tool-user-approval-full-implementation-plan.md](claw-tool-user-approval-full-implementation-plan.md)

本文档记录在按照《Claw 工具用户审批全量实现方案》全量实现过程中发现的不合理或可优化之处。
方案本身已严格按原文实现完毕，下列条目仅作为后续优化候选，等待评审决定是否执行。

## 1. `ApprovalRuntimeContext.messages_snapshot` 字段冗余

方案在 `ApprovalRuntimeContext` 数据结构里保留了 `messages_snapshot` 字段，但实际写入 pending state 时
并没有用到它。

原因：messages 列表会随着 Agent loop 持续追加，如果在 `ApprovalRuntimeContext` 构造时拍快照，
拿到的只是 loop 起点的 messages，并不包含即将触发审批的 assistant tool_call 消息。
真正需要在 pending state 中保存的"恢复 loop 所需 messages"，必须在 `ApprovalToolHooks.before_tool_call`
被调用的瞬间从 `agent.state.messages` 实时读取，而不是从 context 字段读。

当前实现已经采用 `messages_snapshot_provider` 回调懒读取，`messages_snapshot` 字段实际上从未被写入。

建议：删除 `ApprovalRuntimeContext.messages_snapshot` 字段，仅保留 `messages_snapshot_provider` 回调机制。

## 2. `assistant_message` 在 pending state 里始终为 `null`

方案第 4.2 节给出的 pending state 内容示例包含 `"assistant_message": {}`。
但实际恢复 loop 时，`messages` 数组本身已经包含了带 `tool_calls` 的 assistant 消息，
`assistant_message` 字段属于冗余。

当前实现中 `assistant_message` 始终写入 `null`，从未被 resume 流程读取。

建议：从 pending state schema 中移除 `assistant_message` 字段，简化序列化结构。

## 3. `markApproved` 与 Python `resumeAgent` 之间的失败一致性

方案第 11.3 条要求"approve/reject 必须幂等，重复点击不能重复执行工具"。
当前实现顺序为：

```
1. approvalService.markApproved(...)   // MySQL 置为 APPROVED
2. pyclawClient.resumeAgent(...)       // 调 Python /v1/agent/resume
3. approvalService.markConsumed(...)   // 置为 CONSUMED
```

若步骤 2 抛出 502/超时，审批单会停留在 `APPROVED` 状态，之后用户重复点击 approve 时
`requireOwnedPending` 只接受 `PENDING`，会直接 409，导致用户无法重试、也无法回滚。

建议：
- 方案 A：将 `markApproved` 延后到 Python 调用成功之后；失败时保持 PENDING，允许用户重试。
- 方案 B：在 `APPROVED` 状态下也允许 owner 重新触发 resume（带版本号/幂等键防重复执行）。
- 方案 C：增加一个"重试解除锁"接口，把 `APPROVED` 回滚为 `PENDING`。

推荐方案 A，改动最小且语义最清晰。

## 4. 并发 tool_call 挂起时的孤儿 pending state

方案第 11.9 条明确"第一版只允许一个 Agent loop 同时挂起一个审批"。
当前 `execute_tool_call_batch` 在并行模式下会使用 `asyncio.gather`，理论上多个 medium 风险工具
会同时创建 pending state，但 loop 只会抛出第一个 `PendingToolApprovalError`，其余的 Redis 记录
将成为孤儿（永远不会被消费，只能等 TTL 过期）。

当前所有 medium 风险工具（write_file / apply_patch）的 `execution_mode` 都是 `sequential`，
所以暂时不会触发该问题。但一旦未来新增 `parallel` 的 medium 工具，就会暴露这个隐患。

建议：在 `ApprovalToolHooks` 内增加"已挂起则短路"的守卫，命中后直接返回 `DENY`
（理由："本轮已有审批挂起，请先处理"），避免产生孤儿 pending state。

## 5. `FilePendingApprovalStore` 冗余，应统一为 Redis 单后端

方案第 6.4 节原文：

> 第一版可用文件/Redis 二选一，但生产目标应使用 Redis。
>
> 如果 Python runtime 当前没有 Redis 客户端依赖，第一版可先使用本地 JSON 文件作为开发实现，
> 但部署方案必须改为 Redis。

实际实现里保留了两套后端：`RedisPendingApprovalStore`（生产）与 `FilePendingApprovalStore`
（开发/测试 fallback）。这带来如下问题：

- 两套实现需要同步维护接口、TTL 语义、序列化格式，长期维护成本翻倍。
- `FilePendingApprovalStore` 的 TTL 是懒校验，与 Redis 原生 TTL 语义不一致，容易在测试中产生
  与生产行为不同的微妙 bug。
- 单测里也完全可以直接用 in-memory fake 实现 `PendingApprovalStore` Protocol，并不需要文件后端。

建议：
- 删除 `FilePendingApprovalStore`，只保留 `RedisPendingApprovalStore`。
- `build_default_pending_approval_store` 简化为"读 `OPENCLAW_REDIS_URL`，未配置则抛出启动错误"。
- 测试通过注入 fake `PendingApprovalStore`（例如基于 `dict` 的内存实现）覆盖，不再依赖文件后端。

## 6. `FilePendingApprovalStore` 中 `expires_at == 0` 被当作"永不过期"

即便保留文件后端，其 TTL 校验逻辑也有瑕疵：

```python
if expires_at and expires_at < time.time():
    # treat as expired
```

`expires_at == 0` 会被短路为 falsy，从而被当作"永不过期"。虽然实际写入永远是 `time.time() + ttl`，
不会出现 0 值，但语义上更保险的做法是把 0 也当作立即过期。

若按第 5 条删除文件后端，本条自动失效。

## 7. 前端拒绝原因写死

方案第 8.2 节只列了"同意/拒绝"两个按钮，没有要求输入原因。当前前端实现中拒绝时硬编码：

```js
reason: "用户在弹窗中拒绝执行该工具调用"
```

如果希望支持用户备注拒绝理由（例如"路径不对"、"内容不合适"），需要在审批弹窗里增加
一个可选的 textarea 输入框，并在 reject 请求体里把用户输入的 reason 透传给后端。

建议：在审批弹窗中增加可选"拒绝原因"输入框，前端把用户输入透传；后端已有的
`ToolApprovalDecisionRequest.reason` 字段无需改动。

## 8. `PyclawAgentRunResponse` 字段兼容性

新增 `status` 字段后，原来只有 `sessionId / message / text` 三字段的调用点全部需要更新。
当前提供了一个 3 参兼容构造函数缓解 Java 侧调用，但 JSON 反序列化时旧客户端/旧前端
可能仍按旧字段结构解析，导致 `status` 字段被忽略、`approval` 字段被丢弃。

如果需要严格的向后兼容，建议：
- Spring 出口保留旧字段结构不变（`sessionId / message / text` 顶层字段）。
- 仅在新增的 `approval` 字段上扩展，并新增 `status` 作为可选字段（默认 `COMPLETED`）。
- 前端按 `status` 是否存在做兼容判断（`status == null` 视为 `COMPLETED`）。

当前前端实现已经按 `res.status === "PENDING_APPROVAL"` 判断，对旧响应（无 status）会走 else 分支
显示 `res.text`，基本兼容；但严格起见仍建议在协议层固化向后兼容约定。

## 复盘总结

| 序号 | 严重度 | 是否阻塞验收 | 建议处理时机 |
|------|--------|--------------|--------------|
| 1    | 低     | 否           | 任意时间清理 |
| 2    | 低     | 否           | 任意时间清理 |
| 3    | 中     | 否（但影响用户体验） | 下一迭代 |
| 4    | 中     | 否（当前无 parallel medium 工具） | 新增 parallel medium 工具前必须处理 |
| 5    | 中     | 否           | 下一迭代（减少维护负担） |
| 6    | 低     | 否           | 随第 5 条一并处理 |
| 7    | 低     | 否           | 视产品需求决定 |
| 8    | 低     | 否           | 视客户端兼容性策略决定 |

方案第 14 节"最终验收标准"中的 10 条均已在代码层面满足，上述条目均不阻塞当前验收。
