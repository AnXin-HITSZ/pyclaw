"""Workspace endpoints — thin HTTP adaptation layer.

All file paths are validated through PathGuard before any I/O.
"""

from fastapi import APIRouter

from app.schemas.workspace import (
    WorkspaceInfoResponse,
    ListFilesResponse,
    ReadFileResponse,
    WriteFileRequest,
    WriteFileResponse,
    PatchFileRequest,
    PatchFileResponse,
)
from app.workspace.file_service import get_file_service

router = APIRouter(prefix="/v1/workspace", tags=["workspace"])


@router.get("", response_model=WorkspaceInfoResponse)
def workspace_info() -> WorkspaceInfoResponse:
    """Return workspace metadata."""
    info = get_file_service().workspace_info()
    return WorkspaceInfoResponse(**info)


@router.get("/files", response_model=ListFilesResponse)
def list_files(path: str = ".") -> ListFilesResponse:
    """List files and directories under *path* inside the workspace."""
    result = get_file_service().list_files(path)
    return ListFilesResponse(**result)


@router.get("/files/{file_path:path}", response_model=ReadFileResponse)
def read_file(file_path: str) -> ReadFileResponse:
    """Read the contents of a file in the workspace."""
    result = get_file_service().read_file(file_path)
    return ReadFileResponse(**result)


@router.put("/files/{file_path:path}", response_model=WriteFileResponse)
def write_file(file_path: str, request: WriteFileRequest) -> WriteFileResponse:
    """Create or overwrite a file in the workspace."""
    result = get_file_service().write_file(file_path, request.content)
    return WriteFileResponse(**result)


@router.post("/patches", response_model=PatchFileResponse)
def apply_patch(request: PatchFileRequest) -> PatchFileResponse:
    """Apply a text-replacement patch to a file."""
    result = get_file_service().apply_patch(
        request.file_path, request.old_text, request.new_text, request.replace_all
    )
    return PatchFileResponse(**result)
