# Claw 场景工具目录与动态解析长期方案实现记录

日期：2026-07-15

## 1. 背景

本轮实现基于以下长期方向：

- Python `openclaw` Tool Catalog 是工具的唯一事实来源。
- Spring Boot 不再维护一份独立的静态工具目录，而是作为 Tool Resolver Client，把用户、Claw、Agent、运行模式等上下文传给 Python。
- Python 根据上下文返回“当前实际可用工具”和“平台提示词片段”。
- Spring Boot 的 Prompt Composer 将 Agent 自己的 System Prompt 与 Python 返回的平台提示词片段合成最终运行提示词。
- 前端工具目录展示 Tool Resolver 的结果，而不是展示一份静态工具清单。
- 面向用户 Claw 场景时，Agent 工具只能操作当前用户当前 Claw 的资源，不能感知或操作平台宿主机、ECS、K3s 或管理员运维资源。

## 2. 最终边界

Agent 的工具边界确定为：

```text
Agent tools can only operate current user's current Claw resources.
Agent tools must not operate the platform host, ECS, K3s, or administrator-only resources.
```

因此，用户可见工具只保留 Claw 场景工具：

```text
sandbox_workspace_info
sandbox_list_files
sandbox_read_file
sandbox_write_file
sandbox_apply_patch
web_fetch        可选，由 Agent webAccess 开关控制
web_search       可选，由 Agent webAccess 开关控制
```

`host_*`、宿主机 SSH、K3s、ECS 运维类工具不进入用户可见工具目录。

## 3. 架构

长期结构如下：

```text
Python openclaw Tool Catalog
  -> Python Tool Resolver
       输入：profile / allow / deny / alsoAllow / readonly / workspaceMode / webAccess
       输出：tools / deniedTools / promptFragments
  -> Spring PyclawClient
  -> Spring ToolCatalogService
  -> Frontend Tool Catalog Page

Claw Web Chat
  -> Spring ClawChatService
       读取 Agent tool policy
       读取 Claw sandbox context
       调用 Python Tool Resolver
       通过 RuntimePromptComposer 合成最终 system prompt
       把 resolver 输出的工具 allow/deny 传给 Python /v1/agent/run
  -> Python Agent runtime
       根据 allow/deny 构造 ToolRegistry
       sandbox_* 工具通过 sandbox_base_url 操作当前 Claw 的 sandbox-runner
  -> sandbox-runner Pod
       操作当前 Claw PVC 挂载的 /workspace
```

## 4. 为什么需要 Tool Resolver

只靠硬编码提示词，例如“必须使用 sandbox_* 工具”，存在两个问题：

1. 工具增删后，提示词容易忘记同步。
2. 前端展示、后端执行、Prompt 注入可能出现三套不一致的工具认知。

Tool Resolver 解决的是“一次解析，多处复用”：

- 前端展示当前实际可用工具。
- Spring 执行时使用同一份工具 allow/deny。
- Prompt Composer 使用同一份 `promptFragments`。
- Python Agent runtime 最终只注册 resolver 允许的工具。

## 5. Python openclaw 实现

### 5.1 Tool Catalog 扩展

文件：`openclaw/tools/catalog.py`

`ToolCatalogEntry` 新增字段：

```text
workspace_modes
readonly
requires_approval
prompt_hint
user_visible
```

含义：

- `workspace_modes`：工具支持的运行空间，例如 `local`、`sandbox_runner`。
- `readonly`：是否只读。
- `requires_approval`：是否需要审批。
- `prompt_hint`：给 Prompt Composer 使用的工具说明片段。
- `user_visible`：是否展示给用户和 Agent 配置页。

当前用户可见工具：

```text
sandbox_workspace_info
sandbox_list_files
sandbox_read_file
sandbox_write_file
sandbox_apply_patch
web_fetch
web_search
```

旧的本地文件工具 `read/write/edit/apply_patch/shell/exec` 仍保留在 Python 内部，主要用于 CLI 或兼容旧运行模式，但在 Claw SaaS 场景下不会作为用户可见工具暴露。

### 5.2 Tool Resolver

文件：`openclaw/tools/resolver.py`

新增核心类型：

```text
ToolResolveInput
ResolvedTool
DeniedTool
PromptFragment
ToolResolveResult
```

核心函数：

```text
resolve_tools(request)
build_runtime_policy(request)
```

当前规则：

- `workspace_mode=sandbox_runner` 时，强制拒绝本地文件和 shell 工具：

```text
read
list_dir
ls
grep
find
write
edit
apply_patch
shell
exec
```

- `workspace_mode=sandbox_runner` 时，自动允许 sandbox 工作区工具：

```text
sandbox_workspace_info
sandbox_list_files
sandbox_read_file
sandbox_write_file
sandbox_apply_patch
```

- `web_access=true` 时允许：

```text
web_fetch
web_search
```

- `web_access=false` 时拒绝 Web 工具。

- resolver 输出 `promptFragments`，例如 sandbox 运行模式下会生成：

```text
当前工作区是当前 Claw 专属的 sandbox workspace。
你只能通过当前可用的 sandbox 工具访问用户项目文件，不要使用本地文件系统工具操作项目文件。
当前可用的 sandbox 工具：
- sandbox_workspace_info: ...
- sandbox_list_files: ...
```

### 5.3 Python API

文件：`openclaw/api.py`

新增接口：

```text
GET  /v1/tools/catalog
POST /v1/tools/resolve
```

`GET /v1/tools/catalog` 返回用户可见工具目录。

`POST /v1/tools/resolve` 请求示例：

```json
{
  "profile": "coding",
  "allow": null,
  "deny": [],
  "also_allow": [],
  "readonly": false,
  "workspace_mode": "sandbox_runner",
  "web_access": false
}
```

返回：

```text
profile
workspace_mode
tools
denied_tools
prompt_fragments
```

### 5.4 sandbox_apply_patch

文件：`openclaw/tools/sandbox_workspace.py`

新增工具：

```text
sandbox_apply_patch
```

参数：

```text
file_path
old_text
new_text
replace_all
```

它不会直接修改 pyclaw-api Pod 内的 `/app`，而是通过当前 Claw 的 `sandbox_base_url` 调用 sandbox-runner：

```text
POST /v1/workspace/patches
```

## 6. sandbox-runner 实现

文件：`sandbox-runner/app/main.py`

新增接口：

```text
POST /v1/workspace/patches
```

行为：

- 只允许修改 `/workspace` 内的文件。
- `file_path` 不允许逃逸 workspace。
- `old_text` 不存在时返回 409。
- `old_text` 出现多次且 `replace_all=false` 时返回 409。
- 成功后返回替换次数和文件大小。

这使 Agent 可以对当前 Claw 的 workspace 做精确文本替换。

## 7. Spring Boot 实现

### 7.1 PyclawClient

文件：`spring-backend/src/main/java/com/anxin/pyclaw/backend/pyclaw/PyclawClient.java`

新增：

```text
toolCatalog()
resolveTools(PyclawToolResolveRequest request)
```

Spring 通过内部 token 调用 Python：

```text
/v1/tools/catalog
/v1/tools/resolve
```

### 7.2 DTO

新增文件：

```text
PyclawToolCatalogEntry.java
PyclawToolCatalogResponse.java
PyclawToolResolveRequest.java
PyclawToolResolveResponse.java
PyclawDeniedTool.java
PyclawPromptFragment.java
```

这些 DTO 用于映射 Python snake_case 返回字段。

### 7.3 ToolCatalogService

文件：`spring-backend/src/main/java/com/anxin/pyclaw/backend/tool/ToolCatalogService.java`

变化：

- `/api/tools/catalog` 改为代理 Python Tool Catalog。
- `/api/tools/profiles` 从 Python catalog response 读取 profile。
- `/api/tools/effective` 改为调用 Python Tool Resolver。

前端因此看到的是 Python 唯一事实来源解析后的工具目录。

### 7.4 RuntimePromptComposer

文件：`spring-backend/src/main/java/com/anxin/pyclaw/backend/tool/RuntimePromptComposer.java`

职责：

```text
最终 system prompt = Agent 自定义 systemPrompt + Python resolver promptFragments
```

这样平台提示词不再硬编码在 `ClawChatService` 中。

### 7.5 ClawChatService

文件：`spring-backend/src/main/java/com/anxin/pyclaw/backend/clawchat/ClawChatService.java`

运行流程变化：

1. 根据当前 Claw 判断 `workspaceMode`。
2. 读取 Agent 的 tool policy。
3. 调用 Python `/v1/tools/resolve`。
4. 用 resolver 返回的 `promptFragments` 合成 system prompt。
5. 将 resolver 返回的工具名称作为 `tools_allow` 传给 `/v1/agent/run`。
6. 将 resolver 返回的 denied 工具作为 `tools_deny` 传给 `/v1/agent/run`。

这样运行时工具和提示词来自同一次解析。

## 8. 前端实现

### 8.1 工具目录页

文件：`pyclaw-web/src/views/ToolCatalogPage.vue`

变化：

- 展示当前 Claw 运行时可交给 Agent 的工具。
- Profile tab 切换时调用 `/api/tools/effective`。
- 增加“允许 Web 工具”开关。
- 默认 `workspaceMode=sandbox_runner`。
- 显示当前不可用工具名称。

### 8.2 Agent 配置页

文件：`pyclaw-web/src/views/AgentConfigPage.vue`

变化：

- 创建 Agent 时默认 `webAccess=false`。
- 编辑 Agent 时回填 `agent.toolPolicy.webAccess`。
- 保存 Agent 时提交 `toolPolicy.webAccess`。
- 表单增加“允许 Web 工具”开关。

当前 Agent 的工具权限范围由：

```text
Tool Profile
Readonly
Web Access
Allow / Deny / Also Allow
workspaceMode
```

共同决定。

## 9. 关于 read/write/edit 与 sandbox_read/write

`read/write/edit/apply_patch/shell/exec` 是旧的本地运行工具名。

在 Claw SaaS 场景中，不应让 Agent 使用这些工具操作 pyclaw-api Pod 内部文件系统。

长期边界是：

```text
用户 Claw workspace 文件操作必须使用 sandbox_* 工具。
```

因此：

- `read` 不会自动重命名为 `sandbox_read_file`。
- `write` 不会自动重命名为 `sandbox_write_file`。
- `apply_patch` 不会自动重命名为 `sandbox_apply_patch`。
- 它们是两组不同工具。
- sandbox 运行模式下 resolver 会拒绝本地工具，只允许 sandbox 工具。

## 10. 当前验证

已执行：

```powershell
py -m py_compile openclaw\api.py openclaw\tools\catalog.py openclaw\tools\resolver.py openclaw\tools\sandbox_workspace.py sandbox-runner\app\main.py
py -m unittest discover -s tests
npm run build
```

结果：

```text
Python py_compile 通过
Python unittest：Ran 137 tests, OK, skipped=11
pyclaw-web vite build 通过
```

当前本地环境没有 `mvn`，也没有看到 Spring Backend 的 Maven wrapper，因此本轮未能在本机执行 Spring Maven 编译。需要在 CI 或安装 Maven 的环境中验证：

```powershell
cd spring-backend
mvn test
```

## 11. 后续建议

1. 在 CI 中增加 Spring `mvn test`，防止 DTO 或 record 构造签名变化遗漏。
2. 在 Agent 配置页进一步展示 resolver 预览结果，让用户选择 Profile 和 Web Access 后能看到最终可用工具。
3. 给 `sandbox_apply_patch` 增加更完整的测试，包括路径逃逸、多匹配、文件不存在。
4. 将 Python `/v1/agent/run` 的 sandbox allow/deny 兜底逻辑继续收敛到 resolver，最终避免重复维护。
5. 在审计日志中记录每次 Agent run 的 resolved tool list，便于追踪工具权限。
6. 后续如果需要运维工具，也应设计成面向用户 Claw 的工具，而不是平台宿主机工具。

## 12. 本轮改动文件

```text
openclaw/api.py
openclaw/tools/catalog.py
openclaw/tools/resolver.py
openclaw/tools/sandbox_workspace.py
sandbox-runner/app/main.py
spring-backend/src/main/java/com/anxin/pyclaw/backend/pyclaw/PyclawClient.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/pyclaw/PyclawToolCatalogEntry.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/pyclaw/PyclawToolCatalogResponse.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/pyclaw/PyclawToolResolveRequest.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/pyclaw/PyclawToolResolveResponse.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/pyclaw/PyclawDeniedTool.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/pyclaw/PyclawPromptFragment.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/tool/ToolCatalogService.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/tool/ToolCatalogEntryResponse.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/tool/EffectiveToolsRequest.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/tool/RuntimePromptComposer.java
spring-backend/src/main/java/com/anxin/pyclaw/backend/clawchat/ClawChatService.java
pyclaw-web/src/views/ToolCatalogPage.vue
pyclaw-web/src/views/AgentConfigPage.vue
docs/s00_project_notes/0002/tool-catalog-resolver-long-term-implementation.md
```