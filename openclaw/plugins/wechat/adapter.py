"""WeChat webhook and outbound message adapter."""

from __future__ import annotations

import json
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from typing import Any

from openclaw.channels.config import ChannelRuntimeConfig
from openclaw.channels.http import AsyncHttpClient, UrlLibHttpClient
from openclaw.channels.message.adapter import ChannelMessageReceiveAdapter, ChannelMessageSendLifecycleAdapter
from openclaw.channels.message.types import (
    ChannelMessageReceiveAckPolicy,
    ChannelMessageSendResult,
    ChannelMessageSendTextContext,
    MessageReceipt,
    MessageReceiptPart,
    PreparedInboundMessage,
    RawInboundEvent,
)
from openclaw.plugins.wechat.signature import verify_wechat_signature


@dataclass(frozen=True)
class WeChatWebhookEnvelope:
    event: RawInboundEvent
    lane_key: str
    challenge: str | None = None


class WeChatWebhookError(ValueError):
    pass


class WeChatReceiveAdapter(ChannelMessageReceiveAdapter):
    default_ack_policy = ChannelMessageReceiveAckPolicy.AFTER_RECEIVE_RECORD

    async def prepare(self, event: RawInboundEvent) -> PreparedInboundMessage:
        payload = dict(event.platform_payload)
        message_id = str(payload.get("MsgId") or payload.get("msgid") or payload.get("CreateTime") or event.id)
        sender = str(payload.get("FromUserName") or payload.get("from_user_name") or payload.get("FromUserId") or "unknown")
        recipient = str(payload.get("ToUserName") or payload.get("to_user_name") or event.account_id or "wechat")
        text = _wechat_text(payload)
        conversation_id = str(payload.get("conversation_id") or sender)
        return PreparedInboundMessage(
            id=message_id,
            channel="wechat",
            account_id=event.account_id or recipient,
            conversation_id=conversation_id,
            sender_id=sender,
            text=text,
            raw=payload,
        )


class WeChatTextSendAdapter:
    def __init__(
        self,
        config: ChannelRuntimeConfig,
        *,
        http_client: AsyncHttpClient | None = None,
        lifecycle: ChannelMessageSendLifecycleAdapter | None = None,
    ) -> None:
        self.config = config
        self.http_client = http_client or UrlLibHttpClient()
        self.lifecycle = lifecycle

    async def text(self, context: ChannelMessageSendTextContext) -> ChannelMessageSendResult:
        token = await self._access_token()
        base_url = self.config.get_str("api_base_url", "https://api.weixin.qq.com") or "https://api.weixin.qq.com"
        url = f"{base_url.rstrip('/')}/cgi-bin/message/custom/send?access_token={token}"
        payload = {
            "touser": context.conversation_id,
            "msgtype": "text",
            "text": {"content": context.text},
        }
        response = await self.http_client.post_json(url, payload)
        data = response.json()
        errcode = int(data.get("errcode", 0))
        if response.status >= 400 or errcode != 0:
            raise RuntimeError(f"WeChat send failed: http={response.status} errcode={errcode} body={data}")
        platform_id = str(data.get("msgid") or context.metadata.get("inbound_id") or "")
        receipt = MessageReceipt(
            primary_platform_message_id=platform_id or None,
            platform_message_ids=[platform_id] if platform_id else [],
            parts=[
                MessageReceiptPart(
                    platform_message_id=platform_id or None,
                    payload_kind="text",
                    metadata={"http_status": response.status, "errcode": errcode},
                )
            ],
            metadata={"channel": "wechat", "conversation_id": context.conversation_id},
        )
        return ChannelMessageSendResult(receipt=receipt, message_id=platform_id or None, raw=data)

    async def _access_token(self) -> str:
        configured = self.config.get_str("access_token")
        if configured:
            return configured
        app_id = self.config.require("app_id")
        app_secret = self.config.require("app_secret")
        base_url = self.config.get_str("api_base_url", "https://api.weixin.qq.com") or "https://api.weixin.qq.com"
        url = (
            f"{base_url.rstrip()}/cgi-bin/token"
            f"?grant_type=client_credential&appid={app_id}&secret={app_secret}"
        )
        response = await self.http_client.get_json(url)
        data = response.json()
        token = data.get("access_token")
        if response.status >= 400 or not token:
            raise RuntimeError(f"WeChat token fetch failed: http={response.status} body={data}")
        return str(token)


def build_wechat_webhook_event(
    *,
    config: ChannelRuntimeConfig,
    query: dict[str, str],
    body: bytes,
    content_type: str | None = None,
) -> WeChatWebhookEnvelope:
    token = config.require("token")
    signature = _require_query(query, "signature")
    timestamp = _require_query(query, "timestamp")
    nonce = _require_query(query, "nonce")
    if not verify_wechat_signature(token, timestamp, nonce, signature):
        raise WeChatWebhookError("invalid WeChat signature")
    echostr = query.get("echostr")
    if echostr and not body:
        payload = {"echostr": echostr}
        event_id = f"wechat-challenge:{timestamp}:{nonce}"
        return WeChatWebhookEnvelope(
            event=RawInboundEvent(
                id=event_id,
                channel="wechat",
                account_id=config.account_id,
                platform_payload=payload,
                ack_policy=ChannelMessageReceiveAckPolicy.AFTER_RECEIVE_RECORD,
                lane_key=f"wechat:{config.account_id or 'default'}:challenge",
            ),
            lane_key=f"wechat:{config.account_id or 'default'}:challenge",
            challenge=echostr,
        )

    payload = parse_wechat_payload(body, content_type=content_type)
    event_id = str(payload.get("MsgId") or payload.get("msgid") or payload.get("CreateTime") or f"{timestamp}:{nonce}")
    sender = str(payload.get("FromUserName") or payload.get("from_user_name") or "unknown")
    lane_key = f"wechat:{config.account_id or payload.get('ToUserName') or 'default'}:{sender}"
    payload.setdefault("channel", "wechat")
    payload.setdefault("account_id", config.account_id)
    return WeChatWebhookEnvelope(
        event=RawInboundEvent(
            id=event_id,
            channel="wechat",
            account_id=config.account_id,
            platform_payload=payload,
            received_at=time.time(),
            ack_policy=ChannelMessageReceiveAckPolicy.AFTER_RECEIVE_RECORD,
            lane_key=lane_key,
        ),
        lane_key=lane_key,
    )


def parse_wechat_payload(body: bytes, *, content_type: str | None = None) -> dict[str, Any]:
    text = body.decode("utf-8-sig").strip()
    if not text:
        return {}
    if "json" in (content_type or "").lower() or text.startswith("{"):
        value = json.loads(text)
        if not isinstance(value, dict):
            raise WeChatWebhookError("WeChat JSON payload must be an object")
        return value
    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        raise WeChatWebhookError(f"invalid WeChat XML payload: {exc}") from exc
    return {child.tag: child.text or "" for child in root}


def _wechat_text(payload: dict[str, Any]) -> str:
    msg_type = str(payload.get("MsgType") or payload.get("msgtype") or "text")
    if msg_type == "text":
        return str(payload.get("Content") or payload.get("content") or "")
    if msg_type == "event":
        event = str(payload.get("Event") or payload.get("event") or "")
        key = str(payload.get("EventKey") or payload.get("event_key") or "")
        return f"[WeChat event] {event} {key}".strip()
    return str(payload.get("Content") or payload.get("content") or f"[WeChat {msg_type} message]")


def build_wechat_passive_text_response(*, inbound_payload: dict[str, Any], text: str, now: int | None = None) -> str:
    recipient = str(inbound_payload.get("FromUserName") or inbound_payload.get("from_user_name") or "")
    sender = str(inbound_payload.get("ToUserName") or inbound_payload.get("to_user_name") or "")
    if not recipient or not sender:
        raise WeChatWebhookError("WeChat passive reply requires FromUserName and ToUserName")
    root = ET.Element("xml")
    ET.SubElement(root, "ToUserName").text = recipient
    ET.SubElement(root, "FromUserName").text = sender
    ET.SubElement(root, "CreateTime").text = str(now if now is not None else int(time.time()))
    ET.SubElement(root, "MsgType").text = "text"
    ET.SubElement(root, "Content").text = text
    return ET.tostring(root, encoding="unicode", short_empty_elements=False)


def _require_query(query: dict[str, str], key: str) -> str:
    value = query.get(key)
    if not value:
        raise WeChatWebhookError(f"missing WeChat query parameter: {key}")
    return value
