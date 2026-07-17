# Sandbox-Only 工具严格删除与部署加固记录

> 日期：2026-07-17
> 范围：Python OpenClaw runtime、Spring Backend、pyclaw-web、Helm values、GitHub Actions
> 目标：严格删除旧 fs/shell/web 工具链路，不保留旧名称兼容；同时处理 shellApproval 旧数据库列风险，并加固 Spring Backend 自动部署流程。

## 1. 背景

PyClaw 已经转向 SaaS 形态下的 Claw sandbox 模型。用户工具的最终边界是：

```text
Agent 工具只能操作当前用户、当前 Claw 的 sandbox-runner workspace，不能感知或操作平台宿主机，也不能操作 pyclaw-api Pod 的本地文件系统。
```

因此旧版本地工具链路需要彻底下线：

- openclaw/tools/fs：旧本地文件读写、grep、find、edit、apply_patch。
- openclaw/tools/shell：旧 shell/exec 命令执行、shell guard、shell approval。
- openclaw/tools/web：旧 web_fetch/web_search，执行位置在 Python API Pod，不符合当前 Claw sandbox 边界。
- shellApproval：旧 shell/exec 工具的审批配置；删除 shell/exec 后不再有业务意义。

## 2. 最终设计

### 2.1 工具唯一入口

当前用户可见工具由 Python catalog/resolver 提供，工具名不再依赖 sandbox_ 前缀：

```text
workspace_info
list_files
read_file
write_file
apply_patch
```

工具边界由 catalog metadata 表达：

```text
execution_scope = claw_sandbox
```

### 2.2 执行上下文

工具实现仍需要 runtime context：

```text
context.metadata["sandbox_base_url"]
```

该字段指向当前 Claw 的 sandbox-runner Service，例如：

```text
http://sandbox-runner-<clawId>.<tenantNamespace>.svc.cluster.local:8000
```

工具调用通过 sandbox-runner API 访问 workspace，而不是直接读写 Python API Pod 的本地磁盘。

### 2.3 Prompt 生成

Prompt 不再硬编码“必须使用 sandbox_* 工具”。Python /v1/agent/run 和 Spring Claw Chat 都应基于 resolved tools 动态生成工具提示词。

Python 侧新增：

```text
resolve_runtime_tools(policy)
compose_runtime_system_prompt(base_prompt, resolved_tools)
require_sandbox_base_url(request)
```

效果：

- 缺少 sandbox_base_url 时，Agent run 直接失败。
- system prompt 自动拼接 resolver 返回的 prompt_fragments。
- 后续新增工具时，只要进入 catalog/resolver，prompt 就能动态同步。

## 3. 已完成的代码删除

### 3.1 Python 旧工具实现

已物理删除：

```text
openclaw/tools/fs/*
openclaw/tools/shell/*
openclaw/tools/web/*
```

删除后不再存在旧 import：

```text
openclaw.tools.fs
openclaw.tools.shell
openclaw.tools.web
```

### 3.2 旧测试

已删除：

```text
tests/test_fs_tools.py
tests/test_fs_mutation_tools.py
tests/test_shell_tool.py
tests/test_shell_parser.py
tests/test_web_guard.py
```

CLI 测试改为只验证新的 sandbox-only 工具。

### 3.3 workspace_only 残留

旧的 workspace_only 来自 local/sandbox 混合模型。当前所有用户工具都只服务于 Claw sandbox，因此已从以下链路删除：

```text
ToolPolicy
ToolExecutionContext
make_base_context
Agent
LoopConfig
execute_tool_calls
```

## 4. shellApproval 删除与数据库处理

### 4.1 旧版 shellApproval 的作用

shellApproval 是旧 shell/exec 工具的审批模式：

```text
deny    默认拒绝需要审批的命令
require 必须人工审批
auto    部分命令自动通过，危险命令仍拦截
```

因为 shell/exec 工具已删除，shellApproval 不再有业务意义。

### 4.2 已删除的代码链路

已移除：

- Python API：AgentRunRequest.shell_approval、shell_approval_mode metadata。
- Runtime config client：shellApproval 解析。
- Channel config：OPENCLAW_CHANNEL_SHELL_APPROVAL。
- Spring DTO：AgentToolPolicyRequest、AgentToolPolicyResponse、AgentRuntimeToolPolicyResponse、PyclawAgentRunRequest。
- Spring Entity：AgentToolPolicyEntity.shellApproval。
- Spring Service：AgentConfigService、ClawChatService、AgentRuntimeConfigController 中的 shellApproval 读写和传递。
- 前端：AgentConfigPage.vue 的 Shell 审批选择器。
- Helm/env：OPENCLAW_CHANNEL_SHELL_APPROVAL。
- 默认管理员权限：tool:grant:shell、tool:grant:web。

### 4.3 数据库旧列风险

如果线上 MySQL 已存在旧列：

```sql
agent_tool_policies.shell_approval NOT NULL
```

JPA 删除实体字段不会自动删除数据库列。新建 Agent Policy 时，JPA 不再给 shell_approval 赋值，如果该列没有默认值，MySQL 可能拒绝插入。

### 4.4 启动期兼容清理

新增：

```text
spring-backend/src/main/java/com/anxin/pyclaw/backend/agentconfig/LegacyToolPolicySchemaCleaner.java
```

职责：

1. Spring Backend 启动时检查 agent_tool_policies 是否存在旧 shell_approval 列。
2. 如果存在，执行：

```sql
ALTER TABLE agent_tool_policies DROP COLUMN shell_approval;
```

这样代码层面不保留旧字段，同时避免线上旧 schema 绊住新版本。

## 5. GitHub Actions 部署失败分析

用户提供的 Actions 日志显示：

```text
Build and push spring-backend image: success
Deploy to ECS K3s: failed
Error: Kubernetes cluster unreachable: Get "https://127.0.0.1:6443/version": net/http: TLS handshake timeout
```

这说明：

- Docker 镜像已经构建并推送到 ACR。
- SSH 已成功进入 ECS。
- git fetch / git reset 已成功。
- 失败点是 ECS 本机上的 K3s apiserver 暂时无响应。
- 不是 image tag 错误，也不是 Helm values 语法错误。

该问题和之前手动部署时遇到的 `kubectl get pods` 卡住、`TLS handshake timeout` 属于同一类：单机 K3s 在资源紧张或 apiserver 短暂卡顿时无法及时响应。

## 6. Actions 部署加固方案

已修改：

```text
.github/workflows/deploy-spring-backend.yml
```

### 6.1 旧流程

旧流程直接执行：

```text
git fetch
git reset
helm upgrade --install
```

如果 K3s apiserver 当时卡住，Helm 直接失败，Actions 整体失败。

### 6.2 新流程

新流程通过 SSH 运行远程 bash 脚本：

1. 进入 `/opt/pyclaw`。
2. 清理 `.git/index.lock`。
3. `git fetch origin <ref>`。
4. `git reset --hard <commit>`。
5. `wait_for_k3s`：用 `k3s kubectl get nodes --request-timeout=20s` 检查 apiserver。
6. 如果多次失败，执行一次：

```bash
sudo systemctl restart k3s
```

7. 等待 K3s Ready。
8. 执行 Helm：

```bash
sudo helm --kubeconfig /etc/rancher/k3s/k3s.yaml upgrade --install spring-backend ./spring-backend/helm \
  -n pyclaw \
  --create-namespace \
  -f spring-values-k3s.yaml \
  --set-string image.repository="$IMAGE_REPOSITORY" \
  --set-string image.tag="$IMAGE_TAG" \
  --timeout 10m
```

9. 如果 Helm 仍失败，再检查一次 K3s 并重试一次 Helm。

### 6.3 为什么只重启一次

只重启一次是为了避免 Actions 在真实故障时无限循环。若重启后仍无法部署，应视为 ECS/K3s 本身需要人工排查，例如：

- 内存不足
- CPU load 过高
- 磁盘 IO 卡顿
- k3s service 异常
- containerd 卡死

## 7. 验证命令

本地已执行：

```powershell
py -m py_compile openclaw\cli.py openclaw\api.py openclaw\agent\agent.py openclaw\agent\loop.py openclaw\agents\runtime_config_client.py openclaw\channels\config.py openclaw\tools\catalog.py openclaw\tools\executor.py openclaw\tools\resolver.py openclaw\tools\policy.py openclaw\tools\sandbox_workspace.py openclaw\tools\types.py
py -m unittest discover tests
npm run build  # in pyclaw-web
git diff --check
```

结果：

- Python 编译通过。
- Python 测试通过：112 tests OK，11 skipped。
- pyclaw-web Vite build 通过。
- git diff --check 通过。

代码扫描：

```powershell
rg -n "workspace_only|workspaceOnly|SANDBOX_MODE|workspace_mode|shellApproval|shell_approval|ShellApproval|OPENCLAW_CHANNEL_SHELL_APPROVAL|tool:grant:(shell|web)|web_fetch|web_search|list_dir|openclaw\.tools\.(fs|shell|web)|create_shell_tool|create_exec_tool" openclaw tests spring-backend pyclaw-web helm pyclaw-values-k3s.example.yaml
rg -n "sandbox_workspace_info|sandbox_list_files|sandbox_read_file|sandbox_write_file|sandbox_apply_patch" openclaw tests spring-backend pyclaw-web
```

结果：旧工具名、旧协议字段、旧 sandbox_* 工具名均无结果。

## 8. 未完成 / 注意事项

1. 本地没有 mvn，也没有 mvnw wrapper，因此 Spring Backend Maven 编译需以 GitHub Actions 结果为准。
2. 新增的 LegacyToolPolicySchemaCleaner 会在 Spring Backend 启动时修改数据库结构，部署前建议确认数据库已有备份或至少确认旧列无业务价值。
3. 当前只加固了 spring-backend 的部署 workflow。pyclaw-api、pyclaw-web 如果之后也遇到同类 K3s TLS handshake timeout，可以复用同样的 wait/restart/retry 逻辑。
4. 如果 Actions 重启 K3s 后仍失败，应进入 ECS 手动执行：

```bash
sudo systemctl status k3s --no-pager
sudo journalctl -u k3s -n 200 --no-pager
free -h
uptime
df -h
sudo /usr/local/bin/k3s kubectl get nodes --request-timeout=30s
```

## 9. 本次建议 commit 消息

```text
refactor: 严格删除旧工具链路并加固 Spring 部署
```