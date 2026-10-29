import asyncio

import httpx

from app.config import Settings
from app.services.providers.internet_archive import InternetArchiveProvider, clean_term
from app.services.providers.jamendo import JamendoProvider, license_label
from app.services.providers.youtube import YouTubeProvider
from app.services.security import validate_media_url

S = Settings(jamendo_client_id="cid", youtube_api_key="ytkey")


def test_validate_media_url():
    hosts = ("jamendo.com",)
    assert validate_media_url("https://prod.storage.jamendo.com/a.mp3", hosts)
    assert validate_media_url("http://prod.storage.jamendo.com/a.mp3", hosts) is None
    assert validate_media_url("https://evil.com/a.mp3", hosts) is None
    assert validate_media_url("https://jamendo.com.evil.com/a.mp3", hosts) is None
    assert validate_media_url("https://user:pw@a.jamendo.com/x", hosts) is None
    assert validate_media_url("https://127.0.0.1/x", hosts) is None
    assert validate_media_url(None, hosts) is None


def test_license_label():
    assert license_label("https://creativecommons.org/licenses/by-nc-sa/3.0/") == "CC BY-NC-SA 3.0"
    assert license_label(None) is None


def test_jamendo_normalization_and_filtering():
    p = JamendoProvider(S)
    t = p.normalize({
        "id": "42", "name": "Song", "artist_name": "Band", "album_name": "LP", "duration": 185,
        "audio": "https://prod-1.storage.jamendo.com/?trackid=42", "audiodownload_allowed": True,
        "audiodownload": "https://prod-1.storage.jamendo.com/download/track/42",
        "album_image": "https://usercontent.jamendo.com/img.jpg",
        "license_ccurl": "https://creativecommons.org/licenses/by/3.0/",
    })
    assert t and t.id == "42" and t.duration_ms == 185_000 and t.playable and t.downloadable
    assert t.license == "CC BY 3.0" and t.format == "MP3" and t.bitrate_kbps is None
    # downloads not allowed -> never marked downloadable
    t2 = p.normalize({"id": 1, "name": "x", "audio": "https://a.jamendo.com/x", "audiodownload_allowed": False,
                      "audiodownload": "https://a.jamendo.com/d"})
    assert t2 and not t2.downloadable and t2.download_url is None
    # untrusted host is dropped
    assert p.normalize({"id": 1, "name": "x", "audio": "https://evil.example/x.mp3"}) is None


def test_jamendo_search_end_to_end_with_mock_transport():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen.update(dict(request.url.params))
        return httpx.Response(200, json={"headers": {"status": "success"}, "results": [
            {"id": "1", "name": "A", "audio": "https://a.jamendo.com/1", "duration": 10}]})

    p = JamendoProvider(S, httpx.MockTransport(handler))
    page = asyncio.run(p.search("hello", "artist", 2, 10, "low"))
    assert [t.id for t in page.items] == ["1"]
    assert seen["artist_name"] == "hello" and seen["offset"] == "10" and seen["audioformat"] == "mp31"


def test_internet_archive_requires_open_license_and_picks_audio():
    p = InternetArchiveProvider(S)
    meta = {"files": [{"name": "cover.jpg", "format": "JPEG"},
                      {"name": "my song.mp3", "format": "VBR MP3", "size": "1234", "length": "200.5"}]}
    doc = {"identifier": "item_1", "title": ["T"], "creator": "C",
           "licenseurl": "https://creativecommons.org/licenses/by/4.0/"}
    t = p.normalize(doc, meta)
    assert t and t.stream_url == "https://archive.org/download/item_1/my%20song.mp3"
    assert t.size_bytes == 1234 and t.duration_ms == 200_500 and t.format == "MP3" and t.downloadable
    assert p.normalize({**doc, "licenseurl": None}, meta) is None  # no open license -> skipped
    assert p.normalize({**doc, "identifier": "../bad"}, meta) is None
    assert p.normalize(doc, {"files": [{"name": "a.jpg", "format": "JPEG"}]}) is None


def test_clean_term_strips_lucene_operators():
    assert clean_term('rock" OR (x:y) AND *') == "rock  OR  x y  AND"


def test_youtube_is_metadata_only_and_never_leaks_key():
    def ok(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"items": [{"id": {"videoId": "abc"}, "snippet": {
            "title": "Tom &amp; Jerry", "channelTitle": "Artist - Topic",
            "thumbnails": {"high": {"url": "https://i.ytimg.com/vi/abc/hq.jpg"}}}}]})

    page = asyncio.run(YouTubeProvider(S, httpx.MockTransport(ok)).search("q", "track", 1, 10, "auto"))
    t = page.items[0]
    assert t.title == "Tom & Jerry" and t.artist == "Artist"
    assert not t.playable and not t.downloadable and t.stream_url is None
    assert t.external_url == "https://www.youtube.com/watch?v=abc"

    def bad(request: httpx.Request) -> httpx.Response:
        return httpx.Response(403)

    try:
        asyncio.run(YouTubeProvider(S, httpx.MockTransport(bad)).search("q", "track", 1, 10, "auto"))
    except Exception as e:  # ProviderError
        assert "ytkey" not in str(e)
    else:
        raise AssertionError("expected ProviderError")
