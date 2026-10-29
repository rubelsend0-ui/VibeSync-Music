from fastapi import APIRouter, Request

from app.schemas import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
async def health(request: Request) -> HealthResponse:
    registry = request.app.state.registry
    return HealthResponse(
        status="ok", version=request.app.state.settings.version,
        providers_configured=[p.name for p in registry.configured()],
    )
