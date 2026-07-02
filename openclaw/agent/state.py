"""Mutable in-memory agent state."""

from __future__ import annotations

from dataclasses import dataclass, field

from openclaw.llm.types import AgentMessage, AssistantMessage


@dataclass
class AgentState:
    messages: list[AgentMessage] = field(default_factory=list)
    streaming_message: AssistantMessage | None = None
    pending_tool_call_ids: set[str] = field(default_factory=set)
    last_error: str | None = None
    aborted: bool = False
