"""Dispatch prepared channel messages into AgentSession turns."""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from dataclasses import dataclass

from openclaw.channels.message.adapter import ChannelMessageSendAdapter
from openclaw.channels.message.send import send_text_message
from openclaw.channels.message.types import (
    ChannelMessageSendResult,
    ChannelMessageSendTextContext,
    PreparedInboundMessage,
)
from openclaw.llm.types import AssistantMessage
from openclaw.session.agent_session import AgentSession

SessionFactory = Callable[[PreparedInboundMessage], AgentSession | Awaitable[AgentSession]]


@dataclass(frozen=True)
class ChannelTurnResult:
    inbound: PreparedInboundMessage
    session_id: str
    assistant: AssistantMessage
    assistant_text: str
    send_result: ChannelMessageSendResult | None = None


class ChannelTurnDispatcher:
    def __init__(
        self,
        *,
        session_factory: SessionFactory,
        send_adapters: dict[str, ChannelMessageSendAdapter] | None = None,
        prompt_template: str | None = None,
    ) -> None:
        self.session_factory = session_factory
        self.send_adapters = dict(send_adapters or {})
        self.prompt_template = prompt_template

    async def dispatch(self, message: PreparedInboundMessage) -> ChannelTurnResult:
        session = await _maybe_await(self.session_factory(message))
        prompt = self._build_prompt(message)
        assistant = await session.run_prompt(prompt)
        text = assistant_text(assistant)
        send_result = None
        adapter = self.send_adapters.get(message.channel)
        if adapter is not None and text:
            send_result = await send_text_message(
                adapter,
                ChannelMessageSendTextContext(
                    channel=message.channel,
                    account_id=message.account_id,
                    conversation_id=message.conversation_id,
                    text=text,
                    thread_id=message.thread_id,
                    reply_to_id=message.reply_to_id,
                    metadata={
                        "inbound_id": message.id,
                        "sender_id": message.sender_id,
                        "raw": dict(message.raw),
                    },
                ),
            )
        return ChannelTurnResult(
            inbound=message,
            session_id=session.session_id,
            assistant=assistant,
            assistant_text=text,
            send_result=send_result,
        )

    def _build_prompt(self, message: PreparedInboundMessage) -> str:
        if self.prompt_template:
            return self.prompt_template.format(
                channel=message.channel,
                conversation_id=message.conversation_id,
                sender_id=message.sender_id,
                text=message.text,
            )
        return message.text


def build_channel_session_id(message: PreparedInboundMessage) -> str:
    parts = [message.channel, message.account_id or "default", message.conversation_id]
    return "channel-" + "-".join(_safe_session_part(part) for part in parts if part)


def assistant_text(message: AssistantMessage) -> str:
    parts: list[str] = []
    for block in message.content:
        if block.get("type") == "text":
            parts.append(str(block.get("text", "")))
    if parts:
        return "".join(parts)
    return message.error_message or ""


async def _maybe_await(value: AgentSession | Awaitable[AgentSession]) -> AgentSession:
    if hasattr(value, "__await__"):
        return await value  # type: ignore[misc]
    return value  # type: ignore[return-value]


def _safe_session_part(value: str) -> str:
    cleaned = "".join(char if char.isalnum() or char in {"-", "_"} else "-" for char in value.strip())
    return cleaned.strip("-_") or "unknown"
