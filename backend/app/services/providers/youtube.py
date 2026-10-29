from __future__ import annotations

import html
from typing import Any

import httpx

from app.schemas import Track
from app.services.provider_base import MusicProvider, ProviderError, ProviderPage
from app.services.security import validate_media_url

API = "https://www.googleapis.com/youtube/v3/search"
THUMB_HOSTS = ("ytimg.com", "ggpht.com")


class YouTubeProvider(MusicProvider):
    """Official YouTube Data API metadata search. Audio is NOT extracted or streamed by this project;
    results are marked not playable and link out to YouTube, as the API terms require."""

    name = "youtube"
    display_name = "YouTube (metadata only)"
    capabilities = ("search",)
    env_vars = ("YOUTUBE_API_KEY",)
    notes = "Search results open on YouTube. VibeSync does not extract, stream or download YouTube audio."

    def is_configured(self) -> bool:
        return bool(self.settings.youtube_api_key)

    def normalize(self, item: dict[str, Any]) -> Track | None:
        vid = (item.get("id") or {}).get("videoId")
        sn = item.get("snippet") or {}
        if not vid or not sn.get("title"):
            return None
        thumbs = sn.get("thumbnails") or {}
        thumb = (thumbs.get("high") or thumbs.get("medium") or thumbs.get("default") or {}).get("url")
        artist = str(sn.get("channelTitle") or "Unknown artist").removesuffix(" - Topic")
        return Track(
            id=str(vid), provider=self.name, title=html.unescape(sn["title"]), artist=html.unescape(artist),
            artwork_url=validate_media_url(thumb, THUMB_HOSTS), external_url=f"https://www.youtube.com/watch?v={vid}",
            playable=False, downloadable=False,
        )

    async def search(self, q: str, kind: str, page: int, page_size: int, quality: str) -> ProviderPage:
        if not self.is_configured():
            raise ProviderError("YouTube is not configured (YOUTUBE_API_KEY missing).")
        if page > 1:
            return ProviderPage()  # page tokens are not exposed; only the first page is supported
        params = {"part": "snippet", "type": "video", "videoCategoryId": "10", "q": q,
                  "maxResults": min(page_size, 25), "key": self.settings.youtube_api_key}
        try:
            async with self.client() as c:
                resp = await c.get(API, params=params)
                resp.raise_for_status()
                data = resp.json()
        except (httpx.HTTPError, ValueError):
            # Never include the request URL (it contains the API key) in the message.
            raise ProviderError("YouTube search is unavailable (check quota and key).") from None
        return ProviderPage([t for t in (self.normalize(i) for i in data.get("items", [])) if t])
