"""Schemas for tool execution endpoints."""

from __future__ import annotations

from typing import Any, Optional

from pydantic import BaseModel


class ToolExecuteRequest(BaseModel):
    """Request to execute a tool within the runner's environment."""

    tool_name: str
    arguments: dict[str, Any] = {}
    context: Optional[dict[str, Any]] = None


class ToolExecuteResponse(BaseModel):
    """Response from a tool execution."""

    tool_name: str
    success: bool = True
    output: Any = None
    error: Optional[str] = None
    is_error: bool = False


class ToolDefinitionResponse(BaseModel):
    """A tool's definition (name, description, schema)."""

    name: str
    label: str = ""
    description: str = ""
    input_schema: dict[str, Any] = {}
