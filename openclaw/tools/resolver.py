"""Resolve user-facing tools for a concrete Claw runtime context."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal

from openclaw.tools.catalog import ToolCatalogEntry, list_catalog_entries, materialize_core_tools
from openclaw.tools.policy import ToolPolicy, apply_tool_policy_pipeline, expand_tool_names

WorkspaceMode = Literal["local", "sandbox_runner"]

LOCAL_WORKSPACE_TOOLS = {
    "read", "list_dir", "ls", "grep", "find",
    "write", "edit", "apply_patch", "shell", "exec",
}
SANDBOX_WORKSPACE_TOOLS = {
    "sandbox_workspace_info",
    "sandbox_list_files",
    "sandbox_read_file",
    "sandbox_write_file",
    "sandbox_apply_patch",
}


@dataclass(frozen=True)
class ToolResolveInput:
    profile: str = "coding"
    allow: set[str] | None = None
    deny: set[str] = field(default_factory=set)
    also_allow: set[str] = field(default_factory=set)
    readonly: bool = False
    workspace_mode: WorkspaceMode = "sandbox_runner"
    web_access: bool = False


@dataclass(frozen=True)
class ResolvedTool:
    name: str
    label: str
    description: str
    section_id: str
    profiles: tuple[str, ...]
    tags: tuple[str, ...]
    risk: str
    workspace_only: bool
    workspace_modes: tuple[str, ...]
    readonly: bool
    requires_approval: bool
    prompt_hint: str


@dataclass(frozen=True)
class DeniedTool:
    name: str
    reason: str


@dataclass(frozen=True)
class PromptFragment:
    key: str
    content: str


@dataclass(frozen=True)
class ToolResolveResult:
    profile: str
    workspace_mode: str
    tools: list[ResolvedTool]
    denied_tools: list[DeniedTool]
    prompt_fragments: list[PromptFragment]


def user_visible_catalog() -> list[ToolCatalogEntry]:
    return [entry for entry in list_catalog_entries() if entry.user_visible]


def resolve_tools(request: ToolResolveInput) -> ToolResolveResult:
    policy = build_runtime_policy(request)
    pipeline = apply_tool_policy_pipeline(materialize_core_tools(), policy)
    selected = {tool.name for tool in pipeline.tools}

    visible_entries = user_visible_catalog()
    visible_by_name = {entry.name: entry for entry in visible_entries}
    available = [
        to_resolved_tool(entry)
        for entry in visible_entries
        if entry.name in selected and request.workspace_mode in entry.workspace_modes
    ]
    available.sort(key=lambda tool: (tool.section_id, tool.name))

    available_names = {tool.name for tool in available}
    denied = [
        DeniedTool(entry.name, deny_reason(entry, request, selected))
        for entry in visible_entries
        if entry.name not in available_names
    ]
    denied.sort(key=lambda tool: tool.name)

    return ToolResolveResult(
        profile=policy.profile,
        workspace_mode=request.workspace_mode,
        tools=available,
        denied_tools=denied,
        prompt_fragments=build_prompt_fragments(request, available),
    )


def build_runtime_policy(request: ToolResolveInput) -> ToolPolicy:
    deny = set(request.deny)
    also_allow = set(request.also_allow)

    if request.workspace_mode == "sandbox_runner":
        deny.update(LOCAL_WORKSPACE_TOOLS)
        also_allow.update(SANDBOX_WORKSPACE_TOOLS)
    if request.web_access:
        also_allow.update({"web_fetch", "web_search"})
    else:
        deny.update({"web_fetch", "web_search", "group:web"})

    return ToolPolicy(
        profile=normalize_profile(request.profile),
        allow=request.allow,
        deny=deny,
        also_allow=also_allow,
        readonly=request.readonly or normalize_profile(request.profile) == "readonly",
    )


def to_resolved_tool(entry: ToolCatalogEntry) -> ResolvedTool:
    return ResolvedTool(
        name=entry.name,
        label=entry.label,
        description=entry.description,
        section_id=entry.section_id,
        profiles=entry.profiles,
        tags=entry.tags,
        risk=entry.risk,
        workspace_only=entry.workspace_only,
        workspace_modes=entry.workspace_modes,
        readonly=entry.readonly,
        requires_approval=entry.requires_approval,
        prompt_hint=entry.prompt_hint,
    )


def deny_reason(entry: ToolCatalogEntry, request: ToolResolveInput, selected: set[str]) -> str:
    if request.workspace_mode not in entry.workspace_modes:
        return f"workspace_mode={request.workspace_mode} does not support this tool"
    if entry.name in {"web_fetch", "web_search"} and not request.web_access:
        return "web access is disabled for this Agent"
    if entry.name not in selected:
        expanded_allow = expand_tool_names(request.allow) if request.allow is not None else None
        if expanded_allow is not None and entry.name not in expanded_allow:
            return "not included in explicit allow list"
        expanded_deny = expand_tool_names(request.deny)
        if entry.name in expanded_deny:
            return "explicitly denied by Agent policy"
        if request.readonly and not entry.readonly:
            return "readonly policy only allows readonly tools"
        return f"not included by profile={normalize_profile(request.profile)}"
    return "not available in this runtime context"


def build_prompt_fragments(request: ToolResolveInput, tools: list[ResolvedTool]) -> list[PromptFragment]:
    fragments: list[PromptFragment] = []
    sandbox_tools = [tool for tool in tools if tool.section_id == "sandbox"]
    if request.workspace_mode == "sandbox_runner" and sandbox_tools:
        lines = [
            "当前工作区是当前 Claw 专属的 sandbox workspace。",
            "你只能通过当前可用的 sandbox 工具访问用户项目文件，不要使用本地文件系统工具操作项目文件。",
            "当前可用的 sandbox 工具：",
        ]
        lines.extend(f"- {tool.name}: {tool.prompt_hint or tool.description}" for tool in sandbox_tools)
        fragments.append(PromptFragment(key="sandbox_workspace", content="\n".join(lines)))

    web_tools = [tool for tool in tools if tool.section_id == "web"]
    if web_tools:
        lines = ["当前 Agent 可以使用以下 Web 工具访问公开互联网："]
        lines.extend(f"- {tool.name}: {tool.prompt_hint or tool.description}" for tool in web_tools)
        fragments.append(PromptFragment(key="web_tools", content="\n".join(lines)))

    return fragments


def normalize_profile(value: str | None) -> str:
    normalized = (value or "coding").strip().lower().replace("-", "_")
    if normalized not in {"minimal", "readonly", "coding", "messaging", "full"}:
        return "coding"
    return normalized
