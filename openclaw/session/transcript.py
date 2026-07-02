"""JSONL transcript persistence."""

from __future__ import annotations

import json
import threading
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from uuid import uuid4

from openclaw.llm.types import AgentMessage, message_to_dict


class Transcript:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self._lock = threading.Lock()
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def append_message(self, message: AgentMessage) -> dict[str, Any]:
        entry = build_transcript_entry(message)
        append_jsonl_entry(self.path, entry, self._lock)
        return entry


def build_transcript_entry(message: AgentMessage) -> dict[str, Any]:
    return {
        "type": "message",
        "id": uuid4().hex,
        "parentId": None,
        "timestamp": datetime.now(UTC).isoformat(),
        "message": message_to_dict(message),
    }


def append_jsonl_entry(path: Path, entry: dict[str, Any], lock: threading.Lock | None = None) -> None:
    line = json.dumps(entry, ensure_ascii=False, separators=(",", ":")) + "\n"
    if lock is None:
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("a", encoding="utf-8", newline="\n") as handle:
            handle.write(line)
        return
    with lock:
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("a", encoding="utf-8", newline="\n") as handle:
            handle.write(line)
