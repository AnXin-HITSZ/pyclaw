"""Channel platform configuration loading."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class ChannelRuntimeConfig:
    channel: str
    account_id: str | None = None
    name: str | None = None
    enabled: bool = True
    config: dict[str, Any] = field(default_factory=dict)

    def require(self, key: str) -> str:
        value = self.config.get(key)
        if value is None or str(value) == "":
            raise ValueError(f"{self.channel} channel config requires {key}")
        return str(value)

    def get_str(self, key: str, default: str | None = None) -> str | None:
        value = self.config.get(key, default)
        if value is None:
            return None
        return str(value)

    def get_bool(self, key: str, default: bool = False) -> bool:
        value = self.config.get(key, default)
        if isinstance(value, bool):
            return value
        return str(value).strip().lower() in {"1", "true", "yes", "on"}



@dataclass(frozen=True)
class ChannelAgentConfig:
    provider: str = "openai"
    model: str | None = None
    system: str | None = None
    api_mode: str = "auto"
    chatdata_dir: str | None = None
    tool_profile: str = "messaging"
    shell_approval: str = "deny"
    webhook_sync: bool = False


def load_channel_agent_config() -> ChannelAgentConfig:
    """Load shared runtime settings used when channel webhooks dispatch to Agent."""

    provider = os.environ.get("OPENCLAW_CHANNEL_PROVIDER") or os.environ.get("OPENCLAW_API_PROVIDER") or "openai"
    return ChannelAgentConfig(
        provider=provider,
        model=_optional_str(os.environ.get("OPENCLAW_CHANNEL_MODEL")),
        system=_optional_str(os.environ.get("OPENCLAW_CHANNEL_SYSTEM")),
        api_mode=os.environ.get("OPENCLAW_CHANNEL_API_MODE", "auto"),
        chatdata_dir=_optional_str(os.environ.get("OPENCLAW_CHANNEL_CHATDATA_DIR")),
        tool_profile=os.environ.get("OPENCLAW_CHANNEL_TOOL_PROFILE", "messaging"),
        shell_approval=os.environ.get("OPENCLAW_CHANNEL_SHELL_APPROVAL", "deny"),
        webhook_sync=_env_bool("OPENCLAW_CHANNEL_WEBHOOK_SYNC", default=False),
    )


def load_channel_config(channel: str, *, account_id: str | None = None) -> ChannelRuntimeConfig:
    """Load a channel config from JSON/YAML/env variables.

    Priority:
    1. OPENCLAW_<CHANNEL>_CONFIG_JSON
    2. OPENCLAW_<CHANNEL>_CONFIG_FILE
    3. OPENCLAW_CHANNEL_CONFIG_FILE, keyed by channel
    4. Channel-specific environment variables
    """

    normalized = channel.strip().lower()
    prefix = f"OPENCLAW_{normalized.upper()}_"
    raw = _load_channel_mapping(normalized, prefix)
    if account_id:
        raw.setdefault("account_id", account_id)
    return ChannelRuntimeConfig(
        channel=normalized,
        account_id=_optional_str(raw.get("account_id") or raw.get("accountId")),
        name=_optional_str(raw.get("name")),
        enabled=_coerce_bool(raw.get("enabled", True)),
        config={_normalize_key(key): value for key, value in raw.items()},
    )


def _load_channel_mapping(channel: str, prefix: str) -> dict[str, Any]:
    config_json = os.environ.get(f"{prefix}CONFIG_JSON")
    if config_json:
        return _as_mapping(json.loads(config_json), source=f"{prefix}CONFIG_JSON")

    config_file = os.environ.get(f"{prefix}CONFIG_FILE")
    if config_file:
        return _as_mapping(_load_structured_file(Path(config_file)), source=config_file)

    shared_file = os.environ.get("OPENCLAW_CHANNEL_CONFIG_FILE")
    if shared_file:
        shared = _as_mapping(_load_structured_file(Path(shared_file)), source=shared_file)
        selected = shared.get(channel) or shared.get(f"{channel}s") or shared.get("channels", {}).get(channel)
        if selected is not None:
            return _as_mapping(selected, source=f"{shared_file}:{channel}")

    if channel == "wechat":
        return {
            "name": os.environ.get("OPENCLAW_WECHAT_NAME", "wechat"),
            "account_id": os.environ.get("OPENCLAW_WECHAT_ACCOUNT_ID"),
            "token": os.environ.get("OPENCLAW_WECHAT_TOKEN"),
            "app_id": os.environ.get("OPENCLAW_WECHAT_APP_ID"),
            "app_secret": os.environ.get("OPENCLAW_WECHAT_APP_SECRET"),
            "access_token": os.environ.get("OPENCLAW_WECHAT_ACCESS_TOKEN"),
            "api_base_url": os.environ.get("OPENCLAW_WECHAT_API_BASE_URL"),
            "enabled": os.environ.get("OPENCLAW_WECHAT_ENABLED", "true"),
        }
    if channel == "feishu":
        return {
            "name": os.environ.get("OPENCLAW_FEISHU_NAME", "feishu"),
            "account_id": os.environ.get("OPENCLAW_FEISHU_ACCOUNT_ID"),
            "app_id": os.environ.get("OPENCLAW_FEISHU_APP_ID"),
            "app_secret": os.environ.get("OPENCLAW_FEISHU_APP_SECRET"),
            "verification_token": os.environ.get("OPENCLAW_FEISHU_VERIFICATION_TOKEN"),
            "encrypt_key": os.environ.get("OPENCLAW_FEISHU_ENCRYPT_KEY"),
            "sign_secret": os.environ.get("OPENCLAW_FEISHU_SIGN_SECRET"),
            "tenant_access_token": os.environ.get("OPENCLAW_FEISHU_TENANT_ACCESS_TOKEN"),
            "api_base_url": os.environ.get("OPENCLAW_FEISHU_API_BASE_URL"),
            "enabled": os.environ.get("OPENCLAW_FEISHU_ENABLED", "true"),
        }
    raise ValueError(f"unknown channel config: {channel}")


def _load_structured_file(path: Path) -> Any:
    text = path.read_text(encoding="utf-8")
    if path.suffix.lower() in {".yaml", ".yml"}:
        try:
            import yaml  # type: ignore[import-untyped]
        except ImportError:
            return _parse_simple_yaml(text)
        return yaml.safe_load(text) or {}
    return json.loads(text)


def _parse_simple_yaml(text: str) -> dict[str, Any]:
    """Parse the simple key/value YAML shape used for secret configs."""

    root: dict[str, Any] = {}
    stack: list[tuple[int, dict[str, Any]]] = [(-1, root)]
    for raw_line in text.splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        indent = len(raw_line) - len(raw_line.lstrip(" "))
        line = raw_line.strip()
        if ":" not in line:
            raise ValueError("YAML config requires PyYAML for complex files")
        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip()
        while stack and indent <= stack[-1][0]:
            stack.pop()
        parent = stack[-1][1]
        if value == "":
            child: dict[str, Any] = {}
            parent[key] = child
            stack.append((indent, child))
        else:
            parent[key] = _strip_yaml_scalar(value)
    return root


def _strip_yaml_scalar(value: str) -> Any:
    if value in {"true", "True"}:
        return True
    if value in {"false", "False"}:
        return False
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def _as_mapping(value: Any, *, source: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"channel config from {source} must be an object")
    return dict(value)


def _normalize_key(value: str) -> str:
    result: list[str] = []
    for char in value:
        if char.isupper():
            result.append("_")
            result.append(char.lower())
        else:
            result.append(char)
    return "".join(result).strip("_")


def _optional_str(value: Any) -> str | None:
    if value is None or value == "":
        return None
    return str(value)


def _coerce_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "on"}


def _env_bool(name: str, *, default: bool = False) -> bool:
    value = os.environ.get(name)
    if value is None:
        return default
    return _coerce_bool(value)