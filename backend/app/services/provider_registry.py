from __future__ import annotations

import httpx

from app.config import Settings
from app.services.provider_base import MusicProvider
from app.services.providers.demo import DemoProvider
from app.services.providers.internet_archive import InternetArchiveProvider
from app.services.providers.jamendo import JamendoProvider
from app.services.providers.youtube import YouTubeProvider

# Only providers listed here can ever be enabled (explicit allow-list).
KNOWN_PROVIDERS: dict[str, type[MusicProvider]] = {
    "internet_archive": InternetArchiveProvider,
    "jamendo": JamendoProvider,
    "youtube": YouTubeProvider,
}


class ProviderRegistry:
    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None,
                 extra: dict[str, MusicProvider] | None = None) -> None:
        self.settings = settings
        self._providers: dict[str, MusicProvider] = {}
        for name in settings.enabled_providers:
            cls = KNOWN_PROVIDERS.get(name)
            if cls:
                self._providers[name] = cls(settings, transport)
        if extra:
            self._providers.update(extra)
        self.demo = DemoProvider(settings, transport)

    def get(self, name: str) -> MusicProvider:
        if name == "demo" and self.settings.allow_demo:
            return self.demo
        if name not in self._providers:
            raise KeyError(name)
        return self._providers[name]

    def real(self) -> list[MusicProvider]:
        return list(self._providers.values())

    def configured(self) -> list[MusicProvider]:
        return [p for p in self.real() if p.is_configured()]

    def infos(self):
        infos = [p.info() for p in self.real()]
        if self.settings.allow_demo:
            infos.append(self.demo.info())
        return infos
