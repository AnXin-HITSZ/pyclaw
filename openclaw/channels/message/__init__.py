"""Message send/receive contracts for channels."""

from openclaw.channels.message.adapter import (
    ChannelMessageAdapter,
    ChannelMessageReceiveAdapter,
    ChannelMessageSendAdapter,
    define_channel_message_adapter,
)
from openclaw.channels.message.ingress_queue import (
    IngressQueue,
    IngressQueueClaim,
    IngressQueueRecord,
    MySQLIngressQueue,
    MySQLIngressQueueConfig,
    create_ingress_queue_from_env,
    parse_mysql_dsn,
    validate_mysql_identifier,
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
    "IngressQueue",
    "IngressQueueClaim",
    "IngressQueueRecord",
    "MessageReceipt",
    "MessageReceiptPart",
    "MessageReceiveContext",
    "MySQLIngressQueue",
    "MySQLIngressQueueConfig",
    "PreparedInboundMessage",
    "RawInboundEvent",
    "create_ingress_queue_from_env",
    "create_message_receive_context",
    "define_channel_message_adapter",
    "parse_mysql_dsn",
    "validate_mysql_identifier",
]
