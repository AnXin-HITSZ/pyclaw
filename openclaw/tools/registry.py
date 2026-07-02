"""Tool definitions and registry."""

from __future__ import annotations

import inspect
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any, Protocol


class Tool(Protocol):
    name: str
    description: str
    input_schema: dict[str, Any]
    parallel: bool

    async def __call__(self, **kwargs: Any) -> Any:
        ...


@dataclass
class FunctionTool:
    name: str
    description: str
    func: Callable[..., Any | Awaitable[Any]]
    input_schema: dict[str, Any] = field(default_factory=lambda: {"type": "object", "properties": {}})
    parallel: bool = False

    async def __call__(self, **kwargs: Any) -> Any:
        result = self.func(**kwargs)
        if inspect.isawaitable(result):
            return await result
        return result


@dataclass
class ToolRegistry:
    tools: dict[str, Tool] = field(default_factory=dict)

    def register(self, tool: Tool) -> None:
        if not tool.name:
            raise ValueError("tool name cannot be empty")
        self.tools[tool.name] = tool

    def resolve(self, name: str) -> Tool | None:
        return self.tools.get(name)

    def to_llm_tools(self) -> list[dict[str, Any]]:
        return [
            {
                "name": tool.name,
                "description": tool.description,
                "input_schema": tool.input_schema,
            }
            for tool in self.tools.values()
        ]

    def validate_input(self, tool: Tool, value: dict[str, Any]) -> None:
        required = tool.input_schema.get("required", [])
        for key in required:
            if key not in value:
                raise ValueError(f"missing required argument: {key}")
