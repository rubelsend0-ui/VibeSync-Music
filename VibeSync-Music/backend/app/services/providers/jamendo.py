from __future__ import annotations

import re
from typing import Any

import httpx

from app.schemas import Track
from app.services.provider_base import MusicProvider, ProviderError, ProviderPage
from app.services.security import validate_media_url

API = "https://api.jamendo.com/v3.0/tracks/"
ALLOWED_HOSTS = ("jamendo.com", "newjamendo.com")

# Approximate mappings from mood categories to Jamendo tags / ordering.
CATEGORY_PARAMS: dict[str, dict[str, str]] = {
    "trending": {"order": "popularity_week"},
    "new_releases": {"order": "releasedate_desc"},
    "popular": {"order": "popularity_month"},
    "focus": {"fuzzytags": "ambient instrumental focus", "order": "popularity_month"},
    "chill": {"fuzzytags": "chillout relaxing lounge", "order": "popularity_month"},
    "workout": {"fuzzytags": "energetic electronic rock", "order": "popularity_month"},
    "romantic": {"fuzzytags": "romantic love ballad", "order": "popularity_month"},
}
AUDIO_FORMAT = {"low": "mp31", "auto": "mp32", "high": "mp32"}


def license_label(url: str | None) -> str | None:
    """'https://creativecommons.org/licenses/by-nc-sa/3.0/' -> 'CC BY-NC-SA 3.0'."""
    if not url:
        return None
    m = re.search(r"creativecommons\.org/(?:licenses|publicdomain)/([a-z\-]+)/?([0-9.]+)?", url)
    if not m:
        return url
    code = m.group(1).upper()
    return f"CC {code} {m.group(2)}".strip() if m.group(1) != "zero" else "CC0"


class JamendoProvider(MusicProvider):
    name = "jamendo"
    display_name = "Jamendo"
    capabilities = ("search", "recommendations", "stream", "download")
    env_vars = ("JAMENDO_CLIENT_ID",)
    notes = "Creative Commons music. Free API key at devportal.jamendo.com. Downloads only where the artist allows it."

    def is_configured(self) -> bool:
        return bool(self.settings.jamendo_client_id)

    def normalize(self, r: dict[str, Any], quality: str = "auto") -> Track | None:
        stream = validate_media_url(r.get("audio"), ALLOWED_HOSTS)
        track_id = r.get("id")
        if not stream or track_id is None or not r.get("name"):
            return None
        download = validate_media_url(r.get("audiodownload"), ALLOWED_HOSTS) if r.get("audiodownload_allowed") else None
        try:
            duration = int(float(r.get("duration") or 0) * 1000)
        except (TypeError, ValueError):
            duration = 0
        return Track(
            id=str(track_id), provider=self.name, title=str(r["name"]),
            artist=r.get("artist_name") or "Unknown artist", album=r.get("album_name") or None,
            duration_ms=max(duration, 0),
            artwork_url=validate_media_url(r.get("album_image") or r.get("image"), ALLOWED_HOSTS),
            stream_url=stream, download_url=download, external_url=validate_media_url(r.get("shareurl"), ALLOWED_HOSTS),
            license=license_label(r.get("license_ccurl")), format="MP3", playable=True,
            downloadable=download is not None, preview=False,
        )

    async def _fetch(self, params: dict[str, Any], page: int, page_size: int, quality: str) -> ProviderPage:
        if not self.is_configured():
            raise ProviderError("Jamendo is not configured (JAMENDO_CLIENT_ID missing).")
        query = {
            "client_id": self.settings.jamendo_client_id, "format": "json", "limit": page_size,
            "offset": (page - 1) * page_size, "audioformat": AUDIO_FORMAT.get(quality, "mp32"),
            "imagesize": 500, "include": "licenses", **params,
        }
        try:
            async with self.client() as c:
                resp = await c.get(API, params=query)
                resp.raise_for_status()
                data = resp.json()
        except (httpx.HTTPError, ValueError):
            raise ProviderError("Jamendo is temporarily unavailable.") from None
        status = (data.get("headers") or {}).get("status")
        if status != "success":
            raise ProviderError("Jamendo rejected the request.")
        results = data.get("results") or []
        items = [t for t in (self.normalize(r, quality) for r in results) if t]
        return ProviderPage(items, has_more=len(results) >= page_size)

    async def search(self, q: str, kind: str, page: int, page_size: int, quality: str) -> ProviderPage:
        key = {"artist": "artist_name", "album": "album_name"}.get(kind, "search")
        return await self._fetch({key: q, "order": "relevance"}, page, page_size, quality)

    async def recommendations(self, category: str, page_size: int, quality: str) -> ProviderPage:
        return await self._fetch(CATEGORY_PARAMS.get(category, {"order": "popularity_week"}), 1, page_size, quality)
