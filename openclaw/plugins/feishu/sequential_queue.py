"""Per-key sequential async queue."""

from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from typing import TypeVar

T = TypeVar("T")


class SequentialQueue:
    def __init__(self, *, timeout_seconds: float | None = None) -> None:
        self.timeout_seconds = timeout_seconds
        self._locks: dict[str, asyncio.Lock] = {}
        self._guard = asyncio.Lock()

    async def run(self, key: str, task: Callable[[], Awaitable[T]]) -> T:
        lock = await self._lock_for_key(key)
        async with lock:
            if self.timeout_seconds is None:
                return await task()
            return await asyncio.wait_for(task(), timeout=self.timeout_seconds)

    async def _lock_for_key(self, key: str) -> asyncio.Lock:
        async with self._guard:
            lock = self._locks.get(key)
            if lock is None:
                lock = asyncio.Lock()
                self._locks[key] = lock
            return lock
