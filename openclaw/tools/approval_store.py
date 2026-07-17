"""Store for pending tool approval runtime state.

Runtime state contains just enough context to rebuild the Agent loop when the
user resumes an approval (messages, the pending assistant tool_call, non-secret
identifiers). Secret material (API keys, encrypted tokens, provider config) is
NEVER persisted here; Spring re-injects it on ``/v1/agent/resume``.

The store is backed by Redis only. ``OPENCLAW_REDIS_URL`` must be configured at
startup; the API refuses to start otherwise. Tests inject an in-memory fake
via ``api._set_pending_approval_store``.
"""

from __future__ import annotations

import json
import os
from typing import Any, Protocol

DEFAULT_TTL_SECONDS = 30 * 60


class PendingApprovalStore(Protocol):
    def save(self, approval_id: str, state: dict[str, Any], ttl_seconds: int = DEFAULT_TTL_SECONDS) -> None: ...

    def load(self, approval_id: str) -> dict[str, Any] | None: ...

    def delete(self, approval_id: str) -> None: ...


def pending_state_key(approval_id: str) -> str:
    return f"agent:pending_approval:{approval_id}"


class RedisPendingApprovalStore:
    """Redis-backed pending approval store."""

    def __init__(self, client: Any) -> None:
        self.client = client

    def save(self, approval_id: str, state: dict[str, Any], ttl_seconds: int = DEFAULT_TTL_SECONDS) -> None:
        self.client.set(
            pending_state_key(approval_id),
            json.dumps(state, ensure_ascii=False),
            ex=max(1, int(ttl_seconds)),
        )

    def load(self, approval_id: str) -> dict[str, Any] | None:
        raw = self.client.get(pending_state_key(approval_id))
        if raw is None:
            return None
        if isinstance(raw, bytes):
            raw = raw.decode("utf-8")
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            return None
        return dict(data) if isinstance(data, dict) else None

    def delete(self, approval_id: str) -> None:
        self.client.delete(pending_state_key(approval_id))


def build_default_pending_approval_store() -> PendingApprovalStore:
    """Construct the production pending-approval store.

    Requires ``OPENCLAW_REDIS_URL`` (or ``REDIS_URL``) to be set. The API refuses
    to start without it because pending approval state is required for the
    tool approval flow to function correctly.
    """

    redis_url = os.environ.get("OPENCLAW_REDIS_URL") or os.environ.get("REDIS_URL")
    if not redis_url:
        raise RuntimeError(
            "OPENCLAW_REDIS_URL (or REDIS_URL) must be configured for the pending approval store"
        )
    import redis  # type: ignore[import-not-found]

    client = redis.Redis.from_url(redis_url)
    return RedisPendingApprovalStore(client)


__all__ = [
    "DEFAULT_TTL_SECONDS",
    "PendingApprovalStore",
    "RedisPendingApprovalStore",
    "pending_state_key",
    "build_default_pending_approval_store",
]
