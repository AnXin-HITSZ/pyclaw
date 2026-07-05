"""SQLite-backed durable ingress queue."""

from __future__ import annotations

import json
import sqlite3
import time
from contextlib import closing
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Literal
from uuid import uuid4


IngressStatus = Literal["pending", "claimed", "completed", "failed"]


@dataclass(frozen=True)
class IngressQueueRecord:
    event_id: str
    channel: str
    payload: dict[str, Any]
    lane_key: str | None = None
    status: IngressStatus = "pending"
    attempts: int = 0


@dataclass(frozen=True)
class IngressQueueClaim:
    event_id: str
    claim_token: str
    owner_id: str
    lane_key: str | None
    payload: dict[str, Any]


class SQLiteIngressQueue:
    def __init__(self, path: str | Path, *, stale_after_seconds: float = 300) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.stale_after_seconds = stale_after_seconds
        self._init_db()

    def enqueue(self, event_id: str, channel: str, payload: dict[str, Any], *, lane_key: str | None = None) -> bool:
        now = time.time()
        with closing(self._connect()) as db, db:
            existing = db.execute("select status from ingress_queue where event_id = ?", (event_id,)).fetchone()
            if existing is not None:
                return False
            db.execute(
                """
                insert into ingress_queue(event_id, channel, lane_key, payload_json, status, attempts, created_at, updated_at)
                values (?, ?, ?, ?, 'pending', 0, ?, ?)
                """,
                (event_id, channel, lane_key, json.dumps(payload, ensure_ascii=False), now, now),
            )
            return True

    def claim_next(self, owner_id: str, *, blocked_lane_keys: Iterable[str] = ()) -> IngressQueueClaim | None:
        now = time.time()
        blocked = set(blocked_lane_keys)
        with closing(self._connect()) as db, db:
            self._release_stale_claims(db, now)
            rows = db.execute(
                """
                select event_id, lane_key, payload_json
                from ingress_queue
                where status = 'pending'
                order by created_at asc
                """
            ).fetchall()
            for event_id, lane_key, payload_json in rows:
                if lane_key and lane_key in blocked:
                    continue
                if lane_key and self._lane_has_active_claim(db, lane_key):
                    continue
                token = uuid4().hex
                updated = db.execute(
                    """
                    update ingress_queue
                    set status = 'claimed', owner_id = ?, claim_token = ?, claimed_at = ?,
                        attempts = attempts + 1, updated_at = ?
                    where event_id = ? and status = 'pending'
                    """,
                    (owner_id, token, now, now, event_id),
                ).rowcount
                if updated:
                    return IngressQueueClaim(
                        event_id=event_id,
                        claim_token=token,
                        owner_id=owner_id,
                        lane_key=lane_key,
                        payload=json.loads(payload_json),
                    )
        return None

    def complete(self, claim: IngressQueueClaim) -> bool:
        return self._transition_claim(claim, "completed")

    def fail(self, claim: IngressQueueClaim, *, error: str | None = None) -> bool:
        return self._transition_claim(claim, "failed", error=error)

    def release(self, claim: IngressQueueClaim) -> bool:
        now = time.time()
        with closing(self._connect()) as db, db:
            return bool(
                db.execute(
                    """
                    update ingress_queue
                    set status = 'pending', owner_id = null, claim_token = null, claimed_at = null, updated_at = ?
                    where event_id = ? and claim_token = ? and status = 'claimed'
                    """,
                    (now, claim.event_id, claim.claim_token),
                ).rowcount
            )

    def get(self, event_id: str) -> IngressQueueRecord | None:
        with closing(self._connect()) as db:
            row = db.execute(
                "select event_id, channel, lane_key, payload_json, status, attempts from ingress_queue where event_id = ?",
                (event_id,),
            ).fetchone()
        if row is None:
            return None
        return IngressQueueRecord(
            event_id=row[0],
            channel=row[1],
            lane_key=row[2],
            payload=json.loads(row[3]),
            status=row[4],
            attempts=row[5],
        )

    def _transition_claim(self, claim: IngressQueueClaim, status: IngressStatus, *, error: str | None = None) -> bool:
        now = time.time()
        with closing(self._connect()) as db, db:
            return bool(
                db.execute(
                    """
                    update ingress_queue
                    set status = ?, error = ?, updated_at = ?
                    where event_id = ? and claim_token = ? and status = 'claimed'
                    """,
                    (status, error, now, claim.event_id, claim.claim_token),
                ).rowcount
            )

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.path)
        connection.execute("pragma journal_mode = wal")
        return connection

    def _init_db(self) -> None:
        with closing(self._connect()) as db, db:
            db.execute(
                """
                create table if not exists ingress_queue (
                    event_id text primary key,
                    channel text not null,
                    lane_key text,
                    payload_json text not null,
                    status text not null,
                    attempts integer not null default 0,
                    owner_id text,
                    claim_token text,
                    error text,
                    created_at real not null,
                    updated_at real not null,
                    claimed_at real
                )
                """
            )
            db.execute("create index if not exists idx_ingress_status_created on ingress_queue(status, created_at)")
            db.execute("create index if not exists idx_ingress_lane_status on ingress_queue(lane_key, status)")

    def _release_stale_claims(self, db: sqlite3.Connection, now: float) -> None:
        stale_before = now - self.stale_after_seconds
        db.execute(
            """
            update ingress_queue
            set status = 'pending', owner_id = null, claim_token = null, claimed_at = null, updated_at = ?
            where status = 'claimed' and claimed_at is not null and claimed_at < ?
            """,
            (now, stale_before),
        )

    def _lane_has_active_claim(self, db: sqlite3.Connection, lane_key: str) -> bool:
        row = db.execute(
            "select 1 from ingress_queue where lane_key = ? and status = 'claimed' limit 1",
            (lane_key,),
        ).fetchone()
        return row is not None
