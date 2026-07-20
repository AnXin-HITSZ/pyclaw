"""Tool executor — executes a tool by name with arguments."""

from __future__ import annotations

import logging
from typing import Any

from app.tools.registry import ToolRegistry, get_tool_registry
from app.tools.policy import ToolPolicy, get_tool_policy

logger = logging.getLogger(__name__)


class ToolExecutionError(Exception):
    """Raised when a tool execution fails."""


class ToolNotFoundError(ToolExecutionError):
    """Raised when the requested tool is not in the registry."""


class ToolDeniedError(ToolExecutionError):
    """Raised when the tool is denied by policy."""


def execute_tool(
    tool_name: str,
    arguments: dict[str, Any],
    context: dict[str, Any] | None = None,
    registry: ToolRegistry | None = None,
    policy: ToolPolicy | None = None,
) -> dict[str, Any]:
    """Execute a tool by name.

    Args:
        tool_name: Name of the tool to execute.
        arguments: Tool arguments as a dict.
        context: Optional execution context (e.g. session_id, claw_id).
        registry: Tool registry (uses module-level singleton if omitted).
        policy: Tool policy (uses module-level singleton if omitted).

    Returns:
        A dict with {tool_name, success, output, error, is_error}.

    Raises:
        ToolNotFoundError: If the tool is not registered.
        ToolDeniedError: If the tool is denied by policy.
    """
    reg = registry or get_tool_registry()
    pol = policy or get_tool_policy()

    # 1. Resolve tool
    tool = reg.resolve(tool_name)
    if tool is None:
        raise ToolNotFoundError(f"tool not found: {tool_name}")

    # 2. Check policy
    if not pol.is_allowed(tool_name):
        raise ToolDeniedError(f"tool denied by policy: {tool_name}")

    # 3. Execute
    if tool.execute is None:
        return {
            "tool_name": tool_name,
            "success": False,
            "output": None,
            "error": f"tool has no execute function: {tool_name}",
            "is_error": True,
        }

    try:
        result = tool.execute(**arguments)
        return {
            "tool_name": tool_name,
            "success": True,
            "output": result,
            "error": None,
            "is_error": False,
        }
    except Exception as exc:
        logger.exception("Tool execution failed: %s", tool_name)
        return {
            "tool_name": tool_name,
            "success": False,
            "output": None,
            "error": str(exc),
            "is_error": True,
        }
