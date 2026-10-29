from typing import Literal

from fastapi import APIRouter, HTTPException, Query, Request

from app.schemas import SearchResponse
from app.services.aggregate import gather_from_providers

router = APIRouter(prefix="/v1", tags=["search"])

ProviderName = Query(None, pattern=r"^[a-z_]{1,32}$", description="Optional provider name")


@router.get("/search", response_model=SearchResponse)
async def search(
    request: Request,
    q: str = Query(..., min_length=1, max_length=100),
    kind: Literal["track", "artist", "album"] = Query("track", alias="type"),
    provider: str | None = ProviderName,
    page: int = Query(1, ge=1, le=100),
    page_size: int = Query(20, ge=1, le=50),
    quality: Literal["auto", "high", "low"] = "auto",
    demo: bool = False,
) -> SearchResponse:
    term = q.strip()
    if not term:
        raise HTTPException(status_code=422, detail="q must not be blank")
    try:
        return await gather_from_providers(
            request.app.state.registry, provider, term, page, page_size, demo,
            lambda p: p.search(term, kind, page, page_size, quality),
        )
    except KeyError:
        raise HTTPException(status_code=404, detail="Unknown or disabled provider") from None
