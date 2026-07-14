from pathlib import Path
import os

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

WORKSPACE = Path(os.getenv("PYCLAW_WORKSPACE", "/workspace")).resolve()
CLAW_ID = os.getenv("PYCLAW_CLAW_ID", "")
OWNER_USER_ID = os.getenv("PYCLAW_OWNER_USER_ID", "")
CLAW_NAME = os.getenv("PYCLAW_CLAW_NAME", "")

app = FastAPI(title="PyClaw Sandbox Runner")


class WriteFileRequest(BaseModel):
    content: str


def workspace_path(relative_path: str) -> Path:
    candidate = (WORKSPACE / relative_path).resolve()
    if candidate != WORKSPACE and WORKSPACE not in candidate.parents:
        raise HTTPException(status_code=400, detail="path escapes workspace")
    return candidate


@app.get("/healthz")
def healthz():
    return {
        "status": "ok",
        "service": "pyclaw-sandbox-runner",
        "clawId": CLAW_ID,
        "ownerUserId": OWNER_USER_ID,
    }


@app.get("/v1/workspace")
def workspace_info():
    WORKSPACE.mkdir(parents=True, exist_ok=True)
    return {
        "workspace": str(WORKSPACE),
        "clawId": CLAW_ID,
        "clawName": CLAW_NAME,
        "ownerUserId": OWNER_USER_ID,
    }


@app.get("/v1/workspace/files")
def list_files(path: str = "."):
    root = workspace_path(path)
    if not root.exists():
        raise HTTPException(status_code=404, detail="path not found")
    if not root.is_dir():
        raise HTTPException(status_code=400, detail="path is not a directory")
    items = []
    for item in sorted(root.iterdir(), key=lambda p: (not p.is_dir(), p.name.lower())):
        stat = item.stat()
        items.append({
            "name": item.name,
            "path": str(item.relative_to(WORKSPACE)),
            "type": "directory" if item.is_dir() else "file",
            "size": stat.st_size,
        })
    return {"path": str(root.relative_to(WORKSPACE)) if root != WORKSPACE else ".", "items": items}


@app.get("/v1/workspace/files/{file_path:path}")
def read_file(file_path: str):
    target = workspace_path(file_path)
    if not target.exists():
        raise HTTPException(status_code=404, detail="file not found")
    if not target.is_file():
        raise HTTPException(status_code=400, detail="path is not a file")
    return {"path": file_path, "content": target.read_text(encoding="utf-8")}


@app.put("/v1/workspace/files/{file_path:path}")
def write_file(file_path: str, request: WriteFileRequest):
    target = workspace_path(file_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(request.content, encoding="utf-8")
    return {"path": file_path, "size": target.stat().st_size}
