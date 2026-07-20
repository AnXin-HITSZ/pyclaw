"""Sandbox environment — isolates the execution context for this runner instance."""

from __future__ import annotations

import os
from pathlib import Path
from typing import Optional


class SandboxEnvironment:
    """Describes the sandbox execution environment for this claw-runner.

    Each claw-runner instance gets its own sandbox with:
    - A dedicated workspace directory
    - Environment variables inherited from the host, overridable per-run
    - Resource limits (defined in sandbox/limits.py)
    """

    def __init__(
        self,
        workspace_dir: Optional[Path] = None,
        env: Optional[dict[str, str]] = None,
    ) -> None:
        self._workspace = (
            Path(workspace_dir).resolve()
            if workspace_dir
            else Path(os.getenv("PYCLAW_WORKSPACE", "/workspace")).resolve()
        )
        self._base_env = dict(os.environ)
        if env:
            self._base_env.update(env)

    @property
    def workspace(self) -> Path:
        return self._workspace

    @property
    def base_env(self) -> dict[str, str]:
        return dict(self._base_env)

    def ensure_workspace(self) -> Path:
        """Create the workspace directory if it does not exist."""
        self._workspace.mkdir(parents=True, exist_ok=True)
        return self._workspace

    def build_env(self, extra: Optional[dict[str, str]] = None) -> dict[str, str]:
        """Return the full env dict for a command execution.

        Args:
            extra: Additional env vars merged on top of the base.
        """
        env = dict(self._base_env)
        if extra:
            env.update(extra)
        return env


# Module-level singleton
_environment: Optional[SandboxEnvironment] = None


def get_sandbox_environment() -> SandboxEnvironment:
    """Return the module-level SandboxEnvironment singleton."""
    global _environment
    if _environment is None:
        from app.config.settings import settings
        _environment = SandboxEnvironment(workspace_dir=Path(settings.workspace_dir))
    return _environment
