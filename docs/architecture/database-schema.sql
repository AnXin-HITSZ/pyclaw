-- PyClaw 数据库表结构参考。
-- 本文件用于说明 spring-backend 当前使用的 MySQL 表模型。
-- 主要用途是架构阅读、表结构核对和领域模型理解；生产迁移仍以 Flyway 与 JPA 为准。

-- ---------------------------------------------------------------------------
-- 用户身份与访问控制
-- ---------------------------------------------------------------------------

CREATE TABLE users (
  id varchar(255) NOT NULL COMMENT '用户 ID，由应用生成。',
  username varchar(255) NOT NULL COMMENT '唯一登录用户名。',
  password_hash varchar(255) NOT NULL COMMENT '用户密码哈希值，禁止保存明文密码。',
  display_name varchar(255) DEFAULT NULL COMMENT '用户展示名称，用于前端显示。',
  status varchar(255) NOT NULL COMMENT '用户账号状态，例如 ACTIVE 或 DISABLED。',
  authorities varchar(2048) NOT NULL COMMENT '序列化后的权限或角色列表，供 Spring Security 使用。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最后更新时间。',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username)
) COMMENT='SaaS 注册用户及其认证元数据。';

CREATE TABLE api_tokens (
  id varchar(255) NOT NULL COMMENT 'API Token 记录 ID。',
  user_id varchar(255) NOT NULL COMMENT 'Token 所属用户 ID。',
  name varchar(255) NOT NULL COMMENT '用户自定义的 Token 名称。',
  token_hash varchar(255) NOT NULL COMMENT 'Token 哈希值。原始 Token 只在创建时展示一次，不落库。',
  scopes varchar(1000) NOT NULL COMMENT '序列化后的 Token 权限范围。',
  expires_at datetime(6) DEFAULT NULL COMMENT '过期时间，NULL 表示未设置明确过期时间。',
  revoked_at datetime(6) DEFAULT NULL COMMENT '吊销时间，NULL 表示未吊销。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  last_used_at datetime(6) DEFAULT NULL COMMENT '最近一次成功使用时间。',
  PRIMARY KEY (id),
  UNIQUE KEY uk_api_tokens_token_hash (token_hash)
) COMMENT='用于访问 PyClaw API 的长期访问令牌。';

CREATE TABLE audit_logs (
  id varchar(255) NOT NULL COMMENT '审计日志 ID。',
  actor_type varchar(255) NOT NULL COMMENT '操作者类型，例如 USER、SYSTEM 或 SERVICE。',
  actor_id varchar(255) DEFAULT NULL COMMENT '操作者 ID，USER 类型通常为用户 ID。',
  action varchar(255) NOT NULL COMMENT '审计动作名称，例如 tool.approval.created。',
  resource_type varchar(255) DEFAULT NULL COMMENT '目标资源类型。',
  resource_id varchar(255) DEFAULT NULL COMMENT '目标资源 ID。',
  request_id varchar(255) DEFAULT NULL COMMENT '请求关联 ID，用于链路追踪。',
  ip_address varchar(255) DEFAULT NULL COMMENT '客户端 IP 地址。',
  user_agent varchar(255) DEFAULT NULL COMMENT '客户端 User-Agent。',
  success bit(1) NOT NULL COMMENT '动作是否执行成功。',
  error_message longtext COMMENT '失败时的错误信息。',
  created_at datetime(6) NOT NULL COMMENT '审计事件发生时间。',
  PRIMARY KEY (id)
) COMMENT='安全敏感操作和关键运维操作的追加式审计记录。';

-- ---------------------------------------------------------------------------
-- 模型供应商、Agent 模板与工具策略
-- ---------------------------------------------------------------------------

CREATE TABLE provider_configs (
  id varchar(255) NOT NULL COMMENT '模型供应商配置 ID。',
  name varchar(255) NOT NULL COMMENT '供应商配置展示名称。',
  provider_type varchar(255) NOT NULL COMMENT '供应商类型，例如 OpenAI 兼容供应商标识。',
  base_url varchar(255) DEFAULT NULL COMMENT '供应商 API 基础地址，NULL 时可使用运行时默认值。',
  model varchar(255) NOT NULL COMMENT '该供应商配置默认使用的模型名称。',
  api_mode varchar(255) NOT NULL COMMENT 'API 调用模式，例如 chat_completions 或 responses。',
  secret_ref varchar(255) DEFAULT NULL COMMENT '外部密钥引用，用于存放 API 凭据。',
  api_key varchar(4096) DEFAULT NULL COMMENT '内联 API Key。生产环境优先使用 secret_ref。',
  owner_user_id varchar(255) DEFAULT NULL COMMENT '私有供应商配置的所属用户 ID，NULL 可表示平台级配置。',
  shared bit(1) NOT NULL COMMENT '是否共享给其他用户使用。',
  enabled bit(1) NOT NULL COMMENT '是否可被选择和使用。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最后更新时间。',
  PRIMARY KEY (id)
) COMMENT='Agent 可使用的大模型供应商与模型配置。';

CREATE TABLE agents (
  id varchar(255) NOT NULL COMMENT 'Agent 模板或配置 ID。',
  agent_key varchar(255) NOT NULL COMMENT '稳定且唯一的 Agent Key，用于配置和路由。',
  name varchar(255) NOT NULL COMMENT 'Agent 展示名称。',
  description varchar(2048) DEFAULT NULL COMMENT 'Agent 描述，用于前端展示。',
  enabled bit(1) NOT NULL COMMENT '该 Agent 模板是否可用。',
  provider_id varchar(255) DEFAULT NULL COMMENT '首选模型供应商配置 ID。',
  provider varchar(255) DEFAULT NULL COMMENT '已解析的供应商标识，保留用于运行时兼容。',
  model varchar(255) DEFAULT NULL COMMENT '已解析的模型名称，保留用于运行时兼容。',
  system_prompt varchar(8192) DEFAULT NULL COMMENT 'Agent 级系统提示词模板。',
  workspace_dir varchar(255) DEFAULT NULL COMMENT '该 Agent 的工作目录覆盖配置。',
  runtime_type varchar(255) NOT NULL COMMENT '运行时类型，例如 openclaw 或其他兼容执行器。',
  created_by varchar(255) DEFAULT NULL COMMENT '创建该 Agent 的用户 ID 或系统操作者。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最后更新时间。',
  PRIMARY KEY (id),
  UNIQUE KEY uk_agents_agent_key (agent_key)
) COMMENT='安装到 Claw 之前的 Agent 模板或基础配置。';

CREATE TABLE agent_tool_policies (
  id varchar(255) NOT NULL COMMENT '工具策略 ID。',
  agent_id varchar(255) NOT NULL COMMENT '所属 Agent 模板 ID。',
  profile varchar(255) NOT NULL COMMENT '工具 Profile 名称，例如 minimal、readonly、coding 或 full。',
  tools_allow_json longtext COMMENT '显式允许工具列表 JSON。NULL 表示使用 Profile 默认工具集合。',
  tools_deny_json longtext COMMENT '显式拒绝工具列表 JSON，在 Profile 解析后继续生效。',
  tools_also_allow_json longtext COMMENT '在 Profile 默认工具集合之外额外允许的工具列表 JSON。',
  readonly bit(1) NOT NULL COMMENT '运行时是否应将该策略视为只读策略。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最后更新时间。',
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_tool_policies_agent_id (agent_id)
) COMMENT='挂在 Agent 模板上的工具可见性与 Profile 策略。';

-- ---------------------------------------------------------------------------
-- Claw 工作空间与已安装 Agent 实例
-- ---------------------------------------------------------------------------

CREATE TABLE claws (
  id varchar(255) NOT NULL COMMENT 'Claw 工作空间 ID。',
  owner_user_id varchar(255) NOT NULL COMMENT '所属用户 ID。一个 Claw 属于一个用户工作空间。',
  name varchar(255) NOT NULL COMMENT 'Claw 展示名称。',
  description varchar(2048) DEFAULT NULL COMMENT 'Claw 描述。',
  status varchar(255) NOT NULL COMMENT 'Claw 生命周期状态，例如 ACTIVE 或 DISABLED。',
  default_agent_id varchar(255) DEFAULT NULL COMMENT '默认已安装 Agent 实例 ID。用户未指定 Agent 时使用。',
  feishu_enabled bit(1) NOT NULL COMMENT '是否启用飞书 Channel 路由。',
  feishu_account_id varchar(255) DEFAULT NULL COMMENT '飞书账号标识，用于路由绑定。',
  feishu_peer_kind varchar(255) DEFAULT NULL COMMENT '飞书会话类型，例如 chat 或 user。',
  feishu_peer_id varchar(255) DEFAULT NULL COMMENT '飞书会话 ID。',
  feishu_comment varchar(2048) DEFAULT NULL COMMENT '飞书路由备注。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最后更新时间。',
  PRIMARY KEY (id)
) COMMENT='用户拥有的 Claw 工作空间。每个 Claw 是独立工作空间。';

CREATE TABLE claw_agents (
  id varchar(255) NOT NULL COMMENT 'Claw 内已安装 Agent 实例 ID。',
  claw_id varchar(255) NOT NULL COMMENT '所属 Claw 工作空间 ID。',
  agent_id varchar(255) NOT NULL COMMENT '来源 Agent 模板 ID。',
  role_key varchar(255) NOT NULL COMMENT 'Claw 内唯一的角色 Key，用于 @Agent 提及和路由。',
  display_name varchar(255) NOT NULL COMMENT '该已安装 Agent 实例的展示名称。',
  mention_aliases_json longtext COMMENT '可提及该 Agent 的别名 JSON 数组。',
  command_prefixes_json longtext COMMENT '可路由到该 Agent 的命令前缀 JSON 数组。',
  default_role bit(1) NOT NULL COMMENT '是否为该 Claw 的默认 Agent。',
  enabled bit(1) NOT NULL COMMENT '该已安装 Agent 是否可被选择或路由。',
  sort_order int NOT NULL COMMENT 'Claw 内展示和路由排序值。',
  route_binding_id varchar(255) DEFAULT NULL COMMENT '关联的 Channel 路由绑定 ID。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最后更新时间。',
  source_type varchar(255) DEFAULT NULL COMMENT '安装来源类型，预期值包括 local 或 package。NULL 视为历史 local。',
  source_agent_id varchar(255) DEFAULT NULL COMMENT '从本地 Agent 派生时的原始 Agent ID。',
  package_id varchar(255) DEFAULT NULL COMMENT '从 Marketplace 安装时对应的 Agent Package ID。',
  package_version_id varchar(255) DEFAULT NULL COMMENT '安装到该 Claw 的 Agent Package Version ID。',
  local_system_prompt_override longtext COMMENT 'Claw 级 Agent 系统提示词覆盖内容。',
  local_profile varchar(255) DEFAULT NULL COMMENT 'Claw 级工具 Profile 覆盖配置。',
  installed_by varchar(255) DEFAULT NULL COMMENT '执行安装的用户 ID 或 Agent 实例 ID。',
  installed_at datetime(6) DEFAULT NULL COMMENT '安装时间。',
  PRIMARY KEY (id),
  UNIQUE KEY uk_claw_agents_claw_role (claw_id, role_key),
  KEY idx_claw_agents_claw_enabled_sort (claw_id, enabled, sort_order),
  KEY idx_claw_agents_claw_pkgver (claw_id, package_version_id),
  KEY idx_claw_agents_claw_source_agent (claw_id, source_agent_id)
) COMMENT='安装到 Claw 中的 Agent 实例。对话运行时使用该表记录 Agent 身份。';

-- ---------------------------------------------------------------------------
-- Channel 集成与用户密钥
-- ---------------------------------------------------------------------------

CREATE TABLE route_bindings (
  id varchar(255) NOT NULL COMMENT '路由绑定 ID。',
  enabled bit(1) NOT NULL COMMENT '该路由绑定是否启用。',
  priority int NOT NULL COMMENT '多个路由同时匹配同一 Channel 事件时的优先级。',
  claw_id varchar(255) DEFAULT NULL COMMENT '目标 Claw ID。',
  agent_id varchar(255) NOT NULL COMMENT '路由目标 Agent 或 Agent 实例 ID。',
  channel varchar(255) DEFAULT NULL COMMENT 'Channel 名称，例如 feishu 或 wechat。',
  account_id varchar(255) DEFAULT NULL COMMENT 'Channel 账号或机器人标识。',
  peer_kind varchar(255) DEFAULT NULL COMMENT '外部 Channel 会话类型，例如私聊、群聊、chat 或 room。',
  peer_id varchar(255) DEFAULT NULL COMMENT '外部 Channel 会话 ID。',
  parent_peer_kind varchar(255) DEFAULT NULL COMMENT '嵌套 Channel 结构中的父级会话类型。',
  parent_peer_id varchar(255) DEFAULT NULL COMMENT '嵌套 Channel 结构中的父级会话 ID。',
  guild_id varchar(255) DEFAULT NULL COMMENT '支持 guild/server 概念的平台对应 ID。',
  team_id varchar(255) DEFAULT NULL COMMENT '支持 team/workspace 概念的平台对应 ID。',
  roles_json longtext COMMENT '发送者需要满足的角色过滤条件 JSON。',
  sender_ids_json longtext COMMENT '允许匹配该路由的发送者 ID JSON 列表。',
  mention_aliases_json longtext COMMENT '该路由可识别的提及别名 JSON。',
  command_prefixes_json longtext COMMENT '该路由可识别的命令前缀 JSON。',
  dm_scope varchar(255) NOT NULL COMMENT '私聊消息匹配范围。',
  comment varchar(2048) DEFAULT NULL COMMENT '路由备注。',
  owner_user_id varchar(255) DEFAULT NULL COMMENT '所属用户 ID。',
  managed_by varchar(255) NOT NULL COMMENT '路由来源，例如 manual 或 claw。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最后更新时间。',
  PRIMARY KEY (id)
) COMMENT='将外部 Channel 事件映射到 Claw 和 Agent 的路由规则。';

CREATE TABLE channel_configs (
  id varchar(255) NOT NULL COMMENT 'Channel 配置 ID。',
  channel_type varchar(255) NOT NULL COMMENT 'Channel 类型，例如 feishu 或 wechat。',
  name varchar(255) NOT NULL COMMENT 'Channel 配置展示名称。',
  config_json longtext NOT NULL COMMENT 'Channel 专用的非敏感 JSON 配置。',
  secret_ref varchar(255) DEFAULT NULL COMMENT '包含凭据的 Kubernetes Secret 或外部密钥引用。',
  enabled bit(1) NOT NULL COMMENT '该 Channel 配置是否启用。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最后更新时间。',
  PRIMARY KEY (id)
) COMMENT='外部 Channel 集成配置。';

CREATE TABLE user_secrets (
  id varchar(255) NOT NULL COMMENT '用户密钥记录 ID。',
  owner_user_id varchar(255) NOT NULL COMMENT '密钥所属用户 ID。',
  name varchar(255) NOT NULL COMMENT '密钥展示名称。',
  type varchar(255) NOT NULL COMMENT '密钥类型，例如模型供应商凭据或 Channel 凭据。',
  scope varchar(255) NOT NULL COMMENT '密钥作用域，例如 user、claw 或 platform。',
  claw_id varchar(255) DEFAULT NULL COMMENT '密钥作用域为单个 Claw 时对应的 Claw ID。',
  kubernetes_secret_name varchar(255) DEFAULT NULL COMMENT '运行时使用的 Kubernetes Secret 名称。',
  encrypted_values_json longtext NOT NULL COMMENT '加密后的密钥值 JSON。',
  masked_values_json longtext COMMENT '可安全展示在前端的脱敏预览 JSON。',
  enabled bit(1) NOT NULL COMMENT '该密钥是否启用。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最后更新时间。',
  PRIMARY KEY (id)
) COMMENT='用户密钥元数据及加密后的密钥值。';

-- ---------------------------------------------------------------------------
-- 用量与计费
-- ---------------------------------------------------------------------------

CREATE TABLE usage_records (
  id varchar(255) NOT NULL COMMENT '用量记录 ID。',
  user_id varchar(255) DEFAULT NULL COMMENT '关联用户 ID。',
  session_id varchar(255) DEFAULT NULL COMMENT 'Agent 运行时 Session ID。',
  provider varchar(255) DEFAULT NULL COMMENT '本次运行使用的供应商。',
  model varchar(255) DEFAULT NULL COMMENT '本次运行使用的模型。',
  prompt_tokens bigint DEFAULT NULL COMMENT '输入 Token 数。',
  completion_tokens bigint DEFAULT NULL COMMENT '输出 Token 数。',
  total_tokens bigint DEFAULT NULL COMMENT '总 Token 数。',
  success bit(1) NOT NULL COMMENT '本次运行请求是否成功。',
  latency_ms bigint DEFAULT NULL COMMENT '端到端耗时，单位毫秒。',
  created_at datetime(6) NOT NULL COMMENT '用量事件发生时间。',
  PRIMARY KEY (id)
) COMMENT='模型调用用量记录，用于额度、计量和未来计费。';

-- ---------------------------------------------------------------------------
-- 工具审批
-- ---------------------------------------------------------------------------

CREATE TABLE tool_approval_requests (
  id varchar(64) NOT NULL COMMENT '工具审批请求 ID，必须与 Redis pending approval ID 一致。',
  owner_user_id varchar(64) NOT NULL COMMENT '需要审批或拒绝该工具调用的用户 ID。',
  claw_id varchar(64) NOT NULL COMMENT '工具调用发生的 Claw ID。',
  claw_name varchar(255) DEFAULT NULL COMMENT 'Claw 名称快照，用于前端和审计展示。',
  session_id varchar(128) NOT NULL COMMENT '用于恢复被中断 Agent 运行的运行时 Session ID。',
  agent_id varchar(64) DEFAULT NULL COMMENT '与该工具调用关联的 Agent 模板 ID。',
  agent_key varchar(128) DEFAULT NULL COMMENT 'Agent Key 快照。',
  role_key varchar(128) DEFAULT NULL COMMENT 'Claw 内 Agent 的角色 Key。',
  tool_call_id varchar(128) NOT NULL COMMENT '运行时工具调用 ID，或回退使用的工具标识。',
  tool_name varchar(128) NOT NULL COMMENT 'Agent 请求执行的工具名称。',
  risk varchar(32) NOT NULL COMMENT '风险等级，例如 low、medium 或 high。',
  status varchar(32) NOT NULL COMMENT '审批状态：PENDING、RESUMING、CONSUMED、RESUME_FAILED 或 EXPIRED。',
  decision varchar(32) DEFAULT NULL COMMENT '用户决策：APPROVED 或 REJECTED。决策前为 NULL。',
  intent_summary varchar(1024) DEFAULT NULL COMMENT 'Agent 请求执行工具的自然语言意图摘要。',
  arguments_preview longtext COMMENT '展示给用户的工具参数脱敏预览。',
  pending_state_key varchar(255) NOT NULL COMMENT '保存 pending 运行时状态的 Redis Key。',
  expires_at datetime(6) NOT NULL COMMENT '审批过期时间。',
  resolved_by varchar(64) DEFAULT NULL COMMENT '执行审批或拒绝操作的用户 ID。',
  resolved_at datetime(6) DEFAULT NULL COMMENT '用户作出决策的时间。',
  reject_reason varchar(1024) DEFAULT NULL COMMENT '用户填写的拒绝原因。',
  executing_agent_instance_id varchar(64) DEFAULT NULL COMMENT '请求或执行工具的 Agent 实例 ID，嵌套调用时尤其重要。',
  executing_role_key varchar(128) DEFAULT NULL COMMENT '执行 Agent 实例的角色 Key。',
  calling_agent_instance_id varchar(64) DEFAULT NULL COMMENT '嵌套 call_agent 场景下的调用方 Agent 实例 ID。',
  calling_role_key varchar(128) DEFAULT NULL COMMENT '调用方 Agent 实例的角色 Key。',
  conversation_id varchar(64) DEFAULT NULL COMMENT '该审批关联的对话线程 ID。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最后更新时间。',
  PRIMARY KEY (id),
  KEY idx_tool_approval_owner_created (owner_user_id, created_at),
  KEY idx_tool_approval_claw_created (claw_id, created_at),
  KEY idx_tool_approval_session (session_id),
  KEY idx_tool_approval_status_expires (status, expires_at)
) COMMENT='从 Redis pending approval 状态镜像到 MySQL 的持久化工具审批记录。';

-- ---------------------------------------------------------------------------
-- Agent Marketplace
-- ---------------------------------------------------------------------------

CREATE TABLE agent_packages (
  id varchar(64) NOT NULL COMMENT 'Agent Package ID。',
  package_key varchar(255) NOT NULL COMMENT 'Package Key，在 owner_user_id 下唯一。',
  owner_user_id varchar(64) NOT NULL COMMENT '发布者或所有者用户 ID。',
  name varchar(255) NOT NULL COMMENT 'Package 展示名称。',
  summary varchar(2048) DEFAULT NULL COMMENT 'Marketplace 列表中展示的简短摘要。',
  description text COMMENT 'Package 详细描述。',
  visibility varchar(32) NOT NULL COMMENT '可见性，例如 private、unlisted 或 public。',
  latest_version_id varchar(64) DEFAULT NULL COMMENT '最新已发布版本 ID。',
  install_count bigint NOT NULL DEFAULT 0 COMMENT '累计安装次数。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最后更新时间。',
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_packages_owner_key (owner_user_id, package_key),
  KEY idx_agent_packages_visibility_updated (visibility, updated_at),
  KEY idx_agent_packages_owner_updated (owner_user_id, updated_at)
) COMMENT='Marketplace 中可发布和安装的 Agent Package 元数据。';

CREATE TABLE agent_package_versions (
  id varchar(64) NOT NULL COMMENT 'Agent Package Version ID。',
  package_id varchar(64) NOT NULL COMMENT '所属 Agent Package ID。',
  version varchar(64) NOT NULL COMMENT '版本号，同一 Package 内唯一。',
  status varchar(32) NOT NULL COMMENT '版本生命周期状态，例如 draft、published 或 archived。',
  manifest_json text COMMENT '完整 Package manifest JSON 快照。',
  system_prompt_snapshot text COMMENT '该版本包含的系统提示词快照。',
  persona_files_json text COMMENT '该版本包含的人格文件 JSON 表示。',
  skill_files_json text COMMENT '该版本包含的技能文件 JSON 表示。',
  default_profile varchar(64) NOT NULL COMMENT '该 Agent Package 默认请求的工具 Profile。',
  required_profile varchar(64) DEFAULT NULL COMMENT '最低要求工具 Profile，如果比 default_profile 更严格则填写。',
  capabilities_json text COMMENT '声明的能力列表，用于发现和匹配。',
  input_contract_json text COMMENT '可选输入契约，用于 call_agent 或直接调用。',
  output_contract_json text COMMENT '可选输出契约，用于描述期望响应结构。',
  changelog text COMMENT '版本变更说明。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_package_versions_pkg_ver (package_id, version),
  KEY idx_agent_package_versions_pkg_status_created (package_id, status, created_at),
  KEY idx_agent_package_versions_status_created (status, created_at)
) COMMENT='Agent Package 的版本化发布记录。';

CREATE TABLE agent_install_approvals (
  id varchar(64) NOT NULL COMMENT 'Agent 安装审批 ID。',
  approval_type varchar(255) NOT NULL COMMENT '审批类型，例如 USER_INSTALL 或 AGENT_DISCOVERY_INSTALL。',
  claw_id varchar(255) NOT NULL COMMENT '目标 Claw ID，Package 将安装到该 Claw。',
  owner_user_id varchar(255) NOT NULL COMMENT '需要批准安装的用户 ID。',
  requesting_agent_instance_id varchar(255) DEFAULT NULL COMMENT '运行时自动发现并请求安装的 Agent 实例 ID。',
  package_id varchar(255) NOT NULL COMMENT '请求安装的 Agent Package ID。',
  package_version_id varchar(255) NOT NULL COMMENT '请求安装的 Agent Package Version ID。',
  reason longtext COMMENT '展示给用户的安装原因或说明。',
  status varchar(255) NOT NULL COMMENT '审批状态，例如 PENDING、APPROVED、REJECTED 或 EXPIRED。',
  expires_at datetime(6) DEFAULT NULL COMMENT '待审批记录的过期时间。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  resolved_at datetime(6) DEFAULT NULL COMMENT '审批或拒绝时间。',
  PRIMARY KEY (id),
  KEY idx_agent_install_approvals_claw_status_created (claw_id, status, created_at),
  KEY idx_agent_install_approvals_owner_status_created (owner_user_id, status, created_at),
  KEY idx_agent_install_approvals_requester_created (requesting_agent_instance_id, created_at)
) COMMENT='将 Agent Package 安装到 Claw 前的审批记录。';

-- ---------------------------------------------------------------------------
-- 对话与多 Agent 展示模型
-- ---------------------------------------------------------------------------

CREATE TABLE conversations (
  id varchar(64) NOT NULL COMMENT '对话线程 ID。',
  owner_user_id varchar(64) NOT NULL COMMENT '所属用户 ID。',
  claw_id varchar(64) NOT NULL COMMENT '拥有该对话的 Claw ID。',
  title varchar(255) NOT NULL COMMENT '前端展示的对话标题。',
  status varchar(255) NOT NULL COMMENT '对话生命周期状态，例如 ACTIVE 或 ARCHIVED。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  updated_at datetime(6) NOT NULL COMMENT '最近活动或更新时间。',
  PRIMARY KEY (id),
  KEY idx_conversations_owner_updated (owner_user_id, updated_at),
  KEY idx_conversations_claw_updated (claw_id, updated_at)
) COMMENT='Claw 下的对话线程。';

CREATE TABLE conversation_messages (
  id varchar(64) NOT NULL COMMENT '对话消息或事件 ID。',
  conversation_id varchar(64) NOT NULL COMMENT '所属对话线程 ID。',
  owner_user_id varchar(64) NOT NULL COMMENT '所属用户 ID，用于授权和过滤。',
  claw_id varchar(64) NOT NULL COMMENT '所属 Claw ID，用于授权和过滤。',
  agent_instance_id varchar(255) DEFAULT NULL COMMENT '生成该消息的 Agent 实例 ID。',
  agent_id varchar(255) DEFAULT NULL COMMENT 'Agent 模板 ID 快照。',
  agent_key varchar(255) DEFAULT NULL COMMENT 'Agent Key 快照。',
  role_key varchar(255) DEFAULT NULL COMMENT 'Claw 内角色 Key 快照。',
  provider varchar(255) DEFAULT NULL COMMENT '生成该消息时使用的模型供应商。',
  model varchar(255) DEFAULT NULL COMMENT '生成该消息时使用的模型。',
  role varchar(255) NOT NULL COMMENT '消息角色，例如 user、assistant、tool 或 system。',
  content longtext COMMENT '消息或事件文本内容。',
  created_at datetime(6) NOT NULL COMMENT '创建时间。',
  message_type varchar(32) DEFAULT NULL COMMENT '展示或事件类型，例如 USER_MESSAGE、AGENT_MESSAGE、AGENT_CALL_EVENT 或 TOOL_RESULT_DETAIL。',
  parent_message_id varchar(255) DEFAULT NULL COMMENT '父消息 ID，用于折叠展示嵌套事件。',
  metadata_json longtext COMMENT '结构化事件元数据 JSON，例如 approvalId 或 targetAgentInstanceId。',
  visible_in_thread bit(1) NOT NULL COMMENT '是否作为顶层时间线消息展示。',
  sort_order int NOT NULL COMMENT '同级消息或事件之间的排序值。',
  PRIMARY KEY (id),
  KEY idx_conversation_messages_conv_created (conversation_id, created_at),
  KEY idx_conversation_messages_conv_agent_created (conversation_id, agent_instance_id, created_at)
) COMMENT='组成多 Agent 对话时间线的消息与折叠事件。';
