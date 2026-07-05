"""Channel plugin root types and registry."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol

from openclaw.channels.message.adapter import ChannelMessageAdapter


@dataclass(frozen=True)
class ChannelMeta:
    name: str
    description: str = ""
    homepage: str | None = None


@dataclass(frozen=True)
class ChannelCapabilities:
    inbound: bool = False
    outbound_text: bool = False
    outbound_media: bool = False
    outbound_payload: bool = False
    durable_final_delivery: bool = False
    threaded_replies: bool = False
    attachments: bool = False
    reactions: bool = False


class ChannelConfigAdapter(Protocol):
    def resolve(self, raw: dict[str, Any]) -> dict[str, Any]:
        """Validate and normalize platform configuration."""


class ChannelLifecycleAdapter(Protocol):
    async def start(self) -> None:
        """Start background platform monitor tasks."""

    async def stop(self) -> None:
        """Stop background platform monitor tasks."""


class ChannelStatusAdapter(Protocol):
    async def probe(self) -> dict[str, Any]:
        """Return platform health details."""


@dataclass
class ChannelPlugin:
    id: str
    meta: ChannelMeta
    capabilities: ChannelCapabilities
    message: ChannelMessageAdapter | None = None
    config: ChannelConfigAdapter | None = None
    lifecycle: ChannelLifecycleAdapter | None = None
    status: ChannelStatusAdapter | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


class ChannelRegistry:
    def __init__(self) -> None:
        self._plugins: dict[str, ChannelPlugin] = {}

    def register(self, plugin: ChannelPlugin) -> None:
        if plugin.id in self._plugins:
            raise ValueError(f"channel plugin already registered: {plugin.id}")
        self._plugins[plugin.id] = plugin

    def get(self, plugin_id: str) -> ChannelPlugin:
        try:
            return self._plugins[plugin_id]
        except KeyError as exc:
            raise KeyError(f"unknown channel plugin: {plugin_id}") from exc

    def list(self) -> list[ChannelPlugin]:
        return list(self._plugins.values())

