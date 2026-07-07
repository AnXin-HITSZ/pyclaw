"""Channel platform primitives inspired by OpenClaw."""

from openclaw.channels.config import ChannelAgentConfig, ChannelRuntimeConfig, load_channel_agent_config, load_channel_config
from openclaw.channels.core import (
    ChannelCapabilities,
    ChannelMeta,
    ChannelPlugin,
    ChannelRegistry,
)
from openclaw.channels.dispatcher import (
    ChannelTurnDispatcher,
    ChannelTurnResult,
    assistant_text,
    build_channel_session_id,
)
from openclaw.channels.http import AsyncHttpClient, HttpResponse, UrlLibHttpClient
from openclaw.channels.worker import IngressQueueWorker, IngressWorkerResult

__all__ = [
    "AsyncHttpClient",
    "ChannelAgentConfig",
    "ChannelCapabilities",
    "ChannelMeta",
    "ChannelPlugin",
    "ChannelRegistry",
    "ChannelRuntimeConfig",
    "ChannelTurnDispatcher",
    "ChannelTurnResult",
    "HttpResponse",
    "IngressQueueWorker",
    "IngressWorkerResult",
    "UrlLibHttpClient",
    "assistant_text",
    "build_channel_session_id",
    "load_channel_agent_config",
    "load_channel_config",
]
