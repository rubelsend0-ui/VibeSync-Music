import httpx
import pytest

from app.config import Settings
from tests.conftest import make_client


def test_invalid_inputs_are_rejected(client):
    assert client.get("/v1/search").status_code == 422
    assert client.get("/v1/search", params={"q": ""}).status_code == 422
    assert client.get("/v1/search", params={"q": "   "}).status_code == 422
    assert client.get("/v1/search", params={"q": "x" * 101}).status_code == 422
    assert client.get("/v1/search", params={"q": "a", "page_size": 500}).status_code == 422
    assert client.get("/v1/search", params={"q": "a", "page": 0}).status_code == 422
    assert client.get("/v1/search", params={"q": "a", "type": "podcast"}).status_code == 422
    assert client.get("/v1/search", params={"q": "a", "provider": "../etc"}).status_code == 422


def test_unknown_provider_is_404(client):
    assert client.get("/v1/search", params={"q": "a", "provider": "spotify"}).status_code == 404


def test_unconfigured_provider_gives_instructions_not_fake_results():
    client = make_client(Settings(enabled_providers=("jamendo",)))
    body = client.get("/v1/search", params={"q": "jazz"}).json()
    assert body["items"] == []
    assert body["demo"] is False
    assert any("JAMENDO_CLIENT_ID" in n for n in body["notices"])


def test_demo_results_are_marked_as_demo():
    client = make_client(Settings(enabled_providers=("jamendo",)))
    body = client.get("/v1/search", params={"q": "neon", "demo": "true"}).json()
    assert body["demo"] is True
    assert body["items"] and all(t["is_demo"] and not t["playable"] for t in body["items"])
    assert any("DEMO" in n for n in body["notices"])


def test_demo_can_be_disabled():
    client = make_client(Settings(enabled_providers=("jamendo",), allow_demo=False))
    body = client.get("/v1/search", params={"q": "neon", "demo": "true"}).json()
    assert body["items"] == [] and body["demo"] is False


def test_provider_failure_is_reported_safely():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, text="secret internals")

    settings = Settings(enabled_providers=("jamendo",), jamendo_client_id="SECRETID")
    client = make_client(settings, httpx.MockTransport(handler))
    r = client.get("/v1/search", params={"q": "jazz"})
    assert r.status_code == 200
    body = r.json()
    assert body["items"] == []
    text = r.text
    assert "SECRETID" not in text and "secret internals" not in text
    assert any("unavailable" in n for n in body["notices"])


def test_rate_limit_returns_429():
    settings = Settings(enabled_providers=(), rate_limit_per_minute=3)
    client = make_client(settings)
    codes = [client.get("/v1/providers").status_code for _ in range(5)]
    assert codes[:3] == [200, 200, 200] and 429 in codes[3:]
    assert client.get("/health").status_code == 200  # health is exempt


@pytest.mark.parametrize("category", ["trending", "new_releases", "focus", "chill", "workout", "romantic", "popular"])
def test_recommendation_categories_validate(category):
    client = make_client(Settings(enabled_providers=()))
    assert client.get("/v1/recommendations", params={"category": category, "demo": "true"}).status_code == 200


def test_unknown_category_rejected(client):
    assert client.get("/v1/recommendations", params={"category": "hax"}).status_code == 422
