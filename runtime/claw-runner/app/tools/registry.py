"""Tool registry — holds and resolves available tools for the runner."""

from __future__ import annotations

from typing import Any, Callable, Optional
from dataclasses import dataclass, field


@dataclass
class ToolDefinition:
    """Lightweight tool definition for the runner."""

    name: str
    label: str = ""
    description: str = ""
    input_schema: dict[str, Any] = field(default_factory=dict)
    execute: Optional[Callable[..., Any]] = None


class ToolRegistry:
    """Registry of tools available for execution in this runner instance."""

    def __init__(self) -> None:
        self._tools: dict[str, ToolDefinition] = {}

    def register(self, tool: ToolDefinition) -> None:
        """Register a tool definition."""
        self._tools[tool.name] = tool

    def resolve(self, name: str) -> Optional[ToolDefinition]:
        """Look up a tool by name. Returns None if not found."""
        return self._tools.get(name)

    def list_tools(self) -> list[ToolDefinition]:
        """Return all registered tool definitions."""
        return list(self._tools.values())

    def tool_names(self) -> list[str]:
        """Return all registered tool names."""
        return list(self._tools.keys())

    def __len__(self) -> int:
        return len(self._tools)

    def __contains__(self, name: str) -> bool:
        return name in self._tools


# ---------------------------------------------------------------------------
# Module-level singleton
# ---------------------------------------------------------------------------

_registry: Optional[ToolRegistry] = None


def get_tool_registry() -> ToolRegistry:
    """Return the module-level ToolRegistry singleton."""
    global _registry
    if _registry is None:
        _registry = ToolRegistry()
        _register_builtin_tools(_registry)
    return _registry


def _register_builtin_tools(registry: ToolRegistry) -> None:
    """Register the built-in workspace tools that claw-runner provides."""
    from app.workspace.file_service import get_file_service

    fs = get_file_service()

    # workspace_info tool
    registry.register(
        ToolDefinition(
            name="workspace_info",
            label="Workspace Info",
            description="Get information about the current workspace.",
            input_schema={
                "type": "object",
                "properties": {},
                "required": [],
            },
            execute=lambda **kwargs: fs.workspace_info(),
        )
    )

    # list_files tool
    registry.register(
        ToolDefinition(
            name="list_files",
            label="List Files",
            description="List files and directories in the workspace.",
            input_schema={
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Path to list (default: '.')",
                    }
                },
                "required": [],
            },
            execute=lambda path=".", **kwargs: fs.list_files(path),
        )
    )

    # read_file tool
    registry.register(
        ToolDefinition(
            name="read_file",
            label="Read File",
            description="Read the contents of a file in the workspace.",
            input_schema={
                "type": "object",
                "properties": {
                    "file_path": {
                        "type": "string",
                        "description": "Path to the file to read.",
                    }
                },
                "required": ["file_path"],
            },
            execute=lambda file_path, **kwargs: fs.read_file(file_path),
        )
    )

    # write_file tool
    registry.register(
        ToolDefinition(
            name="write_file",
            label="Write File",
            description="Write content to a file in the workspace.",
            input_schema={
                "type": "object",
                "properties": {
                    "file_path": {
                        "type": "string",
                        "description": "Path to the file to write.",
                    },
                    "content": {
                        "type": "string",
                        "description": "Content to write to the file.",
                    },
                },
                "required": ["file_path", "content"],
            },
            execute=lambda file_path, content, **kwargs: fs.write_file(file_path, content),
        )
    )

    # apply_patch tool
    registry.register(
        ToolDefinition(
            name="apply_patch",
            label="Apply Patch",
            description="Apply a text replacement patch to a file.",
            input_schema={
                "type": "object",
                "properties": {
                    "file_path": {
                        "type": "string",
                        "description": "Path to the file to patch.",
                    },
                    "old_text": {
                        "type": "string",
                        "description": "Text to find and replace.",
                    },
                    "new_text": {
                        "type": "string",
                        "description": "Replacement text.",
                    },
                    "replace_all": {
                        "type": "boolean",
                        "description": "Replace all occurrences (default: false).",
                    },
                },
                "required": ["file_path", "old_text", "new_text"],
            },
            execute=lambda file_path, old_text, new_text, replace_all=False, **kwargs: fs.apply_patch(
                file_path, old_text, new_text, replace_all
            ),
        )
    )
