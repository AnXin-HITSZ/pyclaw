"""Message adapter protocols and defaults."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

from openclaw.channels.message.types import (
    ChannelMessageReceiveAckPolicy,
    ChannelMessageSendResult,
    ChannelMessageSendTextContext,
    PreparedInboundMessage,
    RawInboundEvent,
)


class ChannelMessageSendLifecycleAdapter(Protocol):
    async def before_send_attempt(self, context: ChannelMessageSendTextContext) -> None: ...
    async def after_send_success(self, context: ChannelMessageSendTextContext, result: ChannelMessageSendResult) -> None: ...
    async def after_send_failure(self, context: ChannelMessageSendTextContext, error: BaseException) -> None: ...
    async def after_commit(self, context: ChannelMessageSendTextContext, result: ChannelMessageSendResult) -> None: ...


class ChannelMessageSendAdapter(Protocol):
    lifecycle: ChannelMessageSendLifecycleAdapter | None

    async def text(self, context: ChannelMessageSendTextContext) -> ChannelMessageSendResult:
        """Send text to a platform conversation."""


class ChannelMessageReceiveAdapter(Protocol):
    default_ack_policy: ChannelMessageReceiveAckPolicy

    async def prepare(self, event: RawInboundEvent) -> PreparedInboundMessage:
        """Normalize a raw platform payload into a prepared inbound message."""


@dataclass
class ChannelMessageAdapter:
    send: ChannelMessageSendAdapter | None = None
    receive: ChannelMessageReceiveAdapter | None = None


@dataclass
class ManualReceiveAdapter:
    default_ack_policy: ChannelMessageReceiveAckPolicy = ChannelMessageReceiveAckPolicy.MANUAL

    async def prepare(self, event: RawInboundEvent) -> PreparedInboundMessage:
        payload = event.platform_payload
        text = str(payload.get("text", ""))
        conversation_id = str(payload.get("conversation_id", payload.get("chat_id", event.id)))
        sender_id = str(payload.get("sender_id", payload.get("from_id", "unknown")))
        return PreparedInboundMessage(
            id=event.id,
            channel=event.channel,
            account_id=event.account_id,
            conversation_id=conversation_id,
            sender_id=sender_id,
            text=text,
            raw=payload,
        )


def define_channel_message_adapter(
    *,
    send: ChannelMessageSendAdapter | None = None,
    receive: ChannelMessageReceiveAdapter | None = None,
) -> ChannelMessageAdapter:
    return ChannelMessageAdapter(send=send, receive=receive or ManualReceiveAdapter())

