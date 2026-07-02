"""Custom hook protocol for project-specific logic."""

from __future__ import annotations

from typing import Protocol

from openclaw.llm.types import AgentMessage, AssistantMessage, ToolCallBlock, ToolResultMessage


class SessionHooks(Protocol):
    async def before_model_call(self, context: list[AgentMessage]) -> list[AgentMessage]:
        ...

    async def after_model_message(self, message: AssistantMessage) -> AssistantMessage:
        ...

    async def before_tool_call(self, call: ToolCallBlock) -> ToolCallBlock:
        ...

    async def after_tool_result(self, result: ToolResultMessage) -> ToolResultMessage:
        ...

    async def after_message_persisted(self, message: AgentMessage) -> None:
        ...


class NoopSessionHooks:
    async def before_model_call(self, context: list[AgentMessage]) -> list[AgentMessage]:
        return context

    async def after_model_message(self, message: AssistantMessage) -> AssistantMessage:
        return message

    async def before_tool_call(self, call: ToolCallBlock) -> ToolCallBlock:
        return call

    async def after_tool_result(self, result: ToolResultMessage) -> ToolResultMessage:
        return result

    async def after_message_persisted(self, message: AgentMessage) -> None:
        return None
