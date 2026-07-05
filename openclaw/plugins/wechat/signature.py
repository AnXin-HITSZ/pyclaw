"""WeChat webhook signature helpers."""

from __future__ import annotations

import hashlib
import hmac


def build_wechat_signature(token: str, timestamp: str, nonce: str) -> str:
    """Build the SHA-1 signature used by WeChat official account callbacks."""
    pieces = sorted([token, timestamp, nonce])
    raw = "".join(pieces).encode("utf-8")
    return hashlib.sha1(raw).hexdigest()


def verify_wechat_signature(token: str, timestamp: str, nonce: str, signature: str) -> bool:
    expected = build_wechat_signature(token, timestamp, nonce)
    return hmac.compare_digest(expected, signature)
