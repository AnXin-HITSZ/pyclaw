# saas-claw

`saas-claw` 是一个 OpenClaw-inspired Python agent runtime。当前版本已经包含：

- OpenAI / OpenAI-compatible provider adapter
- mock provider
- session transcript JSONL 持久化
- FastAPI API 入口：`/healthz`、`/v1/agent/run`
- Spring Backend 鉴权入口：登录、JWT、API Token、用户管理、Provider 管理、微信/飞书 Channel 管理、审计和用量记录
- CLI 入口：`saas-claw` / `python -m openclaw`
- 工具系统：`read`、`list_dir`、`write`、`edit`、`apply_patch`、`shell`、`web_fetch`、`web_search`
- 工具安全边界：workspace path guard、readonly guard、SSRF guard、shell cwd guard
- Helm / K3s 部署配置

## 1. 安装与虚拟环境

在 Windows cmd 中：

```cmd
cd /d <repo>
py -m venv .venv
.venv\Scripts\activate.bat
python -m pip install -e ".[openai]"
```

如果只运行 mock provider 或单元测试，不需要 OpenAI SDK，也可以：

```cmd
python -m pip install -e .
```

## 2. 环境变量

项目会默认读取当前目录下的 `.env`。

常用 `.env` 示例：

```env
OPENAI_API_KEY=your_api_key_here
OPENAI_MODEL=gpt-4.1-mini
OPENAI_API_MODE=auto
# OPENAI_BASE_URL=https://api.openai.com/v1
```

如果使用 OpenAI-compatible Chat Completions 服务，例如 DeepSeek 一类服务，通常需要：

```env
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=your-model-name
OPENAI_API_MODE=chat_completions
```

## 3. 基本运行

使用真实 provider：

```cmd
saas-claw "你好"
```

使用 mock provider：

```cmd
saas-claw --provider mock "你好"
```

等价 Python 模块入口：

```cmd
python -m openclaw "你好"
```

输出完整 assistant message JSON：

```cmd
saas-claw --json "你好"
```

## 4. 主命令参数

这些参数可以放在 prompt、`transcripts`、`tools` 子命令前面。

| 参数 | 可选值 / 默认值 | 说明 |
| --- | --- | --- |
| `--provider` | `openai` / `mock`，默认 `openai` | 选择 LLM provider。 |
| `--model` | 默认 `OPENAI_MODEL` 或 `gpt-4.1-mini` | 指定模型名。 |
| `--system` | 默认 `You are a helpful assistant.` | 指定 system prompt。 |
| `--env-file` | 默认 `.env` | 指定要加载的 env 文件。 |
| `--no-env-file` | 无 | 不加载 `.env`。 |
| `--chatdata-dir` | 默认 `./chatdata` | 指定 transcript 和 `sessions.json` 保存目录。 |
| `--session-id` | 默认自动生成 | 指定会话 ID，对应 transcript 文件名。 |
| `--format` | `text` / `detail` / `json`，默认 `text` | `transcripts show` 的输出格式。 |
| `--api-mode` | `auto` / `responses` / `chat_completions` / `chat-completions`，默认 `auto` | OpenAI SDK API 模式。 |
| `--reasoning-effort` | `low` / `medium` / `high` | 传递 reasoning effort。Chat Completions 模式会自动剔除不兼容字段。 |
| `--max-output-tokens` | 整数 | 限制最大输出 token。Chat Completions 会映射为 `max_tokens`。 |
| `--tool-profile` | `minimal` / `readonly` / `coding` / `messaging` / `full`，默认 `coding` | 控制 Agent 暴露给模型的工具集合。 |
| `--json` | 无 | 输出 JSON；在 `tools run` 中也表示以 JSON 格式输出工具结果。 |

## 5. Tool Profile

`--tool-profile` 控制模型能看到哪些工具。

| Profile | 暴露工具 | 适用场景 |
| --- | --- | --- |
| `minimal` | 最小工具集合 | API 调试、低风险调用。 |
| `readonly` | `read`、`list_dir` | 只允许读取 workspace 内文件。 |
| `coding` | `read`、`list_dir`、`write`、`edit`、`apply_patch` | 默认编码模式。 |
| `messaging` | 消息渠道相关工具 | 微信 / 飞书渠道场景。 |
| `full` | `read`、`list_dir`、`write`、`edit`、`apply_patch`、`shell`、`web_fetch`、`web_search` | 显式启用 shell 和 web 工具。 |

示例：

```cmd
saas-claw --tool-profile readonly "请阅读 README 并总结"
saas-claw --tool-profile coding "请修改 README"
saas-claw --tool-profile full "请读取 README 并搜索相关资料"
```

## 6. Transcript 命令

默认 transcript 保存在：

```text
./chatdata
```

指定 session id 运行：

```cmd
saas-claw --provider mock --session-id demo "你好"
```

查看 transcript：

```cmd
saas-claw transcripts show demo --format text
saas-claw transcripts show demo --format detail
saas-claw transcripts show demo --format json
```

指定 transcript 目录：

```cmd
saas-claw --chatdata-dir .\chatdata transcripts show demo --format detail
```

## 7. Tools 命令

### 7.1 查看工具列表

```cmd
saas-claw tools list
saas-claw --json tools list
```

### 7.2 查看工具详情

```cmd
saas-claw tools describe read
saas-claw --json tools describe read
```

### 7.3 手动执行工具

`tools run` 支持两种参数形式。

JSON 形式：

```cmd
saas-claw --json tools run read "{\"path\":\"README.md\"}"
```

`key=value` 形式：

```cmd
saas-claw tools run read path=README.md
```

注意：`tools run` 是人工显式执行入口，会使用 full registry；Agent prompt 默认仍使用 `--tool-profile coding`。

## 8. 当前工具参数

### read

读取 workspace 内文本文件。

```cmd
saas-claw --json tools run read "{\"path\":\"README.md\"}"
```

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `path` | string | 是 | 要读取的文件路径。相对路径基于当前 cwd。 |
| `offset` | integer | 否 | 从第几行开始返回，0-based。 |
| `limit` | integer | 否 | 最多返回多少行。 |
| `max_chars` | integer | 否 | 最多返回多少字符，默认 20000。 |

### list_dir

列出 workspace 内目录。

```cmd
saas-claw --json tools run list_dir "{\"path\":\"chatdata\"}"
```

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `path` | string | 否 | 要列出的目录路径，默认 `.`。 |
| `recursive` | boolean | 否 | 是否递归列出。 |
| `include_hidden` | boolean | 否 | 是否包含点号开头的隐藏项。 |
| `max_entries` | integer | 否 | 最多返回多少项，默认 200。 |

如果想查看 `chatdata` 下有哪些 transcript 文件，先执行：

```cmd
saas-claw --json tools run list_dir "{\"path\":\"chatdata\"}"
```

然后再读取具体文件，例如：

```cmd
saas-claw --json tools run read "{\"path\":\"chatdata/demo.jsonl\"}"
```

### write

写入 workspace 内文本文件。

```cmd
saas-claw --json tools run write "{\"path\":\"tmp.txt\",\"content\":\"hello\"}"
```

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `path` | string | 是 | 要写入的文件路径。 |
| `content` | string | 是 | 写入内容。 |
| `overwrite` | boolean | 否 | 是否覆盖已有文件，默认 `true`。 |
| `create_dirs` | boolean | 否 | 是否创建父目录，默认 `true`。 |

### edit

对文件做精确文本替换。

```cmd
saas-claw --json tools run edit "{\"path\":\"tmp.txt\",\"old_text\":\"hello\",\"new_text\":\"hi\"}"
```

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `path` | string | 是 | 要编辑的文件路径。 |
| `old_text` | string | 是 | 要匹配的旧文本。 |
| `new_text` | string | 是 | 替换后的新文本。 |
| `replace_all` | boolean | 否 | 是否替换所有匹配。默认要求只匹配一次。 |

### apply_patch

当前是保守版 exact-text patch，复用 `edit` 的执行逻辑。

```cmd
saas-claw --json tools run apply_patch "{\"path\":\"tmp.txt\",\"old_text\":\"hi\",\"new_text\":\"hello again\"}"
```

参数同 `edit`：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `path` | string | 是 | 要修改的文件路径。 |
| `old_text` | string | 是 | 精确匹配文本。 |
| `new_text` | string | 是 | 替换文本。 |
| `replace_all` | boolean | 否 | 是否替换所有匹配。 |

### shell

在 workspace 内执行 shell 命令。

```cmd
saas-claw --json tools run shell "{\"command\":\"echo hello\"}"
```

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `command` | string | 是 | 要执行的 shell 命令。 |
| `cwd` | string | 否 | 命令执行目录，必须在 workspace 内，默认 `.`。 |
| `timeout_seconds` | integer | 否 | 超时时间，默认 30。 |
| `max_chars` | integer | 否 | stdout/stderr 最大保留字符数，默认 20000。 |

### web_fetch

抓取公开 HTTP(S) URL。

```cmd
saas-claw --json tools run web_fetch "{\"url\":\"https://example.com\"}"
```

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `url` | string | 是 | 公开 HTTP(S) URL。 |
| `timeout_seconds` | integer | 否 | 请求超时时间，默认 10。 |
| `max_bytes` | integer | 否 | 最大读取字节数，默认 200000。 |

安全限制：会阻止 localhost、private IP、loopback、link-local、reserved 等地址。

### web_search

简单 web 搜索。

```cmd
saas-claw --json tools run web_search "{\"query\":\"OpenClaw agent tools\",\"limit\":5}"
```

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `query` | string | 是 | 搜索关键词。 |
| `limit` | integer | 否 | 最多返回多少条结果，默认 5。 |
| `timeout_seconds` | integer | 否 | 请求超时时间，默认 10。 |

## 9. API 服务

安装 API 依赖后可启动 FastAPI 服务：

```cmd
python -m pip install -e ".[api,openai]"
uvicorn openclaw.api:app --host 0.0.0.0 --port 8000
```

健康检查：

```cmd
curl http://localhost:8000/healthz
```

调用 Agent：

```cmd
curl -X POST http://localhost:8000/v1/agent/run ^
  -H "Content-Type: application/json" ^
  -d "{\"prompt\":\"hello\",\"provider\":\"mock\",\"session_id\":\"demo\",\"tool_profile\":\"minimal\"}"
```

如果设置了 `SAAS_CLAW_API_TOKEN`，`/v1/agent/run` 需要携带：

```text
Authorization: Bearer <SAAS_CLAW_API_TOKEN>
```

## 10. Spring Backend

`spring-backend/` 是 saas-claw 的 Spring Boot 鉴权与管理后端，职责包括：

- 登录与 JWT
- API Token
- 用户管理
- Provider 配置管理
- 微信 / 飞书 Channel 配置管理
- Agent 代理调用
- 审计日志
- 用量记录

本地验证：

```cmd
cd spring-backend
mvn -s .mvn\settings.xml -gs .mvn\settings.xml test
```

相关文档：

- [Spring 后端技术设计](docs/000/0001/spring-backend-auth-technical-design.md)
- [Spring 后端实现记录](docs/000/0001/spring-backend-auth-implementation-log.md)
- [Spring 后端 API Contract](docs/000/0001/spring-backend-api-contract.md)
- [Spring Backend K3s 部署 SOP](docs/000/0000/spring-backend-k3s-deployment-sop.md)

## 11. K3s / Helm 部署文档

主要部署文档：

- [saas-claw K3s Helm 部署 SOP](docs/000/0000/saas-claw-k3s-helm-deployment-sop.md)
- [Spring Backend K3s 部署 SOP](docs/000/0000/spring-backend-k3s-deployment-sop.md)
- [K3s 系统镜像 ACR 镜像 SOP](docs/000/0000/k3s-system-images-acr-mirror-sop.md)
- [saas-claw Helm Chart 实现说明](docs/000/0000/saas-claw-helm-chart-implementation-notes.md)

推荐生产入口：

```text
公网 -> Spring Backend -> saas-claw-api ClusterIP -> 模型服务
```

`saas-claw-api` 自身应保留 `SAAS_CLAW_API_TOKEN` 作为内部服务鉴权。

## 12. Gateway 命令

当前 `gateway run` 只是保留入口，还没有实现：

```cmd
saas-claw gateway run
```

会返回：

```text
gateway run is registered but not implemented yet.
```

## 13. 验证命令

```cmd
py -m compileall openclaw tests
py -m unittest discover -s tests
```

Spring Backend 验证：

```cmd
cd spring-backend
mvn -s .mvn\settings.xml -gs .mvn\settings.xml test
```
