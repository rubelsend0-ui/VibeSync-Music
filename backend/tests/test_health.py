def test_health_starts_without_credentials(client):
    r = client.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert "internet_archive" in body["providers_configured"]
    assert "jamendo" not in body["providers_configured"]


def test_providers_list_has_setup_hints(client):
    r = client.get("/v1/providers")
    assert r.status_code == 200
    by_name = {p["name"]: p for p in r.json()["providers"]}
    assert by_name["jamendo"]["configured"] is False
    assert "JAMENDO_CLIENT_ID" in by_name["jamendo"]["setup_hint"]
    assert by_name["youtube"]["setup_hint"].endswith("YOUTUBE_API_KEY")


def test_openapi_docs_available(client):
    assert client.get("/openapi.json").status_code == 200
