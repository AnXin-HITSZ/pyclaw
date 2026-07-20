"""Sandbox resource limits — constrains command and tool execution.

All limits are overridable via environment variables (SAAS_CLAW_ prefix).
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class SandboxLimits:
    """Resource limits for sandboxed execution."""

    # Command execution
    command_timeout_seconds: int = 300      # max wall-clock time per command
    command_max_output_chars: int = 1_000_000  # max stdout + stderr chars
    command_max_env_vars: int = 100         # max env vars allowed

    # File operations
    file_max_size_bytes: int = 100 * 1024 * 1024  # 100 MiB
    file_max_read_chars: int = 5_000_000         # ~5 MiB of UTF-8 text

    # Tool execution
    tool_max_duration_seconds: int = 120

    @classmethod
    def from_env(cls) -> "SandboxLimits":
        """Build limits from environment variables (SAAS_CLAW_ prefix)."""
        import os

        def _int(key: str, default: int) -> int:
            val = os.getenv(f"SAAS_CLAW_{key}", "")
            return int(val) if val else default

        return cls(
            command_timeout_seconds=_int("COMMAND_TIMEOUT_SECONDS", 300),
            command_max_output_chars=_int("COMMAND_MAX_OUTPUT_CHARS", 1_000_000),
            command_max_env_vars=_int("COMMAND_MAX_ENV_VARS", 100),
            file_max_size_bytes=_int("FILE_MAX_SIZE_BYTES", 100 * 1024 * 1024),
            file_max_read_chars=_int("FILE_MAX_READ_CHARS", 5_000_000),
            tool_max_duration_seconds=_int("TOOL_MAX_DURATION_SECONDS", 120),
        )


# Module-level singleton
_limits: SandboxLimits | None = None


def get_limits() -> SandboxLimits:
    """Return the module-level SandboxLimits singleton."""
    global _limits
    if _limits is None:
        _limits = SandboxLimits.from_env()
    return _limits
