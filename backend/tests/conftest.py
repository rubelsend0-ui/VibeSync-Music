import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.services.provider_registry import ProviderRegistry


def make_client(settings: Settings | None = None, transport: httpx.AsyncBaseTransport | None = None) -> TestClient:
    settings = settings or Settings(enabled_providers=("internet_archive", "jamendo", "youtube"))
    return TestClient(create_app(settings, ProviderRegistry(settings, transport)))


@pytest.fixture
def client() -> TestClient:
    return make_client()
