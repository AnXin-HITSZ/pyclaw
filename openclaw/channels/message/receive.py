"""Receive ack/nack state machine."""

from __future__ import annotations

import inspect
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from typing import Any

from openclaw.channels.message.types import (
    ACK_STAGE_FOR_POLICY,
    ChannelMessageReceiveAckPolicy,
    ChannelMessageReceiveStage,
    RawInboundEvent,
)


AckCallback = Callable[[RawInboundEvent], Any | Awaitable[Any]]
NackCallback = Callable[[RawInboundEvent, BaseException | None], Any | Awaitable[Any]]


def should_ack_message_after_stage(
    policy: ChannelMessageReceiveAckPolicy,
    stage: ChannelMessageReceiveStage,
) -> bool:
    return ACK_STAGE_FOR_POLICY[policy] == stage


async def _maybe_await(value: Any) -> Any:
    if inspect.isawaitable(value):
        return await value
    return value


@dataclass
class MessageReceiveContext:
    event: RawInboundEvent
    ack_policy: ChannelMessageReceiveAckPolicy
    ack_callback: AckCallback | None = None
    nack_callback: NackCallback | None = None
    acknowledged: bool = False
    nacked: bool = False

    async def ack(self) -> bool:
        if self.acknowledged or self.nacked:
            return False
        self.acknowledged = True
        if self.ack_callback is not None:
            await _maybe_await(self.ack_callback(self.event))
        return True

    async def nack(self, error: BaseException | None = None) -> bool:
        if self.acknowledged or self.nacked:
            return False
        self.nacked = True
        if self.nack_callback is not None:
            await _maybe_await(self.nack_callback(self.event, error))
        return True

    async def ack_after_stage(self, stage: ChannelMessageReceiveStage) -> bool:
        if should_ack_message_after_stage(self.ack_policy, stage):
            return await self.ack()
        return False


def create_message_receive_context(
    event: RawInboundEvent,
    *,
    ack: AckCallback | None = None,
    nack: NackCallback | None = None,
) -> MessageReceiveContext:
    return MessageReceiveContext(
        event=event,
        ack_policy=event.ack_policy,
        ack_callback=ack,
        nack_callback=nack,
    )

