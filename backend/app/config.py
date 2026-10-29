"""Environment-based configuration. Secrets are never logged or included in repr()."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from functools import lru_cache

DEFAULT_PROVIDERS = ("internet_archive", "jamendo", "youtube")


def _bool(value: str | None, default: bool) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _float(value: str | None, default: float) -> float:
    try:
        return float(value) if value else default
    except ValueError:
        return default


def _int(value: str | None, default: int) -> int:
    try:
        return int(value) if value else default
    except ValueError:
        return default


@dataclass(frozen=True)
class Settings:
    app_name: str = "VibeSync Backend"
    version: str = "1.0.0"
    enabled_providers: tuple[str, ...] = DEFAULT_PROVIDERS
    jamendo_client_id: str | None = field(default=None, repr=False)
    youtube_api_key: str | None = field(default=None, repr=False)
    http_timeout_seconds: float = 8.0
    rate_limit_per_minute: int = 120
    allow_demo: bool = True

    @classmethod
    def from_env(cls) -> "Settings":
        raw = os.environ.get("VIBESYNC_PROVIDERS")
        providers = tuple(p.strip().lower() for p in raw.split(",") if p.strip()) if raw else DEFAULT_PROVIDERS
        return cls(
            enabled_providers=providers,
            jamendo_client_id=os.environ.get("JAMENDO_CLIENT_ID") or None,
            youtube_api_key=os.environ.get("YOUTUBE_API_KEY") or None,
            http_timeout_seconds=_float(os.environ.get("VIBESYNC_HTTP_TIMEOUT"), 8.0),
            rate_limit_per_minute=_int(os.environ.get("VIBESYNC_RATE_LIMIT_PER_MINUTE"), 120),
            allow_demo=_bool(os.environ.get("VIBESYNC_ALLOW_DEMO"), True),
        )


@lru_cache
def get_settings() -> Settings:
    return Settings.from_env()
