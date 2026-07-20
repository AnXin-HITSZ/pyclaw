"""Command execution endpoints — thin HTTP adaptation layer."""

from fastapi import APIRouter

from app.schemas.commands import CommandRunRequest, CommandRunResponse
from app.sandbox.command_runner import get_command_runner

router = APIRouter(prefix="/v1/commands", tags=["commands"])


@router.post("/execute", response_model=CommandRunResponse)
def command_execute(request: CommandRunRequest) -> CommandRunResponse:
    """Execute a shell command inside the sandbox."""
    result = get_command_runner().run(
        command=request.command,
        cwd=request.cwd,
        timeout_seconds=request.timeout_seconds,
        env=request.env,
    )
    return CommandRunResponse(**result)
