"""Tool policy — controls which tools can be executed in this runner instance."""

from __future__ import annotations

from typing import Optional


class ToolPolicy:
    """Simple allow/deny policy for tool execution.

    Default: all registered tools are allowed.
    When *allow* is non-empty, only those tools are allowed.
    *deny* overrides *allow* (deny takes precedence).
    """

    def __init__(
        self,
        allow: Optional[list[str]] = None,
        deny: Optional[list[str]] = None,
        readonly: bool = False,
    ) -> None:
        self._allow: set[str] = set(allow or [])
        self._deny: set[str] = set(deny or [])
        self._readonly = readonly

    @property
    def readonly(self) -> bool:
        return self._readonly

    def is_allowed(self, tool_name: str) -> bool:
        """Check if a tool is allowed under this policy."""
        # Deny takes precedence
        if tool_name in self._deny:
            return False
        # If allow-list is set, only those are allowed
        if self._allow and tool_name not in self._allow:
            return False
        # In readonly mode, deny write tools
        if self._readonly and _is_write_tool(tool_name):
            return False
        return True

    def allowed_tools(self, all_tool_names: list[str]) -> list[str]:
        """Return the subset of tool names that are allowed."""
        return [n for n in all_tool_names if self.is_allowed(n)]


_WRITE_TOOLS = {"write_file", "apply_patch"}


def _is_write_tool(name: str) -> bool:
    return name in _WRITE_TOOLS


# ---------------------------------------------------------------------------
# Module-level singleton
# ---------------------------------------------------------------------------

_policy: Optional[ToolPolicy] = None


def get_tool_policy() -> ToolPolicy:
    """Return the module-level ToolPolicy singleton (permissive default)."""
    global _policy
    if _policy is None:
        _policy = ToolPolicy()
    return _policy


def set_tool_policy(policy: ToolPolicy) -> None:
    """Replace the module-level ToolPolicy singleton."""
    global _policy
    _policy = policy
