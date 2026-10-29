from __future__ import annotations

from pydantic import BaseModel, Field


class Track(BaseModel):
    """Normalized track returned by every provider."""

    id: str
    provider: str
    title: str
    artist: str = "Unknown artist"
    album: str | None = None
    duration_ms: int = Field(default=0, ge=0)
    artwork_url: str | None = None
    stream_url: str | None = None
    download_url: str | None = None
    external_url: str | None = None
    license: str | None = None
    format: str | None = None
    bitrate_kbps: int | None = None
    size_bytes: int | None = None
    playable: bool = False
    downloadable: bool = False
    preview: bool = False
    is_demo: bool = False


class SearchResponse(BaseModel):
    query: str
    items: list[Track] = []
    page: int = 1
    page_size: int = 20
    has_more: bool = False
    notices: list[str] = []
    demo: bool = False


class ProviderInfo(BaseModel):
    name: str
    display_name: str
    configured: bool
    capabilities: list[str] = []
    setup_hint: str | None = None
    notes: str | None = None


class ProvidersResponse(BaseModel):
    providers: list[ProviderInfo]


class HealthResponse(BaseModel):
    status: str
    version: str
    providers_configured: list[str]
