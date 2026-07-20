"""Application settings for claw-runner data plane."""

import os

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    model_config = {"env_prefix": "PYCLAW_", "case_sensitive": False}

    host: str = "0.0.0.0"
    port: int = 8091
    log_level: str = "INFO"

    # Claw identity (set per-runner instance)
    claw_id: str = ""
    owner_user_id: str = ""
    claw_name: str = ""

    # Workspace root directory
    workspace_dir: str = "/workspace"


settings = Settings()
