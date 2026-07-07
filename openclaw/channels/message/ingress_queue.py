"""Durable ingress queue backends for channel messages."""

from __future__ import annotations

import json
import os
import re
import time
from collections.abc import Callable
from contextlib import closing
from dataclasses import dataclass
from typing import Any, Iterable, Literal, Protocol
from urllib.parse import parse_qs, unquote, urlparse
from uuid import uuid4


IngressStatus = Literal["pending", "claimed", "completed", "failed"]
ConnectionFactory = Callable[[], Any]
_IDENTIFIER_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


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


class IngressQueue(Protocol):
    def enqueue(self, event_id: str, channel: str, payload: dict[str, Any], *, lane_key: str | None = None) -> bool:
        ...

    def claim_next(
        self,
        owner_id: str,
        *,
        blocked_lane_keys: Iterable[str] = (),
        channel: str | None = None,
    ) -> IngressQueueClaim | None:
        ...

    def complete(self, claim: IngressQueueClaim) -> bool:
        ...

    def fail(self, claim: IngressQueueClaim, *, error: str | None = None) -> bool:
        ...

    def release(self, claim: IngressQueueClaim) -> bool:
        ...

    def get(self, event_id: str) -> IngressQueueRecord | None:
        ...



@dataclass(frozen=True)
class MySQLIngressQueueConfig:
    host: str = "127.0.0.1"
    port: int = 3306
    user: str = "root"
    password: str = ""
    database: str = "pyclaw"
    charset: str = "utf8mb4"
    table_name: str = "ingress_queue"


class MySQLIngressQueue:
    """MySQL-backed durable ingress queue."""

    def __init__(
        self,
        config: MySQLIngressQueueConfig | str,
        *,
        connection_factory: ConnectionFactory | None = None,
        stale_after_seconds: float = 300,
        init_schema: bool = True,
    ) -> None:
        self.config = parse_mysql_dsn(config) if isinstance(config, str) else config
        self.table_name = validate_mysql_identifier(self.config.table_name)
        self.charset = validate_mysql_identifier(self.config.charset)
        self.stale_after_seconds = stale_after_seconds
        self._connection_factory = connection_factory
        if init_schema:
            self._init_db()

    def enqueue(self, event_id: str, channel: str, payload: dict[str, Any], *, lane_key: str | None = None) -> bool:
        now = time.time()
        with self._transaction() as db:
            cursor = db.cursor()
            cursor.execute(f"select status from `{self.table_name}` where event_id = %s", (event_id,))
            if cursor.fetchone() is not None:
                return False
            cursor.execute(
                f"""
                insert into `{self.table_name}`
                    (event_id, channel, lane_key, payload_json, status, attempts, created_at, updated_at)
                values (%s, %s, %s, %s, 'pending', 0, %s, %s)
                """,
                (event_id, channel, lane_key, json.dumps(payload, ensure_ascii=False), now, now),
            )
            return True

    def claim_next(
        self,
        owner_id: str,
        *,
        blocked_lane_keys: Iterable[str] = (),
        channel: str | None = None,
    ) -> IngressQueueClaim | None:
        now = time.time()
        blocked = set(blocked_lane_keys)
        with self._transaction() as db:
            self._release_stale_claims(db, now)
            cursor = db.cursor()
            where_sql = "where status = 'pending'"
            params: list[Any] = []
            if channel:
                where_sql += " and channel = %s"
                params.append(channel)
            cursor.execute(
                f"""
                select event_id, lane_key, payload_json
                from `{self.table_name}`
                {where_sql}
                order by created_at asc
                limit 100
                for update skip locked
                """,
                tuple(params),
            )
            rows = cursor.fetchall()
            for event_id, lane_key, payload_json in rows:
                if lane_key and lane_key in blocked:
                    continue
                if lane_key and self._lane_has_active_claim(db, lane_key):
                    continue
                token = uuid4().hex
                updated = db.cursor()
                updated.execute(
                    f"""
                    update `{self.table_name}`
                    set status = 'claimed', owner_id = %s, claim_token = %s, claimed_at = %s,
                        attempts = attempts + 1, updated_at = %s
                    where event_id = %s and status = 'pending'
                    """,
                    (owner_id, token, now, now, event_id),
                )
                if updated.rowcount:
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
        with self._transaction() as db:
            cursor = db.cursor()
            cursor.execute(
                f"""
                update `{self.table_name}`
                set status = 'pending', owner_id = null, claim_token = null, claimed_at = null, updated_at = %s
                where event_id = %s and claim_token = %s and status = 'claimed'
                """,
                (now, claim.event_id, claim.claim_token),
            )
            return bool(cursor.rowcount)

    def get(self, event_id: str) -> IngressQueueRecord | None:
        with closing(self._connect()) as db:
            cursor = db.cursor()
            cursor.execute(
                f"""
                select event_id, channel, lane_key, payload_json, status, attempts
                from `{self.table_name}`
                where event_id = %s
                """,
                (event_id,),
            )
            row = cursor.fetchone()
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
        with self._transaction() as db:
            cursor = db.cursor()
            cursor.execute(
                f"""
                update `{self.table_name}`
                set status = %s, error = %s, updated_at = %s
                where event_id = %s and claim_token = %s and status = 'claimed'
                """,
                (status, error, now, claim.event_id, claim.claim_token),
            )
            return bool(cursor.rowcount)

    def _connect(self) -> Any:
        if self._connection_factory is not None:
            return self._connection_factory()
        try:
            import pymysql
        except ImportError as exc:
            raise RuntimeError(
                "MySQLIngressQueue requires the optional 'pymysql' package. "
                'Install it with: python -m pip install "pyclaw[mysql]"'
            ) from exc
        return pymysql.connect(
            host=self.config.host,
            port=self.config.port,
            user=self.config.user,
            password=self.config.password,
            database=self.config.database,
            charset=self.config.charset,
            autocommit=False,
        )

    def _transaction(self) -> Any:
        return _DatabaseTransaction(self._connect())

    def _init_db(self) -> None:
        with self._transaction() as db:
            cursor = db.cursor()
            cursor.execute(
                f"""
                create table if not exists `{self.table_name}` (
                    event_id varchar(191) primary key,
                    channel varchar(64) not null,
                    lane_key varchar(191) null,
                    payload_json longtext not null,
                    status varchar(32) not null,
                    attempts int not null default 0,
                    owner_id varchar(191) null,
                    claim_token varchar(64) null,
                    error text null,
                    created_at double not null,
                    updated_at double not null,
                    claimed_at double null
                ) engine=InnoDB default charset={self.charset}
                """
            )
            self._ensure_index(cursor, "idx_ingress_status_created", "(status, created_at)")
            self._ensure_index(cursor, "idx_ingress_lane_status", "(lane_key, status)")

    def _ensure_index(self, cursor: Any, index_name: str, columns_sql: str) -> None:
        index_name = validate_mysql_identifier(index_name)
        cursor.execute(f"show index from `{self.table_name}` where Key_name = %s", (index_name,))
        if cursor.fetchone() is None:
            cursor.execute(f"create index `{index_name}` on `{self.table_name}` {columns_sql}")

    def _release_stale_claims(self, db: Any, now: float) -> None:
        stale_before = now - self.stale_after_seconds
        cursor = db.cursor()
        cursor.execute(
            f"""
            update `{self.table_name}`
            set status = 'pending', owner_id = null, claim_token = null, claimed_at = null, updated_at = %s
            where status = 'claimed' and claimed_at is not null and claimed_at < %s
            """,
            (now, stale_before),
        )

    def _lane_has_active_claim(self, db: Any, lane_key: str) -> bool:
        cursor = db.cursor()
        cursor.execute(
            f"select 1 from `{self.table_name}` where lane_key = %s and status = 'claimed' limit 1",
            (lane_key,),
        )
        return cursor.fetchone() is not None


class _DatabaseTransaction:
    def __init__(self, connection: Any) -> None:
        self.connection = connection

    def __enter__(self) -> Any:
        return self.connection

    def __exit__(self, exc_type: Any, exc: Any, traceback: Any) -> None:
        try:
            if exc_type is None:
                self.connection.commit()
            else:
                self.connection.rollback()
        finally:
            self.connection.close()


def parse_mysql_dsn(dsn: str) -> MySQLIngressQueueConfig:
    parsed = urlparse(dsn)
    if parsed.scheme not in {"mysql", "mysql+pymysql"}:
        raise ValueError("MySQL ingress queue DSN must start with mysql:// or mysql+pymysql://")
    database = parsed.path.lstrip("/")
    if not database:
        raise ValueError("MySQL ingress queue DSN must include a database name")
    query = parse_qs(parsed.query)
    charset = query.get("charset", ["utf8mb4"])[0]
    table_name = query.get("table", ["ingress_queue"])[0]
    return MySQLIngressQueueConfig(
        host=parsed.hostname or "127.0.0.1",
        port=parsed.port or 3306,
        user=unquote(parsed.username or "root"),
        password=unquote(parsed.password or ""),
        database=unquote(database),
        charset=validate_mysql_identifier(charset),
        table_name=validate_mysql_identifier(table_name),
    )


def validate_mysql_identifier(value: str) -> str:
    if not _IDENTIFIER_RE.match(value):
        raise ValueError(f"invalid MySQL identifier: {value!r}")
    return value


def create_ingress_queue_from_env(
    *,
    connection_factory: ConnectionFactory | None = None,
    init_schema: bool = True,
) -> IngressQueue:
    """Create the production ingress queue from environment variables.

    pyclaw now uses MySQL as the only durable ingress queue backend. Local
    tests should inject a fake queue instead of relying on a file-backed queue.
    """

    dsn = os.environ.get("OPENCLAW_INGRESS_QUEUE_DSN")
    if not dsn:
        raise ValueError("OPENCLAW_INGRESS_QUEUE_DSN is required; pyclaw ingress queue uses MySQL only")
    stale_after = float(os.environ.get("OPENCLAW_INGRESS_QUEUE_STALE_AFTER_SECONDS", "300"))
    return MySQLIngressQueue(
        dsn,
        connection_factory=connection_factory,
        stale_after_seconds=stale_after,
        init_schema=init_schema,
    )