from __future__ import annotations

import time
from collections import defaultdict, deque

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from app.config import Settings, get_settings
from app.routers import health, providers, recommendations, search
from app.services.provider_registry import ProviderRegistry


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Small in-memory sliding-window limiter per client IP (single-process only)."""

    def __init__(self, app, limit_per_minute: int) -> None:
        super().__init__(app)
        self.limit = limit_per_minute
        self.hits: dict[str, deque[float]] = defaultdict(deque)

    async def dispatch(self, request: Request, call_next):
        if request.url.path == "/health" or self.limit <= 0:
            return await call_next(request)
        ip = request.client.host if request.client else "unknown"
        now = time.monotonic()
        window = self.hits[ip]
        while window and now - window[0] > 60:
            window.popleft()
        if len(window) >= self.limit:
            retry = max(1, int(60 - (now - window[0])))
            return JSONResponse({"detail": "Rate limit exceeded"}, status_code=429, headers={"Retry-After": str(retry)})
        window.append(now)
        if len(self.hits) > 10_000:  # bound memory
            for key in [k for k, v in self.hits.items() if not v or now - v[-1] > 60]:
                self.hits.pop(key, None)
        return await call_next(request)


def create_app(settings: Settings | None = None, registry: ProviderRegistry | None = None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(title=settings.app_name, version=settings.version,
                  description="Provider-based music metadata API for the VibeSync Android app.")
    app.state.settings = settings
    app.state.registry = registry or ProviderRegistry(settings)
    app.add_middleware(RateLimitMiddleware, limit_per_minute=settings.rate_limit_per_minute)
    for r in (health.router, providers.router, search.router, recommendations.router):
        app.include_router(r)

    @app.exception_handler(Exception)
    async def unhandled(_: Request, __: Exception) -> JSONResponse:  # never leak internals
        return JSONResponse({"detail": "Internal server error"}, status_code=500)

    return app


app = create_app()
