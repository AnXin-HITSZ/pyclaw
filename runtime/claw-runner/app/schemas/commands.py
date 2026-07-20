"""Schemas for command execution endpoints."""

from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field


class CommandRunRequest(BaseModel):
    """Request to execute a shell command inside the sandbox."""

    command: str
    cwd: str = "."
    timeout_seconds: int = Field(default=60, ge=1, le=600)
    env: Optional[dict[str, str]] = None


class CommandRunResponse(BaseModel):
    """Response from a command execution."""

    command: str
    exit_code: int = -1
    stdout: str = ""
    stderr: str = ""
    timed_out: bool = False
    duration_ms: int = 0
