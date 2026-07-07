"""FastAPI routes for channel webhooks."""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import Any

from fastapi import APIRouter, Header, HTTPException, Request
from fastapi.responses import JSONResponse, PlainTextResponse

from openclaw.channels.config import load_channel_agent_config, load_channel_config
from openclaw.channels.dispatcher import ChannelTurnDispatcher
from openclaw.channels.message.ingress_queue import create_ingress_queue_from_env
from openclaw.channels.worker import IngressQueueWorker, raw_event_payload
from openclaw.plugins.feishu.adapter import (
    FeishuReceiveAdapter,
    FeishuTextSendAdapter,
    FeishuWebhookError,
    build_feishu_webhook_event,
)
from openclaw.plugins.wechat.adapter import (
    WeChatReceiveAdapter,
    WeChatTextSendAdapter,
    WeChatWebhookError,
    build_wechat_webhook_event,
)
from openclaw.session.agent_session import AgentSession

SessionFactory = Callable[[Any], AgentSession | Awaitable[AgentSession]]


def create_channel_router(*, session_factory: SessionFactory) -> APIRouter:
    router = APIRouter(prefix="/v1/channels", tags=["channels"])

    @router.get("/wechat/webhook")
    async def verify_wechat(request: Request) -> PlainTextResponse:
        query = _single_value_query(request)
        try:
            config = load_channel_config("wechat", account_id=query.get("account_id"))
            _ensure_channel_enabled(config)
            envelope = build_wechat_webhook_event(config=config, query=query, body=b"")
        except (ValueError, WeChatWebhookError) as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        return PlainTextResponse(envelope.challenge or query.get("echostr", ""))

    @router.post("/wechat/webhook")
    async def receive_wechat(
        request: Request,
        content_type: str | None = Header(default=None),
    ) -> PlainTextResponse:
        query = _single_value_query(request)
        body = await request.body()
        try:
            config = load_channel_config("wechat", account_id=query.get("account_id"))
            _ensure_channel_enabled(config)
            envelope = build_wechat_webhook_event(
                config=config,
                query=query,
                body=body,
                content_type=content_type,
            )
            queue = create_ingress_queue_from_env()
            queue.enqueue(
                envelope.event.id,
                envelope.event.channel,
                raw_event_payload(envelope.event),
                lane_key=envelope.lane_key,
            )
            if _sync_webhook_processing_enabled():
                await _build_worker("wechat", config, session_factory, queue).process_one()
        except (ValueError, WeChatWebhookError) as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        return PlainTextResponse("success")

    @router.post("/feishu/webhook")
    async def receive_feishu(request: Request) -> JSONResponse:
        body = await request.body()
        headers = {key: value for key, value in request.headers.items()}
        try:
            config = load_channel_config("feishu")
            _ensure_channel_enabled(config)
            envelope = build_feishu_webhook_event(config=config, headers=headers, body=body)
            if envelope.challenge:
                return JSONResponse({"challenge": envelope.challenge})
            if envelope.event is None:
                return JSONResponse({"code": 0, "msg": "success"})
            queue = create_ingress_queue_from_env()
            queue.enqueue(
                envelope.event.id,
                envelope.event.channel,
                raw_event_payload(envelope.event),
                lane_key=envelope.lane_key,
            )
            if _sync_webhook_processing_enabled():
                await _build_worker("feishu", config, session_factory, queue).process_one()
        except (ValueError, FeishuWebhookError) as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        return JSONResponse({"code": 0, "msg": "success"})

    return router


def _ensure_channel_enabled(config: Any) -> None:
    if not getattr(config, "enabled", True):
        raise HTTPException(status_code=404, detail=f"{config.channel} channel is disabled")


def _build_worker(channel: str, config: Any, session_factory: SessionFactory, queue: Any) -> IngressQueueWorker:
    if channel == "wechat":
        receive_adapters = {"wechat": WeChatReceiveAdapter()}
        send_adapters = {"wechat": WeChatTextSendAdapter(config)}
    elif channel == "feishu":
        receive_adapters = {"feishu": FeishuReceiveAdapter()}
        send_adapters = {"feishu": FeishuTextSendAdapter(config)}
    else:
        raise ValueError(f"unknown channel: {channel}")
    return IngressQueueWorker(
        queue=queue,
        receive_adapters=receive_adapters,
        dispatcher=ChannelTurnDispatcher(session_factory=session_factory, send_adapters=send_adapters),
        owner_id=f"pyclaw-{channel}-webhook",
        channel=channel,
    )


def _single_value_query(request: Request) -> dict[str, str]:
    return {key: value for key, value in request.query_params.multi_items()}


def _sync_webhook_processing_enabled() -> bool:
    return load_channel_agent_config().webhook_sync
