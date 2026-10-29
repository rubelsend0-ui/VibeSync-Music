from __future__ import annotations

from app.schemas import Track
from app.services.provider_base import MusicProvider, ProviderPage

_NAMES = [
    ("Neon Avenue", "Aurora Static"), ("Midnight Circuit", "Violet Harbor"), ("Glass Horizon", "Aurora Static"),
    ("Slow Orbit", "Paper Satellites"), ("Pulse Garden", "Violet Harbor"), ("Low Tide Radio", "Paper Satellites"),
]


class DemoProvider(MusicProvider):
    """Placeholder data for development and screenshots. Never playable, always marked is_demo."""

    name = "demo"
    display_name = "Demo data"
    capabilities = ("search", "recommendations", "demo")
    notes = "Placeholder tracks without audio. For development and screenshots only."
    is_demo = True

    def _tracks(self) -> list[Track]:
        return [
            Track(id=f"demo-{i}", provider=self.name, title=t, artist=a, album=f"Demo Album {i // 3 + 1}",
                  duration_ms=150_000 + i * 7000, playable=False, is_demo=True)
            for i, (t, a) in enumerate(_NAMES)
        ]

    async def search(self, q: str, kind: str, page: int, page_size: int, quality: str) -> ProviderPage:
        needle = q.lower()
        items = [t for t in self._tracks() if needle in t.title.lower() or needle in t.artist.lower()
                 or needle in (t.album or "").lower()]
        start = (page - 1) * page_size
        return ProviderPage(items[start:start + page_size], has_more=len(items) > start + page_size)

    async def recommendations(self, category: str, page_size: int, quality: str) -> ProviderPage:
        return ProviderPage(self._tracks()[:page_size])
