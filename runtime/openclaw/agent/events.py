"""Agent event definitions and helpers."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Literal

AgentEventType = Literal[
    "agent_start",
    "agent_end",
    "turn_start",
    "turn_end",
    "message_start",
    "message_update",
    "message_end",
    "tool_execution_start",
    "tool_execution_end",
    "auto_retry_start",
    "auto_retry_end",
]


@dataclass
class AgentEvent:
    type: AgentEventType
    payload: dict[str, Any]


def message_start_event(message: Any) -> AgentEvent:
    return AgentEvent("message_start", {"message": message})


def message_update_event(message: Any) -> AgentEvent:
    return AgentEvent("message_update", {"message": message})


def message_end_event(message: Any) -> AgentEvent:
    return AgentEvent("message_end", {"message": message})


def turn_end_event(message: Any) -> AgentEvent:
    return AgentEvent("turn_end", {"message": message})
