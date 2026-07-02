"""Section-based system prompt builder."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

CACHE_BOUNDARY = "<!-- cache-boundary -->"


@dataclass
class SystemPromptParams:
    workspace_dir: str
    extra_system_prompt: str | None = None
    tool_names: list[str] = field(default_factory=list)
    tool_summaries: list[str] = field(default_factory=list)
    user_timezone: str | None = None
    context_files: list[str] = field(default_factory=list)
    skills_prompt: str | None = None
    docs_path: str | None = None
    source_path: str | None = None
    runtime_info: dict[str, Any] = field(default_factory=dict)


def build_system_prompt(params: SystemPromptParams) -> str:
    sections = [
        build_tooling_section(params),
        build_tool_call_style_section(params),
        build_execution_bias_section(params),
        build_safety_section(params),
        build_workspace_section(params),
        build_documentation_section(params),
        build_project_context_section(params),
        CACHE_BOUNDARY,
        build_messaging_section(params),
        build_runtime_section(params),
    ]
    if params.extra_system_prompt:
        sections.append(params.extra_system_prompt)
    return "\n\n".join(section for section in sections if section)


def build_tooling_section(params: SystemPromptParams) -> str:
    if not params.tool_names and not params.tool_summaries:
        return "## Tooling\nNo tools are currently registered."
    lines = ["## Tooling"]
    lines.extend(f"- {name}" for name in params.tool_names)
    lines.extend(f"- {summary}" for summary in params.tool_summaries)
    return "\n".join(lines)


def build_tool_call_style_section(params: SystemPromptParams) -> str:
    return "## Tool Call Style\nUse tools when they materially improve correctness or let you inspect local state."


def build_execution_bias_section(params: SystemPromptParams) -> str:
    return "## Execution Bias\nPrefer small, verifiable steps and keep user-visible changes scoped."


def build_safety_section(params: SystemPromptParams) -> str:
    return "## Safety\nDo not perform destructive actions without explicit user approval."


def build_workspace_section(params: SystemPromptParams) -> str:
    return f"## Workspace\nworkspace_dir: {params.workspace_dir}"


def build_documentation_section(params: SystemPromptParams) -> str:
    parts = []
    if params.docs_path:
        parts.append(f"docs_path: {params.docs_path}")
    if params.source_path:
        parts.append(f"source_path: {params.source_path}")
    if not parts:
        return ""
    return "## Documentation\n" + "\n".join(parts)


def build_project_context_section(params: SystemPromptParams) -> str:
    if not params.context_files:
        return ""
    files = "\n".join(f"- {path}" for path in params.context_files)
    return f"## Project Context\n{files}"


def build_messaging_section(params: SystemPromptParams) -> str:
    return "## Messaging\nReport progress clearly and keep responses concise."


def build_runtime_section(params: SystemPromptParams) -> str:
    lines = ["## Runtime"]
    if params.user_timezone:
        lines.append(f"timezone: {params.user_timezone}")
    for key, value in params.runtime_info.items():
        lines.append(f"{key}: {value}")
    return "\n".join(lines)
