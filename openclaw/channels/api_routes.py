"""FastAPI routes for channel webhooks."""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Awaitable, Callable
from typing import Any

from fastapi import APIRouter, Header, HTTPException, Request
from fastapi.responses import JSONResponse, PlainTextResponse

from openclaw.channels.config import load_channel_config
from openclaw.channels.dispatcher import ChannelTurnDispatcher
from openclaw.channels.message.ingress_queue import create_ingress_queue_from_env
from openclaw.channels.worker import raw_event_payload
from openclaw.plugins.feishu.adapter import (
    FeishuWebhookError,
    build_feishu_webhook_event,
)
from openclaw.plugins.wechat.adapter import (
    WeChatReceiveAdapter,
    WeChatWebhookError,
    build_wechat_passive_text_response,
    build_wechat_webhook_event,
)
from openclaw.session.agent_session import AgentSession

LOGGER = logging.getLogger(__name__)
SessionFactory = Callable[[Any], AgentSession | Awaitable[AgentSession]]
ASYNC_WORKER_REPLY_MODE = "async_worker"
PASSIVE_XML_REPLY_MODE = "passive_xml"
SUPPORTED_REPLY_MODES = {ASYNC_WORKER_REPLY_MODE, PASSIVE_XML_REPLY_MODE}


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
            reply_mode = _reply_mode(config)
            if reply_mode == PASSIVE_XML_REPLY_MODE:
                xml = await _build_wechat_passive_reply_xml(envelope.event, session_factory, config)
                return PlainTextResponse(xml, media_type="application/xml")
            _enqueue_event(envelope, create_ingress_queue_from_env())
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
            _enqueue_event(envelope, create_ingress_queue_from_env())
        except (ValueError, FeishuWebhookError) as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        return JSONResponse({"code": 0, "msg": "success"})

    return router


def _ensure_channel_enabled(config: Any) -> None:
    if not getattr(config, "enabled", True):
        raise HTTPException(status_code=404, detail=f"{config.channel} channel is disabled")


def _enqueue_event(envelope: Any, queue: Any) -> None:
    queue.enqueue(
        envelope.event.id,
        envelope.event.channel,
        raw_event_payload(envelope.event),
        lane_key=envelope.lane_key,
    )


async def _build_wechat_passive_reply_xml(event: Any, session_factory: SessionFactory, config: Any) -> str:
    fallback = config.get_str("passive_reply_fallback_text", "\u5df2\u6536\u5230\uff0c\u6b63\u5728\u5904\u7406\u3002") or "\u5df2\u6536\u5230\uff0c\u6b63\u5728\u5904\u7406\u3002"
    timeout = _positive_float(config.get_str("passive_reply_timeout_seconds"), default=4.5)
    try:
        prepared = await WeChatReceiveAdapter().prepare(event)
        turn = await asyncio.wait_for(
            ChannelTurnDispatcher(session_factory=session_factory).dispatch(prepared),
            timeout=timeout,
        )
        text = _passive_reply_text(turn, fallback)
    except Exception:
        LOGGER.exception("failed to build WeChat passive reply: event_id=%s", getattr(event, "id", "unknown"))
        text = fallback
    return build_wechat_passive_text_response(inbound_payload=dict(event.platform_payload), text=text)


def _passive_reply_text(turn: Any, fallback: str) -> str:
    assistant = getattr(turn, "assistant", None)
    if getattr(assistant, "error_message", None) or getattr(assistant, "stop_reason", None) == "aborted":
        return fallback
    return str(getattr(turn, "assistant_text", "")).strip() or fallback

def _reply_mode(config: Any) -> str:
    mode = (config.get_str("reply_mode") or ASYNC_WORKER_REPLY_MODE).strip().lower().replace("-", "_")
    if mode not in SUPPORTED_REPLY_MODES:
        raise ValueError(f"unsupported {config.channel} reply_mode: {mode}")
    if config.channel != "wechat" and mode == PASSIVE_XML_REPLY_MODE:
        raise ValueError("passive_xml reply_mode is only supported for WeChat")
    return mode


def _positive_float(value: str | None, *, default: float) -> float:
    if value is None or value == "":
        return default
    parsed = float(value)
    if parsed <= 0:
        raise ValueError("passive_reply_timeout_seconds must be greater than 0")
    return parsed


def _single_value_query(request: Request) -> dict[str, str]:
    return {key: value for key, value in request.query_params.multi_items()}
