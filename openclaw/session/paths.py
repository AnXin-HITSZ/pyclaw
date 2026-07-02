"""Session path helpers."""

from __future__ import annotations

import os
from pathlib import Path

DEFAULT_CHATDATA_DIR = "chatdata"


def resolve_chatdata_dir(base_dir: str | Path | None = None) -> Path:
    """Resolve the directory used for local chat/session data."""

    if base_dir is not None:
        return Path(base_dir)
    env_value = os.environ.get("OPENCLAW_CHATDATA_DIR")
    if env_value:
        return Path(env_value)
    return Path.cwd() / DEFAULT_CHATDATA_DIR


def resolve_session_transcript_path(base_dir: str | Path, session_id: str) -> Path:
    return Path(base_dir) / f"{session_id}.jsonl"


def resolve_session_store_path(base_dir: str | Path) -> Path:
    return Path(base_dir) / "sessions.json"
