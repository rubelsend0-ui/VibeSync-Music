from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable

from app.schemas import SearchResponse, Track
from app.services.provider_base import MusicProvider, ProviderError, ProviderPage
from app.services.provider_registry import ProviderRegistry


async def gather_from_providers(
    registry: ProviderRegistry,
    provider: str | None,
    query_label: str,
    page: int,
    page_size: int,
    demo: bool,
    call: Callable[[MusicProvider], Awaitable[ProviderPage]],
) -> SearchResponse:
    """Query one provider (if named) or every configured one; never invent results."""
    notices: list[str] = []
    if provider:
        selected = [registry.get(provider)]  # raises KeyError for unknown/disabled providers
    else:
        selected = registry.configured()
        for p in registry.real():
            if not p.is_configured():
                notices.append(f"{p.display_name} isn't configured. {p.setup_hint() or ''}".strip())

    results = await asyncio.gather(*(call(p) for p in selected), return_exceptions=True)
    items: list[Track] = []
    has_more = False
    for p, res in zip(selected, results):
        if isinstance(res, ProviderError):
            notices.append(f"{p.display_name}: {res}")
        elif isinstance(res, Exception):
            notices.append(f"{p.display_name} failed unexpectedly.")
        else:
            items.extend(res.items)
            has_more = has_more or res.has_more

    used_demo = False
    if not items and demo and registry.settings.allow_demo and not (provider and provider != "demo"):
        demo_page = await call(registry.demo)
        if demo_page.items:
            items, has_more, used_demo = demo_page.items, False, True
            notices.append("DEMO DATA: no real provider returned results, so placeholder tracks are shown. They can't be played.")
    if not selected and not used_demo:
        notices.append("No music provider is enabled. Set VIBESYNC_PROVIDERS and see the README for provider setup.")
    return SearchResponse(query=query_label, items=items, page=page, page_size=page_size,
                          has_more=has_more, notices=notices, demo=used_demo)
