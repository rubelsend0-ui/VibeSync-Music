from typing import Literal

from fastapi import APIRouter, HTTPException, Query, Request

from app.schemas import SearchResponse
from app.services.aggregate import gather_from_providers

router = APIRouter(prefix="/v1", tags=["recommendations"])

Category = Literal["trending", "new_releases", "popular", "focus", "chill", "workout", "romantic"]


@router.get("/recommendations", response_model=SearchResponse)
async def recommendations(
    request: Request,
    category: Category = "trending",
    provider: str | None = Query(None, pattern=r"^[a-z_]{1,32}$"),
    page_size: int = Query(20, ge=1, le=50),
    quality: Literal["auto", "high", "low"] = "auto",
    demo: bool = False,
) -> SearchResponse:
    try:
        return await gather_from_providers(
            request.app.state.registry, provider, category, 1, page_size, demo,
            lambda p: p.recommendations(category, page_size, quality),
        )
    except KeyError:
        raise HTTPException(status_code=404, detail="Unknown or disabled provider") from None
