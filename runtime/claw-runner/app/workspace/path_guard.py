"""Workspace path guard — prevents path traversal and ensures workspace isolation.

This is the highest-priority security module for claw-runner.
Every file-system operation MUST validate paths through this guard.
"""

from __future__ import annotations

import os
from pathlib import Path

from fastapi import HTTPException


class PathTraversalError(HTTPException):
    """Raised when a requested path escapes the workspace boundary."""

    def __init__(self, detail: str = "path escapes workspace") -> None:
        super().__init__(status_code=400, detail=detail)


class PathNotFoundError(HTTPException):
    """Raised when a requested path does not exist."""

    def __init__(self, detail: str = "path not found") -> None:
        super().__init__(status_code=404, detail=detail)


class PathNotFileError(HTTPException):
    """Raised when a path is not a regular file."""

    def __init__(self, detail: str = "path is not a file") -> None:
        super().__init__(status_code=400, detail=detail)


class PathNotDirectoryError(HTTPException):
    """Raised when a path is not a directory."""

    def __init__(self, detail: str = "path is not a directory") -> None:
        super().__init__(status_code=400, detail=detail)


class PathGuard:
    """Validates and resolves paths within the workspace boundary.

    Rules enforced:
    1. All paths MUST resolve inside WORKSPACE.
    2. No relative-path (`..`) escapes allowed.
    3. No symlink escapes allowed (resolved via .resolve()).
    4. Operations stay within `/.claw/` boundary for Phase 2.
    """

    def __init__(self, workspace_root: Path) -> None:
        self._root = workspace_root.resolve()

    @property
    def root(self) -> Path:
        return self._root

    def resolve(self, relative_path: str) -> Path:
        """Resolve a workspace-relative path and validate it stays within bounds.

        Args:
            relative_path: A path relative to the workspace root.

        Returns:
            The resolved absolute Path.

        Raises:
            PathTraversalError: If the resolved path escapes the workspace.
        """
        # Normalise: strip leading slashes / backslashes so we always join
        # relative to root, never interpret as absolute.
        cleaned = relative_path.lstrip("/").lstrip("\\")
        if cleaned == "":
            cleaned = "."

        candidate = (self._root / cleaned).resolve()

        # Reject if the resolved path is not under the workspace root.
        # This covers `..` traversal AND symlink escapes.
        if candidate != self._root and self._root not in candidate.parents:
            raise PathTraversalError(
                f"path escapes workspace: {relative_path!r} resolves to "
                f"{candidate} which is outside {self._root}"
            )

        return candidate

    def require_exists(self, path: Path) -> Path:
        """Ensure the resolved path exists on disk."""
        if not path.exists():
            raise PathNotFoundError(f"path not found: {path}")
        return path

    def require_file(self, path: Path) -> Path:
        """Ensure the resolved path is a regular file."""
        self.require_exists(path)
        if not path.is_file():
            raise PathNotFileError(f"path is not a file: {path}")
        return path

    def require_directory(self, path: Path) -> Path:
        """Ensure the resolved path is a directory."""
        self.require_exists(path)
        if not path.is_dir():
            raise PathNotDirectoryError(f"path is not a directory: {path}")
        return path

    def relative(self, path: Path) -> str:
        """Return a root-relative string for the given absolute path."""
        try:
            rel = path.relative_to(self._root)
            return str(rel) if str(rel) != "." else "."
        except ValueError:
            raise PathTraversalError(f"path is not within workspace: {path}")


# ---------------------------------------------------------------------------
# Module-level convenience
# ---------------------------------------------------------------------------

_guard: PathGuard | None = None


def get_path_guard() -> PathGuard:
    """Return the module-level PathGuard singleton, initialising it if needed."""
    global _guard
    if _guard is None:
        from app.config.settings import settings
        _guard = PathGuard(Path(settings.workspace_dir))
    return _guard


def workspace_path(relative_path: str) -> Path:
    """Convenience: resolve and validate a workspace-relative path."""
    return get_path_guard().resolve(relative_path)
