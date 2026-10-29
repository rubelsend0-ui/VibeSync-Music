from fastapi import APIRouter, Request

from app.schemas import ProvidersResponse

router = APIRouter(prefix="/v1", tags=["providers"])


@router.get("/providers", response_model=ProvidersResponse)
async def list_providers(request: Request) -> ProvidersResponse:
    return ProvidersResponse(providers=request.app.state.registry.infos())
