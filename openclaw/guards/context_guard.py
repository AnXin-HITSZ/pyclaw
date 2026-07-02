"""Model-call context guard."""

from __future__ import annotations

import copy
import json
from dataclasses import dataclass
from typing import Any

from openclaw.llm.types import AgentMessage


class ContextOverflowError(RuntimeError):
    """Raised when the next provider request would exceed the configured budget."""


@dataclass
class ContextGuard:
    max_context_chars: int = 120_000
    max_tool_result_chars: int = 20_000

    def transform(self, messages: list[AgentMessage]) -> list[AgentMessage]:
        cloned = copy.deepcopy(messages)
        for message in cloned:
            for block in message.content:
                if block.get("type") != "toolResult":
                    continue
                output = block.get("output")
                text = output if isinstance(output, str) else json.dumps(output, ensure_ascii=False)
                if len(text) > self.max_tool_result_chars:
                    block["output"] = text[: self.max_tool_result_chars] + "\n...[truncated]"

        size = self.estimate_chars(cloned)
        if size > self.max_context_chars:
            raise ContextOverflowError(f"context length exceeded: {size} > {self.max_context_chars}")
        return cloned

    def estimate_chars(self, messages: list[AgentMessage]) -> int:
        payload: list[dict[str, Any]] = [
            {"role": message.role, "content": message.content} for message in messages
        ]
        return len(json.dumps(payload, ensure_ascii=False))
