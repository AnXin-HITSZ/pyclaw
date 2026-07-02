import unittest

from openclaw import Agent, AssistantMessage, MockProvider
from openclaw.llm.openai_provider import OpenAIProvider


class FakeResponses:
    def __init__(self, events):
        self.events = events
        self.requests = []

    async def create(self, **kwargs):
        self.requests.append(kwargs)
        return FakeStream(self.events)


class FakeClient:
    def __init__(self, events):
        self.responses = FakeResponses(events)


class FakeStream:
    def __init__(self, events):
        self.events = events

    def __aiter__(self):
        self._iter = iter(self.events)
        return self

    async def __anext__(self):
        try:
            return next(self._iter)
        except StopIteration:
            raise StopAsyncIteration


class ModelOptionsTests(unittest.IsolatedAsyncioTestCase):
    async def test_agent_model_options_are_passed_to_provider(self) -> None:
        provider = MockProvider(
            [
                AssistantMessage(
                    content=[{"type": "text", "text": "hello"}],
                    stop_reason="stop",
                )
            ]
        )
        agent = Agent(
            model="mock-model",
            provider=provider,
            system_prompt="You are helpful.",
            model_options={
                "reasoning": {"effort": "low", "summary": "auto"},
                "max_output_tokens": 1024,
            },
        )

        await agent.prompt("hi")

        self.assertEqual(
            provider.calls[0]["options"]["reasoning"],
            {"effort": "low", "summary": "auto"},
        )
        self.assertEqual(provider.calls[0]["options"]["max_output_tokens"], 1024)

    async def test_openai_provider_merges_model_options_into_request(self) -> None:
        response = {
            "model": "gpt-test",
            "status": "completed",
            "output": [
                {
                    "type": "message",
                    "content": [{"type": "output_text", "text": "done"}],
                }
            ],
        }
        client = FakeClient([{"type": "response.completed", "response": response}])
        provider = OpenAIProvider(client=client, api_mode="responses")

        output = [
            event
            async for event in provider.stream(
                model="gpt-test",
                system_prompt="You are helpful.",
                messages=[],
                tools=[],
                options={
                    "reasoning": {"effort": "low", "summary": "auto"},
                    "max_output_tokens": 2048,
                },
            )
        ]

        self.assertEqual(output[-1].type, "done")
        request = client.responses.requests[0]
        self.assertEqual(request["reasoning"], {"effort": "low", "summary": "auto"})
        self.assertEqual(request["max_output_tokens"], 2048)


if __name__ == "__main__":
    unittest.main()
