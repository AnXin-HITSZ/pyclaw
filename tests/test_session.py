import json
import tempfile
import unittest
from pathlib import Path

from openclaw import Agent, AssistantMessage, MockProvider
from openclaw.session import AgentSession, RetryPolicy, SessionStore, Transcript


class SessionTests(unittest.IsolatedAsyncioTestCase):
    async def test_session_persists_messages_and_updates_store(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            base = Path(temp_dir)
            provider = MockProvider(
                [
                    AssistantMessage(
                        content=[{"type": "text", "text": "done"}],
                        stop_reason="stop",
                    )
                ]
            )
            agent = Agent(model="mock-model", provider=provider, system_prompt="You are helpful.")
            store = SessionStore(base / "sessions.json")
            transcript = Transcript(base / "session-1.jsonl")
            session = AgentSession(
                session_id="session-1",
                agent=agent,
                store=store,
                transcript=transcript,
                retry_policy=RetryPolicy(base_delay_seconds=0),
                cwd=str(base),
                workspace_dir=str(base),
            )

            result = await session.run_prompt("hello")

            self.assertEqual(result.stop_reason, "stop")
            lines = transcript.path.read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(lines), 2)
            for line in lines:
                json.loads(line)
            store_data = json.loads((base / "sessions.json").read_text(encoding="utf-8"))
            entry = store_data["sessions"]["session-1"]
            self.assertEqual(entry["status"], "active")
            self.assertEqual(entry["model"], "mock-model")

    async def test_retry_keeps_error_in_transcript_but_removes_from_memory(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            base = Path(temp_dir)
            provider = MockProvider(
                [
                    RuntimeError("rate limit exceeded: 429"),
                    AssistantMessage(
                        content=[{"type": "text", "text": "recovered"}],
                        stop_reason="stop",
                    ),
                ]
            )
            agent = Agent(model="mock-model", provider=provider, system_prompt="You are helpful.")
            session = AgentSession(
                session_id="session-1",
                agent=agent,
                store=SessionStore(base / "sessions.json"),
                transcript=Transcript(base / "session-1.jsonl"),
                retry_policy=RetryPolicy(max_attempts=2, base_delay_seconds=0),
            )

            result = await session.run_prompt("hello")

            self.assertEqual(result.stop_reason, "stop")
            self.assertEqual(result.content[0]["text"], "recovered")
            self.assertFalse(
                any(getattr(message, "stop_reason", None) == "error" for message in agent.state.messages)
            )
            transcript_text = session.transcript.path.read_text(encoding="utf-8")
            self.assertIn("rate limit exceeded", transcript_text)
            self.assertIn("recovered", transcript_text)


if __name__ == "__main__":
    unittest.main()
