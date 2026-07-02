import unittest

from openclaw import Agent, AssistantMessage, FunctionTool, MockProvider, ToolRegistry
from openclaw.llm.types import tool_call_content


class AgentLoopTests(unittest.IsolatedAsyncioTestCase):
    async def test_plain_prompt_returns_stop(self) -> None:
        provider = MockProvider(
            [
                AssistantMessage(
                    content=[{"type": "text", "text": "hello"}],
                    stop_reason="stop",
                )
            ]
        )
        agent = Agent(model="mock-model", provider=provider, system_prompt="You are helpful.")

        result = await agent.prompt("hi")

        self.assertEqual(result.stop_reason, "stop")
        self.assertEqual(result.content[0]["text"], "hello")
        self.assertEqual([message.role for message in agent.state.messages], ["user", "assistant"])

    async def test_tool_use_executes_tool_and_continues(self) -> None:
        registry = ToolRegistry()
        registry.register(
            FunctionTool(
                name="read_file",
                description="Read a file",
                input_schema={"type": "object", "required": ["path"], "properties": {"path": {"type": "string"}}},
                func=lambda path: f"contents of {path}",
            )
        )
        provider = MockProvider(
            [
                AssistantMessage(
                    content=tool_call_content("call_1", "read_file", {"path": "README.md"}),
                    stop_reason="toolUse",
                ),
                AssistantMessage(
                    content=[{"type": "text", "text": "README summarized"}],
                    stop_reason="stop",
                ),
            ]
        )
        agent = Agent(
            model="mock-model",
            provider=provider,
            system_prompt="You are helpful.",
            tools=registry,
        )

        result = await agent.prompt("summarize README")

        self.assertEqual(result.stop_reason, "stop")
        self.assertEqual(len(provider.calls), 2)
        tool_messages = [message for message in agent.state.messages if message.role == "tool"]
        self.assertEqual(len(tool_messages), 1)
        self.assertEqual(tool_messages[0].content[0]["output"], "contents of README.md")
        self.assertFalse(tool_messages[0].content[0]["is_error"])

    async def test_missing_tool_returns_error_tool_result(self) -> None:
        provider = MockProvider(
            [
                AssistantMessage(
                    content=tool_call_content("call_1", "missing_tool", {}),
                    stop_reason="toolUse",
                ),
                AssistantMessage(
                    content=[{"type": "text", "text": "handled missing tool"}],
                    stop_reason="stop",
                ),
            ]
        )
        agent = Agent(model="mock-model", provider=provider, system_prompt="You are helpful.")

        result = await agent.prompt("use a missing tool")

        self.assertEqual(result.stop_reason, "stop")
        tool_message = next(message for message in agent.state.messages if message.role == "tool")
        self.assertTrue(tool_message.content[0]["is_error"])
        self.assertIn("tool not found", tool_message.content[0]["output"])

    async def test_provider_exception_becomes_error_message(self) -> None:
        provider = MockProvider([RuntimeError("network error: timeout")])
        agent = Agent(model="mock-model", provider=provider, system_prompt="You are helpful.")

        result = await agent.prompt("hi")

        self.assertEqual(result.stop_reason, "error")
        self.assertIn("timeout", result.error_message or "")
        self.assertIs(agent.state.messages[-1], result)


if __name__ == "__main__":
    unittest.main()
