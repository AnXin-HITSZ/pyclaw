from __future__ import annotations

import asyncio
import tempfile
import time
import unittest
from pathlib import Path

from openclaw.channels import ChannelCapabilities, ChannelMeta, ChannelPlugin, ChannelRegistry
from openclaw.channels.message.ingress_queue import SQLiteIngressQueue
from openclaw.channels.message.receive import create_message_receive_context
from openclaw.channels.message.types import (
    ChannelMessageReceiveAckPolicy,
    ChannelMessageReceiveStage,
    RawInboundEvent,
)
from openclaw.plugins.feishu import SequentialQueue
from openclaw.plugins.wechat import build_wechat_signature, verify_wechat_signature
from openclaw.state import JsonPluginStateStore


class ChannelRegistryTests(unittest.TestCase):
    def test_register_and_get_plugin(self) -> None:
        registry = ChannelRegistry()
        plugin = ChannelPlugin(
            id="wechat",
            meta=ChannelMeta(name="WeChat"),
            capabilities=ChannelCapabilities(inbound=True, outbound_text=True),
        )

        registry.register(plugin)

        self.assertIs(registry.get("wechat"), plugin)
        with self.assertRaises(ValueError):
            registry.register(plugin)


class MessageReceiveContextTests(unittest.IsolatedAsyncioTestCase):
    async def test_ack_after_policy_stage_is_idempotent(self) -> None:
        calls: list[str] = []
        event = RawInboundEvent(
            id="evt-1",
            channel="wechat",
            account_id="acct-1",
            platform_payload={"text": "hello"},
            ack_policy=ChannelMessageReceiveAckPolicy.AFTER_AGENT_DISPATCH,
        )
        context = create_message_receive_context(event, ack=lambda _: calls.append("ack"))

        self.assertFalse(await context.ack_after_stage(ChannelMessageReceiveStage.RECEIVE_RECORD))
        self.assertTrue(await context.ack_after_stage(ChannelMessageReceiveStage.AGENT_DISPATCH))
        self.assertFalse(await context.ack())
        self.assertEqual(calls, ["ack"])

    async def test_nack_prevents_later_ack(self) -> None:
        calls: list[str] = []
        event = RawInboundEvent(
            id="evt-1",
            channel="wechat",
            account_id=None,
            platform_payload={},
        )
        context = create_message_receive_context(
            event,
            ack=lambda _: calls.append("ack"),
            nack=lambda _, __: calls.append("nack"),
        )

        self.assertTrue(await context.nack(RuntimeError("boom")))
        self.assertFalse(await context.ack())
        self.assertEqual(calls, ["nack"])


class IngressQueueTests(unittest.TestCase):
    def test_enqueue_claim_complete_and_duplicate_detection(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            queue = SQLiteIngressQueue(Path(temp_dir) / "queue.db")

            self.assertTrue(queue.enqueue("evt-1", "wechat", {"text": "hello"}, lane_key="chat-1"))
            self.assertFalse(queue.enqueue("evt-1", "wechat", {"text": "hello"}, lane_key="chat-1"))

            claim = queue.claim_next("worker-1")
            self.assertIsNotNone(claim)
            assert claim is not None
            self.assertEqual(claim.payload["text"], "hello")

            self.assertIsNone(queue.claim_next("worker-2"))
            self.assertTrue(queue.complete(claim))
            self.assertFalse(queue.complete(claim))
            self.assertEqual(queue.get("evt-1").status, "completed")  # type: ignore[union-attr]

    def test_lane_is_blocked_while_claimed(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            queue = SQLiteIngressQueue(Path(temp_dir) / "queue.db")
            queue.enqueue("evt-1", "wechat", {"n": 1}, lane_key="same-chat")
            queue.enqueue("evt-2", "wechat", {"n": 2}, lane_key="same-chat")
            queue.enqueue("evt-3", "wechat", {"n": 3}, lane_key="other-chat")

            first = queue.claim_next("worker-1")
            second = queue.claim_next("worker-2")

            self.assertEqual(first.event_id if first else None, "evt-1")
            self.assertEqual(second.event_id if second else None, "evt-3")

    def test_stale_claim_is_released(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            queue = SQLiteIngressQueue(Path(temp_dir) / "queue.db", stale_after_seconds=0.01)
            queue.enqueue("evt-1", "wechat", {})
            first = queue.claim_next("worker-1")
            self.assertIsNotNone(first)
            time.sleep(0.02)

            second = queue.claim_next("worker-2")

            self.assertIsNotNone(second)
            self.assertEqual(second.owner_id if second else None, "worker-2")


class PluginStateAndWeChatSignatureTests(unittest.IsolatedAsyncioTestCase):
    async def test_json_store_register_lookup_delete(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            store = JsonPluginStateStore[dict[str, str]](Path(temp_dir) / "state.json")

            await store.register("key", {"value": "ok"})

            self.assertEqual(await store.lookup("key"), {"value": "ok"})
            self.assertTrue(await store.delete("key"))
            self.assertIsNone(await store.lookup("key"))

    async def test_wechat_signature_verification(self) -> None:
        token = "token"
        timestamp = "1710000000"
        nonce = "nonce"
        signature = build_wechat_signature(token, timestamp, nonce)

        self.assertTrue(verify_wechat_signature(token, timestamp, nonce, signature))
        self.assertFalse(verify_wechat_signature(token, timestamp, nonce, "bad-signature"))


class SequentialQueueTests(unittest.IsolatedAsyncioTestCase):
    async def test_same_key_runs_sequentially_but_different_key_can_overlap(self) -> None:
        queue = SequentialQueue()
        events: list[str] = []

        async def task(name: str, delay: float) -> str:
            events.append(f"start:{name}")
            await asyncio.sleep(delay)
            events.append(f"end:{name}")
            return name

        results = await asyncio.gather(
            queue.run("same", lambda: task("a", 0.02)),
            queue.run("same", lambda: task("b", 0)),
            queue.run("other", lambda: task("c", 0)),
        )

        self.assertEqual(results, ["a", "b", "c"])
        self.assertLess(events.index("end:a"), events.index("start:b"))
        self.assertLess(events.index("start:c"), events.index("end:a"))


if __name__ == "__main__":
    unittest.main()
