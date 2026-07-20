"""Claw Runner — FastAPI data plane (isolated execution environment)."""

from fastapi import FastAPI

from app.config import logging  # noqa: F401 — initialise logging
from app.api.health import router as health_router
from app.api.workspace import router as workspace_router
from app.api.tools import router as tools_router
from app.api.commands import router as commands_router

app = FastAPI(title="Claw Runner", version="0.1.0")

app.include_router(health_router)
app.include_router(workspace_router)
app.include_router(tools_router)
app.include_router(commands_router)
