"""Sandbox command runner — executes shell commands inside the isolated workspace."""

from __future__ import annotations

import logging
import subprocess
import time
from pathlib import Path
from typing import Optional

from app.sandbox.environment import get_sandbox_environment
from app.sandbox.limits import get_limits
from app.workspace.path_guard import get_path_guard

logger = logging.getLogger(__name__)


class CommandRunner:
    """Executes shell commands within the sandbox workspace boundary."""

    def __init__(self) -> None:
        self._env = get_sandbox_environment()
        self._limits = get_limits()
        self._guard = get_path_guard()

    def run(
        self,
        command: str,
        cwd: str = ".",
        timeout_seconds: int = 60,
        env: Optional[dict[str, str]] = None,
    ) -> dict:
        """Execute a shell command inside the sandbox.

        Args:
            command: The shell command to run.
            cwd: Working directory, relative to workspace root.
            timeout_seconds: Max execution time (clamped to limits).
            env: Additional environment variables.

        Returns:
            Dict with {command, exit_code, stdout, stderr, timed_out, duration_ms}.
        """
        # Resolve and validate cwd
        cwd_path = self._guard.resolve(cwd)
        self._guard.require_directory(cwd_path)

        # Clamp timeout
        effective_timeout = min(timeout_seconds, self._limits.command_timeout_seconds)

        # Build environment
        cmd_env = self._env.build_env(extra=env)

        # Ensure env var count is within limits
        if len(cmd_env) > self._limits.command_max_env_vars:
            logger.warning(
                "Command env has %d vars, limit is %d — truncating",
                len(cmd_env),
                self._limits.command_max_env_vars,
            )
            # Keep only the first N vars
            cmd_env = dict(list(cmd_env.items())[: self._limits.command_max_env_vars])

        # Default response
        result: dict = {
            "command": command,
            "exit_code": -1,
            "stdout": "",
            "stderr": "",
            "timed_out": False,
            "duration_ms": 0,
        }

        started = time.monotonic()
        try:
            proc = subprocess.run(
                command,
                shell=True,
                cwd=str(cwd_path),
                env=cmd_env,
                capture_output=True,
                text=True,
                timeout=effective_timeout,
            )
            elapsed_ms = int((time.monotonic() - started) * 1000)

            stdout = proc.stdout or ""
            stderr = proc.stderr or ""

            # Truncate output if needed
            max_chars = self._limits.command_max_output_chars
            if len(stdout) > max_chars:
                stdout = stdout[:max_chars] + "\n... [truncated]"
            if len(stderr) > max_chars:
                stderr = stderr[:max_chars] + "\n... [truncated]"

            result.update(
                exit_code=proc.returncode,
                stdout=stdout,
                stderr=stderr,
                duration_ms=elapsed_ms,
            )

        except subprocess.TimeoutExpired:
            elapsed_ms = int((time.monotonic() - started) * 1000)
            result.update(
                exit_code=-1,
                stderr="Command timed out",
                timed_out=True,
                duration_ms=elapsed_ms,
            )

        except Exception as exc:
            elapsed_ms = int((time.monotonic() - started) * 1000)
            result.update(
                exit_code=-1,
                stderr=str(exc),
                duration_ms=elapsed_ms,
            )

        return result


# Module-level singleton
_runner: Optional[CommandRunner] = None


def get_command_runner() -> CommandRunner:
    """Return the module-level CommandRunner singleton."""
    global _runner
    if _runner is None:
        _runner = CommandRunner()
    return _runner
