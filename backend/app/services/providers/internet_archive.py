from __future__ import annotations

import asyncio
import re
from typing import Any
from urllib.parse import quote

import httpx

from app.schemas import Track
from app.services.provider_base import MusicProvider, ProviderError, ProviderPage
from app.services.security import validate_media_url

SEARCH_API = "https://archive.org/advancedsearch.php"
METADATA_API = "https://archive.org/metadata/"
ALLOWED_HOSTS = ("archive.org",)
IDENTIFIER = re.compile(r"^[A-Za-z0-9._-]{1,200}$")
OPEN_LICENSE = re.compile(r"creativecommons\.org|publicdomain", re.I)

# (IA format label, display format, bitrate)
FORMATS_AUTO = [("VBR MP3", "MP3", None), ("MP3", "MP3", None), ("128Kbps MP3", "MP3", 128),
                ("Ogg Vorbis", "OGG", None), ("64Kbps MP3", "MP3", 64)]
FORMATS_LOW = [("64Kbps MP3", "MP3", 64), ("128Kbps MP3", "MP3", 128), ("Ogg Vorbis", "OGG", None),
               ("VBR MP3", "MP3", None), ("MP3", "MP3", None)]

CATEGORY_QUERIES = {
    "trending": ("collection:(opensource_audio)", "downloads desc"),
    "popular": ("collection:(opensource_audio)", "downloads desc"),
    "new_releases": ("collection:(opensource_audio)", "addeddate desc"),
    "focus": ("subject:(ambient OR instrumental)", "downloads desc"),
    "chill": ("subject:(chillout OR downtempo OR lounge)", "downloads desc"),
    "workout": ("subject:(electronic OR rock OR dance)", "downloads desc"),
    "romantic": ("subject:(love OR romantic OR ballad)", "downloads desc"),
}


def clean_term(term: str) -> str:
    """Strip Lucene operators so user input cannot alter the structure of the query."""
    return re.sub(r"[^\w\s]", " ", term, flags=re.UNICODE).strip()[:100]


def _first(value: Any) -> str | None:
    if isinstance(value, list):
        value = value[0] if value else None
    return str(value) if value else None


class InternetArchiveProvider(MusicProvider):
    name = "internet_archive"
    display_name = "Internet Archive"
    capabilities = ("search", "recommendations", "stream", "download")
    notes = ("No key needed. Only items that declare a Creative Commons or public-domain license are returned. "
             "Quality varies and the collection includes non-music audio.")

    def normalize(self, doc: dict[str, Any], meta: dict[str, Any], quality: str = "auto") -> Track | None:
        identifier = doc.get("identifier")
        if not isinstance(identifier, str) or not IDENTIFIER.match(identifier):
            return None
        license_url = _first(doc.get("licenseurl")) or _first((meta.get("metadata") or {}).get("licenseurl"))
        if not license_url or not OPEN_LICENSE.search(license_url):
            return None
        prefs = FORMATS_LOW if quality == "low" else FORMATS_AUTO
        files = [f for f in meta.get("files", []) if isinstance(f, dict) and f.get("name")]
        chosen = None
        for label, fmt, bitrate in prefs:
            chosen = next((f for f in files if f.get("format") == label), None)
            if chosen:
                break
        if not chosen:
            return None
        url = validate_media_url(f"https://archive.org/download/{identifier}/{quote(str(chosen['name']))}", ALLOWED_HOSTS)
        if not url:
            return None
        try:
            size = int(chosen.get("size")) if chosen.get("size") else None
        except (TypeError, ValueError):
            size = None
        try:
            duration = int(float(chosen.get("length") or 0) * 1000) if ":" not in str(chosen.get("length", "")) else 0
        except (TypeError, ValueError):
            duration = 0
        bitrate_val = bitrate
        raw_br = chosen.get("bitrate")
        if bitrate_val is None and str(raw_br or "").isdigit():
            bitrate_val = int(raw_br)
        return Track(
            id=identifier, provider=self.name, title=_first(doc.get("title")) or identifier,
            artist=_first(doc.get("creator")) or "Unknown artist", album=None, duration_ms=max(duration, 0),
            artwork_url=f"https://archive.org/services/img/{identifier}", stream_url=url, download_url=url,
            external_url=f"https://archive.org/details/{identifier}", license=license_url, format=fmt,
            bitrate_kbps=bitrate_val, size_bytes=size, playable=True, downloadable=True,
        )

    async def _metadata(self, client: httpx.AsyncClient, identifier: str) -> dict[str, Any]:
        resp = await client.get(METADATA_API + identifier)
        resp.raise_for_status()
        return resp.json()

    async def _run(self, query: str, sort: str, page: int, page_size: int, quality: str) -> ProviderPage:
        params = {"q": query, "fl[]": ["identifier", "title", "creator", "licenseurl"], "rows": page_size,
                  "page": page, "output": "json", "sort[]": sort}
        try:
            async with self.client() as c:
                resp = await c.get(SEARCH_API, params=params)
                resp.raise_for_status()
                body = resp.json().get("response") or {}
                docs = [d for d in body.get("docs", []) if isinstance(d, dict)]
                # Pre-filter on license before spending metadata requests.
                docs = [d for d in docs if OPEN_LICENSE.search(_first(d.get("licenseurl")) or "")]
                sem = asyncio.Semaphore(5)

                async def one(d: dict[str, Any]) -> Track | None:
                    ident = d.get("identifier")
                    if not isinstance(ident, str) or not IDENTIFIER.match(ident):
                        return None
                    async with sem:
                        try:
                            meta = await self._metadata(c, ident)
                        except (httpx.HTTPError, ValueError):
                            return None
                    return self.normalize(d, meta, quality)

                tracks = [t for t in await asyncio.gather(*(one(d) for d in docs)) if t]
                found = int(body.get("numFound") or 0)
        except (httpx.HTTPError, ValueError):
            raise ProviderError("Internet Archive is temporarily unavailable.") from None
        return ProviderPage(tracks, has_more=found > page * page_size)

    async def search(self, q: str, kind: str, page: int, page_size: int, quality: str) -> ProviderPage:
        term = clean_term(q)
        if not term:
            return ProviderPage()
        field_name = "creator" if kind == "artist" else "title"
        return await self._run(f"mediatype:audio AND {field_name}:({term})", "downloads desc", page, page_size, quality)

    async def recommendations(self, category: str, page_size: int, quality: str) -> ProviderPage:
        clause, sort = CATEGORY_QUERIES.get(category, CATEGORY_QUERIES["trending"])
        return await self._run(f"mediatype:audio AND {clause}", sort, 1, page_size, quality)
