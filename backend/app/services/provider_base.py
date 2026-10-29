from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field

import httpx

from app.config import Settings
from app.schemas import ProviderInfo, Track


class ProviderError(Exception):
    """Safe-to-show provider failure (never contains secrets)."""


@dataclass
class ProviderPage:
    items: list[Track] = field(default_factory=list)
    has_more: bool = False


class MusicProvider(ABC):
    name: str = ""
    display_name: str = ""
    capabilities: tuple[str, ...] = ()
    env_vars: tuple[str, ...] = ()
    notes: str = ""
    is_demo: bool = False

    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None) -> None:
        self.settings = settings
        self.transport = transport

    def is_configured(self) -> bool:
        return True

    def setup_hint(self) -> str | None:
        if self.is_configured() or not self.env_vars:
            return None
        return "Set environment variable(s): " + ", ".join(self.env_vars)

    def info(self) -> ProviderInfo:
        return ProviderInfo(
            name=self.name,
            display_name=self.display_name,
            configured=self.is_configured(),
            capabilities=list(self.capabilities),
            setup_hint=self.setup_hint(),
            notes=self.notes or None,
        )

    def client(self) -> httpx.AsyncClient:
        # No redirects are followed and only fixed provider hosts are ever contacted (no SSRF surface).
        return httpx.AsyncClient(
            timeout=self.settings.http_timeout_seconds,
            transport=self.transport,
            follow_redirects=False,
            headers={"User-Agent": "VibeSync-Backend/1.0"},
        )

    @abstractmethod
    async def search(self, q: str, kind: str, page: int, page_size: int, quality: str) -> ProviderPage: ...

    async def recommendations(self, category: str, page_size: int, quality: str) -> ProviderPage:
        return ProviderPage()
