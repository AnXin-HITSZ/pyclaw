-- Agent Marketplace + Conversation + Agent Install Approval base schema.
-- Portable across H2 (PostgreSQL mode, dev/test) and MySQL (prod).
-- String IDs are application-generated UUIDs (VARCHAR(64)).
-- @Lob columns are stored as TEXT.

CREATE TABLE agent_packages (
    id              VARCHAR(64)  NOT NULL,
    package_key     VARCHAR(255) NOT NULL,
    owner_user_id   VARCHAR(64)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    summary         VARCHAR(2048),
    description     TEXT,
    visibility      VARCHAR(32)  NOT NULL,
    latest_version_id VARCHAR(64),
    install_count   BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_agent_packages PRIMARY KEY (id),
    CONSTRAINT uk_agent_packages_owner_key UNIQUE (owner_user_id, package_key)
);
CREATE INDEX idx_agent_packages_visibility_updated ON agent_packages (visibility, updated_at);
CREATE INDEX idx_agent_packages_owner_updated ON agent_packages (owner_user_id, updated_at);

CREATE TABLE agent_package_versions (
    id                       VARCHAR(64)  NOT NULL,
    package_id               VARCHAR(64)  NOT NULL,
    version                  VARCHAR(64)  NOT NULL,
    status                   VARCHAR(32)  NOT NULL,
    manifest_json            TEXT,
    system_prompt_snapshot   TEXT,
    persona_files_json       TEXT,
    skill_files_json         TEXT,
    default_profile          VARCHAR(64)  NOT NULL,
    required_profile         VARCHAR(64),
    capabilities_json        TEXT,
    input_contract_json      TEXT,
    output_contract_json     TEXT,
    changelog                TEXT,
    created_at               TIMESTAMP    NOT NULL,
    CONSTRAINT pk_agent_package_versions PRIMARY KEY (id),
    CONSTRAINT uk_agent_package_versions_pkg_ver UNIQUE (package_id, version)
);
CREATE INDEX idx_agent_package_versions_pkg_status_created ON agent_package_versions (package_id, status, created_at);
CREATE INDEX idx_agent_package_versions_status_created ON agent_package_versions (status, created_at);

CREATE TABLE conversations (
    id            VARCHAR(64)  NOT NULL,
    owner_user_id VARCHAR(64)  NOT NULL,
    claw_id       VARCHAR(64)  NOT NULL,
    title         VARCHAR(512) NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_conversations PRIMARY KEY (id)
);
CREATE INDEX idx_conversations_owner_updated ON conversations (owner_user_id, updated_at);
CREATE INDEX idx_conversations_claw_updated ON conversations (claw_id, updated_at);

CREATE TABLE conversation_messages (
    id                 VARCHAR(64)  NOT NULL,
    conversation_id    VARCHAR(64)  NOT NULL,
    owner_user_id      VARCHAR(64)  NOT NULL,
    claw_id            VARCHAR(64)  NOT NULL,
    agent_instance_id  VARCHAR(64),
    agent_id           VARCHAR(64),
    agent_key          VARCHAR(255),
    role_key           VARCHAR(255),
    provider           VARCHAR(255),
    model              VARCHAR(255),
    role               VARCHAR(32)  NOT NULL,
    content            TEXT,
    created_at         TIMESTAMP    NOT NULL,
    CONSTRAINT pk_conversation_messages PRIMARY KEY (id)
);
CREATE INDEX idx_conversation_messages_conv_created ON conversation_messages (conversation_id, created_at);
CREATE INDEX idx_conversation_messages_conv_agent_created ON conversation_messages (conversation_id, agent_instance_id, created_at);

CREATE TABLE agent_install_approvals (
    id                            VARCHAR(64)  NOT NULL,
    approval_type                 VARCHAR(32)  NOT NULL,
    claw_id                       VARCHAR(64)  NOT NULL,
    owner_user_id                 VARCHAR(64)  NOT NULL,
    requesting_agent_instance_id  VARCHAR(64),
    package_id                    VARCHAR(64)  NOT NULL,
    package_version_id            VARCHAR(64)  NOT NULL,
    reason                        TEXT,
    status                        VARCHAR(32)  NOT NULL,
    expires_at                    TIMESTAMP,
    created_at                    TIMESTAMP    NOT NULL,
    resolved_at                   TIMESTAMP,
    CONSTRAINT pk_agent_install_approvals PRIMARY KEY (id)
);
CREATE INDEX idx_agent_install_approvals_claw_status_created ON agent_install_approvals (claw_id, status, created_at);
CREATE INDEX idx_agent_install_approvals_owner_status_created ON agent_install_approvals (owner_user_id, status, created_at);
CREATE INDEX idx_agent_install_approvals_requester_created ON agent_install_approvals (requesting_agent_instance_id, created_at);
