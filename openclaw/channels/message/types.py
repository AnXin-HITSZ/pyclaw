"""Shared channel message dataclasses."""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any, Mapping


class ChannelMessageReceiveAckPolicy(StrEnum):
    AFTER_RECEIVE_RECORD = "after_receive_record"
    AFTER_AGENT_DISPATCH = "after_agent_dispatch"
    AFTER_DURABLE_SEND = "after_durable_send"
    MANUAL = "manual"


class ChannelMessageReceiveStage(StrEnum):
    RECEIVE_RECORD = "receive_record"
    AGENT_DISPATCH = "agent_dispatch"
    DURABLE_SEND = "durable_send"


ACK_STAGE_FOR_POLICY: dict[ChannelMessageReceiveAckPolicy, ChannelMessageReceiveStage | None] = {
    ChannelMessageReceiveAckPolicy.AFTER_RECEIVE_RECORD: ChannelMessageReceiveStage.RECEIVE_RECORD,
    ChannelMessageReceiveAckPolicy.AFTER_AGENT_DISPATCH: ChannelMessageReceiveStage.AGENT_DISPATCH,
    ChannelMessageReceiveAckPolicy.AFTER_DURABLE_SEND: ChannelMessageReceiveStage.DURABLE_SEND,
    ChannelMessageReceiveAckPolicy.MANUAL: None,
}


@dataclass(frozen=True)
class Attachment:
    id: str | None = None
    media_type: str | None = None
    url: str | None = None
    filename: str | None = None
    size: int | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class RawInboundEvent:
    id: str
    channel: str
    account_id: str | None
    platform_payload: Mapping[str, Any]
    received_at: float = field(default_factory=time.time)
    ack_policy: ChannelMessageReceiveAckPolicy = ChannelMessageReceiveAckPolicy.MANUAL
    lane_key: str | None = None


@dataclass(frozen=True)
class PreparedInboundMessage:
    id: str
    channel: str
    account_id: str | None
    conversation_id: str
    sender_id: str
    text: str
    thread_id: str | None = None
    reply_to_id: str | None = None
    attachments: list[Attachment] = field(default_factory=list)
    raw: Mapping[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class MessageReceiptPart:
    platform_message_id: str | None = None
    status: str = "sent"
    index: int = 0
    payload_kind: str = "text"
    error: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class MessageReceipt:
    primary_platform_message_id: str | None = None
    platform_message_ids: list[str] = field(default_factory=list)
    parts: list[MessageReceiptPart] = field(default_factory=list)
    thread_id: str | None = None
    reply_to_id: str | None = None
    sent_at: float = field(default_factory=time.time)
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class ChannelMessageSendResult:
    receipt: MessageReceipt
    message_id: str | None = None
    raw: Mapping[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class ChannelMessageSendTextContext:
    channel: str
    account_id: str | None
    conversation_id: str
    text: str
    thread_id: str | None = None
    reply_to_id: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)

