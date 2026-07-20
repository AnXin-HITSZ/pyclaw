"""Tool execution endpoints — thin HTTP adaptation layer."""

from fastapi import APIRouter

from app.schemas.tools import ToolExecuteRequest, ToolExecuteResponse
from app.tools.executor import execute_tool

router = APIRouter(prefix="/v1/tools", tags=["tools"])


@router.post("/execute", response_model=ToolExecuteResponse)
def tool_execute(request: ToolExecuteRequest) -> ToolExecuteResponse:
    """Execute a tool by name with the given arguments."""
    result = execute_tool(
        tool_name=request.tool_name,
        arguments=request.arguments,
        context=request.context,
    )
    return ToolExecuteResponse(**result)
