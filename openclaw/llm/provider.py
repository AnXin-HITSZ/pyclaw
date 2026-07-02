"""Provider protocol and a deterministic mock provider for tests."""

from __future__ import annotations

from collections.abc import AsyncIterator, Iterable
from dataclasses import dataclass
from typing import Any, Literal, Protocol

from openclaw.llm.types import AssistantMessage


@dataclass
class ProviderEvent:
    type: Literal["start", "delta", "done", "error"]
    data: dict[str, Any]


class LlmProvider(Protocol):
    async def stream(
        self,
        *,
        model: str,
        system_prompt: str,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
        options: dict[str, Any] | None = None,
    ) -> AsyncIterator[ProviderEvent]:
        ...


class MockProvider:
    """Deterministic provider that yields scripted assistant messages or errors."""

    def __init__(self, responses: Iterable[AssistantMessage | Exception]) -> None:
        self._responses = list(responses)
        self.calls: list[dict[str, Any]] = []

    async def stream(
        self,
        *,
        model: str,
        system_prompt: str,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
        options: dict[str, Any] | None = None,
    ) -> AsyncIterator[ProviderEvent]:
        self.calls.append(
            {
                "model": model,
                "system_prompt": system_prompt,
                "messages": messages,
                "tools": tools,
                "options": options or {},
            }
        )
        if not self._responses:
            raise RuntimeError("mock provider has no scripted response")

        response = self._responses.pop(0)
        if isinstance(response, Exception):
            raise response

        yield ProviderEvent("start", {"provider": "mock", "model": model})
        yield ProviderEvent("done", {"message": response})
