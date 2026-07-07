"""Small async HTTP helpers for channel adapters."""

from __future__ import annotations

import asyncio
import json
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any, Protocol


@dataclass(frozen=True)
class HttpResponse:
    status: int
    body: bytes
    headers: dict[str, str]

    def json(self) -> dict[str, Any]:
        if not self.body:
            return {}
        value = json.loads(self.body.decode("utf-8"))
        if not isinstance(value, dict):
            raise ValueError("HTTP response JSON body must be an object")
        return value


class AsyncHttpClient(Protocol):
    async def post_json(
        self,
        url: str,
        payload: dict[str, Any],
        *,
        headers: dict[str, str] | None = None,
        timeout_seconds: float = 10,
    ) -> HttpResponse:
        ...

    async def get_json(
        self,
        url: str,
        *,
        headers: dict[str, str] | None = None,
        timeout_seconds: float = 10,
    ) -> HttpResponse:
        ...


class UrlLibHttpClient:
    async def post_json(
        self,
        url: str,
        payload: dict[str, Any],
        *,
        headers: dict[str, str] | None = None,
        timeout_seconds: float = 10,
    ) -> HttpResponse:
        return await asyncio.to_thread(
            _request_json,
            "POST",
            url,
            payload,
            headers or {},
            timeout_seconds,
        )

    async def get_json(
        self,
        url: str,
        *,
        headers: dict[str, str] | None = None,
        timeout_seconds: float = 10,
    ) -> HttpResponse:
        return await asyncio.to_thread(
            _request_json,
            "GET",
            url,
            None,
            headers or {},
            timeout_seconds,
        )


def _request_json(
    method: str,
    url: str,
    payload: dict[str, Any] | None,
    headers: dict[str, str],
    timeout_seconds: float,
) -> HttpResponse:
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request_headers = {"Accept": "application/json", **headers}
    if payload is not None:
        request_headers.setdefault("Content-Type", "application/json; charset=utf-8")
    request = urllib.request.Request(url, data=body, method=method, headers=request_headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            return HttpResponse(
                status=int(response.status),
                body=response.read(),
                headers=dict(response.headers.items()),
            )
    except urllib.error.HTTPError as exc:
        return HttpResponse(
            status=int(exc.code),
            body=exc.read(),
            headers=dict(exc.headers.items()),
        )
