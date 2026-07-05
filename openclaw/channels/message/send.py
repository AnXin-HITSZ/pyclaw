"""Durable-ish message send helpers."""

from __future__ import annotations

from openclaw.channels.message.adapter import ChannelMessageSendAdapter
from openclaw.channels.message.types import ChannelMessageSendResult, ChannelMessageSendTextContext


class ChannelSendError(RuntimeError):
    pass


async def send_text_message(
    adapter: ChannelMessageSendAdapter,
    context: ChannelMessageSendTextContext,
) -> ChannelMessageSendResult:
    lifecycle = getattr(adapter, "lifecycle", None)
    if lifecycle is not None:
        await lifecycle.before_send_attempt(context)
    try:
        result = await adapter.text(context)
    except BaseException as exc:
        if lifecycle is not None:
            await lifecycle.after_send_failure(context, exc)
        raise
    if lifecycle is not None:
        await lifecycle.after_send_success(context, result)
        await lifecycle.after_commit(context, result)
    return result

