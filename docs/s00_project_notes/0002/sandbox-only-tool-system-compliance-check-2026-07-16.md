# PyClaw Sandbox-Only 工具系统验收核查记录

> 日期：2026-07-16
> 依据：./sandbox-only-tool-system-final-plan-and-log.md
> 范围：openclaw、spring-backend、pyclaw-web、helm、测试代码
> 结论：当前代码链路已按 sandbox-only 方案完成收口；旧 fs/shell/web 工具实现已物理删除，旧 shell approval 协议字段已移除，用户可见工具不再使用 sandbox_ 前缀命名。

## 1. 验收结果

| 验收项 | 当前状态 | 依据 |
|---|---|---|
| 前端工具目录只展示 Claw sandbox 工具 | 通过 | Tool catalog 只来自 Python catalog/resolver，工具 executionScope 为 claw_sandbox |
| LLM 只拿到 sandbox 工具 schema | 通过 | catalog 只注册 workspace_info/list_files/read_file/write_file/apply_patch |
| sandbox_base_url 缺失时 Agent run 直接失败 | 通过 | Python /v1/agent/run 入口执行 require_sandbox_base_url |
| local/host/shell/web 工具不进入用户链路 | 通过 | openclaw/tools/fs、openclaw/tools/shell、openclaw/tools/web 已删除 |
| workspaceMode/webAccess/SANDBOX_MODE/workspace_only 残留清理 | 通过 | 代码扫描无 workspace_only/workspaceMode/SANDBOX_MODE |
| Prompt 根据 resolved tools 动态生成 | 通过 | /v1/tools/resolve 返回 prompt_fragments；/v1/agent/run 使用 resolve_runtime_tools + compose_runtime_system_prompt |
| 工具调用只指向当前 Claw sandbox-runner | 通过 | 工具实现从 context.metadata.sandbox_base_url 访问 sandbox-runner workspace API |

## 2. 已完成的严格删除

- 删除旧本地文件工具：openclaw/tools/fs/*
- 删除旧 shell/exec 工具：openclaw/tools/shell/*
- 删除旧 web 工具：openclaw/tools/web/*
- 删除旧工具测试：test_fs_tools、test_fs_mutation_tools、test_shell_tool、test_shell_parser、test_web_guard
- 删除 CLI shell approval 参数和审批回调
- 删除 Python API shell_approval 字段和 shell_approval_mode metadata
- 删除 Spring shellApproval DTO/Entity/Service/RunRequest 链路
- 删除前端 Agent 配置页 Shell 审批控件
- 删除 Helm/env 中 OPENCLAW_CHANNEL_SHELL_APPROVAL
- 删除默认管理员权限中的 tool:grant:shell、tool:grant:web
- 新增 LegacyToolPolicySchemaCleaner，启动时清理线上旧 agent_tool_policies 兼容列，避免删除实体字段后旧 NOT NULL 列影响插入

## 3. 当前保留的 sandbox 词汇说明

代码中仍保留 sandbox_base_url、sandbox-runner、sandbox_workspace.py 等运行边界命名。这些不是旧 sandbox_* 工具名兼容，而是 Claw sandbox 运行时上下文和实现模块名称。用户可见工具名仍为：

- workspace_info
- list_files
- read_file
- write_file
- apply_patch

## 4. 验证命令和结果

```powershell
py -m py_compile openclaw\cli.py openclaw\api.py openclaw\agent\agent.py openclaw\agent\loop.py openclaw\agents\runtime_config_client.py openclaw\channels\config.py openclaw\tools\catalog.py openclaw\tools\executor.py openclaw\tools\resolver.py openclaw\tools\policy.py openclaw\tools\sandbox_workspace.py openclaw\tools\types.py
py -m unittest discover tests
npm run build  # in pyclaw-web
rg -n "workspace_only|workspaceOnly|SANDBOX_MODE|workspace_mode|shellApproval|shell_approval|ShellApproval|OPENCLAW_CHANNEL_SHELL_APPROVAL|tool:grant:(shell|web)|web_fetch|web_search|list_dir|openclaw\.tools\.(fs|shell|web)|create_shell_tool|create_exec_tool" openclaw tests spring-backend pyclaw-web helm pyclaw-values-k3s.example.yaml
rg -n "sandbox_workspace_info|sandbox_list_files|sandbox_read_file|sandbox_write_file|sandbox_apply_patch" openclaw tests spring-backend pyclaw-web
```

结果：

- Python 编译通过。
- Python unittest discover：112 tests OK，11 skipped。
- pyclaw-web Vite build 通过。
- 旧工具/旧协议字段扫描无结果。
- 旧 sandbox_* 工具名扫描无结果。
- git diff --check 通过。

## 5. 未执行项

本机未安装 mvn，也没有 mvnw wrapper，因此 Spring Boot Maven 编译未在本地执行。已通过字段引用扫描、DTO 构造器复查和前后端构建降低风险；最终仍建议在 CI/Actions 中以 Maven 构建结果为准。