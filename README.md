# VibeSync Music — Premium Edition

Android music app (Kotlin, Jetpack Compose, Material 3, Media3 ExoPlayer, Room) plus an optional
Python FastAPI backend that talks to lawful music providers.

> **Build status: NOT verified.** This source was written without an Android SDK, Gradle or network access, so the
> Android build, the Android unit tests and the backend tests have **not been executed**. Python/YAML/XML syntax was
> checked only. Expect that the first GitHub Actions run may reveal compile errors to fix (see Troubleshooting).

## Features
- Home (greeting, continue listening, recently played, device music, trending, new releases, popular artists, album
  collections, focus/chill/workout/romantic, history-based recommendations) with loading, error and empty states.
- Search: songs / artists / albums, local device audio, your playlists, debounce, recent searches, suggestions, pagination.
- Playback: one shared Media3 player in a `MediaSessionService` (notification, lock screen, Bluetooth buttons, audio focus,
  noisy-headphone handling, queue, shuffle, repeat, restore of queue/position, sleep timer). Mini-player + full player.
- Library: downloads, device music (MediaStore), favorites, playlists (create/rename/delete/reorder), albums, artists, history, sort/filter.
- Download manager: queue, real progress, pause/resume (HTTP Range), retry, cancel, size check, audio validity check,
  duplicate detection, Wi-Fi-only, storage usage. **Only** tracks a provider marks as downloadable (e.g. Creative Commons).
- Equalizer: real `android.media.audiofx` Equalizer, BassBoost, Virtualizer; shown as unavailable when the device lacks them.
- Settings: theme, accent, quality preference, Wi-Fi only, backend URL (validated), provider status, clear caches/history, privacy, licenses.

## Architecture
```
app/   Android (single module, package com.vibesync.music)
  data/{model,db,remote,repo}   Room, Retrofit, repositories, DataStore settings
  playback/                     PlaybackService, PlayerController, EqualizerManager, SleepTimer
  download/                     DownloadManager
  ui/{theme,components,screens} Compose UI, ViewModels, navigation
backend/  FastAPI (providers: internet_archive, jamendo, youtube(metadata only), demo)
.github/workflows/  android-build.yml (debug APK + tests), android-release.yml (optional, signed)
```
Dependencies are wired manually (`AppContainer`), no Hilt. Versions: Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, KSP 1.9.24-1.0.20,
Compose BOM 2024.06.00 (compiler 1.5.14), Media3 1.3.1, Room 2.6.1, Retrofit 2.11.0, OkHttp 4.12.0, minSdk 26, target/compile 34, JDK 17.

## Honest limitations
- **Audio sources**: Playback needs a provider that allows streaming. Out of the box: Internet Archive (open-licensed items only, no key),
  Jamendo (free API key), and your own files. **YouTube is metadata-only** (official Data API); results open in YouTube and are not
  extracted, streamed or downloaded. There is no Spotify/Apple/etc. integration. Not every song exists in these catalogs.
- **Lyrics**: no lawful lyrics provider is integrated; the player says so instead of showing fake lyrics.
- **Sleep timer**: pauses at expiry; no fade-out (not reliably possible through `MediaController`).
- **Downloads** run while the app process is alive (not a WorkManager/foreground service). Interrupted downloads become *Paused*.
- **Demo mode** shows clearly labelled placeholder tracks with no audio. It is for screenshots only.
- **Gradle wrapper jar** is not in the ZIP (binary, could not be fetched offline). The CI workflow generates it with Gradle 8.7 when missing.
- Icons are original vector drawables; no PNG launcher fallbacks (minSdk 26 uses adaptive icons).
- Quality labels show only what the source reports (format, bitrate if known). Nothing is transcoded or upscaled.

## Backend setup (optional but needed for online music)
```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate     # Termux: pkg install python; pip install -r requirements.txt
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
python -m pytest -q
```
Docs: http://127.0.0.1:8000/docs. The backend starts with no credentials; Internet Archive works immediately.

| Variable | Provider / purpose |
|---|---|
| `VIBESYNC_PROVIDERS` | Comma list of enabled providers (`internet_archive,jamendo,youtube`). Unknown names are ignored. |
| `JAMENDO_CLIENT_ID` | Jamendo (CC-licensed streaming/downloads). Free key: devportal.jamendo.com |
| `YOUTUBE_API_KEY` | YouTube Data API v3 search (metadata only). Mind quota and YouTube API terms. |
| `VIBESYNC_RATE_LIMIT_PER_MINUTE` | Per-IP limit (default 120, `0` disables) |
| `VIBESYNC_HTTP_TIMEOUT` | Provider timeout seconds (default 8) |
| `VIBESYNC_ALLOW_DEMO` | Allow `demo=true` placeholder results (default true) |

Security: only fixed provider hosts are contacted, there is no URL-fetching/proxy endpoint, returned media URLs must be https on an
allow-listed host, keys come from the environment only and are never logged or echoed. Deploy: `backend/render.yaml` (set keys in the dashboard).

Connect the app: Settings → Backend URL. Use `https://…` for hosted backends. Plain `http://` is accepted only for
`localhost`, `127.0.0.1`, `10.0.2.2` (e.g. backend running in Termux on the same phone: `http://127.0.0.1:8000`).

## Build the APK with GitHub Actions (from Termux)
```bash
pkg update && pkg install -y git unzip
termux-setup-storage
cd ~ && unzip /sdcard/Download/VibeSync-Music.zip && cd VibeSync-Music

git init -b main
git config user.name  "<YOUR_NAME>"
git config user.email "<YOUR_EMAIL>"
git add .
git commit -m "VibeSync Music"
git remote add origin https://github.com/<YOUR_USER>/<YOUR_REPO>.git
git push -u origin main
```
Create the empty repo on github.com first. When Git asks for a password, use a **personal access token** with the
`repo` and `workflow` scopes (the `workflow` scope is required to push files under `.github/workflows`).

Run / download:
1. GitHub → your repo → **Actions** → **Android Build** → **Run workflow** (it also runs on every push to `main`).
2. Open the finished run → scroll to **Artifacts** at the bottom of the summary page → download **VibeSync-Music-Debug-APK**.
3. Unzip it, install `app-debug.apk` (allow "install unknown apps"). If the build fails, download the **build-logs** artifact.

Optional signed release: add secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, then run **Android Release**.
Never commit a keystore.

## Local Android build (if you have the SDK)
`gradle wrapper --gradle-version 8.7` (once), then `./gradlew testDebugUnitTest assembleDebug`.

## Troubleshooting
- Compile error in CI: open the failed step log; the file/line is shown. The code has not been compiled by the author, so small fixes are plausible.
- `gradle wrapper` step fails: check the log of "Ensure Gradle wrapper exists"; the repository must have `settings.gradle.kts` at the root.
- Robolectric test download errors: re-run; it needs Maven Central access on the runner.
- Search says "backend isn't set up": add the URL in Settings, enable demo mode, or use local music.
- No notification controls on Android 13+: allow notifications when asked on first playback.
- Local music missing: grant the audio permission from Home/Library.

## Music licensing
Use only audio you have the right to stream/download. The app never bypasses DRM, paywalls, authentication or provider restrictions,
and downloads are limited to tracks where the provider supplies an authorized, downloadable URL. Respect each provider's terms.
