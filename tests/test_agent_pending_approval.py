"""End-to-end pending approval tests through the Agent API surface."""

from __future__ import annotations

import importlib.util
import unittest
from typing import Any

HAS_API_DEPS = bool(importlib.util.find_spec("fastapi")) and bool(importlib.util.find_spec("httpx"))


class InMemoryPendingApprovalStore:
    """Minimal in-memory fake of PendingApprovalStore for tests."""

    def __init__(self) -> None:
        self._store: dict[str, dict[str, Any]] = {}

    def save(self, approval_id: str, state: dict[str, Any], ttl_seconds: int = 30 * 60) -> None:
        self._store[approval_id] = dict(state)

    def load(self, approval_id: str) -> dict[str, Any] | None:
        return dict(self._store[approval_id]) if approval_id in self._store else None

    def delete(self, approval_id: str) -> None:
        self._store.pop(approval_id, None)


def _build_scripted_provider(sequence):
    """Return a MockProvider with the scripted messages configured."""

    from openclaw.llm.provider import MockProvider
    from openclaw.llm.types import AssistantMessage

    responses = []
    for entry in sequence:
        stop_reason = entry.get("stop_reason", "stop")
        responses.append(
            AssistantMessage(
                content=entry["content"],
                provider="mock",
                model="mock-model",
                stop_reason=stop_reason,
            )
        )
    return MockProvider(responses)


@unittest.skipUnless(HAS_API_DEPS, "FastAPI/httpx are not installed; install with .[api]")
class AgentPendingApprovalApiTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        from openclaw import api

        self.store = InMemoryPendingApprovalStore()
        api._set_pending_approval_store(self.store)

        self._original_build_provider = api.build_provider
        self._original_execute_tool_call_batch = api.execute_tool_call_batch

        tool_call_message = [
            {
                "type": "toolCall",
                "id": "call_1",
                "name": "write_file",
                "input": {"file_path": "a.txt", "content": "hi"},
            }
        ]
        final_text_message = [{"type": "text", "text": "文件操作已处理"}]

        def _fake_build_provider(request, *, model):  # noqa: ANN001
            if request.prompt.startswith("PLEASE_WRITE"):
                return _build_scripted_provider(
                    [
                        {"content": tool_call_message, "stop_reason": "toolUse"},
                        {"content": final_text_message, "stop_reason": "stop"},
                    ]
                )
            return _build_scripted_provider(
                [
                    {"content": final_text_message, "stop_reason": "stop"},
                ]
            )

        api.build_provider = _fake_build_provider

    async def asyncTearDown(self) -> None:
        from openclaw import api

        api.build_provider = self._original_build_provider
        api.execute_tool_call_batch = self._original_execute_tool_call_batch
        api._set_pending_approval_store(None)

    async def test_medium_risk_returns_pending_approval_via_api(self) -> None:
        from openclaw.api import AgentRunRequest, run_agent_request

        request = AgentRunRequest(
            prompt="PLEASE_WRITE hello",
            provider="mock",
            model="mock-model",
            tool_profile="coding",
            sandbox_base_url="http://sandbox.local",
            claw_id="claw-1",
            owner_user_id="user-1",
        )
        outcome = await run_agent_request(request)
        self.assertEqual(outcome.status, "PENDING_APPROVAL")
        self.assertIsNotNone(outcome.approval)
        self.assertEqual(outcome.approval.tool_name, "write_file")
        state = self.store.load(outcome.approval.approval_id)
        self.assertIsNotNone(state)
        self.assertEqual(state["tool_call"]["name"], "write_file")
        self.assertNotIn("assistant_message", state)

    async def test_resume_reject_produces_completed(self) -> None:
        from openclaw.api import AgentResumeRequest, AgentRunRequest, resume_agent_request, run_agent_request

        run_request = AgentRunRequest(
            prompt="PLEASE_WRITE hello",
            provider="mock",
            model="mock-model",
            tool_profile="coding",
            sandbox_base_url="http://sandbox.local",
            claw_id="claw-1",
            owner_user_id="user-1",
        )
        outcome = await run_agent_request(run_request)
        self.assertEqual(outcome.status, "PENDING_APPROVAL")

        resume_request = AgentResumeRequest(
            approval_id=outcome.approval.approval_id,
            decision="REJECTED",
            rejection_reason="路径不对",
            provider="mock",
            model="mock-model",
            tool_profile="coding",
            sandbox_base_url="http://sandbox.local",
        )
        resume_outcome = await resume_agent_request(resume_request)
        self.assertEqual(resume_outcome.status, "COMPLETED")
        self.assertIsNotNone(resume_outcome.message)
        # After consumption the pending state must be removed.
        self.assertIsNone(self.store.load(outcome.approval.approval_id))

    async def test_resume_approve_failure_keeps_pending_state_for_retry(self) -> None:
        from openclaw.api import AgentResumeRequest, AgentRunRequest, resume_agent_request, run_agent_request
        from openclaw import api

        run_request = AgentRunRequest(
            prompt="PLEASE_WRITE hello",
            provider="mock",
            model="mock-model",
            tool_profile="coding",
            sandbox_base_url="http://sandbox.local",
            claw_id="claw-1",
            owner_user_id="user-1",
        )
        outcome = await run_agent_request(run_request)
        self.assertEqual(outcome.status, "PENDING_APPROVAL")

        async def _raise_tool_failure(*args, **kwargs):  # noqa: ANN002, ANN003
            raise RuntimeError("sandbox unavailable")

        api.execute_tool_call_batch = _raise_tool_failure

        resume_request = AgentResumeRequest(
            approval_id=outcome.approval.approval_id,
            decision="APPROVED",
            provider="mock",
            model="mock-model",
            tool_profile="coding",
            sandbox_base_url="http://sandbox.local",
        )

        with self.assertRaises(RuntimeError):
            await resume_agent_request(resume_request)

        self.assertIsNotNone(self.store.load(outcome.approval.approval_id))


if __name__ == "__main__":
    unittest.main()
