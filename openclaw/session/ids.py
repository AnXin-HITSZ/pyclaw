"""Session identifier helpers."""

from __future__ import annotations

import re


def sanitize_session_id(value: str) -> str:
    sanitized = re.sub(r"[^A-Za-z0-9._-]+", "-", value.strip())
    sanitized = sanitized.strip(".-_")
    if not sanitized:
        raise ValueError("session id must contain at least one letter or number")
    return sanitized