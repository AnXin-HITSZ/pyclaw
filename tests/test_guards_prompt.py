import unittest

from openclaw.guards import ContextGuard, ContextOverflowError, TranscriptToolResultGuard
from openclaw.llm.types import ToolResultMessage, tool_result_content
from openclaw.prompt import CACHE_BOUNDARY, SystemPromptParams, build_system_prompt


class GuardsAndPromptTests(unittest.TestCase):
    def test_context_guard_truncates_tool_results_without_mutating_original(self) -> None:
        message = ToolResultMessage(content=tool_result_content("call_1", "huge", "abcdef"))
        guard = ContextGuard(max_context_chars=1_000, max_tool_result_chars=3)

        transformed = guard.transform([message])

        self.assertEqual(message.content[0]["output"], "abcdef")
        self.assertIn("...[truncated]", transformed[0].content[0]["output"])

    def test_context_guard_raises_overflow(self) -> None:
        guard = ContextGuard(max_context_chars=10, max_tool_result_chars=100)
        message = ToolResultMessage(content=tool_result_content("call_1", "huge", "abcdef"))

        with self.assertRaises(ContextOverflowError):
            guard.transform([message])

    def test_transcript_guard_redacts_and_truncates_tool_result(self) -> None:
        message = ToolResultMessage(
            content=tool_result_content("call_1", "tool", "token=abc123 " + "x" * 20)
        )
        guard = TranscriptToolResultGuard(max_tool_result_chars=18)

        safe = guard.before_append(message)

        self.assertIn("[REDACTED]", safe.content[0]["output"])
        self.assertIn("...[truncated]", safe.content[0]["output"])
        self.assertIn("abc123", message.content[0]["output"])

    def test_system_prompt_contains_cache_boundary(self) -> None:
        prompt = build_system_prompt(
            SystemPromptParams(
                workspace_dir="D:/project/pyclaw",
                tool_names=["read_file"],
                user_timezone="Asia/Shanghai",
            )
        )

        self.assertIn("## Tooling", prompt)
        self.assertIn("read_file", prompt)
        self.assertIn(CACHE_BOUNDARY, prompt)
        self.assertLess(prompt.index("## Workspace"), prompt.index(CACHE_BOUNDARY))


if __name__ == "__main__":
    unittest.main()
