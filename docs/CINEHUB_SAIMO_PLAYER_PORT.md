# CineHUB Player — Saimo TV port

This is an isolated reconstruction based on the authorized Saimo TV Android player implementation. Do not merge or publish it until CI and real-device playback are validated.

## Ported implementation

- The full Saimo TV `PlayerActivity` playback/UI flow, adapted to package `com.cinehub.player`.
- Saimo network transport and URL handling (`Playback`, `Confianca`, `Source`) for HLS, DASH, progressive media, referer/user-agent headers and provider-specific URL fixes.
- 15-second source timeout/failover that keeps the playback position and presents a recoverable error when all sources fail.
- Resume/history, episode panel, next-episode preview/autoplay, same-server preference, TheIntroDB skip markers, OpenSubtitles, audio/subtitle tracks, subtitle style/synchronization and recommendations.
- Low-memory buffer profile (32 MB / 15–30 s) and normal profile (96 MB / 30–50 s); manual buffer profile switch on devices that are not classified as low-memory.
- Playback speed presets from 0.75× to 2×.
- CineHUB radio experience screen and live-stream protection against remote/media-session Play/Pause.
- CineHUB red/black/white palette, CineHUB Player theme, and CineHUB branding on the radio screen.
- Google TV Watch Next integration where the platform provider exists.

## Intent contract

Launch `com.cinehub.player.PlayerActivity` with an explicit Intent.

| Extra | Type | Purpose |
|---|---|---|
| `title` | String | Display title (preferred) |
| `content_type` | String | `movie`, `series`, `anime`, `dorama`, `live`, or `radio` |
| `sources` | ArrayList<String> | Ordered source URLs |
| `sources_json` | String | JSON array fallback for ordered source URLs |
| `url` | String | Single fallback source |
| `player.direct_url` | String | Saimo-compatible single direct URL |
| `source_referers` | ArrayList<String> | Per-source Referer values in the same order |
| `source_user_agents` | ArrayList<String> | Per-source User-Agent values in the same order |
| `tmdb_id` | Int | TMDB id for episode metadata, similar titles and intro markers |
| `season_number` / `episode_number` | Int | Current episode |
| `episode_queue` | String | JSON array; each item uses `season`, `episode`, and `sources` |
| `start_position_ms` | Long | Resume position in milliseconds |
| `radio_name` / `radio_genre` | String | Radio screen metadata |

Only safe HTTP(S) source URLs are accepted. Direct CineHUB sources are preferred over the fallback Saimo catalog lookup.

## Validation gates

- [ ] GitHub Actions unit tests and debug APK build pass on the current head.
- [ ] CineHUB TV passes real ordered sources, Referer/User-Agent values, TMDB metadata and episode queue.
- [ ] Film playback, source failover, resume and speed tested on a real TV Box.
- [ ] Series panel, same-source next episode, credits preview/autoplay and TheIntroDB skip tested.
- [ ] External OpenSubtitles, audio/subtitle tracks, size/background/sync controls tested.
- [ ] Live TV ignores Play/Pause, while radio remains independently pausable.
- [ ] 1 GB TV Box and Google TV/Fire TV tested for memory and resolution behavior.
- [ ] Official OTA remains unchanged until all gates pass.

A successful compile is necessary but is not proof of successful playback on hardware.
