"""Feishu webhook signature helpers."""

from __future__ import annotations

import base64
import hashlib
import hmac


def build_feishu_signature(timestamp: str, nonce: str, secret: str, body: bytes) -> str:
    raw = f"{timestamp}{nonce}{secret}".encode("utf-8") + body
    digest = hmac.new(raw, digestmod=hashlib.sha256).digest()
    return base64.b64encode(digest).decode("utf-8")


def verify_feishu_signature(timestamp: str, nonce: str, secret: str, body: bytes, signature: str) -> bool:
    expected = build_feishu_signature(timestamp, nonce, secret, body)
    return hmac.compare_digest(expected, signature)
