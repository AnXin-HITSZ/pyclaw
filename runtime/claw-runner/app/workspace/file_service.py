"""Workspace file service — file-system operations within the sandbox boundary.

All paths are validated through PathGuard before any I/O.
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Optional

from app.workspace.path_guard import (
    PathGuard,
    get_path_guard,
    PathNotFoundError,
    PathNotFileError,
    PathNotDirectoryError,
)


class FileService:
    """Encapsulates file read/write/list/patch operations within the workspace."""

    def __init__(self, guard: Optional[PathGuard] = None) -> None:
        self._guard = guard or get_path_guard()

    # ------------------------------------------------------------------
    # Info
    # ------------------------------------------------------------------

    def workspace_info(self) -> dict:
        """Return metadata about the workspace."""
        self._guard.root.mkdir(parents=True, exist_ok=True)
        from app.config.settings import settings
        return {
            "workspace": str(self._guard.root),
            "clawId": settings.claw_id,
            "clawName": settings.claw_name,
            "ownerUserId": settings.owner_user_id,
        }

    # ------------------------------------------------------------------
    # List
    # ------------------------------------------------------------------

    def list_files(self, path: str = ".") -> dict:
        """List files and directories at *path* inside the workspace."""
        root = self._guard.resolve(path)
        self._guard.require_directory(root)

        items = []
        for item in sorted(
            root.iterdir(),
            key=lambda p: (not p.is_dir(), p.name.lower()),
        ):
            stat = item.stat()
            items.append({
                "name": item.name,
                "path": self._guard.relative(item),
                "type": "directory" if item.is_dir() else "file",
                "size": stat.st_size,
            })

        return {
            "path": self._guard.relative(root),
            "items": items,
        }

    # ------------------------------------------------------------------
    # Read
    # ------------------------------------------------------------------

    def read_file(self, file_path: str) -> dict:
        """Read a UTF-8 text file from the workspace."""
        target = self._guard.resolve(file_path)
        self._guard.require_file(target)

        return {
            "path": file_path,
            "content": target.read_text(encoding="utf-8"),
        }

    # ------------------------------------------------------------------
    # Write
    # ------------------------------------------------------------------

    def write_file(self, file_path: str, content: str) -> dict:
        """Write (create or overwrite) a UTF-8 text file in the workspace."""
        target = self._guard.resolve(file_path)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

        return {
            "path": file_path,
            "size": target.stat().st_size,
        }

    # ------------------------------------------------------------------
    # Patch
    # ------------------------------------------------------------------

    def apply_patch(
        self, file_path: str, old_text: str, new_text: str, replace_all: bool = False
    ) -> dict:
        """Apply a text-replacement patch to an existing file."""
        target = self._guard.resolve(file_path)
        self._guard.require_file(target)

        content = target.read_text(encoding="utf-8")
        count = content.count(old_text)

        if count == 0:
            from fastapi import HTTPException
            raise HTTPException(status_code=409, detail="old_text not found")
        if count > 1 and not replace_all:
            from fastapi import HTTPException
            raise HTTPException(
                status_code=409,
                detail="old_text appears multiple times; set replace_all=true to replace all occurrences",
            )

        patched = (
            content.replace(old_text, new_text)
            if replace_all
            else content.replace(old_text, new_text, 1)
        )
        target.write_text(patched, encoding="utf-8")

        return {
            "path": file_path,
            "replacements": count if replace_all else 1,
            "size": target.stat().st_size,
        }


# Module-level singleton
_file_service: Optional[FileService] = None


def get_file_service() -> FileService:
    """Return the module-level FileService singleton."""
    global _file_service
    if _file_service is None:
        _file_service = FileService()
    return _file_service
