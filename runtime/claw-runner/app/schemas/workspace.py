"""Schemas for workspace endpoints."""

from __future__ import annotations

from pydantic import BaseModel, Field


class WorkspaceInfoResponse(BaseModel):
    """Metadata about the workspace."""

    workspace: str
    clawId: str = ""
    clawName: str = ""
    ownerUserId: str = ""


class FileItem(BaseModel):
    """A single file or directory entry in a listing."""

    name: str
    path: str
    type: str  # "file" | "directory"
    size: int = 0


class ListFilesResponse(BaseModel):
    """Response for listing workspace files."""

    path: str = "."
    items: list[FileItem] = []


class ReadFileResponse(BaseModel):
    """Response for reading a file's contents."""

    path: str
    content: str


class WriteFileRequest(BaseModel):
    """Request to write (create/replace) a file."""

    content: str


class WriteFileResponse(BaseModel):
    """Response for a file write operation."""

    path: str
    size: int = 0


class PatchFileRequest(BaseModel):
    """Request to apply a text-replacement patch to a file."""

    file_path: str
    old_text: str
    new_text: str
    replace_all: bool = False


class PatchFileResponse(BaseModel):
    """Response for a patch operation."""

    path: str
    replacements: int = 0
    size: int = 0
