"""Transcript write guard for tool results."""

from __future__ import annotations

import copy
import json
import re
from dataclasses import dataclass, field

from openclaw.llm.types import AgentMessage

SECRET_PATTERN = re.compile(r"(?i)(api[_-]?key|token|secret|password)=([^\s]+)")


@dataclass
class TranscriptToolResultGuard:
    max_tool_result_chars: int = 50_000
    redaction: str = "[REDACTED]"
    secret_pattern: re.Pattern[str] = field(default=SECRET_PATTERN)

    def before_append(self, message: AgentMessage) -> AgentMessage:
        cloned = copy.deepcopy(message)
        for block in cloned.content:
            if block.get("type") != "toolResult":
                continue
            if "name" in block:
                block["name"] = str(block["name"]).strip()
            output = block.get("output")
            text = output if isinstance(output, str) else json.dumps(output, ensure_ascii=False)
            text = self.secret_pattern.sub(lambda m: f"{m.group(1)}={self.redaction}", text)
            if len(text) > self.max_tool_result_chars:
                text = text[: self.max_tool_result_chars] + "\n...[truncated]"
            block["output"] = text
        return cloned
