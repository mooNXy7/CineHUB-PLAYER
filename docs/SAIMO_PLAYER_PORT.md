# CineHUB Player — Saimo TV port checklist

This branch is an isolated rewrite. It must not be merged or used for OTA until the acceptance checks below are satisfied.

## Reference implementation

Primary source: `gabrielsaimo/SaimoTV-Android`.

- `PlayerActivity.kt`: playback lifecycle, remote controls, resume, source selection, next episode and end-of-playback flows.
- `Playback.kt` + `Confianca.kt`: HTTP transport, DoH, TLS roots, referer/user-agent handling, HLS/DASH/progressive source creation and provider URL correction.
- `PainelEpisodios.kt`: season/episode panel.
- `Faixas.kt` + `Legendas.kt`: audio/subtitle track selection, OpenSubtitles, subtitle size/background/sync.
- `Pulos.kt`: TheIntroDB intro/recap/credits markers.
- `Aparelho.kt`: low-memory profile and resource policy.

## Non-negotiable behavior

- No Activity may call `finish()` solely because a source failed or source input was empty; show a recoverable error state with diagnostics.
- A failed source must not reset the saved resume position. Retry another source within 15 seconds.
- Keep playback UI, focus movement and every action reachable by D-pad/Enter/Back.
- Live TV must ignore Play/Pause; radio behavior is separate.
- Detect low-memory devices and cap buffers/decoder workload without imposing a high-resolution default.
- Keep CineHUB package/application identity, logo, colors and strings. Remove Saimo branding from user-facing UI.
- The player must accept both direct URL launches and CineHUB-ACERVO metadata/episode queues; the bridge contract must be explicit and tested.

## Acceptance checklist

- [ ] Port the Saimo player Activity flow and its lifecycle, rather than layering more fixes onto the old Activity.
- [ ] Wire the CineHUB launch contract (source URLs, referers/headers, content id, TMDB id, season/episode and queue index).
- [ ] Speed, persistent resume, configurable/adaptive buffer, 15-second source failover and low-memory handling.
- [ ] Audio tracks; external subtitles; OpenSubtitles lookup; subtitle size, background and synchronization.
- [ ] Season/episode panel inside the player; next-episode autoplay on credits; reuse previous source when possible.
- [ ] TheIntroDB intro/recap skipping and next-episode preview.
- [ ] “More like this” metadata panel and “still watching?” prompt.
- [ ] D-pad navigation; live TV Play/Pause suppression; Google TV/Fire TV resolution profile.
- [ ] CI unit tests and APK build pass.
- [ ] Install and test on a physical TV Box with real CineHUB-ACERVO streams; inspect logcat on any failure.

A successful build is necessary but is not proof of successful playback. Hardware playback remains an explicit release gate.
