# Claw 工具用户审批实现复盘：发现的不合理之处

日期：2026-07-17（初稿）/ 2026-07-17（按用户决策落地处理）
关联方案：[claw-tool-user-approval-full-implementation-plan.md](claw-tool-user-approval-full-implementation-plan.md)

本文档记录在按照《Claw 工具用户审批全量实现方案》全量实现过程中发现的不合理或可优化之处。
方案本身已严格按原文实现完毕，下列条目作为后续优化候选提出。

## 处理状态总览

| 序号 | 条目 | 状态 | 处理说明 |
|------|------|------|----------|
| 1    | `ApprovalRuntimeContext.messages_snapshot` 字段冗余 | ✅ 已处理 | 删除字段与 fallback 分支 |
| 2    | `assistant_message` 在 pending state 里始终为 `null` | ✅ 已处理 | 从 pending state schema 移除，删除 `set_assistant_snapshot` |
| 3    | `markApproved` 与 Python `resumeAgent` 之间的失败一致性 | ⏸ 暂不处理 | 用户未指示处理，保留当前行为 |
| 4    | 并发 tool_call 挂起时的孤儿 pending state | ⏸ 暂不处理 | 当前所有审批工具都是串行，不影响功能正确性 |
| 5    | `FilePendingApprovalStore` 冗余，应统一为 Redis 单后端 | ✅ 已处理 | 删除文件后端，`build_default_pending_approval_store` 改为强制 Redis |
| 6    | `FilePendingApprovalStore` 中 `expires_at == 0` 被当作"永不过期" | ✅ 自动失效 | 随第 5 条删除 `FilePendingApprovalStore` 一并失效 |
| 7    | 前端拒绝原因写死 | ✅ 已处理 | 弹窗新增 textarea，透传用户输入到 reject 请求 |
| 8    | `PyclawAgentRunResponse` 字段兼容性 | ✅ 已处理 | 系统未投产，删除所有兼容构造与兜底，`status` 成为必填字段 |

---

## 1. `ApprovalRuntimeContext.messages_snapshot` 字段冗余 ✅

方案在 `ApprovalRuntimeContext` 数据结构里保留了 `messages_snapshot` 字段，但实际写入 pending state 时
并没有用到它。

原因：messages 列表会随着 Agent loop 持续追加，如果在 `ApprovalRuntimeContext` 构造时拍快照，
拿到的只是 loop 起点的 messages，并不包含即将触发审批的 assistant tool_call 消息。
真正需要在 pending state 中保存的"恢复 loop 所需 messages"，必须在 `ApprovalToolHooks.before_tool_call`
被调用的瞬间从 `agent.state.messages` 实时读取，而不是从 context 字段读。

当前实现已经采用 `messages_snapshot_provider` 回调懒读取，`messages_snapshot` 字段实际上从未被写入。

### 已落地处理

- 删除 `ApprovalRuntimeContext.messages_snapshot` 字段（[approval.py](../../../openclaw/tools/approval.py)）。
- 删除 `approval_hooks.py` 里 `elif self.request_context.messages_snapshot:` fallback 分支。
- pending state 里的 `messages` 仅来源于 `_messages_snapshot_provider` 回调。

---

## 2. `assistant_message` 在 pending state 里始终为 `null` ✅

方案第 4.2 节给出的 pending state 内容示例包含 `"assistant_message": {}`。
但实际恢复 loop 时，`messages` 数组本身已经包含了带 `tool_calls` 的 assistant 消息，
`assistant_message` 字段属于冗余。

当前实现中 `assistant_message` 始终写入 `null`，从未被 resume 流程读取。

### 已落地处理

- pending state schema 移除 `assistant_message` 字段。
- 删除 `ApprovalToolHooks._current_assistant_snapshot` 实例属性。
- 删除 `ApprovalToolHooks.set_assistant_snapshot` 方法。
- 测试新增断言 `self.assertNotIn("assistant_message", state)` 防止回退。

---

## 3. `markApproved` 与 Python `resumeAgent` 之间的失败一致性 ⏸

方案第 11.3 条要求"approve/reject 必须幂等，重复点击不能重复执行工具"。
当前实现顺序为：

```
1. approvalService.markApproved(...)   // MySQL 置为 APPROVED
2. pyclawClient.resumeAgent(...)       // 调 Python /v1/agent/resume
3. approvalService.markConsumed(...)   // 置为 CONSUMED
```

若步骤 2 抛出 502/超时，审批单会停留在 `APPROVED` 状态，之后用户重复点击 approve 时
`requireOwnedPending` 只接受 `PENDING`，会直接 409，导致用户无法重试、也无法回滚。

### 候选方案（暂不落地，保留备忘）

- 方案 A：将 `markApproved` 延后到 Python 调用成功之后；失败时保持 PENDING，允许用户重试。
- 方案 B：在 `APPROVED` 状态下也允许 owner 重新触发 resume（带版本号/幂等键防重复执行）。
- 方案 C：增加"重试解除锁"接口，把 `APPROVED` 回滚为 `PENDING`。

推荐方案 A，改动最小且语义最清晰。**当前暂不处理，等待后续迭代。**

---

## 4. 并发 tool_call 挂起时的孤儿 pending state ⏸

方案第 11.9 条明确"第一版只允许一个 Agent loop 同时挂起一个审批"。
当前 `execute_tool_call_batch` 在并行模式下会使用 `asyncio.gather`，理论上多个 medium 风险工具
会同时创建 pending state，但 loop 只会抛出第一个 `PendingToolApprovalError`，其余的 Redis 记录
将成为孤儿（永远不会被消费，只能等 TTL 过期）。

### 当前不处理的理由

经核查，当前所有 medium 风险工具（`write_file` / `apply_patch`）的 `execution_mode` 都是 `sequential`，
所以 `can_execute_parallel` 永远返回 False，并行分支根本不会被触发。
**问题只是理论隐患，当前不影响功能正确性。**

### 候选方案（暂不落地，保留备忘）

- 在 `ApprovalToolHooks` 内增加"已挂起则短路"守卫，命中后直接返回 `DENY`。
- 在 `can_execute_parallel` 里把"含 medium/high 工具"判为不能并行。

未来新增 `execution_mode="parallel"` 且 `risk="medium"` 的工具时再处理。

---

## 5. `FilePendingApprovalStore` 冗余，应统一为 Redis 单后端 ✅

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

### 已落地处理

- 删除 `FilePendingApprovalStore` 类。
- `build_default_pending_approval_store` 改为：读 `OPENCLAW_REDIS_URL`（或 `REDIS_URL`），未配置则抛 `RuntimeError`，API 拒绝启动。
- 测试改用 `InMemoryPendingApprovalStore`（直接定义在测试文件里，不污染生产代码）：
  - [tests/test_tool_approval.py](../../../tests/test_tool_approval.py)
  - [tests/test_agent_pending_approval.py](../../../tests/test_agent_pending_approval.py)
- 测试通过 `api._set_pending_approval_store(fake)` 注入 fake，不依赖 Redis 连接。

---

## 6. `FilePendingApprovalStore` 中 `expires_at == 0` 被当作"永不过期" ✅ 自动失效

即便保留文件后端，其 TTL 校验逻辑也有瑕疵：

```python
if expires_at and expires_at < time.time():
    # treat as expired
```

`expires_at == 0` 会被短路为 falsy，从而被当作"永不过期"。

### 处理结果

随第 5 条删除 `FilePendingApprovalStore` 一并失效。**本条已自动作废，无需额外改动。**

---

## 7. 前端拒绝原因写死 ✅

方案第 8.2 节只列了"同意/拒绝"两个按钮，没有要求输入原因。改造前前端实现中拒绝时硬编码：

```js
reason: "用户在弹窗中拒绝执行该工具调用"
```

如果希望支持用户备注拒绝理由（例如"路径不对"、"内容不合适"），需要在审批弹窗里增加
一个可选的 textarea 输入框，并在 reject 请求体里把用户输入的 reason 透传给后端。

### 已落地处理

[ClawChatPage.vue](../../../pyclaw-web/src/views/ClawChatPage.vue) 改造：

- 审批弹窗新增"拒绝原因（可选）" textarea，绑定 `rejectReasonInput` ref。
- `rejectApproval` 把用户输入透传给后端：

  ```js
  const reason = rejectReasonInput.value.trim();
  const res = await api.post(`/api/claws/${cid}/chat/approvals/${approvalId}/reject`, {
    reason: reason || undefined,
  });
  ```

- `closeApprovalModal` 清空 `rejectReasonInput`，下次打开弹窗时为空。
- 用户不填 reason 时传 `undefined`，Spring 收到 `null`，Python 兜底用默认文案
  `"用户拒绝执行该工具调用"`，LLM 在 tool_result 里仍能看到拒绝信号，向后兼容。
- 用户填了 reason 时，reason 经 Spring（写 MySQL `reject_reason` 列 + 透传到 resume 请求）→
  Python（拼进 `tool_result.output` 文本和 `details.reason` 字段）→ LLM，Agent 能完整看到
  用户的拒绝理由并据此决定下一步（换路径重试、改内容重试、或放弃）。

### 链路验证

Python 侧的 reason 传递链路（`AgentResumeRequest.rejection_reason`、`PyclawAgentResumeRequest.rejectionReason`、
`ToolApprovalDecisionRequest.reason`、`tool_approval_requests.reject_reason` 列、`_build_reject_tool_result_message`）
当前代码已完全打通，前端只需要透传字段即可，无需改 Python/Spring。

---

## 8. `PyclawAgentRunResponse` 字段兼容性 ✅

新增 `status` 字段后，原来只有 `sessionId / message / text` 三字段的调用点全部需要更新。
当前实现曾提供一个 3 参兼容构造函数缓解 Java 侧调用，并在 compact constructor 里给 `status` 兜底成
`"COMPLETED"`。

### 已落地处理

由于系统尚未投入生产，不需要考虑兼容性，**已删除所有兼容代码**：

- [PyclawAgentRunResponse.java](../../../spring-backend/src/main/java/com/anxin/pyclaw/backend/pyclaw/PyclawAgentRunResponse.java)：
  删除 3 参兼容构造函数，删除 compact constructor 里的 `status` 兜底。`status` 成为必填字段。
- [ClawChatRunResponse.java](../../../spring-backend/src/main/java/com/anxin/pyclaw/backend/clawchat/ClawChatRunResponse.java)：
  删除 8 参兼容构造函数，`status` 成为必填字段。
- Python 侧 [api.py:AgentRunResponse](../../../openclaw/api.py) 已经显式传 `status`，无兼容代码需要清理。
- 所有 `new ClawChatRunResponse(...)` / `AgentRunResponse(...)` 调用点都显式传 `status` 字段。

---

## 复盘总结

| 序号 | 严重度 | 是否阻塞验收 | 处理状态 |
|------|--------|--------------|----------|
| 1    | 低     | 否           | ✅ 已处理 |
| 2    | 低     | 否           | ✅ 已处理 |
| 3    | 中     | 否（但影响用户体验） | ⏸ 暂不处理 |
| 4    | 中     | 否（当前无 parallel medium 工具） | ⏸ 暂不处理 |
| 5    | 中     | 否           | ✅ 已处理 |
| 6    | 低     | 否           | ✅ 随第 5 条自动失效 |
| 7    | 低     | 否           | ✅ 已处理 |
| 8    | 低     | 否           | ✅ 已处理 |

方案第 14 节"最终验收标准"中的 10 条均已在代码层面满足，上述条目均不阻塞当前验收。
第 3、4 条保留为后续迭代候选，待触发条件成熟时再处理。
