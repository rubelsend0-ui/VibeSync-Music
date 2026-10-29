"""URL validation. Providers may only hand back https URLs on hosts they are known to use."""
from __future__ import annotations

from urllib.parse import urlparse


def validate_media_url(url: str | None, allowed_suffixes: tuple[str, ...]) -> str | None:
    """Return the URL only if it is https, has no credentials and its host is in the allow-list."""
    if not url or not isinstance(url, str):
        return None
    try:
        parsed = urlparse(url.strip())
        host = (parsed.hostname or "").lower()
        if parsed.scheme != "https" or not host or parsed.username or parsed.password:
            return None
    except ValueError:
        return None
    for suffix in allowed_suffixes:
        if host == suffix or host.endswith("." + suffix):
            return url.strip()
    return None
