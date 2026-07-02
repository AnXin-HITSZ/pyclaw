"""Command line interface for the Python OpenClaw runtime."""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import re
import sys
from pathlib import Path
from typing import Any, Sequence
from uuid import uuid4

from openclaw.agent.agent import Agent
from openclaw.config import load_env_file
from openclaw.llm.openai_provider import OpenAIProvider
from openclaw.llm.provider import MockProvider
from openclaw.llm.types import AssistantMessage, message_to_dict
from openclaw.session.agent_session import AgentSession
from openclaw.session.paths import (
    resolve_chatdata_dir,
    resolve_session_store_path,
    resolve_session_transcript_path,
)
from openclaw.session.store import SessionStore
from openclaw.session.transcript import Transcript

DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant."
DEFAULT_MODEL = "gpt-4.1-mini"
TRANSCRIPT_FORMATS = ("text", "detail", "json")


class CliError(Exception):
    """User-facing CLI failure."""


def main(argv: Sequence[str] | None = None) -> int:
    """Run the CLI and return a process exit code."""

    parser = build_parser()
    args = parser.parse_args(list(argv) if argv is not None else None)

    try:
        return asyncio.run(run(args, parser))
    except KeyboardInterrupt:
        print("Interrupted.", file=sys.stderr)
        return 130
    except CliError as exc:
        print(f"pyclaw: {exc}", file=sys.stderr)
        return 2


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="pyclaw",
        description="OpenClaw-inspired Python agent runtime.",
    )
    parser.add_argument(
        "arguments",
        nargs="*",
        help='Prompt text, or command path such as "transcripts show <session-id>".',
    )
    parser.add_argument(
        "--provider",
        choices=["openai", "mock"],
        default="openai",
        help="LLM provider to use.",
    )
    parser.add_argument(
        "--model",
        default=None,
        help="Model name. Defaults to OPENAI_MODEL or gpt-4.1-mini.",
    )
    parser.add_argument(
        "--system",
        default=DEFAULT_SYSTEM_PROMPT,
        help="System prompt for the agent.",
    )
    parser.add_argument(
        "--env-file",
        default=".env",
        help="Path to a .env file to load before provider creation.",
    )
    parser.add_argument(
        "--no-env-file",
        action="store_true",
        help="Do not load a .env file.",
    )
    parser.add_argument(
        "--chatdata-dir",
        default=None,
        help="Directory for sessions.json and transcript JSONL files. Defaults to ./chatdata.",
    )
    parser.add_argument(
        "--session-id",
        default=None,
        help="Session id used for transcript persistence. Defaults to a generated id.",
    )
    parser.add_argument(
        "--format",
        dest="transcript_format",
        choices=TRANSCRIPT_FORMATS,
        default="text",
        help="Output format for transcripts show: text, detail, or json.",
    )
    parser.add_argument(
        "--api-mode",
        choices=["auto", "responses", "chat_completions", "chat-completions"],
        default="auto",
        help="OpenAI SDK API mode. Use chat_completions for OpenAI-compatible providers.",
    )
    parser.add_argument(
        "--reasoning-effort",
        choices=["low", "medium", "high"],
        default=None,
        help="Pass reasoning.effort through to providers that support it.",
    )
    parser.add_argument(
        "--max-output-tokens",
        type=int,
        default=None,
        help="Pass max_output_tokens through to providers that support it.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Print the assistant message as JSON instead of plain text.",
    )
    return parser


async def run(args: argparse.Namespace, parser: argparse.ArgumentParser) -> int:
    if not args.no_env_file:
        load_env_file(args.env_file)

    if args.arguments[:2] == ["gateway", "run"]:
        raise CliError("gateway run is registered but not implemented yet.")
    if args.arguments[:1] == ["gateway"]:
        raise CliError('unknown gateway command. Did you mean "gateway run"?')
    if args.arguments[:2] == ["transcripts", "show"]:
        return show_transcript_command(args)
    if args.arguments[:1] == ["transcripts"]:
        raise CliError('unknown transcripts command. Did you mean "transcripts show <session-id>"?')

    prompt = " ".join(args.arguments).strip()
    if not prompt:
        parser.print_help()
        return 0

    message = await run_prompt(args, prompt)
    if args.json:
        print(json.dumps(message_to_dict(message), ensure_ascii=False, indent=2))
    else:
        print(assistant_text(message))
    return 0


def show_transcript_command(args: argparse.Namespace) -> int:
    if len(args.arguments) < 3:
        raise CliError("missing session id. Usage: pyclaw transcripts show <session-id> --format text")
    if len(args.arguments) > 3:
        extra = " ".join(args.arguments[3:])
        raise CliError(f"unexpected transcripts show arguments: {extra}")

    session_id = sanitize_session_id(args.arguments[2])
    path = resolve_session_transcript_path(resolve_chatdata_dir(args.chatdata_dir), session_id)
    entries = read_transcript_entries(path)
    if args.transcript_format == "json":
        print(json.dumps(entries, ensure_ascii=False, indent=2))
    elif args.transcript_format == "detail":
        print(format_transcript_detail(entries))
    else:
        print(format_transcript_text(entries))
    return 0


def read_transcript_entries(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        raise CliError(f"transcript not found: {path}")

    entries: list[dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        try:
            entry = json.loads(line)
        except json.JSONDecodeError as exc:
            raise CliError(f"invalid transcript JSONL at line {line_number}: {exc.msg}") from exc
        if isinstance(entry, dict):
            entries.append(entry)
    return entries


def format_transcript_text(entries: list[dict[str, Any]]) -> str:
    lines: list[str] = []
    for entry in entries:
        message = dict(entry.get("message") or {})
        role = str(message.get("role") or "unknown")
        text = message_text(message)
        lines.append(f"{role}: {text}" if text else f"{role}: [non-text content]")
    return "\n".join(lines)


def format_transcript_detail(entries: list[dict[str, Any]]) -> str:
    sections: list[str] = []
    for entry in entries:
        message = dict(entry.get("message") or {})
        role = str(message.get("role") or "unknown")
        timestamp = str(entry.get("timestamp") or message.get("timestamp") or "")
        header_parts = [f"[{timestamp}]" if timestamp else "[no timestamp]", role]
        provider = message.get("provider")
        model = message.get("model")
        stop_reason = message.get("stopReason") or message.get("stop_reason")
        if provider:
            header_parts.append(f"provider={provider}")
        if model:
            header_parts.append(f"model={model}")
        if stop_reason:
            header_parts.append(f"stop={stop_reason}")
        usage = message.get("usage")
        if isinstance(usage, dict) and usage:
            header_parts.append(f"usage={json.dumps(usage, ensure_ascii=False, separators=(',', ':'))}")
        body = message_text(message) or format_non_text_blocks(message)
        sections.append(" ".join(header_parts) + "\n" + body)
    return "\n\n".join(sections)


def message_text(message: dict[str, Any]) -> str:
    parts: list[str] = []
    for block in message.get("content") or []:
        if isinstance(block, dict) and block.get("type") == "text":
            parts.append(str(block.get("text", "")))
    return "".join(parts)


def format_non_text_blocks(message: dict[str, Any]) -> str:
    lines: list[str] = []
    for block in message.get("content") or []:
        if not isinstance(block, dict):
            continue
        block_type = block.get("type")
        if block_type == "toolCall":
            lines.append(
                "toolCall "
                + str(block.get("name", ""))
                + " "
                + json.dumps(block.get("input") or {}, ensure_ascii=False, separators=(",", ":"))
            )
        elif block_type == "toolResult":
            prefix = "toolResult error" if block.get("is_error") else "toolResult"
            lines.append(prefix + " " + str(block.get("name", "")) + " " + str(block.get("output", "")))
        elif block_type != "text":
            lines.append(json.dumps(block, ensure_ascii=False, separators=(",", ":")))
    return "\n".join(lines) if lines else "[empty message]"


async def run_prompt(args: argparse.Namespace, prompt: str) -> AssistantMessage:
    model = args.model or os.environ.get("OPENAI_MODEL") or DEFAULT_MODEL
    provider = build_provider(args.provider, prompt, api_mode=args.api_mode)
    agent = Agent(
        model=model,
        provider=provider,
        system_prompt=args.system,
        model_options=build_model_options(args),
    )
    session = build_agent_session(args, agent)
    return await session.run_prompt(prompt)


def build_agent_session(args: argparse.Namespace, agent: Agent) -> AgentSession:
    chatdata_dir = resolve_chatdata_dir(args.chatdata_dir)
    session_id = sanitize_session_id(args.session_id or uuid4().hex)
    return AgentSession(
        session_id=session_id,
        agent=agent,
        store=SessionStore(resolve_session_store_path(chatdata_dir)),
        transcript=Transcript(resolve_session_transcript_path(chatdata_dir, session_id)),
        cwd=os.getcwd(),
        workspace_dir=os.getcwd(),
    )


def sanitize_session_id(value: str) -> str:
    sanitized = re.sub(r"[^A-Za-z0-9._-]+", "-", value.strip())
    sanitized = sanitized.strip(".-_")
    if not sanitized:
        raise CliError("session id must contain at least one letter or number")
    return sanitized


def build_provider(name: str, prompt: str, *, api_mode: str = "auto") -> Any:
    if name == "mock":
        return MockProvider(
            [
                AssistantMessage(
                    content=[{"type": "text", "text": f"mock response: {prompt}"}],
                    provider="mock",
                    model="mock-model",
                    stop_reason="stop",
                )
            ]
        )

    if name == "openai":
        if not os.environ.get("OPENAI_API_KEY"):
            raise CliError("OPENAI_API_KEY is not set. Put it in .env or set it in the shell.")
        return OpenAIProvider(api_mode=normalize_api_mode_arg(api_mode))

    raise CliError(f"unsupported provider: {name}")


def normalize_api_mode_arg(value: str) -> str:
    return value.replace("-", "_")


def build_model_options(args: argparse.Namespace) -> dict[str, Any]:
    options: dict[str, Any] = {}
    if args.reasoning_effort:
        options["reasoning"] = {"effort": args.reasoning_effort}
    if args.max_output_tokens is not None:
        options["max_output_tokens"] = args.max_output_tokens
    return options


def assistant_text(message: AssistantMessage) -> str:
    parts: list[str] = []
    for block in message.content:
        if block.get("type") == "text":
            parts.append(str(block.get("text", "")))
    if parts:
        return "".join(parts)
    if message.error_message:
        return message.error_message
    return ""


if __name__ == "__main__":
    raise SystemExit(main())
