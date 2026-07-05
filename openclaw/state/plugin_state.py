"""Keyed plugin state store with atomic JSON persistence."""

from __future__ import annotations

import json
import os
import time
from pathlib import Path
from typing import Any, Generic, Protocol, TypeVar

T = TypeVar("T")


class PluginStateKeyedStore(Protocol[T]):
    async def register(self, key: str, value: T, ttl_ms: int | None = None) -> None: ...
    async def lookup(self, key: str) -> T | None: ...
    async def delete(self, key: str) -> bool: ...


class JsonPluginStateStore(Generic[T]):
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)

    async def register(self, key: str, value: T, ttl_ms: int | None = None) -> None:
        data = self._read()
        expires_at = None if ttl_ms is None else time.time() + ttl_ms / 1000
        data[key] = {"value": value, "expires_at": expires_at}
        self._write(data)

    async def lookup(self, key: str) -> T | None:
        data = self._read()
        entry = data.get(key)
        if not isinstance(entry, dict):
            return None
        expires_at = entry.get("expires_at")
        if isinstance(expires_at, (int, float)) and expires_at <= time.time():
            data.pop(key, None)
            self._write(data)
            return None
        return entry.get("value")

    async def delete(self, key: str) -> bool:
        data = self._read()
        existed = key in data
        data.pop(key, None)
        if existed:
            self._write(data)
        return existed

    def _read(self) -> dict[str, Any]:
        if not self.path.exists():
            return {}
        text = self.path.read_text(encoding="utf-8")
        if not text.strip():
            return {}
        raw = json.loads(text)
        return raw if isinstance(raw, dict) else {}

    def _write(self, data: dict[str, Any]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp_path = self.path.with_suffix(self.path.suffix + ".tmp")
        with tmp_path.open("w", encoding="utf-8", newline="\n") as handle:
            json.dump(data, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_path, self.path)

