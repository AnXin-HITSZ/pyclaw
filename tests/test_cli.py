import io
import json
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

from openclaw.cli import assistant_text, build_model_options, main
from openclaw.llm.types import AssistantMessage


class CliTests(unittest.TestCase):
    def run_cli(self, argv, *, chatdata_dir=None):
        stdout = io.StringIO()
        stderr = io.StringIO()
        full_argv = list(argv)
        if chatdata_dir is not None and "--chatdata-dir" not in full_argv:
            full_argv = ["--chatdata-dir", str(chatdata_dir), *full_argv]
        with redirect_stdout(stdout), redirect_stderr(stderr):
            code = main(full_argv)
        return code, stdout.getvalue(), stderr.getvalue()

    def test_mock_prompt_prints_text_and_writes_transcript(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            chatdata_dir = Path(temp_dir)
            code, stdout, stderr = self.run_cli(
                ["--provider", "mock", "--session-id", "test-session", "hello"],
                chatdata_dir=chatdata_dir,
            )

            self.assertEqual(code, 0)
            self.assertEqual(stdout.strip(), "mock response: hello")
            self.assertEqual(stderr, "")

            transcript = chatdata_dir / "test-session.jsonl"
            self.assertTrue(transcript.exists())
            lines = transcript.read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(lines), 2)
            entries = [json.loads(line) for line in lines]
            self.assertEqual(entries[0]["message"]["role"], "user")
            self.assertEqual(entries[1]["message"]["role"], "assistant")

            store = json.loads((chatdata_dir / "sessions.json").read_text(encoding="utf-8"))
            self.assertIn("test-session", store["sessions"])
            self.assertEqual(store["sessions"]["test-session"]["session_file"], "test-session.jsonl")

    def test_transcripts_show_text(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            chatdata_dir = Path(temp_dir)
            self.run_cli(
                ["--provider", "mock", "--session-id", "demo", "hello"],
                chatdata_dir=chatdata_dir,
            )

            code, stdout, stderr = self.run_cli(
                ["transcripts", "show", "demo", "--format", "text"],
                chatdata_dir=chatdata_dir,
            )

        self.assertEqual(code, 0)
        self.assertEqual(stderr, "")
        self.assertIn("user: hello", stdout)
        self.assertIn("assistant: mock response: hello", stdout)

    def test_transcripts_show_detail(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            chatdata_dir = Path(temp_dir)
            self.run_cli(
                ["--provider", "mock", "--session-id", "demo", "hello"],
                chatdata_dir=chatdata_dir,
            )

            code, stdout, stderr = self.run_cli(
                ["transcripts", "show", "demo", "--format", "detail"],
                chatdata_dir=chatdata_dir,
            )

        self.assertEqual(code, 0)
        self.assertEqual(stderr, "")
        self.assertIn("assistant provider=mock model=mock-model stop=stop", stdout)
        self.assertIn("mock response: hello", stdout)

    def test_transcripts_show_json(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            chatdata_dir = Path(temp_dir)
            self.run_cli(
                ["--provider", "mock", "--session-id", "demo", "hello"],
                chatdata_dir=chatdata_dir,
            )

            code, stdout, stderr = self.run_cli(
                ["transcripts", "show", "demo", "--format", "json"],
                chatdata_dir=chatdata_dir,
            )

        self.assertEqual(code, 0)
        self.assertEqual(stderr, "")
        entries = json.loads(stdout)
        self.assertEqual(entries[0]["message"]["role"], "user")
        self.assertEqual(entries[1]["message"]["role"], "assistant")

    def test_transcripts_show_missing_session(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            code, stdout, stderr = self.run_cli(
                ["transcripts", "show", "missing"],
                chatdata_dir=temp_dir,
            )

        self.assertEqual(code, 2)
        self.assertEqual(stdout, "")
        self.assertIn("transcript not found", stderr)

    def test_gateway_run_is_registered_placeholder(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            code, stdout, stderr = self.run_cli(["gateway", "run"], chatdata_dir=temp_dir)

        self.assertEqual(code, 2)
        self.assertEqual(stdout, "")
        self.assertIn("gateway run is registered but not implemented yet", stderr)

    def test_no_arguments_prints_help(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            code, stdout, stderr = self.run_cli([], chatdata_dir=temp_dir)

        self.assertEqual(code, 0)
        self.assertIn("usage: pyclaw", stdout)
        self.assertEqual(stderr, "")

    def test_assistant_text_joins_text_blocks(self):
        message = AssistantMessage(
            content=[
                {"type": "text", "text": "hello"},
                {"type": "toolCall", "id": "call_1", "name": "noop", "input": {}},
                {"type": "text", "text": " world"},
            ]
        )

        self.assertEqual(assistant_text(message), "hello world")

    def test_build_model_options(self):
        class Args:
            reasoning_effort = "low"
            max_output_tokens = 128

        self.assertEqual(
            build_model_options(Args()),
            {"reasoning": {"effort": "low"}, "max_output_tokens": 128},
        )


if __name__ == "__main__":
    unittest.main()
