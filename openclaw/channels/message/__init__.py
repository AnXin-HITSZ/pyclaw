"""Message send/receive contracts for channels."""

from openclaw.channels.message.adapter import (
    ChannelMessageAdapter,
    ChannelMessageReceiveAdapter,
    ChannelMessageSendAdapter,
    define_channel_message_adapter,
)
from openclaw.channels.message.receive import MessageReceiveContext, create_message_receive_context
from openclaw.channels.message.types import (
    Attachment,
    ChannelMessageReceiveAckPolicy,
    ChannelMessageReceiveStage,
    MessageReceipt,
    MessageReceiptPart,
    PreparedInboundMessage,
    RawInboundEvent,
)

__all__ = [
    "Attachment",
    "ChannelMessageAdapter",
    "ChannelMessageReceiveAckPolicy",
    "ChannelMessageReceiveAdapter",
    "ChannelMessageReceiveStage",
    "ChannelMessageSendAdapter",
    "MessageReceipt",
    "MessageReceiptPart",
    "MessageReceiveContext",
    "PreparedInboundMessage",
    "RawInboundEvent",
    "create_message_receive_context",
    "define_channel_message_adapter",
]

